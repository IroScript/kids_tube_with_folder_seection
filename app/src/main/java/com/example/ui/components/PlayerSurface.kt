package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.VideoItem
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun PlayerSurface(
    video: VideoItem?,
    isPlaying: Boolean,
    playTrigger: Long = 0L,
    seekRequestMs: Long? = null,
    onSeekHandled: () -> Unit = {},
    onPlaybackStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    onProgressUpdate: (currentPos: Long, totalDuration: Long) -> Unit,
    onVideoFinished: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val libVLC = remember {
        try {
            val options = arrayListOf(
                "--no-video-title-show",
                "--no-sub-autodetect-file",
                "--audio-time-stretch",
                "-vvv"
            )
            LibVLC(context, options)
        } catch (e: Throwable) {
            LibVLC(context)
        }
    }

    val mediaPlayer = remember(libVLC) {
        MediaPlayer(libVLC)
    }

    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    onPlaybackStateChanged(true, false)
                }
                MediaPlayer.Event.Paused -> {
                    onPlaybackStateChanged(false, false)
                }
                MediaPlayer.Event.Stopped -> {
                    onPlaybackStateChanged(false, false)
                }
                MediaPlayer.Event.EndReached -> {
                    onPlaybackStateChanged(false, false)
                    onVideoFinished()
                }
                MediaPlayer.Event.Buffering -> {
                    val isBuffering = event.buffering < 100f
                    onPlaybackStateChanged(mediaPlayer.isPlaying, isBuffering)
                }
                MediaPlayer.Event.TimeChanged -> {
                    val current = event.timeChanged
                    val total = mediaPlayer.length
                    if (total > 0) {
                        onProgressUpdate(current, total)
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    val total = event.lengthChanged
                    val current = mediaPlayer.time
                    if (total > 0) {
                        onProgressUpdate(current, total)
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    android.util.Log.e("KidsTubePlayer", "VLC encountered error event for video: ${video?.title}")
                    onError("Playback error occurred in VLC engine")
                }
            }
        }

        mediaPlayer.setEventListener(listener)

        onDispose {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    // Handle video source changes and instant touch play
    LaunchedEffect(video?.uriString, playTrigger) {
        if (video != null) {
            try {
                android.util.Log.d("KidsTubePlayer", "Loading video: ${video.title} (URI: ${video.uriString})")
                val uri = Uri.parse(video.uriString)
                val media = Media(libVLC, uri).apply {
                    setHWDecoderEnabled(true, false)
                    addOption(":file-caching=150")
                    addOption(":network-caching=500")
                }
                mediaPlayer.media = media
                media.release()
                mediaPlayer.play()
            } catch (e: Exception) {
                android.util.Log.e("KidsTubePlayer", "Exception initializing media: ${e.message}", e)
                onError(e.message ?: "Failed to load media in VLC")
            }
        } else {
            mediaPlayer.stop()
        }
    }

    // Handle isPlaying state changes
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.play()
            }
        } else {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
        }
    }

    // Handle seeking
    LaunchedEffect(seekRequestMs) {
        if (seekRequestMs != null) {
            mediaPlayer.time = seekRequestMs
            onSeekHandled()
        }
    }

    // Periodic progress updates fallback
    LaunchedEffect(video, isPlaying) {
        while (true) {
            if (mediaPlayer.isPlaying || mediaPlayer.time > 0) {
                val current = mediaPlayer.time.coerceAtLeast(0L)
                val duration = mediaPlayer.length.coerceAtLeast(0L)
                if (duration > 0) {
                    onProgressUpdate(current, duration)
                }
            }
            delay(500)
        }
    }

    AndroidView(
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                mediaPlayer.attachViews(this, null, false, false)
                mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
