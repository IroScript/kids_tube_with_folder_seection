package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoItem(
    val id: String,
    val title: String,
    val uriString: String,
    val folderName: String = "All Videos",
    val youtubeId: String? = null,
    val isSample: Boolean = false,
    val durationMs: Long = 0L,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val dateAdded: Long = 0L
)
