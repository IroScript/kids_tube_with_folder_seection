package com.example

import com.example.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun testAllFolderFiltering() {
        val all = filterVideos(sampleVideos, null)
        assertEquals(5, all.size)
    }

    @Test
    fun testSpecificSubfolderFiltering() {
        val rhymes = filterVideos(sampleVideos, "Rhymes")
        assertEquals(2, rhymes.size)
        assertTrue(rhymes.all { it.folderName.startsWith("Rhymes") })

        val cartoons = filterVideos(sampleVideos, "Cartoons")
        assertEquals(2, cartoons.size)
        assertTrue(cartoons.all { it.folderName.startsWith("Cartoons") })

        val stories = filterVideos(sampleVideos, "Stories")
        assertEquals(1, stories.size)
        assertEquals("Story A", stories.first().title)
    }

    @Test
    fun testDynamicShuffleChain() {
        val filtered = sampleVideos
        val selected = filtered[2] // Cartoon A
        val remaining = filtered.filter { it.id != selected.id }.shuffled()
        val dynamicChain = listOf(selected) + remaining

        assertEquals(selected.id, dynamicChain.first().id)
        assertEquals(filtered.size, dynamicChain.size)
    }

    @Test
    fun testMathQuestionVerification() {
        val a = 7
        val b = 8
        val expected = a * b
        assertEquals(56, expected)

        val correctInput = "56"
        val wrongInput = "54"

        assertEquals(expected, correctInput.toIntOrNull())
        assertNotEquals(expected, wrongInput.toIntOrNull())
    }

    @Test
    fun testThumbnailKeyHashing() {
        val uri1 = "content://media/external/video/media/101"
        val uri2 = "content://media/external/video/media/102"
        val digest = java.security.MessageDigest.getInstance("MD5")
        
        val hash1 = digest.digest(uri1.toByteArray()).joinToString("") { "%02x".format(it) }
        val hash2 = java.security.MessageDigest.getInstance("MD5").digest(uri2.toByteArray()).joinToString("") { "%02x".format(it) }

        assertNotEquals(hash1, hash2)
        assertEquals(32, hash1.length)
        assertEquals(32, hash2.length)
    }

    @Test
    fun testVlcMediaFileDetection() {
        val nonMediaExtensions = setOf(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
            "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java", "py", "c", "cpp", "h",
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "psd", "ai",
            "apk", "aab", "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
            "exe", "bat", "cmd", "sh", "bin", "tmp", "bak", "log", "db", "db-journal",
            "sqlite", "nomedia", "ini", "properties", "md"
        )

        fun isPotentialMedia(fileName: String?, mimeType: String? = null): Boolean {
            if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) return true
            if (fileName == null) return false
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return false
            return ext !in nonMediaExtensions
        }

        val supportedByVlc = listOf(
            "cartoon.mp4", "rhyme.mkv", "song.webm", "clip.MOV", "show.AVI",
            "video.3gp", "movie.ts", "cam.m2ts", "dvd.vob", "stream.flv",
            "legacy.wmv", "raw.m4v", "audio_story.mp3", "track.ogg", "rhyme.flac"
        )
        val rejectedNonMedia = listOf(
            "image.png", "doc.pdf", "script.py", "notes.txt", "app.apk", "data.json", "styles.css"
        )

        for (file in supportedByVlc) {
            assertTrue("Expected VLC to accept $file", isPotentialMedia(file))
        }

        for (file in rejectedNonMedia) {
            assertTrue("Expected non-media file $file to be filtered", !isPotentialMedia(file))
        }
    }

    @Test
    fun testTitleCleaning() {
        fun cleanTitle(rawName: String): String {
            return rawName.substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
        }

        assertEquals("Baby Shark Dance", cleanTitle("Baby_Shark_Dance.mp4"))
        assertEquals("Peppa Pig Fun Episode", cleanTitle("Peppa-Pig-Fun-Episode.mkv"))
        assertEquals("Tom and Jerry", cleanTitle("Tom_and_Jerry.webm"))
    }
}
