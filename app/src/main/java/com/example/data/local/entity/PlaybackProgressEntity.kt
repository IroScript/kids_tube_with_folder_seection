package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents the current playback position and completion status for a video.
 * Kept conceptually separate from historical viewing logs.
 */
@Entity(
    tableName = "playback_progress",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["videoId"])
    ]
)
data class PlaybackProgressEntity(
    @PrimaryKey
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)
