package com.example.model

/**
 * Constants governing playback persistence, throttle intervals, and resume calculation.
 */
object PlaybackConstants {
    // If playback position is within first 3 seconds, start from beginning (0 ms)
    const val MIN_RESUME_POSITION_MS = 3_000L

    // If playback position is within 5 seconds of the end or >= 95% of total duration, treat as completed
    const val COMPLETION_THRESHOLD_OFFSET_MS = 5_000L
    const val COMPLETION_PERCENTAGE = 0.95f

    // Throttled interval for Room DB writes during active playback
    const val SAVE_PROGRESS_INTERVAL_MS = 2_000L
}

/**
 * Deterministic resume position calculation based on current position, duration, and completion status.
 * Reopening a completed video starts from 0L.
 * Videos with duration <= 0 or position < 5s start from 0L.
 * Videos within 5s of end or >= 95% duration start from 0L.
 */
fun calculateResumePosition(positionMs: Long, durationMs: Long, isCompleted: Boolean): Long {
    if (isCompleted) return 0L
    if (durationMs <= 0L) return 0L
    if (positionMs < PlaybackConstants.MIN_RESUME_POSITION_MS) return 0L

    val isNearEnd = positionMs >= (durationMs - PlaybackConstants.COMPLETION_THRESHOLD_OFFSET_MS) ||
            (durationMs > 0 && (positionMs.toFloat() / durationMs) >= PlaybackConstants.COMPLETION_PERCENTAGE)
    if (isNearEnd) {
        return 0L
    }

    return positionMs
}

/**
 * Checks whether a video has reached the completion threshold.
 */
fun isPlaybackCompleted(positionMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L) return false
    return positionMs >= (durationMs - PlaybackConstants.COMPLETION_THRESHOLD_OFFSET_MS) ||
            ((positionMs.toFloat() / durationMs) >= PlaybackConstants.COMPLETION_PERCENTAGE)
}

/**
 * Represents protected parental actions that require calculation gate authentication.
 */
sealed interface ParentProtectedAction {
    data object OpenFolderManager : ParentProtectedAction
    data object OpenParentDashboard : ParentProtectedAction
}
