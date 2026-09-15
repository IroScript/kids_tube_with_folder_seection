package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a persistent Storage Access Framework (SAF) folder selected by the parent.
 * The treeUriString retains persistable URI permissions across device reboots.
 */
@Entity(
    tableName = "tracked_folders",
    indices = [
        Index(value = ["treeUriString"], unique = true)
    ]
)
data class TrackedFolderEntity(
    @PrimaryKey
    val id: String,
    val treeUriString: String,
    val displayName: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastScanned: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val isPermissionGranted: Boolean = true,
    val videoCount: Int = 0
)
