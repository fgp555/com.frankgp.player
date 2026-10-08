package com.frankgp.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UrlInputDialog(
    onDismiss: () -> Unit,
    onPlayStream: (url: String, title: String) -> Unit,
    onDownload: (url: String, title: String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reproducir o Descargar URL") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("URL del Video (MP4, HLS, Web)") },
                    placeholder = { Text("https://ejemplo.com/video.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Título (Opcional)") },
                    placeholder = { Text("Mi Video en Red") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(
                    onClick = {
                        val cleanUrl = urlText.trim()
                        if (cleanUrl.isNotEmpty()) {
                            onDownload(cleanUrl, titleText.trim())
                            onDismiss()
                        }
                    },
                    enabled = urlText.trim().isNotEmpty()
                ) {
                    Text("Descargar")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val cleanUrl = urlText.trim()
                        if (cleanUrl.isNotEmpty()) {
                            onPlayStream(cleanUrl, titleText.trim())
                            onDismiss()
                        }
                    },
                    enabled = urlText.trim().isNotEmpty()
                ) {
                    Text("Reproducir")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
