package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_unlock")
data class SessionUnlockEntity(
    @PrimaryKey
    val sessionId: String,
    val isUnlocked: Boolean = true,
    val unlockedAt: Long = System.currentTimeMillis(),
    val authMethod: String = "math_calculation"
)
