package com.frankgp.player.model

import android.net.Uri

data class VideoItem(
    val id: String,
    val title: String,
    val uri: Uri,
    val duration: Long = 0L,
    val size: Long = 0L,
    val path: String = ""
)
