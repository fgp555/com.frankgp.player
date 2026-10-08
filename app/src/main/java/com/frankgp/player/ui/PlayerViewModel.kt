package com.frankgp.player.ui

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frankgp.player.model.VideoItem
import com.frankgp.player.service.PlaybackService
import com.frankgp.player.util.DownloadHelper
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel : ViewModel() {

    private val _playlist = MutableStateFlow<List<VideoItem>>(emptyList())
    val playlist: StateFlow<List<VideoItem>> = _playlist.asStateFlow()

    private val _currentVideoIndex = MutableStateFlow(0)
    val currentVideoIndex: StateFlow<Int> = _currentVideoIndex.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isControlsLocked = MutableStateFlow(false)
    val isControlsLocked: StateFlow<Boolean> = _isControlsLocked.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun initializePlayer(context: Context) {
        if (_player.value != null) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    _player.value = mediaController
                    setupPlayerListener(mediaController)
                    if (_playlist.value.isNotEmpty()) {
                        updateMediaItemsInPlayer(_playlist.value)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupPlayerListener(player: Player) {
        _isPlaying.value = player.isPlaying
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentMediaItemIndex = player.currentMediaItemIndex
                if (currentMediaItemIndex in _playlist.value.indices) {
                    _currentVideoIndex.value = currentMediaItemIndex
                }
                _isPlaying.value = player.isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isPlaying.value = player.isPlaying
            }
        })
    }

    fun toggleFullscreen(enabled: Boolean? = null) {
        _isFullscreen.value = enabled ?: !_isFullscreen.value
    }

    fun toggleControlsLock(locked: Boolean? = null) {
        _isControlsLocked.value = locked ?: !_isControlsLocked.value
    }

    fun loadLocalVideos(context: Context) {
        viewModelScope.launch {
            val videoMap = LinkedHashMap<String, VideoItem>()
            val primaryVideos = mutableListOf<VideoItem>()
            val otherVideos = mutableListOf<VideoItem>()

            val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".ts")

            // 1. Direct File System Scanning in Download, Movies, DCIM folders
            withContext(Dispatchers.IO) {
                val publicFolders = listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                )

                val pathsToScan = mutableListOf<String>()

                for (folder in publicFolders) {
                    if (folder.exists() && folder.isDirectory) {
                        folder.listFiles()?.forEach { file ->
                            if (file.isFile && videoExtensions.any { ext -> file.name.endsWith(ext, ignoreCase = true) }) {
                                var fileDuration = 0L
                                try {
                                    val retriever = MediaMetadataRetriever()
                                    retriever.setDataSource(file.absolutePath)
                                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    fileDuration = durationStr?.toLongOrNull() ?: 0L
                                    retriever.release()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                val item = VideoItem(
                                    id = file.absolutePath,
                                    title = file.name,
                                    uri = Uri.fromFile(file),
                                    duration = fileDuration,
                                    size = file.length(),
                                    path = file.absolutePath
                                )
                                if (!videoMap.containsKey(item.id)) {
                                    videoMap[item.id] = item
                                    primaryVideos.add(item)
                                    pathsToScan.add(file.absolutePath)
                                }
                            }
                        }
                    }
                }

                // Trigger MediaScanner for newly discovered files
                if (pathsToScan.isNotEmpty()) {
                    try {
                        MediaScannerConnection.scanFile(
                            context.applicationContext,
                            pathsToScan.toTypedArray(),
                            null,
                            null
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 2. Query MediaStore Database
                val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.DURATION,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.RELATIVE_PATH,
                        MediaStore.Files.FileColumns.DATA
                    )
                } else {
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.DURATION,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.DATA
                    )
                }

                val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO} " +
                        "OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%' " +
                        "OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.mp4' " +
                        "OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.mkv' " +
                        "OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.webm' " +
                        "OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.avi' " +
                        "OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.mov'"

                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val durationCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Video sin título"
                        val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                        val fileSize = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val dataPath = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                        val relPath = if (relPathCol != -1) cursor.getString(relPathCol) ?: "" else ""

                        val contentUri: Uri = ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri("external"),
                            id
                        )

                        val item = VideoItem(
                            id = id.toString(),
                            title = name,
                            uri = contentUri,
                            duration = duration,
                            size = fileSize,
                            path = dataPath.ifEmpty { relPath }
                        )

                        val isDuplicate = videoMap.values.any { existing ->
                            existing.title.equals(name, ignoreCase = true) || existing.id == dataPath
                        }

                        if (!isDuplicate) {
                            videoMap[item.id] = item
                            val isPrimaryFolder = relPath.contains("Download", ignoreCase = true) ||
                                    relPath.contains("Movies", ignoreCase = true) ||
                                    dataPath.contains("Download", ignoreCase = true) ||
                                    dataPath.contains("Movies", ignoreCase = true)

                            if (isPrimaryFolder) {
                                primaryVideos.add(item)
                            } else {
                                otherVideos.add(item)
                            }
                        }
                    }
                }
            }

            val combined = primaryVideos + otherVideos
            _playlist.value = combined
            if (combined.isNotEmpty()) {
                updateMediaItemsInPlayer(combined)
            }
        }
    }

    fun addCustomVideoUri(uri: Uri, title: String) {
        val newItem = VideoItem(
            id = System.currentTimeMillis().toString(),
            title = title,
            uri = uri
        )
        val updated = _playlist.value + newItem
        _playlist.value = updated
        updateMediaItemsInPlayer(updated)
    }

    fun playOnlineUrl(url: String, title: String) {
        val displayTitle = title.ifEmpty { "Stream: ${Uri.parse(url).lastPathSegment ?: url}" }
        val newItem = VideoItem(
            id = System.currentTimeMillis().toString(),
            title = displayTitle,
            uri = Uri.parse(url)
        )
        val updated = _playlist.value + newItem
        _playlist.value = updated
        updateMediaItemsInPlayer(updated)
        playVideoAtIndex(updated.size - 1)
    }

    fun downloadVideoUrl(context: Context, url: String, title: String) {
        val displayTitle = title.ifEmpty { Uri.parse(url).lastPathSegment ?: "Video_URL" }
        DownloadHelper.downloadVideo(context, url, displayTitle)
    }

    private fun buildMediaItem(video: VideoItem): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(video.title)
            .setDisplayTitle(video.title)
            .setArtworkUri(video.uri)
            .build()

        return MediaItem.Builder()
            .setUri(video.uri)
            .setMediaId(video.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun updateMediaItemsInPlayer(list: List<VideoItem>) {
        val p = _player.value ?: return
        p.clearMediaItems()
        val mediaItems = list.map { buildMediaItem(it) }
        p.setMediaItems(mediaItems)
        p.prepare()
    }

    fun playVideoAtIndex(index: Int) {
        val p = _player.value ?: return
        if (index in _playlist.value.indices) {
            _currentVideoIndex.value = index
            if (p.mediaItemCount != _playlist.value.size) {
                updateMediaItemsInPlayer(_playlist.value)
            }
            p.seekTo(index, 0L)
            p.playWhenReady = true
            _isPlaying.value = true
        }
    }

    fun playNext() {
        val p = _player.value ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
        }
    }

    fun playPrevious() {
        val p = _player.value ?: return
        if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
        }
    }

    override fun onCleared() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        _player.value = null
        super.onCleared()
    }
}
