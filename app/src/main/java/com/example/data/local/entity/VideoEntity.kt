package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a video file indexed from a user-selected local folder.
 * Maintains a cascade foreign-key relationship to TrackedFolderEntity.
 */
@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = TrackedFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uriString"], unique = true),
        Index(value = ["folderId"])
    ]
)
data class VideoEntity(
    @PrimaryKey
    val id: String,
    val folderId: String,
    val uriString: String,
    val fileName: String,
    val displayTitle: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val lastModified: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val youtubeId: String? = null,
    val isSample: Boolean = false
)
