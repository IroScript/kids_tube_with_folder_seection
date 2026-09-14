package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.PlaybackProgressEntity

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE videoId = :videoId LIMIT 1")
    suspend fun getProgress(videoId: String): PlaybackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE videoId = :videoId")
    suspend fun deleteProgress(videoId: String)

    @Query("DELETE FROM playback_progress")
    suspend fun deleteAll()
}
