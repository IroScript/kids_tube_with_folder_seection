package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.WatchHistoryEntity

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordHistory(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedTimestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 50): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()
}
