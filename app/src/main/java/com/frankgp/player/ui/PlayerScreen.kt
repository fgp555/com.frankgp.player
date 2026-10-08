package com.frankgp.player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.frankgp.player.model.VideoItem
import java.io.File

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val player by viewModel.player.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val currentIndex by viewModel.currentVideoIndex.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isControlsLocked by viewModel.isControlsLocked.collectAsState()

    var showUrlDialog by remember { mutableStateOf(false) }
    var selectedVideoForInfo by remember { mutableStateOf<VideoItem?>(null) }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadLocalVideos(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            permissionToRequest
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            viewModel.loadLocalVideos(context)
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, uri) ?: "Video local"
            viewModel.addCustomVideoUri(uri, fileName)
        }
    }

    if (showUrlDialog) {
        UrlInputDialog(
            onDismiss = { showUrlDialog = false },
            onPlayStream = { url, title ->
                viewModel.playOnlineUrl(url, title)
            },
            onDownload = { url, title ->
                viewModel.downloadVideoUrl(context, url, title)
            }
        )
    }

    selectedVideoForInfo?.let { videoItem ->
        VideoInfoDialog(
            item = videoItem,
            onDismiss = { selectedVideoForInfo = null }
        )
    }

    // Single Pip View when in Picture-in-Picture mode
    if (isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                    }
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                    view.useController = false
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // Fullscreen View Mode
    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // Swipe down to exit fullscreen
                        if (dragAmount > 40f) {
                            viewModel.toggleFullscreen(false)
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = !isControlsLocked
                    }
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                    view.useController = !isControlsLocked
                },
                modifier = Modifier.fillMaxSize()
            )

            // Exit Fullscreen Button
            IconButton(
                onClick = { viewModel.toggleFullscreen(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "Salir de Pantalla Completa",
                    tint = Color.White
                )
            }
        }
        return
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Player Area with Swipe Up Gesture to Fullscreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            // Swipe Up gesture -> Enter Fullscreen
                            if (dragAmount < -30f) {
                                viewModel.toggleFullscreen(true)
                            }
                        }
                    }
            ) {
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = !isControlsLocked
                            }
                        },
                        update = { view ->
                            if (view.player != player) {
                                view.player = player
                            }
                            view.useController = !isControlsLocked
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cargando reproductor...",
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Icon Bar (Actions & Controls)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scan / Refresh Button Icon
                FilledTonalIconButton(
                    onClick = {
                        val isGranted = ContextCompat.checkSelfPermission(
                            context,
                            permissionToRequest
                        ) == PackageManager.PERMISSION_GRANTED

                        if (isGranted) {
                            viewModel.loadLocalVideos(context)
                        } else {
                            permissionLauncher.launch(permissionToRequest)
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Escanear Videos"
                    )
                }

                // File Picker Icon
                FilledTonalIconButton(
                    onClick = { filePickerLauncher.launch("video/*") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Abrir Archivo"
                    )
                }

                // URL Stream / Download Icon
                FilledTonalIconButton(
                    onClick = { showUrlDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Abrir URL / Descargar"
                    )
                }

                // PiP Mode Icon
                FilledTonalIconButton(
                    onClick = onEnterPip,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Modo PiP"
                    )
                }

                // Fullscreen Icon
                FilledTonalIconButton(
                    onClick = { viewModel.toggleFullscreen(true) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Pantalla Completa"
                    )
                }

                // Play / Pause Toggle Icon
                FilledTonalIconButton(
                    onClick = {
                        player?.let { p ->
                            if (p.isPlaying) {
                                p.pause()
                            } else {
                                p.play()
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                    )
                }

                // Lock / Unlock Controls Toggle Icon
                FilledTonalIconButton(
                    onClick = { viewModel.toggleControlsLock() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isControlsLocked) "Desbloquear Controles" else "Bloquear Controles",
                        tint = if (isControlsLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Playlist Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lista de Reproducción (${playlist.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playlist Items
            if (playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay videos. Refresca carpetas, abre un archivo o pega una URL con el icono 🔗.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    itemsIndexed(playlist) { index, item ->
                        PlaylistItemCard(
                            item = item,
                            isSelected = index == currentIndex,
                            onClick = { viewModel.playVideoAtIndex(index) },
                            onInfoClick = { selectedVideoForInfo = item }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistItemCard(
    item: VideoItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail Preview Image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 80.dp, height = 50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val folderName = getParentFolderName(item.path, item.uri)
                val formattedSize = formatFileSize(item.size)
                val formattedDuration = formatDuration(item.duration)

                Text(
                    text = "📁 $folderName  •  ⏱ $formattedDuration  •  💾 $formattedSize",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isSelected) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reproduciendo ahora",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Detalles del Video",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getParentFolderName(path: String, uri: Uri): String {
    if (path.isNotEmpty()) {
        val file = File(path)
        val parentName = file.parentFile?.name
        if (!parentName.isNullOrEmpty()) return parentName
    }
    val uriString = uri.toString()
    return when {
        uriString.contains("Download", ignoreCase = true) -> "Download"
        uriString.contains("Movies", ignoreCase = true) -> "Movies"
        uriString.contains("DCIM", ignoreCase = true) -> "DCIM"
        uriString.startsWith("http", ignoreCase = true) -> "Red / Stream"
        else -> "Almacenamiento"
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    return fileName
}
