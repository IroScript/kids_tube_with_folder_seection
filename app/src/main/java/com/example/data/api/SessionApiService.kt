package com.example.data.api

import com.example.data.local.KidsTubeDatabase
import com.example.data.local.entity.SessionUnlockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * REST / Data API Service Contract for Session Unlock Management.
 * Dispatches session unlock data and persists it in SQLite Room DB.
 */
interface SessionApiService {
    suspend fun recordSessionUnlock(sessionId: String, authMethod: String = "math_calculation"): Boolean
    suspend fun isSessionUnlocked(sessionId: String): Boolean
    suspend fun clearSession(sessionId: String): Boolean
}

class LocalSessionApiServiceImpl(private val database: KidsTubeDatabase) : SessionApiService {
    override suspend fun recordSessionUnlock(sessionId: String, authMethod: String): Boolean = withContext(Dispatchers.IO) {
        try {
            database.sessionUnlockDao().insertSessionUnlock(
                SessionUnlockEntity(
                    sessionId = sessionId,
                    isUnlocked = true,
                    unlockedAt = System.currentTimeMillis(),
                    authMethod = authMethod
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun isSessionUnlocked(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            database.sessionUnlockDao().isSessionUnlocked(sessionId) == true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun clearSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            database.sessionUnlockDao().deleteSession(sessionId)
            true
        } catch (e: Exception) {
            false
        }
    }
}
