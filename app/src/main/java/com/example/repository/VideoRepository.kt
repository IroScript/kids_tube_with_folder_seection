package com.example.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.KidsTubeDatabase
import com.example.data.local.entity.PlaybackProgressEntity
import com.example.data.local.entity.TrackedFolderEntity
import com.example.data.local.entity.VideoEntity
import com.example.data.local.entity.WatchHistoryEntity
import com.example.model.VideoItem
import com.example.util.YoutubeIdHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class VideoRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kids_tube_prefs", Context.MODE_PRIVATE)

    private val database: KidsTubeDatabase = KidsTubeDatabase.getInstance(context)
    private val trackedFolderDao = database.trackedFolderDao()
    private val videoDao = database.videoDao()
    private val playbackProgressDao = database.playbackProgressDao()
    private val watchHistoryDao = database.watchHistoryDao()

    companion object {
        private const val KEY_VIDEOS = "kids_videos_json"
        private const val KEY_FIRST_RUN = "kids_first_run_initialized"
        private const val KEY_ROOM_MIGRATED = "kids_room_migrated_v1"
        private const val KEY_VIDEOS_BACKUP = "kids_videos_json_backup"

        val SAMPLE_VIDEOS = listOf(
            VideoItem(
                id = "sample_1",
                title = "Big Buck Bunny - Forest Friends 🐰",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                folderName = "Kids Adventures",
                youtubeId = "aqz-KE-bpKQ",
                isSample = true,
                durationMs = 596000L
            ),
            VideoItem(
                id = "sample_2",
                title = "Elephant Dream - Fun Science 🐘",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                folderName = "Kids Adventures",
                youtubeId = "TLkA0RELQ1g",
                isSample = true,
                durationMs = 653000L
            ),
            VideoItem(
                id = "sample_3",
                title = "For Bigger Blazes - Animated Cars 🚗",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                folderName = "Fun Cartoons",
                youtubeId = "b9kgVz-eK5U",
                isSample = true,
                durationMs = 15000L
            ),
            VideoItem(
                id = "sample_4",
                title = "For Bigger Escapes - Nature Wonder 🌲",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                folderName = "Animals & Nature",
                youtubeId = "yA7CE35c5c0",
                isSample = true,
                durationMs = 15000L
            ),
            VideoItem(
                id = "sample_5",
                title = "For Bigger Joyrides - Roller Coaster 🎡",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                folderName = "Fun Cartoons",
                youtubeId = "60e-y2x0x5U",
                isSample = true,
                durationMs = 15000L
            ),
            VideoItem(
                id = "sample_6",
                title = "Sintel - The Dragon Quest 🐲",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                folderName = "Kids Adventures",
                youtubeId = "eRsGyueVLvQ",
                isSample = true,
                durationMs = 888000L
            ),
            VideoItem(
                id = "sample_7",
                title = "Tears of Steel - Robot Galaxy 🤖",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                folderName = "Kids Adventures",
                youtubeId = "R6MlUcmOul8",
                isSample = true,
                durationMs = 734000L
            )
        )
    }

    /**
     * Safe Migration Flow:
     * SharedPreferences JSON -> Parse -> Room Transaction -> Verify -> Marker -> Retain Backup
     * Does NOT delete original KEY_VIDEOS in Phase A.
     */
    private suspend fun ensureRoomMigrated() = withContext(Dispatchers.IO) {
        if (prefs.getBoolean(KEY_ROOM_MIGRATED, false)) {
            return@withContext
        }

        val jsonString = prefs.getString(KEY_VIDEOS, null)
        if (jsonString.isNullOrBlank()) {
            prefs.edit().putBoolean(KEY_ROOM_MIGRATED, true).apply()
            return@withContext
        }

        try {
            val array = JSONArray(jsonString)
            val migratedItems = mutableListOf<VideoItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                migratedItems.add(
                    VideoItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        uriString = obj.getString("uriString"),
                        folderName = obj.optString("folderName", "All Videos"),
                        youtubeId = if (obj.has("youtubeId")) obj.getString("youtubeId") else null,
                        isSample = obj.optBoolean("isSample", false),
                        durationMs = obj.optLong("durationMs", 0L),
                        mimeType = if (obj.has("mimeType")) obj.getString("mimeType") else null,
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        dateAdded = obj.optLong("dateAdded", System.currentTimeMillis()),
                        playbackPositionMs = 0L,
                        isCompleted = false
                    )
                )
            }

            if (migratedItems.isNotEmpty()) {
                // Register TrackedFolder entities to satisfy foreign key constraints
                val folderNames = migratedItems.map { it.folderName.ifBlank { "All Videos" } }.distinct()
                val folderMap = mutableMapOf<String, String>()

                for (fName in folderNames) {
                    val folderId = "folder_" + Math.abs(fName.hashCode())
                    val existing = trackedFolderDao.getFolderById(folderId)
                    if (existing == null) {
                        val treeUri = "migrated://folder/" + Math.abs(fName.hashCode())
                        val folderEntity = TrackedFolderEntity(
                            id = folderId,
                            treeUriString = treeUri,
                            displayName = fName,
                            dateAdded = System.currentTimeMillis(),
                            lastScanned = System.currentTimeMillis(),
                            isEnabled = true,
                            videoCount = migratedItems.count { it.folderName == fName }
                        )
                        trackedFolderDao.insertOrUpdate(folderEntity)
                    }
                    folderMap[fName] = folderId
                }

                val entities = migratedItems.map { item ->
                    val fId = folderMap[item.folderName.ifBlank { "All Videos" }] ?: "folder_default"
                    VideoEntity(
                        id = item.id,
                        folderId = fId,
                        uriString = item.uriString,
                        fileName = item.title,
                        displayTitle = item.title,
                        durationMs = item.durationMs,
                        sizeBytes = item.sizeBytes,
                        mimeType = item.mimeType,
                        lastModified = item.dateAdded,
                        dateAdded = item.dateAdded,
                        youtubeId = item.youtubeId,
                        isSample = item.isSample
                    )
                }

                videoDao.insertOrUpdateAll(entities)

                val count = videoDao.getVideoCount()
                if (count >= migratedItems.size) {
                    prefs.edit()
                        .putString(KEY_VIDEOS_BACKUP, jsonString)
                        .putBoolean(KEY_ROOM_MIGRATED, true)
                        .apply()
                    Log.d("VideoRepository", "Successfully migrated ${migratedItems.size} videos from SharedPreferences to Room DB.")
                }
            } else {
                prefs.edit().putBoolean(KEY_ROOM_MIGRATED, true).apply()
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Migration to Room failed; retaining SharedPreferences: ${e.message}", e)
        }
    }

    /**
     * Primary Caller-Facing API: Returns indexed videos with folder & playback details from Room DB.
     * Guaranteed zero automatic whole-device scans and zero remote sample injection on fresh launch.
     */
    suspend fun getSavedVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        ensureRoomMigrated()

        val detailsList = videoDao.getAllVideosWithDetails()
        if (detailsList.isNotEmpty()) {
            val mapped = detailsList.map { details ->
                VideoItem(
                    id = details.id,
                    title = details.displayTitle.ifBlank { details.fileName },
                    uriString = details.uriString,
                    folderName = details.folderDisplayName ?: "All Videos",
                    youtubeId = details.youtubeId,
                    isSample = details.isSample,
                    durationMs = details.durationMs,
                    mimeType = details.mimeType,
                    sizeBytes = details.sizeBytes,
                    dateAdded = details.dateAdded,
                    playbackPositionMs = details.playbackPositionMs ?: 0L,
                    isCompleted = details.isCompleted ?: false
                )
            }
            return@withContext sortVideosDeterministically(mapped)
        }

        // Check fallback if SharedPreferences has unmigrated items
        val jsonString = prefs.getString(KEY_VIDEOS, null)
        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<VideoItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        VideoItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            uriString = obj.getString("uriString"),
                            folderName = obj.optString("folderName", "All Videos"),
                            youtubeId = if (obj.has("youtubeId")) obj.getString("youtubeId") else null,
                            isSample = obj.optBoolean("isSample", false),
                            durationMs = obj.optLong("durationMs", 0L),
                            mimeType = if (obj.has("mimeType")) obj.getString("mimeType") else null,
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            dateAdded = obj.optLong("dateAdded", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    saveVideos(list)
                    return@withContext sortVideosDeterministically(list)
                }
            } catch (ignored: Exception) {}
        }

        // When empty on fresh install, return empty list (No automatic device scan, no remote HTTP videos)
        emptyList()
    }

    /**
     * Saves videos into Room Database while retaining SharedPreferences backup.
     */
    suspend fun saveVideos(videos: List<VideoItem>) = withContext(Dispatchers.IO) {
        val sorted = sortVideosDeterministically(videos)

        // 1. SharedPreferences backup
        try {
            val array = JSONArray()
            for (item in sorted) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("title", item.title)
                obj.put("uriString", item.uriString)
                obj.put("folderName", item.folderName)
                item.youtubeId?.let { obj.put("youtubeId", it) }
                obj.put("isSample", item.isSample)
                obj.put("durationMs", item.durationMs)
                item.mimeType?.let { obj.put("mimeType", it) }
                obj.put("sizeBytes", item.sizeBytes)
                obj.put("dateAdded", item.dateAdded)
                array.put(obj)
            }
            prefs.edit().putString(KEY_VIDEOS, array.toString()).apply()
        } catch (ignored: Exception) {}

        // 2. Room Database persistence
        if (videos.isEmpty()) {
            videoDao.deleteAll()
            return@withContext
        }

        try {
            val folders = sorted.map { it.folderName.ifBlank { "All Videos" } }.distinct()
            val folderMap = mutableMapOf<String, String>()
            for (fName in folders) {
                val folderId = "folder_" + Math.abs(fName.hashCode())
                val existing = trackedFolderDao.getFolderById(folderId)
                if (existing == null) {
                    trackedFolderDao.insertOrUpdate(
                        TrackedFolderEntity(
                            id = folderId,
                            treeUriString = "migrated://folder/" + Math.abs(fName.hashCode()),
                            displayName = fName,
                            dateAdded = System.currentTimeMillis(),
                            lastScanned = System.currentTimeMillis(),
                            isEnabled = true,
                            videoCount = sorted.count { it.folderName == fName }
                        )
                    )
                }
                folderMap[fName] = folderId
            }

            val entities = sorted.map { item ->
                val fId = folderMap[item.folderName.ifBlank { "All Videos" }] ?: "folder_default"
                VideoEntity(
                    id = item.id,
                    folderId = fId,
                    uriString = item.uriString,
                    fileName = item.title,
                    displayTitle = item.title,
                    durationMs = item.durationMs,
                    sizeBytes = item.sizeBytes,
                    mimeType = item.mimeType,
                    lastModified = item.dateAdded,
                    dateAdded = item.dateAdded,
                    youtubeId = item.youtubeId,
                    isSample = item.isSample
                )
            }
            videoDao.insertOrUpdateAll(entities)
        } catch (e: Exception) {
            Log.e("VideoRepository", "Failed to save videos to Room: ${e.message}", e)
        }
    }

    /**
     * Scans files from DocumentTree URI (Storage Access Framework) recursively across all subfolders.
     * Persists TrackedFolderEntity and verifies persistable URI permissions.
     * Uses safe identity matching without full-file hashing.
     */
    suspend fun scanDocumentTree(treeUri: Uri): List<VideoItem> = withContext(Dispatchers.IO) {
        val contentResolver: ContentResolver = context.contentResolver

        // 1. Request and verify persistable URI permission
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w("VideoRepository", "takePersistableUriPermission failed for $treeUri: ${e.message}")
        }

        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            Log.e("VideoRepository", "Invalid tree URI: $treeUri", e)
            return@withContext emptyList()
        }

        val rootFolderName = getDocumentDisplayName(contentResolver, treeUri) ?: "Folder"

        // 2. Prevent duplicate folder registration
        val existingFolder = trackedFolderDao.getFolderByTreeUri(treeUri.toString())
        val folderId = if (existingFolder != null) {
            existingFolder.id
        } else {
            val newId = "folder_" + UUID.randomUUID().toString()
            val newFolder = TrackedFolderEntity(
                id = newId,
                treeUriString = treeUri.toString(),
                displayName = rootFolderName,
                dateAdded = System.currentTimeMillis(),
                lastScanned = System.currentTimeMillis(),
                isEnabled = true,
                videoCount = 0
            )
            trackedFolderDao.insertOrUpdate(newFolder)
            newId
        }

        // 3. Scan directory tree recursively
        val discoveredEntities = mutableListOf<VideoEntity>()

        suspend fun scanDirectory(docId: String, currentFolderPath: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor: Cursor? = try {
                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null,
                    null,
                    null
                )
            } catch (e: Exception) {
                Log.w("VideoRepository", "Directory query failed for docId $docId: ${e.message}")
                null
            }

            cursor?.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (it.moveToNext()) {
                    val childId = it.getString(idIndex)
                    val displayName = it.getString(nameIndex) ?: continue
                    val mimeType = it.getString(mimeIndex)
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    val modified = if (modIndex >= 0) it.getLong(modIndex) else 0L

                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
                            mimeType == "vnd.android.document/directory"

                    if (isDir) {
                        val nextPath = if (currentFolderPath.isEmpty()) displayName else "$currentFolderPath / $displayName"
                        scanDirectory(childId, nextPath)
                    } else {
                        val isMedia = mimeType?.startsWith("video/") == true ||
                                isPotentialMediaFile(displayName, mimeType)

                        if (isMedia) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val uriStr = fileUri.toString()
                            val clean = cleanTitle(displayName)
                            val ytId = YoutubeIdHelper.extractYoutubeId(displayName)

                            // Identity Reconciliation:
                            // 1. Exact URI Match
                            val existingByUri = videoDao.getVideoByUri(uriStr)
                            if (existingByUri != null) {
                                discoveredEntities.add(
                                    existingByUri.copy(
                                        fileName = displayName,
                                        displayTitle = clean,
                                        sizeBytes = size,
                                        lastModified = modified,
                                        youtubeId = ytId
                                    )
                                )
                            } else {
                                // 2. If URI changes, check candidate match ONLY if uniquely unambiguous
                                val candidates = videoDao.findReconciliationCandidates(
                                    folderId = folderId,
                                    sizeBytes = size,
                                    minModified = modified - 3000L,
                                    maxModified = modified + 3000L
                                )
                                if (candidates.size == 1) {
                                    val matched = candidates.first()
                                    discoveredEntities.add(
                                        matched.copy(
                                            uriString = uriStr,
                                            fileName = displayName,
                                            displayTitle = clean,
                                            lastModified = modified,
                                            youtubeId = ytId
                                        )
                                    )
                                } else {
                                    // Multiple or no candidates: create new record (prevents false positive merge)
                                    val stableId = "vid_" + Math.abs(uriStr.hashCode()) + "_" + (System.currentTimeMillis() % 10000)
                                    discoveredEntities.add(
                                        VideoEntity(
                                            id = stableId,
                                            folderId = folderId,
                                            uriString = uriStr,
                                            fileName = displayName,
                                            displayTitle = clean,
                                            durationMs = 0L,
                                            sizeBytes = size,
                                            mimeType = mimeType,
                                            lastModified = modified,
                                            dateAdded = System.currentTimeMillis(),
                                            youtubeId = ytId,
                                            isSample = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        scanDirectory(rootDocId, "")

        // 4. Batch persist to Room Database
        if (discoveredEntities.isNotEmpty()) {
            videoDao.insertOrUpdateAll(discoveredEntities)
            trackedFolderDao.updateScanStats(folderId, System.currentTimeMillis(), discoveredEntities.size)
        }

        // 5. Query and return all current videos with details
        val allDetails = videoDao.getAllVideosWithDetails()
        val mapped = allDetails.map { d ->
            VideoItem(
                id = d.id,
                title = d.displayTitle.ifBlank { d.fileName },
                uriString = d.uriString,
                folderName = d.folderDisplayName ?: rootFolderName,
                youtubeId = d.youtubeId,
                isSample = d.isSample,
                durationMs = d.durationMs,
                mimeType = d.mimeType,
                sizeBytes = d.sizeBytes,
                dateAdded = d.dateAdded,
                playbackPositionMs = d.playbackPositionMs ?: 0L,
                isCompleted = d.isCompleted ?: false
            )
        }

        sortVideosDeterministically(mapped)
    }

    suspend fun createVideoFromUri(uri: Uri, context: Context): VideoItem = withContext(Dispatchers.IO) {
        var displayName = "Video ${System.currentTimeMillis() % 1000}"
        var sizeBytes = 0L
        var mimeType: String? = null
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}

            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        displayName = it.getString(nameIdx)
                    }
                    val sizeIdx = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (sizeIdx >= 0) {
                        sizeBytes = it.getLong(sizeIdx)
                    }
                    val mimeIdx = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    if (mimeIdx >= 0) {
                        mimeType = it.getString(mimeIdx)
                    }
                }
            }
        } catch (ignored: Exception) {}

        val importedFolderId = "folder_imported"
        if (trackedFolderDao.getFolderById(importedFolderId) == null) {
            trackedFolderDao.insertOrUpdate(
                TrackedFolderEntity(
                    id = importedFolderId,
                    treeUriString = "content://kids_tube/imported",
                    displayName = "Imported",
                    dateAdded = System.currentTimeMillis(),
                    lastScanned = System.currentTimeMillis(),
                    isEnabled = true,
                    videoCount = 1
                )
            )
        }

        val videoEntity = VideoEntity(
            id = UUID.randomUUID().toString(),
            folderId = importedFolderId,
            uriString = uri.toString(),
            fileName = displayName,
            displayTitle = cleanTitle(displayName),
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            dateAdded = System.currentTimeMillis()
        )
        videoDao.insertOrUpdate(videoEntity)

        VideoItem(
            id = videoEntity.id,
            title = videoEntity.displayTitle,
            uriString = videoEntity.uriString,
            folderName = "Imported",
            youtubeId = YoutubeIdHelper.extractYoutubeId(displayName),
            isSample = false,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            dateAdded = videoEntity.dateAdded
        )
    }

    suspend fun scanDeviceVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoItem>()
        val seenUris = mutableSetOf<String>()
        val seenFileSignatures = mutableSetOf<String>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )

        val collections = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI
        )

        for (collection in collections) {
            try {
                val cursor = context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameCol = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val bucketCol = it.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val sizeCol = it.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val dateCol = it.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val mimeCol = it.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                    val dataCol = it.getColumnIndex(MediaStore.Video.Media.DATA)

                    while (it.moveToNext()) {
                        val id = if (idCol >= 0) it.getLong(idCol) else -1L
                        if (id == -1L) continue
                        val name = if (nameCol >= 0) it.getString(nameCol) ?: "Video $id" else "Video $id"
                        val duration = if (durCol >= 0) it.getLong(durCol) else 0L
                        val bucketName = if (bucketCol >= 0) it.getString(bucketCol) ?: "Device" else "Device"
                        val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                        val dateAdded = if (dateCol >= 0) it.getLong(dateCol) else 0L
                        val mimeType = if (mimeCol >= 0) it.getString(mimeCol) else null
                        val dataPath = if (dataCol >= 0) it.getString(dataCol) else null
                        val contentUri = ContentUris.withAppendedId(collection, id).toString()

                        val signature = "${name.lowercase()}_$size"
                        if (contentUri !in seenUris && signature !in seenFileSignatures) {
                            seenUris.add(contentUri)
                            seenFileSignatures.add(signature)
                            dataPath?.let { p -> seenUris.add(Uri.fromFile(File(p)).toString()) }

                            results.add(
                                VideoItem(
                                    id = "device_${id}_${results.size}",
                                    title = cleanTitle(name),
                                    uriString = contentUri,
                                    folderName = bucketName.ifBlank { "Device" },
                                    youtubeId = YoutubeIdHelper.extractYoutubeId(name),
                                    isSample = false,
                                    durationMs = duration,
                                    mimeType = mimeType,
                                    sizeBytes = size,
                                    dateAdded = dateAdded
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val commonDirs = mutableListOf<File>()
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.let { commonDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { commonDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.let { commonDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.let { commonDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { commonDirs.add(it) }
            try {
                Environment.getExternalStorageDirectory()?.let { commonDirs.add(it) }
            } catch (ignored: Exception) {}

            try {
                val storageRoot = File("/storage")
                if (storageRoot.exists() && storageRoot.isDirectory) {
                    val subDirs = storageRoot.listFiles()
                    if (subDirs != null) {
                        for (sub in subDirs) {
                            if (sub.isDirectory && !sub.name.startsWith(".") && sub.name != "emulated" && sub.name != "self") {
                                commonDirs.add(sub)
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {}

            fun scanDirFiles(dir: File, depth: Int = 0) {
                if (depth > 8 || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
                if (dir.name.startsWith(".")) return
                val nomediaFile = File(dir, ".nomedia")
                if (nomediaFile.exists()) return

                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) {
                        if (!file.name.startsWith(".")) {
                            scanDirFiles(file, depth + 1)
                        }
                    } else if (file.isFile && isPotentialMediaFile(file.name) && file.length() > 512) {
                        if (file.name.endsWith(".ts", ignoreCase = true)) {
                            try {
                                val headerBuf = ByteArray(188)
                                file.inputStream().use { it.read(headerBuf) }
                                val headerStr = String(headerBuf, Charsets.UTF_8)
                                if (headerStr.startsWith("import ") || headerStr.startsWith("export ") || headerStr.startsWith("/*") || headerStr.startsWith("//") || headerStr.startsWith("const ")) {
                                    continue
                                }
                            } catch (ignored: Exception) {}
                        }

                        val fileUri = Uri.fromFile(file).toString()
                        val signature = "${file.name.lowercase()}_${file.length()}"
                        if (fileUri !in seenUris && signature !in seenFileSignatures) {
                            seenUris.add(fileUri)
                            seenFileSignatures.add(signature)
                            val parentName = file.parentFile?.name ?: "Storage"
                            results.add(
                                VideoItem(
                                    id = "file_${file.name.hashCode()}_${results.size}",
                                    title = cleanTitle(file.name),
                                    uriString = fileUri,
                                    folderName = parentName.ifBlank { "Device" },
                                    youtubeId = YoutubeIdHelper.extractYoutubeId(file.name),
                                    isSample = false,
                                    durationMs = 0L,
                                    sizeBytes = file.length(),
                                    dateAdded = file.lastModified()
                                )
                            )
                        }
                    }
                }
            }

            for (dir in commonDirs.distinctBy { it.absolutePath }) {
                scanDirFiles(dir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sortVideosDeterministically(results)
    }

    suspend fun deleteVideo(videoId: String) = withContext(Dispatchers.IO) {
        videoDao.deleteById(videoId)
    }

    suspend fun getTrackedFolders(): List<TrackedFolderEntity> = withContext(Dispatchers.IO) {
        trackedFolderDao.getAllFolders()
    }

    fun getTrackedFoldersFlow(): Flow<List<TrackedFolderEntity>> {
        return trackedFolderDao.getAllFoldersFlow()
    }

    suspend fun removeTrackedFolder(folderId: String) = withContext(Dispatchers.IO) {
        trackedFolderDao.deleteById(folderId)
    }

    suspend fun savePlaybackProgress(videoId: String, positionMs: Long, durationMs: Long, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        try {
            playbackProgressDao.saveProgress(
                PlaybackProgressEntity(
                    videoId = videoId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lastUpdated = System.currentTimeMillis(),
                    isCompleted = isCompleted
                )
            )
        } catch (e: Exception) {
            Log.w("VideoRepository", "Failed to save playback progress: ${e.message}")
        }
    }

    suspend fun getPlaybackProgress(videoId: String): PlaybackProgressEntity? = withContext(Dispatchers.IO) {
        playbackProgressDao.getProgress(videoId)
    }

    suspend fun recordWatchHistory(videoId: String, watchedDurationMs: Long) = withContext(Dispatchers.IO) {
        try {
            watchHistoryDao.recordHistory(
                WatchHistoryEntity(
                    videoId = videoId,
                    watchedTimestamp = System.currentTimeMillis(),
                    watchedDurationMs = watchedDurationMs
                )
            )
        } catch (e: Exception) {
            Log.w("VideoRepository", "Failed to record watch history: ${e.message}")
        }
    }

    private fun getDocumentDisplayName(resolver: ContentResolver, uri: Uri): String? {
        try {
            val cursor = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (ignored: Exception) {}
        return null
    }

    fun isPotentialMediaFile(fileName: String?, mimeType: String? = null): Boolean {
        if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) return true
        if (fileName == null) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return true

        val nonMediaExtensions = setOf(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
            "json", "xml", "html", "htm", "css", "js", "jsx", "tsx", "kt", "java", "py", "c", "cpp", "h",
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "psd", "ai", "tiff", "tif",
            "apk", "aab", "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "img",
            "exe", "bat", "cmd", "sh", "bin", "tmp", "bak", "log", "db", "db-journal",
            "sqlite", "nomedia", "ini", "properties", "md", "yaml", "yml", "toml", "lock",
            "ttf", "otf", "woff", "woff2", "eot", "fon"
        )
        return ext !in nonMediaExtensions
    }

    fun sortVideosDeterministically(videos: List<VideoItem>): List<VideoItem> {
        return videos.sortedWith(
            compareBy<VideoItem> { it.folderName.lowercase() }
                .thenBy { it.title.lowercase() }
        )
    }

    fun cleanTitle(rawName: String): String {
        val withoutExt = rawName.substringBeforeLast('.')
        val withoutTags = withoutExt
            .replace(Regex("(?i)\\b(1080p|720p|480p|360p|2160p|4k|x264|x265|hevc|h264|aac|webrip|bluray|dvdrip)\\b"), "")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()

        return if (withoutTags.isBlank()) {
            withoutExt.replace('_', ' ').replace('-', ' ').trim()
        } else {
            withoutTags.replace(Regex("\\s+"), " ")
        }
    }
}
