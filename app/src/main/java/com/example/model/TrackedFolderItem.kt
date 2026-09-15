package com.example.model

import androidx.compose.runtime.Immutable

/**
 * Domain representation of a tracked Storage Access Framework (SAF) folder.
 * Keeps UI components decoupled from Room entity implementation details.
 */
@Immutable
data class TrackedFolderItem(
    val id: String,
    val treeUriString: String,
    val displayName: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastScanned: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val isPermissionGranted: Boolean = true,
    val videoCount: Int = 0
)
