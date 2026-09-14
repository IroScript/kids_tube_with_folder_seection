package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an event in the historical viewing log.
 * Kept conceptually separate from current resume state.
 */
@Entity(
    tableName = "watch_history",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["videoId"]),
        Index(value = ["watchedTimestamp"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val historyId: Long = 0L,
    val videoId: String,
    val watchedTimestamp: Long = System.currentTimeMillis(),
    val watchedDurationMs: Long = 0L
)
