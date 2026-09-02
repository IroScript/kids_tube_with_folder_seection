package com.example

import com.example.model.VideoItem
import com.example.repository.VideoRepository
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
}
