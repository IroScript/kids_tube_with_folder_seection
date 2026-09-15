package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TrackedFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedFolderDao {
    @Query("SELECT * FROM tracked_folders ORDER BY displayName ASC")
    fun getAllFoldersFlow(): Flow<List<TrackedFolderEntity>>

    @Query("SELECT * FROM tracked_folders ORDER BY displayName ASC")
    suspend fun getAllFolders(): List<TrackedFolderEntity>

    @Query("SELECT * FROM tracked_folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): TrackedFolderEntity?

    @Query("SELECT * FROM tracked_folders WHERE treeUriString = :treeUri LIMIT 1")
    suspend fun getFolderByTreeUri(treeUri: String): TrackedFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(folder: TrackedFolderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(folder: TrackedFolderEntity): Long

    @Update
    suspend fun update(folder: TrackedFolderEntity)

    @Query("DELETE FROM tracked_folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tracked_folders")
    suspend fun deleteAll()

    @Query("UPDATE tracked_folders SET lastScanned = :timestamp, videoCount = :count WHERE id = :folderId")
    suspend fun updateScanStats(folderId: String, timestamp: Long, count: Int)

    @Query("UPDATE tracked_folders SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateFolderEnabled(id: String, isEnabled: Boolean)

    @Query("UPDATE tracked_folders SET isPermissionGranted = :isGranted WHERE id = :id")
    suspend fun updateFolderPermission(id: String, isGranted: Boolean)

    @Query("UPDATE tracked_folders SET isPermissionGranted = :isGranted, treeUriString = :newTreeUri WHERE id = :id")
    suspend fun updateFolderUriAndPermission(id: String, newTreeUri: String, isGranted: Boolean)

    @Query("UPDATE tracked_folders SET displayName = :displayName, lastScanned = :lastScanned, videoCount = :videoCount WHERE id = :id")
    suspend fun updateFolderMetadata(id: String, displayName: String, lastScanned: Long, videoCount: Int)

    @Query("SELECT * FROM tracked_folders WHERE isEnabled = 1 AND isPermissionGranted = 1")
    suspend fun getActivePermittedFolders(): List<TrackedFolderEntity>
}
