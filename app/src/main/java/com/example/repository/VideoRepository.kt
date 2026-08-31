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
import com.example.model.VideoItem
import com.example.util.YoutubeIdHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class VideoRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kids_tube_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VIDEOS = "kids_videos_json"
        private const val KEY_FIRST_RUN = "kids_first_run_initialized"

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

    suspend fun getSavedVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(KEY_VIDEOS, null)
        if (jsonString != null) {
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
                            folderName = obj.optString("folderName", "Default"),
                            youtubeId = if (obj.has("youtubeId")) obj.getString("youtubeId") else null,
                            isSample = obj.optBoolean("isSample", false),
                            durationMs = obj.optLong("durationMs", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    return@withContext list
                }
            } catch (e: Exception) {
                // fall through to device scan / sample
            }
        }

        // By default, if no videos are selected, automatically scan & load all device videos from all directories
        val deviceVideos = scanDeviceVideos()
        if (deviceVideos.isNotEmpty()) {
            saveVideos(deviceVideos)
            return@withContext deviceVideos
        }

        // If no local videos exist anywhere on device storage, fallback to sample videos
        saveVideos(SAMPLE_VIDEOS)
        SAMPLE_VIDEOS
    }

    suspend fun saveVideos(videos: List<VideoItem>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        for (item in videos) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("uriString", item.uriString)
            obj.put("folderName", item.folderName)
            item.youtubeId?.let { obj.put("youtubeId", it) }
            obj.put("isSample", item.isSample)
            obj.put("durationMs", item.durationMs)
            array.put(obj)
        }
        prefs.edit().putString(KEY_VIDEOS, array.toString()).apply()
    }

    /**
     * Scan files from DocumentTree URI (Storage Access Framework) recursively across all subfolders
     */
    suspend fun scanDocumentTree(treeUri: Uri): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoItem>()
        val contentResolver: ContentResolver = context.contentResolver

        try {
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}

            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootFolderName = getDocumentDisplayName(contentResolver, treeUri) ?: "Root Folder"

            fun scanDirectory(docId: String, currentFolderPath: String) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val cursor: Cursor? = contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (it.moveToNext()) {
                        val childId = it.getString(idIndex)
                        val displayName = it.getString(nameIndex) ?: continue
                        val mimeType = it.getString(mimeIndex)

                        val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
                                mimeType == "vnd.android.document/directory"

                        if (isDir) {
                            // Recursively scan subfolder and sub-subfolder
                            val nextPath = if (currentFolderPath.isEmpty()) displayName else "$currentFolderPath / $displayName"
                            scanDirectory(childId, nextPath)
                        } else {
                            val isMedia = mimeType?.startsWith("video/") == true ||
                                isPotentialMediaFile(displayName, mimeType)

                            if (isMedia) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                val folderDisplay = if (currentFolderPath.isEmpty()) rootFolderName else currentFolderPath
                                results.add(
                                    VideoItem(
                                        id = UUID.randomUUID().toString(),
                                        title = cleanTitle(displayName),
                                        uriString = fileUri.toString(),
                                        folderName = folderDisplay,
                                        youtubeId = YoutubeIdHelper.extractYoutubeId(displayName),
                                        isSample = false
                                    )
                                )
                            }
                        }
                    }
                }
            }

            scanDirectory(rootDocId, "")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }

    suspend fun createVideoFromUri(uri: Uri, context: Context): VideoItem = withContext(Dispatchers.IO) {
        var displayName = "Video ${System.currentTimeMillis() % 1000}"
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
                }
            }
        } catch (ignored: Exception) {}

        VideoItem(
            id = UUID.randomUUID().toString(),
            title = cleanTitle(displayName),
            uriString = uri.toString(),
            folderName = "Imported",
            youtubeId = YoutubeIdHelper.extractYoutubeId(displayName),
            isSample = false
        )
    }

    suspend fun scanDeviceVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoItem>()
        val seenUris = mutableSetOf<String>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        // 1. Query MediaStore External & Internal
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

                    while (it.moveToNext()) {
                        val id = if (idCol >= 0) it.getLong(idCol) else -1L
                        if (id == -1L) continue
                        val name = if (nameCol >= 0) it.getString(nameCol) ?: "Video $id" else "Video $id"
                        val duration = if (durCol >= 0) it.getLong(durCol) else 0L
                        val bucketName = if (bucketCol >= 0) it.getString(bucketCol) ?: "Device" else "Device"
                        val contentUri = ContentUris.withAppendedId(collection, id).toString()

                        if (contentUri !in seenUris) {
                            seenUris.add(contentUri)
                            results.add(
                                VideoItem(
                                    id = "device_${id}_${results.size}",
                                    title = cleanTitle(name),
                                    uriString = contentUri,
                                    folderName = bucketName.ifBlank { "Device" },
                                    youtubeId = YoutubeIdHelper.extractYoutubeId(name),
                                    isSample = false,
                                    durationMs = duration
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Direct File System Scanner Fallback (Movies, Download, DCIM, Pictures, SDCard)
        try {
            val commonDirs = mutableListOf<File>()
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.let { commonDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { commonDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.let { commonDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.let { commonDirs.add(it) }
            try {
                Environment.getExternalStorageDirectory()?.let { commonDirs.add(it) }
            } catch (ignored: Exception) {}

            fun scanDirFiles(dir: File, depth: Int = 0) {
                if (depth > 3 || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory && !file.name.startsWith(".")) {
                        scanDirFiles(file, depth + 1)
                    } else if (file.isFile && isPotentialMediaFile(file.name) && file.length() > 1024) {
                        val fileUri = Uri.fromFile(file).toString()
                        if (fileUri !in seenUris) {
                            seenUris.add(fileUri)
                            val parentName = file.parentFile?.name ?: "Storage"
                            results.add(
                                VideoItem(
                                    id = "file_${file.name.hashCode()}_${results.size}",
                                    title = cleanTitle(file.name),
                                    uriString = fileUri,
                                    folderName = parentName.ifBlank { "Device" },
                                    youtubeId = YoutubeIdHelper.extractYoutubeId(file.name),
                                    isSample = false,
                                    durationMs = 0L
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

        results
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
        if (ext.isEmpty()) return false

        // Exclude only known non-media documents, archives, code, images, system files
        // VLC media player engine natively decodes all audio/video formats and containers
        val nonMediaExtensions = setOf(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
            "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java", "py", "c", "cpp", "h",
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "psd", "ai",
            "apk", "aab", "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
            "exe", "bat", "cmd", "sh", "bin", "tmp", "bak", "log", "db", "db-journal",
            "sqlite", "nomedia", "ini", "properties", "md"
        )
        return ext !in nonMediaExtensions
    }

    private fun cleanTitle(rawName: String): String {
        return rawName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
    }
}
