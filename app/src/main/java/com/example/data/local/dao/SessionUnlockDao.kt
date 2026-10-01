package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SessionUnlockEntity

@Dao
interface SessionUnlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionUnlock(session: SessionUnlockEntity)

    @Query("SELECT * FROM session_unlock WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionUnlock(sessionId: String): SessionUnlockEntity?

    @Query("SELECT isUnlocked FROM session_unlock WHERE sessionId = :sessionId LIMIT 1")
    suspend fun isSessionUnlocked(sessionId: String): Boolean?

    @Query("DELETE FROM session_unlock WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM session_unlock")
    suspend fun clearAllSessions()
}
