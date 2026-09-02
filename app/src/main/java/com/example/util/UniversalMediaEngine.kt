package com.example.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.File

object UniversalMediaEngine {
    private const val TAG = "UniversalMediaEngine"
    private var libVLCInstance: LibVLC? = null

    @Synchronized
    fun getOrCreateLibVLC(context: Context): LibVLC {
        libVLCInstance?.let { return it }

        val options = arrayListOf(
            "--no-video-title-show",
            "--no-sub-autodetect-file",
            "--audio-time-stretch",
            "--avcodec-skiploopfilter=0",
            "--avcodec-skip-frame=0",
            "--avcodec-skip-idct=0",
            "--avcodec-fast",
            "--avcodec-hw=any",
            "--sout-keep",
            "--file-caching=1000",
            "--network-caching=1500",
            "--live-caching=1000",
            "--disc-caching=1000",
            "-vv"
        )

        val instance = try {
            LibVLC(context.applicationContext, options)
        } catch (e: Throwable) {
            Log.w(TAG, "Falling back to default LibVLC initialization: ${e.message}")
            LibVLC(context.applicationContext)
        }

        libVLCInstance = instance
        return instance
    }

    /**
     * Universal Media preparation:
     * Handles content:// (via ParcelFileDescriptor for native POSIX kernel access),
     * file://, direct filesystem paths, and network streams.
     * Configures universal decoding parameters with hardware acceleration and automatic software fallback.
     */
    fun createMedia(
        context: Context,
        libVLC: LibVLC,
        uriString: String,
        forceSoftwareDecode: Boolean = false
    ): Pair<Media, ParcelFileDescriptor?> {
        val uri = Uri.parse(uriString)
        var pfd: ParcelFileDescriptor? = null

        val media: Media = if (uri.scheme == "content") {
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    Log.d(TAG, "Opening content URI via native ParcelFileDescriptor: $uriString")
                    Media(libVLC, pfd.fileDescriptor)
                } else {
                    Log.d(TAG, "ParcelFileDescriptor is null, opening via Uri: $uriString")
                    Media(libVLC, uri)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not open FileDescriptor for $uriString, falling back to Uri: ${e.message}")
                Media(libVLC, uri)
            }
        } else if (uri.scheme == "file") {
            val path = uri.path
            if (path != null && File(path).exists()) {
                Log.d(TAG, "Opening local file path: $path")
                Media(libVLC, path)
            } else {
                Media(libVLC, uri)
            }
        } else {
            Media(libVLC, uri)
        }

        media.apply {
            // Enable hardware decoding with automatic software fallback, or force software decoding
            setHWDecoderEnabled(!forceSoftwareDecode, false)
            addOption(":file-caching=1000")
            addOption(":network-caching=1500")
            addOption(":audio-time-stretch")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
        }

        return Pair(media, pfd)
    }
}
