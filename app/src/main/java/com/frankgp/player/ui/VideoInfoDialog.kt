package com.frankgp.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frankgp.player.model.VideoItem
import java.util.Locale

@Composable
fun VideoInfoDialog(
    item: VideoItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detalles del Video") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoRow(label = "Nombre:", value = item.title)
                Spacer(modifier = Modifier.height(8.dp))

                InfoRow(
                    label = "Ubicación / Ruta:",
                    value = if (item.path.isNotEmpty()) item.path else item.uri.toString()
                )
                Spacer(modifier = Modifier.height(8.dp))

                InfoRow(label = "Tamaño de Archivo:", value = formatFileSize(item.size))
                Spacer(modifier = Modifier.height(8.dp))

                InfoRow(label = "Duración:", value = formatDuration(item.duration))
                Spacer(modifier = Modifier.height(8.dp))

                InfoRow(label = "URI:", value = item.uri.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "No disponible"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB (%,d bytes)", gb, bytes)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB (%,d bytes)", mb, bytes)
        else -> String.format(Locale.US, "%.1f KB (%,d bytes)", kb, bytes)
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "Desconocida"
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
