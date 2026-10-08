package com.frankgp.player.util

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object DownloadHelper {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun downloadVideo(context: Context, videoUrl: String, title: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var finalUrl = videoUrl.trim()

                // Check if URL is HTML page or direct video via OkHttp
                val requestCheck = Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "*/*")
                    .build()

                client.newCall(requestCheck).execute().use { response ->
                    if (response.isSuccessful) {
                        finalUrl = response.request.url.toString()
                        val contentType = response.header("Content-Type", "") ?: ""

                        if (contentType.contains("text/html", ignoreCase = true) &&
                            (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be") || videoUrl.contains("tiktok.com"))
                        ) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Las URLs de páginas web de YouTube/TikTok requieren el enlace directo de video .mp4",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@launch
                        }
                    }
                }

                val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                val sanitizedTitle = title.trim()
                    .ifEmpty { "Video_${System.currentTimeMillis()}" }
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")

                val fileName = if (sanitizedTitle.endsWith(".mp4", ignoreCase = true) ||
                    sanitizedTitle.endsWith(".mkv", ignoreCase = true) ||
                    sanitizedTitle.endsWith(".webm", ignoreCase = true)
                ) {
                    sanitizedTitle
                } else {
                    "$sanitizedTitle.mp4"
                }

                val downloadRequest = DownloadManager.Request(Uri.parse(finalUrl)).apply {
                    setTitle(title.ifEmpty { fileName })
                    setDescription("Descargando video para reproducción offline...")
                    addRequestHeader("User-Agent", BROWSER_USER_AGENT)
                    addRequestHeader("Accept", "*/*")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }

                downloadManager.enqueue(downloadRequest)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Descarga iniciada: $fileName", Toast.LENGTH_SHORT).show()
                }

                val downloadedFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(downloadedFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error al descargar: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
