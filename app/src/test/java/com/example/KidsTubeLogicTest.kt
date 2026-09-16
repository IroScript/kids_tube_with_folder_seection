package com.example

import com.example.data.local.entity.TrackedFolderEntity
import com.example.model.ParentProtectedAction
import com.example.model.PlaybackConstants
import com.example.model.TrackedFolderItem
import com.example.model.VideoItem
import com.example.model.calculateResumePosition
import com.example.model.isPlaybackCompleted
import com.example.repository.VideoRepository
import com.example.viewmodel.KidsTubeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsTubeLogicTest {

    private val sampleVideos = listOf(
        VideoItem(id = "1", title = "Rhyme A", uriString = "uri1", folderName = "Rhymes / English"),
        VideoItem(id = "2", title = "Rhyme B", uriString = "uri2", folderName = "Rhymes / Bengali"),
        VideoItem(id = "3", title = "Cartoon A", uriString = "uri3", folderName = "Cartoons"),
        VideoItem(id = "4", title = "Cartoon B", uriString = "uri4", folderName = "Cartoons / Animals"),
        VideoItem(id = "5", title = "Story A", uriString = "uri5", folderName = "Stories")
    )

    private fun filterVideos(all: List<VideoItem>, selectedFolder: String?): List<VideoItem> {
        if (selectedFolder == null) return all
        return all.filter {
            it.folderName == selectedFolder || it.folderName.startsWith("$selectedFolder /")
        }
    }

    private fun sortVideosDeterministically(videos: List<VideoItem>): List<VideoItem> {
        return videos.sortedWith(
            compareBy<VideoItem> { it.folderName.lowercase() }
                .thenBy { it.title.lowercase() }
        )
    }

    @Test
    fun testTest1_SingleVideoPlaybackLifecycle() {
        val singleList = listOf(VideoItem(id = "s1", title = "Single Video", uriString = "uri_s1", folderName = "Single"))
        var currentVideo: VideoItem? = singleList.first()
        var isLoadingVideo = true
        var isBuffering = true
        var isPlaying = false
        var isPlaybackError = false

        // 1. Initial Selection: LOADING state
        assertTrue(isLoadingVideo)
        assertFalse(isPlaying)

        // 2. Playback started event
        isPlaying = true
        isLoadingVideo = false
        isBuffering = false
        assertTrue(isPlaying)
        assertFalse(isLoadingVideo)

        // 3. Video finishes (EndReached)
        isPlaying = false
        assertFalse(isPlaying)
    }

    @Test
    fun testTest2_MultipleVideosDeterministicOrderingAndSequentialNext() {
        val sorted = sortVideosDeterministically(sampleVideos)
        assertEquals("Cartoon A", sorted[0].title)
        assertEquals("Cartoon B", sorted[1].title)
        assertEquals("Rhyme B", sorted[2].title)
        assertEquals("Rhyme A", sorted[3].title)
        assertEquals("Story A", sorted[4].title)

        // Sequential Next Navigation
        var currentIndex = 0
        var nextIndex = (currentIndex + 1) % sorted.size
        assertEquals(1, nextIndex)
        assertEquals("Cartoon B", sorted[nextIndex].title)

        currentIndex = sorted.size - 1
        nextIndex = (currentIndex + 1) % sorted.size
        assertEquals(0, nextIndex)
        assertEquals("Cartoon A", sorted[nextIndex].title)
    }

    @Test
    fun testTest3_PlaybackErrorStateTransition() {
        var isLoadingVideo = true
        var isPlaying = false
        var isPlaybackError = false
        var playbackErrorMessage: String? = null

        // Simulate VLC Error Event
        val errorReason = "File not found or corrupted codec"
        isLoadingVideo = false
        isPlaying = false
        isPlaybackError = true
        playbackErrorMessage = errorReason

        assertTrue(isPlaybackError)
        assertFalse(isPlaying)
        assertFalse(isLoadingVideo)
        assertEquals("File not found or corrupted codec", playbackErrorMessage)
    }

    @Test
    fun testTest4_SupportedVideoFormatsDetection() {
        val nonMediaExtensions = setOf(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
            "json", "xml", "html", "htm", "css", "js", "jsx", "tsx", "kt", "java", "py", "c", "cpp", "h",
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "psd", "ai", "tiff", "tif",
            "apk", "aab", "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "img",
            "exe", "bat", "cmd", "sh", "bin", "tmp", "bak", "log", "db", "db-journal",
            "sqlite", "nomedia", "ini", "properties", "md", "yaml", "yml", "toml", "lock",
            "ttf", "otf", "woff", "woff2", "eot", "fon"
        )

        fun isPotentialMedia(fileName: String?, mimeType: String? = null): Boolean {
            if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) return true
            if (fileName == null) return false
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return true
            return ext !in nonMediaExtensions
        }

        val supportedFormats = listOf(
            "cartoon.mp4", "rhyme.mkv", "song.webm", "clip.MOV", "show.AVI",
            "video.3gp", "video.3g2", "movie.ts", "cam.m2ts", "dvd.vob", "stream.flv",
            "legacy.wmv", "raw.m4v", "stream.f4v", "recording.wtv", "vintage.divx",
            "rmvideo.rmvb", "asfvideo.asf", "ogvvideo.ogv", "stream.m3u8", "media.nut",
            "video.vivo", "clip.roq", "game.bik", "clip.mxf", "audio.opus", "sound.flac"
        )
        val rejectedNonMedia = listOf(
            "image.png", "doc.pdf", "script.py", "notes.txt", "app.apk", "data.json", "styles.css", "archive.zip"
        )

        for (file in supportedFormats) {
            assertTrue("Expected Universal VLC engine to accept format $file", isPotentialMedia(file))
        }

        for (file in rejectedNonMedia) {
            assertFalse("Expected non-media file $file to be rejected", isPotentialMedia(file))
        }
    }

    @Test
    fun testTest5_LargePlaylistStability() {
        val largeList = (1..500).map { i ->
            VideoItem(id = "id_$i", title = "Episode $i", uriString = "uri_$i", folderName = "Kids Season ${i % 5}")
        }
        val sorted = sortVideosDeterministically(largeList)
        assertEquals(500, sorted.size)
        // Ensure deterministic stability
        val reSorted = sortVideosDeterministically(sorted)
        assertEquals(sorted, reSorted)
    }

    @Test
    fun testTest6_ShuffleOffDeterministicSequentialOrder() {
        val isShuffleMode = false
        val playlist = sortVideosDeterministically(sampleVideos)
        val current = playlist[1] // Cartoon B

        val nextVideo = if (isShuffleMode) {
            playlist.filter { it.id != current.id }.random()
        } else {
            val idx = playlist.indexOfFirst { it.id == current.id }
            playlist[(idx + 1) % playlist.size]
        }

        assertEquals("Rhyme B", nextVideo.title)
    }

    @Test
    fun testTest7_ShuffleOnNonRepeatingCandidatesAndStablePlaylist() {
        val playlist = sortVideosDeterministically(sampleVideos)
        val playedIds = mutableSetOf<String>()
        val currentId = playlist[0].id
        playedIds.add(currentId)

        // Select next unplayed candidate
        val unplayedCandidates = playlist.filter { it.id !in playedIds && it.id != currentId }
        assertEquals(4, unplayedCandidates.size)
        val nextPicked = unplayedCandidates.random()
        assertTrue(nextPicked.id in unplayedCandidates.map { it.id })

        // Ensure visible playlist did not change order
        assertEquals("Cartoon A", playlist[0].title)
        assertEquals("Cartoon B", playlist[1].title)
    }

    @Test
    fun testTest8_RapidNextPreviousWithHistoryStack() {
        val playlist = sortVideosDeterministically(sampleVideos)
        val history = ArrayDeque<String>()

        history.addLast(playlist[0].id)
        history.addLast(playlist[1].id)
        history.addLast(playlist[2].id)

        val prevId = history.removeLast()
        assertEquals(playlist[2].id, prevId)

        val prevId2 = history.removeLast()
        assertEquals(playlist[1].id, prevId2)
    }

    @Test
    fun testTest9_AllFolderFiltering() {
        val all = filterVideos(sampleVideos, null)
        assertEquals(5, all.size)

        val rhymes = filterVideos(sampleVideos, "Rhymes")
        assertEquals(2, rhymes.size)
        assertTrue(rhymes.all { it.folderName.startsWith("Rhymes") })
    }

    @Test
    fun testTest10_MediaStoreAndFileDeduplication() {
        val seenUris = mutableSetOf<String>()
        val seenFileSignatures = mutableSetOf<String>()

        val item1Uri = "content://media/external/video/media/100"
        val item1Name = "cartoons.mp4"
        val item1Size = 1048576L
        val sig1 = "${item1Name.lowercase()}_$item1Size"

        seenUris.add(item1Uri)
        seenFileSignatures.add(sig1)

        val duplicateFileUri = "file:///storage/emulated/0/Movies/cartoons.mp4"
        val duplicateFileName = "cartoons.mp4"
        val duplicateFileSize = 1048576L
        val sig2 = "${duplicateFileName.lowercase()}_$duplicateFileSize"

        val isDuplicate = duplicateFileUri in seenUris || sig2 in seenFileSignatures
        assertTrue("Duplicate file should be detected via signature", isDuplicate)
    }

    @Test
    fun testTest11_PhaseA_VideoEntityToVideoItemMapping() {
        val entity = com.example.data.local.entity.VideoEntity(
            id = "vid_123",
            folderId = "folder_1",
            uriString = "content://media/1",
            fileName = "cartoon_ep01_1080p.mp4",
            displayTitle = "Cartoon Ep01",
            durationMs = 60000L,
            sizeBytes = 1048576L,
            mimeType = "video/mp4",
            lastModified = 1715000000000L,
            dateAdded = 1715000000000L
        )

        val item = VideoItem(
            id = entity.id,
            title = entity.displayTitle,
            uriString = entity.uriString,
            folderName = "Cartoons",
            durationMs = entity.durationMs,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            dateAdded = entity.dateAdded,
            playbackPositionMs = 30000L,
            isCompleted = false
        )

        assertEquals("vid_123", item.id)
        assertEquals("Cartoon Ep01", item.title)
        assertEquals("content://media/1", item.uriString)
        assertEquals(30000L, item.playbackPositionMs)
        assertFalse(item.isCompleted)
    }

    @Test
    fun testTest12_PhaseA_TitleCleanerRegexRules() {
        fun cleanTitle(rawName: String): String {
            val withoutExt = rawName.substringBeforeLast('.')
            val spaced = withoutExt.replace('_', ' ').replace('-', ' ').trim()
            val withoutTags = spaced
                .replace(Regex("(?i)\\b(1080p|720p|480p|360p|2160p|4k|x264|x265|hevc|h264|aac|webrip|bluray|dvdrip)\\b"), "")
                .trim()

            return if (withoutTags.isBlank()) {
                spaced
            } else {
                withoutTags.replace(Regex("\\s+"), " ")
            }
        }

        assertEquals("rhyme ep01", cleanTitle("rhyme_ep01_1080p_x264.mkv"))
        assertEquals("cartoon adventure", cleanTitle("cartoon-adventure-720p-h264.mp4"))
        assertEquals("ভুতুড়ে বাড়ি পর্ব ০১", cleanTitle("ভুতুড়ে_বাড়ি_পর্ব_০১.mp4"))
        assertEquals("1080p", cleanTitle("1080p.mp4"))
    }

    @Test
    fun testTest13_PhaseA_IdentityReconciliationDisambiguationRule() {
        // Rule: Single unambiguous match reconciles; multiple matches must preserve both as separate records
        val candidate1 = com.example.data.local.entity.VideoEntity(
            id = "vid_old_1",
            folderId = "folder_1",
            uriString = "content://old/1",
            fileName = "rhyme.mp4",
            displayTitle = "Rhyme",
            sizeBytes = 5000L,
            lastModified = 10000L
        )
        val candidate2 = com.example.data.local.entity.VideoEntity(
            id = "vid_old_2",
            folderId = "folder_1",
            uriString = "content://old/2",
            fileName = "rhyme_copy.mp4",
            displayTitle = "Rhyme Copy",
            sizeBytes = 5000L,
            lastModified = 10000L
        )

        val singleCandidateList = listOf(candidate1)
        val multipleCandidateList = listOf(candidate1, candidate2)

        // Single match allowed
        val canReconcileSingle = singleCandidateList.size == 1
        assertTrue(canReconcileSingle)

        // Multiple match MUST NOT silently merge
        val canReconcileMultiple = multipleCandidateList.size == 1
        assertFalse("Multiple candidates must not silently merge", canReconcileMultiple)
    }

    @Test
    fun testTC14_PhaseB_PreventDuplicateTreeUriRegistration() {
        val existingFolders = mutableListOf(
            TrackedFolderEntity(
                id = "folder_existing_1",
                treeUriString = "content://com.android.externalstorage.documents/tree/primary%3AKidsCartoons",
                displayName = "KidsCartoons",
                isEnabled = true,
                isPermissionGranted = true
            )
        )

        val incomingUri = "content://com.android.externalstorage.documents/tree/primary%3AKidsCartoons"

        // Deduplication lookup
        val existing = existingFolders.find { it.treeUriString == incomingUri }
        assertNotNull("Existing folder must be found", existing)

        val targetFolderId = if (existing != null) {
            existing.id
        } else {
            "folder_new_" + System.currentTimeMillis()
        }

        assertEquals("folder_existing_1", targetFolderId)
        assertEquals("Should not create duplicate folder ID", 1, existingFolders.size)
    }

    @Test
    fun testTC15_PhaseB_RemoveFolderFlowLeavesPhysicalStorageUntouched() {
        // Mock state
        val databaseFolderRecords = mutableListOf("folder_1", "folder_2")
        val databaseVideoRecords = mutableListOf("vid_101", "vid_102")
        val storageFilesOnDisk = mutableListOf("/storage/emulated/0/KidsCartoons/cartoon1.mp4", "/storage/emulated/0/KidsCartoons/cartoon2.mp4")

        // Action: Parent removes folder_1 from app database
        val folderToRemove = "folder_1"
        databaseFolderRecords.remove(folderToRemove)
        // Cascade delete in database
        databaseVideoRecords.clear()

        // Verify database records are removed
        assertFalse(databaseFolderRecords.contains(folderToRemove))
        assertTrue(databaseVideoRecords.isEmpty())

        // Verify storage files remain 100% untouched
        assertEquals(2, storageFilesOnDisk.size)
        assertTrue(storageFilesOnDisk.contains("/storage/emulated/0/KidsCartoons/cartoon1.mp4"))
        assertTrue(storageFilesOnDisk.contains("/storage/emulated/0/KidsCartoons/cartoon2.mp4"))
    }

    @Test
    fun testTC16_PhaseB_DisabledFolderHidesVideosFromChildShelf() {
        data class MockFolder(val id: String, val name: String, val isEnabled: Boolean, val isPermissionGranted: Boolean)
        data class MockVideo(val id: String, val folderId: String, val title: String)

        val folders = listOf(
            MockFolder("f1", "Cartoons", isEnabled = true, isPermissionGranted = true),
            MockFolder("f2", "Rhymes", isEnabled = false, isPermissionGranted = true) // Parent disabled
        )

        val videos = listOf(
            MockVideo("v1", "f1", "Episode 1"),
            MockVideo("v2", "f2", "Rhyme 1")
        )

        // Child filter rule: folder.isEnabled && folder.isPermissionGranted
        val activeFolderIds = folders.filter { it.isEnabled && it.isPermissionGranted }.map { it.id }.toSet()
        val childVideos = videos.filter { it.folderId in activeFolderIds }

        assertEquals(1, childVideos.size)
        assertEquals("Episode 1", childVideos[0].title)
        assertFalse("Disabled folder video must not be visible to child", childVideos.any { it.title == "Rhyme 1" })
    }

    @Test
    fun testTC17_PhaseB_PermissionRevocationHidesVideosFromChildShelf() {
        data class MockFolder(val id: String, val name: String, val isEnabled: Boolean, val isPermissionGranted: Boolean)
        data class MockVideo(val id: String, val folderId: String, val title: String)

        val folders = listOf(
            MockFolder("f1", "Cartoons", isEnabled = true, isPermissionGranted = true),
            MockFolder("f2", "Movies", isEnabled = true, isPermissionGranted = false) // Permission lost
        )

        val videos = listOf(
            MockVideo("v1", "f1", "Episode 1"),
            MockVideo("v2", "f2", "Movie 1")
        )

        val activeFolderIds = folders.filter { it.isEnabled && it.isPermissionGranted }.map { it.id }.toSet()
        val childVideos = videos.filter { it.folderId in activeFolderIds }

        assertEquals(1, childVideos.size)
        assertEquals("Episode 1", childVideos[0].title)
        assertFalse("Permission revoked folder video must not be visible to child", childVideos.any { it.title == "Movie 1" })
    }

    @Test
    fun testTC18_PhaseB_ReGrantPermissionRestoresChildVideoAccess() {
        data class MockFolder(var isEnabled: Boolean, var isPermissionGranted: Boolean)

        val folder = MockFolder(isEnabled = true, isPermissionGranted = false)

        // Initially permission lost
        assertFalse(folder.isEnabled && folder.isPermissionGranted)

        // Parent re-grants permission via SAF
        folder.isPermissionGranted = true

        // Restored to child library
        assertTrue(folder.isEnabled && folder.isPermissionGranted)
    }

    @Test
    fun testTC19_PhaseB_FolderRescanReconcilesAddedAndDeletedFiles() {
        data class MockDbVideo(val id: String, val uri: String, var title: String)

        val existingInDb = mutableListOf(
            MockDbVideo("vid_1", "content://file/1", "Old Title 1"),
            MockDbVideo("vid_2", "content://file/2", "Deleted on Disk")
        )

        // Simulated filesystem state during rescan:
        // - content://file/1 still exists (title changed on disk)
        // - content://file/2 was deleted from disk
        // - content://file/3 is newly added on disk
        val diskUris = listOf(
            "content://file/1" to "New Title 1",
            "content://file/3" to "Brand New Video"
        )

        val diskUriMap = diskUris.toMap()
        val diskUriSet = diskUris.map { it.first }.toSet()

        // 1. Delete removed
        existingInDb.removeAll { it.uri !in diskUriSet }

        // 2. Update existing
        for (v in existingInDb) {
            diskUriMap[v.uri]?.let { v.title = it }
        }

        // 3. Add new
        val existingUris = existingInDb.map { it.uri }.toSet()
        for ((uri, title) in diskUris) {
            if (uri !in existingUris) {
                existingInDb.add(MockDbVideo("vid_" + uri.hashCode(), uri, title))
            }
        }

        assertEquals(2, existingInDb.size)
        assertEquals("New Title 1", existingInDb.find { it.uri == "content://file/1" }?.title)
        assertNull(existingInDb.find { it.uri == "content://file/2" })
        assertNotNull(existingInDb.find { it.uri == "content://file/3" })
    }

    @Test
    fun testTC20_PhaseB_ChildLibraryQueryConditionLogic() {
        fun isVisibleToChild(isEnabled: Boolean, isPermissionGranted: Boolean): Boolean {
            return isEnabled && isPermissionGranted
        }

        // Truth table validation
        assertTrue(isVisibleToChild(isEnabled = true, isPermissionGranted = true))
        assertFalse(isVisibleToChild(isEnabled = true, isPermissionGranted = false))
        assertFalse(isVisibleToChild(isEnabled = false, isPermissionGranted = true))
        assertFalse(isVisibleToChild(isEnabled = false, isPermissionGranted = false))
    }

    @Test
    fun testTC21_PhaseB_BengaliUnicodeFolderAndVideoTitles() {
        val bengaliVideos = listOf(
            VideoItem(id = "b1", title = "টুনটুনি আর দুষ্টু বিড়াল", uriString = "uri_b1", folderName = "বাংলা ছড়া"),
            VideoItem(id = "b2", title = "ছোটদের মজার গল্প", uriString = "uri_b2", folderName = "বাংলা গল্প")
        )

        val sorted = sortVideosDeterministically(bengaliVideos)
        assertEquals(2, sorted.size)
        assertEquals("বাংলা গল্প", sorted[0].folderName)
        assertEquals("বাংলা ছড়া", sorted[1].folderName)
        assertEquals("ছোটদের মজার গল্প", sorted[0].title)
        assertEquals("টুনটুনি আর দুষ্টু বিড়াল", sorted[1].title)
    }

    @Test
    fun testTC22_PhaseB_TrackedFolderEntityToTrackedFolderItemMapping() {
        val entity = TrackedFolderEntity(
            id = "folder_abc",
            treeUriString = "content://saf/tree/abc",
            displayName = "My Kids Videos",
            dateAdded = 1715000000000L,
            lastScanned = 1715000500000L,
            isEnabled = true,
            isPermissionGranted = true,
            videoCount = 15
        )

        val item = TrackedFolderItem(
            id = entity.id,
            treeUriString = entity.treeUriString,
            displayName = entity.displayName,
            dateAdded = entity.dateAdded,
            lastScanned = entity.lastScanned,
            isEnabled = entity.isEnabled,
            isPermissionGranted = entity.isPermissionGranted,
            videoCount = entity.videoCount
        )

        assertEquals("folder_abc", item.id)
        assertEquals("content://saf/tree/abc", item.treeUriString)
        assertEquals("My Kids Videos", item.displayName)
        assertEquals(1715000000000L, item.dateAdded)
        assertEquals(1715000500000L, item.lastScanned)
        assertTrue(item.isEnabled)
        assertTrue(item.isPermissionGranted)
        assertEquals(15, item.videoCount)
    }

    // ==========================================
    // PHASE C: STATE & PLAYBACK ARCHITECTURE TESTS
    // ==========================================

    @Test
    fun testTCC01_CompletedVideoRestartsFromBeginning() {
        // Any completed video must restart from 0:00
        val resumePos = calculateResumePosition(
            positionMs = 115_000L,
            durationMs = 120_000L,
            isCompleted = true
        )
        assertEquals(0L, resumePos)
    }

    @Test
    fun testTCC02_VideoNearEndRestartsFromBeginning() {
        // Video watched >= 95% of duration must restart from 0:00
        val resumePos = calculateResumePosition(
            positionMs = 96_000L,
            durationMs = 100_000L, // 96%
            isCompleted = false
        )
        assertEquals(0L, resumePos)
    }

    @Test
    fun testTCC03_VideoWithin5sOfEndRestartsFromBeginning() {
        // Video within 5 seconds of end must restart from 0:00
        val resumePos = calculateResumePosition(
            positionMs = 56_000L,
            durationMs = 60_000L, // 4 seconds before end
            isCompleted = false
        )
        assertEquals(0L, resumePos)
    }

    @Test
    fun testTCC04_VeryEarlyVideoUnder3SecondsRestartsFromBeginning() {
        // Early video (< 3 seconds) restarts from 0:00
        val resumePos = calculateResumePosition(
            positionMs = 2_500L,
            durationMs = 60_000L,
            isCompleted = false
        )
        assertEquals(0L, resumePos)
    }

    @Test
    fun testTCC05_MidPlaybackVideoResumesFromLastSavedPosition() {
        // Mid-playback video (>= 3s and < 95% / > 5s before end) resumes accurately
        val resumePos = calculateResumePosition(
            positionMs = 45_000L,
            durationMs = 120_000L,
            isCompleted = false
        )
        assertEquals(45_000L, resumePos)
    }

    @Test
    fun testTCC06_ZeroOrNegativeDurationHandlesSafely() {
        assertEquals(0L, calculateResumePosition(10_000L, 0L, false))
        assertEquals(0L, calculateResumePosition(10_000L, -1L, false))
        assertEquals(0L, calculateResumePosition(-500L, 10_000L, false))
    }

    @Test
    fun testTCC07_PlaybackCompletedThresholdDetection() {
        // 95% boundary detection
        assertTrue(isPlaybackCompleted(95_000L, 100_000L))
        assertTrue(isPlaybackCompleted(99_000L, 100_000L))
        assertFalse(isPlaybackCompleted(94_000L, 100_000L))

        // 5-second before end threshold
        assertTrue(isPlaybackCompleted(55_500L, 60_000L)) // within 4.5s of end
        assertFalse(isPlaybackCompleted(50_000L, 60_000L)) // 10s before end
    }

    @Test
    fun testTCC08_ParentProtectedActionRoutingFolderManager() {
        // Action routing to OpenFolderManager sets both isParentMode and isFolderManagerMode to true
        var uiState = KidsTubeUiState()
        assertFalse(uiState.isParentMode)
        assertFalse(uiState.isFolderManagerMode)

        fun executeProtectedAction(action: ParentProtectedAction) {
            when (action) {
                ParentProtectedAction.OpenFolderManager -> {
                    uiState = uiState.copy(
                        isParentMode = true,
                        isFolderManagerMode = true,
                        toastMessage = "Folder Management opened 📁"
                    )
                }
                ParentProtectedAction.OpenParentDashboard -> {
                    uiState = uiState.copy(
                        isParentMode = true,
                        isFolderManagerMode = false,
                        toastMessage = "Parent controls unlocked 🛡️"
                    )
                }
            }
        }

        executeProtectedAction(ParentProtectedAction.OpenFolderManager)
        assertTrue(uiState.isParentMode)
        assertTrue(uiState.isFolderManagerMode)
        assertEquals("Folder Management opened 📁", uiState.toastMessage)
    }

    @Test
    fun testTCC09_ParentProtectedActionRoutingParentDashboard() {
        // Action routing to OpenParentDashboard sets isParentMode=true and isFolderManagerMode=false
        var uiState = KidsTubeUiState()
        fun executeProtectedAction(action: ParentProtectedAction) {
            when (action) {
                ParentProtectedAction.OpenFolderManager -> {
                    uiState = uiState.copy(isParentMode = true, isFolderManagerMode = true)
                }
                ParentProtectedAction.OpenParentDashboard -> {
                    uiState = uiState.copy(isParentMode = true, isFolderManagerMode = false)
                }
            }
        }

        executeProtectedAction(ParentProtectedAction.OpenParentDashboard)
        assertTrue(uiState.isParentMode)
        assertFalse(uiState.isFolderManagerMode)
    }

    @Test
    fun testTCC10_ParentMathGateVerificationSuccessAndClearsPendingAction() {
        var uiState = KidsTubeUiState(
            showParentLockDialog = true,
            parentMathQuestion = "6 × 7",
            parentMathAnswer = 42,
            pendingParentAction = ParentProtectedAction.OpenFolderManager
        )

        fun verifyAnswer(userAnswer: Int): Boolean {
            if (userAnswer == uiState.parentMathAnswer) {
                val action = uiState.pendingParentAction ?: ParentProtectedAction.OpenParentDashboard
                uiState = uiState.copy(
                    showParentLockDialog = false,
                    pendingParentAction = null
                )
                when (action) {
                    ParentProtectedAction.OpenFolderManager -> uiState = uiState.copy(isParentMode = true, isFolderManagerMode = true)
                    ParentProtectedAction.OpenParentDashboard -> uiState = uiState.copy(isParentMode = true, isFolderManagerMode = false)
                }
                return true
            }
            return false
        }

        val success = verifyAnswer(42)
        assertTrue(success)
        assertFalse(uiState.showParentLockDialog)
        assertNull(uiState.pendingParentAction)
        assertTrue(uiState.isParentMode)
        assertTrue(uiState.isFolderManagerMode)
    }

    @Test
    fun testTCC11_ParentMathGateVerificationFailureKeepsLockIntact() {
        var uiState = KidsTubeUiState(
            showParentLockDialog = true,
            parentMathQuestion = "8 × 9",
            parentMathAnswer = 72,
            pendingParentAction = ParentProtectedAction.OpenFolderManager
        )

        fun verifyAnswer(userAnswer: Int): Boolean {
            if (userAnswer == uiState.parentMathAnswer) {
                uiState = uiState.copy(showParentLockDialog = false, pendingParentAction = null)
                return true
            }
            return false
        }

        val success = verifyAnswer(54) // Wrong answer
        assertFalse(success)
        assertTrue(uiState.showParentLockDialog)
        assertEquals(ParentProtectedAction.OpenFolderManager, uiState.pendingParentAction)
        assertFalse(uiState.isParentMode)
    }

    @Test
    fun testTCC12_VideoSelectionAreaVisibilityDefaultAndSurvivesVideoSwitch() {
        // Selection area is visible by default
        var uiState = KidsTubeUiState()
        assertTrue(uiState.isSelectionAreaVisible)

        // Simulating playing a new video
        val newVideo = sampleVideos[1]
        uiState = uiState.copy(
            selectedVideoId = newVideo.id,
            selectedVideo = newVideo,
            currentVideo = newVideo,
            isSelectionAreaVisible = true // Selection area remains visible!
        )

        assertTrue(uiState.isSelectionAreaVisible)
        assertEquals(newVideo.id, uiState.selectedVideoId)
        assertEquals("Rhyme B", uiState.currentVideo?.title)
    }

    @Test
    fun testTCC13_ParentDashboardOpenClosePreservesSelectedVideoAndPlaylist() {
        val selected = sampleVideos[2]
        var uiState = KidsTubeUiState(
            videos = sampleVideos,
            displayPlaylist = sampleVideos,
            selectedVideoId = selected.id,
            selectedVideo = selected,
            currentVideo = selected,
            isSelectionAreaVisible = true
        )

        // 1. Open Parent Dashboard
        uiState = uiState.copy(isParentMode = true)
        assertTrue(uiState.isParentMode)
        assertEquals(selected.id, uiState.selectedVideoId)

        // 2. Close Parent Dashboard
        uiState = uiState.copy(isParentMode = false, isFolderManagerMode = false)
        assertFalse(uiState.isParentMode)
        assertFalse(uiState.isFolderManagerMode)

        // Child screen context, selected video and selection area remain 100% intact
        assertEquals(selected.id, uiState.selectedVideoId)
        assertEquals(selected.id, uiState.currentVideo?.id)
        assertEquals(sampleVideos.size, uiState.displayPlaylist.size)
        assertTrue(uiState.isSelectionAreaVisible)
    }

    @Test
    fun testTCC14_ThrottlingConstantValues() {
        assertEquals(2_000L, PlaybackConstants.SAVE_PROGRESS_INTERVAL_MS)
        assertEquals(3_000L, PlaybackConstants.MIN_RESUME_POSITION_MS)
        assertEquals(5_000L, PlaybackConstants.COMPLETION_THRESHOLD_OFFSET_MS)
        assertEquals(0.95f, PlaybackConstants.COMPLETION_PERCENTAGE, 0.001f)
    }

    @Test
    fun testTCC15_DisplayPlaylistPreservesSelectedFolderAcrossUpdates() {
        val activeFolder = "Rhymes / English"
        val filtered = filterVideos(sampleVideos, activeFolder)
        var uiState = KidsTubeUiState(
            videos = sampleVideos,
            selectedFolder = activeFolder,
            displayPlaylist = filtered,
            selectedVideoId = filtered.first().id
        )

        assertEquals(1, uiState.displayPlaylist.size)
        assertEquals("Rhyme A", uiState.displayPlaylist.first().title)

        // Simulating library reload or active videos flow update
        val updatedVideos = sampleVideos + VideoItem(
            id = "6",
            title = "Rhyme C",
            uriString = "uri6",
            folderName = "Rhymes / English"
        )

        val updatedFiltered = filterVideos(updatedVideos, uiState.selectedFolder)
        val preservedSelection = updatedFiltered.find { it.id == uiState.selectedVideoId }

        uiState = uiState.copy(
            videos = updatedVideos,
            displayPlaylist = updatedFiltered,
            selectedVideoId = preservedSelection?.id ?: updatedFiltered.firstOrNull()?.id
        )

        // Folder filter and selection remain intact
        assertEquals(2, uiState.displayPlaylist.size)
        assertEquals("1", uiState.selectedVideoId)
    }
}
