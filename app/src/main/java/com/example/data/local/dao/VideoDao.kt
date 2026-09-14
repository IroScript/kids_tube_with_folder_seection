package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

data class VideoWithDetails(
    val id: String,
    val folderId: String,
    val uriString: String,
    val fileName: String,
    val displayTitle: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val lastModified: Long,
    val dateAdded: Long,
    val youtubeId: String?,
    val isSample: Boolean,
    val folderDisplayName: String?,
    val playbackPositionMs: Long?,
    val isCompleted: Boolean?
)

@Dao
interface VideoDao {
    @Query("""
        SELECT 
            v.id, v.folderId, v.uriString, v.fileName, v.displayTitle,
            v.durationMs, v.sizeBytes, v.mimeType, v.lastModified, v.dateAdded,
            v.youtubeId, v.isSample,
            f.displayName AS folderDisplayName,
            p.positionMs AS playbackPositionMs,
            p.isCompleted AS isCompleted
        FROM videos v
        LEFT JOIN tracked_folders f ON v.folderId = f.id
        LEFT JOIN playback_progress p ON v.id = p.videoId
        ORDER BY COALESCE(f.displayName, 'All Videos') ASC, v.displayTitle ASC
    """)
    suspend fun getAllVideosWithDetails(): List<VideoWithDetails>

    @Query("""
        SELECT 
            v.id, v.folderId, v.uriString, v.fileName, v.displayTitle,
            v.durationMs, v.sizeBytes, v.mimeType, v.lastModified, v.dateAdded,
            v.youtubeId, v.isSample,
            f.displayName AS folderDisplayName,
            p.positionMs AS playbackPositionMs,
            p.isCompleted AS isCompleted
        FROM videos v
        LEFT JOIN tracked_folders f ON v.folderId = f.id
        LEFT JOIN playback_progress p ON v.id = p.videoId
        ORDER BY COALESCE(f.displayName, 'All Videos') ASC, v.displayTitle ASC
    """)
    fun getAllVideosWithDetailsFlow(): Flow<List<VideoWithDetails>>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE uriString = :uriString LIMIT 1")
    suspend fun getVideoByUri(uriString: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE folderId = :folderId")
    suspend fun getVideosByFolderId(folderId: String): List<VideoEntity>

    /**
     * Identity reconciliation candidate search:
     * Used only when URI changes, matching on identical size and close lastModified window (within ±3000ms).
     */
    @Query("""
        SELECT * FROM videos 
        WHERE folderId = :folderId 
          AND sizeBytes = :sizeBytes 
          AND lastModified BETWEEN :minModified AND :maxModified
    """)
    suspend fun findReconciliationCandidates(
        folderId: String,
        sizeBytes: Long,
        minModified: Long,
        maxModified: Long
    ): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(videos: List<VideoEntity>)

    @Query("UPDATE videos SET uriString = :newUri, fileName = :newName, displayTitle = :newTitle, lastModified = :newModified WHERE id = :id")
    suspend fun updateReconciledFile(id: String, newUri: String, newName: String, newTitle: String, newModified: Long)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM videos WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("DELETE FROM videos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM videos")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun getVideoCount(): Int
}
