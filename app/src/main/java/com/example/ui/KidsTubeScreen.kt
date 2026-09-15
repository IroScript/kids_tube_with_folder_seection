package com.example.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.KidsHeader
import com.example.ui.components.ParentControlsSheet
import com.example.ui.components.ParentLockDialog
import com.example.ui.components.PlayerSurface
import com.example.ui.components.ToddlerLockOverlay
import com.example.ui.components.VideoShelf
import com.example.ui.theme.KidsAmber
import com.example.ui.theme.KidsBlue
import com.example.ui.theme.KidsGreen
import com.example.ui.theme.KidsOrange
import com.example.ui.theme.KidsPurple
import com.example.ui.theme.KidsRed
import com.example.ui.theme.KidsYellow
import com.example.viewmodel.KidsTubeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KidsTubeScreen(
    viewModel: KidsTubeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()

    // Stabilize playlist & currentVideo so progress-update recompositions (every 500ms)
    // do NOT cause VideoShelf to re-render/re-layout items
    val stablePlaylist by remember { derivedStateOf { uiState.displayPlaylist } }
    val stableCurrentVideo by remember { derivedStateOf { uiState.currentVideo } }
    val stableFolders by remember { derivedStateOf { uiState.folders } }
    val stableSelectedFolder by remember { derivedStateOf { uiState.selectedFolder } }

    var areOverlaysVisible by remember { mutableStateOf(true) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun startHideTimer() {
        hideJob?.cancel()
        if (uiState.isParentMode || uiState.isToddlerLockActive) return
        hideJob = coroutineScope.launch {
            delay(3500)
            areOverlaysVisible = false
        }
    }

    LaunchedEffect(uiState.isPlaying, uiState.isParentMode, uiState.isToddlerLockActive) {
        if (uiState.isParentMode) {
            areOverlaysVisible = true
            hideJob?.cancel()
        } else if (uiState.isPlaying && !uiState.isToddlerLockActive) {
            startHideTimer()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!uiState.isToddlerLockActive) {
                    areOverlaysVisible = !areOverlaysVisible
                    if (areOverlaysVisible) {
                        startHideTimer()
                    } else {
                        hideJob?.cancel()
                    }
                }
            }
    ) {
        // 1. Full Screen Video Player
        if (uiState.currentVideo != null && !uiState.isScreenTimeUp) {
            PlayerSurface(
                video = uiState.currentVideo,
                isPlaying = uiState.isPlaying,
                playTrigger = uiState.playTrigger,
                seekRequestMs = uiState.seekRequestMs,
                onSeekHandled = { viewModel.onSeekHandled() },
                onPlaybackStateChanged = { isPlaying, isBuffering ->
                    viewModel.onPlayerPlaybackStateChanged(isPlaying, isBuffering)
                },
                onProgressUpdate = { currentPos, duration ->
                    viewModel.onProgressUpdate(currentPos, duration)
                },
                onVideoFinished = {
                    viewModel.onVideoFinished()
                },
                onError = { errorDesc ->
                    viewModel.onPlayerError(errorDesc)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Playback Failure State Overlay (MX Player Parity: Clearly inform user and offer recovery)
            if (uiState.isPlaybackError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xEE0D0E17)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = KidsRed.copy(alpha = 0.2f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.VideocamOff,
                                    contentDescription = "Playback Error",
                                    tint = KidsRed,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Cannot Play Video",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = uiState.currentVideo?.title ?: "Unknown video",
                            color = KidsYellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = uiState.playbackErrorMessage ?: "Format unsupported or source unavailable",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.retryCurrentVideo() },
                                colors = ButtonDefaults.buttonColors(containerColor = KidsBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Retry 🔄", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.playNextVideo() },
                                colors = ButtonDefaults.buttonColors(containerColor = KidsOrange),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Next Video ⏭️", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if ((uiState.isLoadingVideo || uiState.isBuffering) && !uiState.isPlaying) {
                // Explicit Video Loading / Buffering Indicator
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x44000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = KidsYellow,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.isLoadingVideo) "Loading video..." else "Buffering...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (uiState.isScreenTimeUp) {
            // Screen Time Expired Visual Bedtime Screen
            ScreenTimeUpView(
                onParentUnlock = { viewModel.requestParentMode() }
            )
        } else {
            // Empty Library State
            EmptyLibraryView(
                onOpenParentSettings = { viewModel.requestParentMode() },
                onLoadSamples = { viewModel.restoreSampleVideos() }
            )
        }

        // 2. Top Header Overlay
        AnimatedVisibility(
            visible = areOverlaysVisible && !uiState.isToddlerLockActive && !uiState.isScreenTimeUp,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xEE000000), Color(0x88000000), Color.Transparent)
                        )
                    )
            ) {
                KidsHeader(
                    isParentMode = uiState.isParentMode,
                    isToddlerLockActive = uiState.isToddlerLockActive,
                    screenTimeRemainingSeconds = uiState.screenTimerRemainingSeconds,
                    onToddlerLockClick = { viewModel.toggleToddlerLock() },
                    onParentModeClick = { viewModel.requestParentMode() }
                )
            }
        }

        // 3. Bottom Video Shelf, Playbar & Seekbar Overlay
        AnimatedVisibility(
            visible = areOverlaysVisible && !uiState.isToddlerLockActive && !uiState.isScreenTimeUp,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xAA000000), Color(0xF5000000))
                        )
                    )
                    .padding(bottom = 8.dp)
            ) {
                // Play button inline with Seekbar Row (Seekbar occupies flexible area: weight 1f)
                if (uiState.currentVideo != null && uiState.durationMs > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Previous Button
                        Surface(
                            shape = CircleShape,
                            color = Color(0x66000000),
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.playPreviousVideo()
                                    startHideTimer()
                                },
                                modifier = Modifier.testTag("prev_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Play / Pause Button
                        Surface(
                            shape = CircleShape,
                            color = KidsRed,
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.togglePlayPause()
                                    startHideTimer()
                                },
                                modifier = Modifier.testTag("play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Next Button
                        Surface(
                            shape = CircleShape,
                            color = Color(0x66000000),
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.playNextVideo()
                                    startHideTimer()
                                },
                                modifier = Modifier.testTag("next_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Next Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Shuffle Toggle Indicator / Button
                        Surface(
                            shape = CircleShape,
                            color = if (uiState.isShuffleMode) KidsPurple else Color(0x44000000),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.toggleShuffle()
                                    startHideTimer()
                                },
                                modifier = Modifier.testTag("shuffle_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = "Toggle Shuffle",
                                    tint = if (uiState.isShuffleMode) Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Current Position Time Text
                        Text(
                            text = formatTime(uiState.currentPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Seekbar (Takes 1-part proportional flexible area: weight 1f)
                        Slider(
                            value = if (uiState.durationMs > 0) {
                                (uiState.currentPositionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f)
                            } else 0f,
                            onValueChange = { fraction ->
                                if (uiState.durationMs > 0) {
                                    val targetMs = (fraction * uiState.durationMs).toLong()
                                    viewModel.seekTo(targetMs)
                                    startHideTimer()
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = KidsYellow,
                                activeTrackColor = KidsRed,
                                inactiveTrackColor = Color(0x66FFFFFF)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_seekbar_slider")
                        )

                        // Total Duration Time Text
                        Text(
                            text = formatTime(uiState.durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Horizontal Video Carousel Shelf (Directly underneath Seekbar)
                VideoShelf(
                    videos = stablePlaylist,
                    allVideos = uiState.videos,
                    currentVideo = stableCurrentVideo,
                    folders = stableFolders,
                    selectedFolder = stableSelectedFolder,
                    onSelectFolder = {
                        viewModel.filterByFolder(it)
                        startHideTimer()
                    },
                    onSelectVideo = {
                        viewModel.playVideo(it)
                        startHideTimer()
                    }
                )
            }
        }

        // 5. Toddler Lock Overlay
        ToddlerLockOverlay(
            isLocked = uiState.isToddlerLockActive,
            onUnlock = { viewModel.unlockToddlerLock() }
        )

        // 6. Parent Math Gate Dialog
        if (uiState.showParentLockDialog) {
            ParentLockDialog(
                question = uiState.parentMathQuestion,
                onVerify = { viewModel.verifyParentAnswer(it) },
                onDismiss = { viewModel.dismissParentLockDialog() }
            )
        }

        // 7. Parent Controls Bottom Sheet
        if (uiState.isParentMode) {
            ParentControlsSheet(
                videos = uiState.videos,
                isAutoPlay = uiState.isAutoPlayNext,
                isShuffleMode = uiState.isShuffleMode,
                screenTimerMinutes = uiState.screenTimerMinutes,
                trackedFolders = uiState.trackedFolders,
                onImportFolder = { viewModel.importFolder(it) },
                onImportFiles = { viewModel.importUris(it, context) },
                onScanDevice = { viewModel.scanDeviceVideos() },
                onRestoreSamples = { viewModel.restoreSampleVideos() },
                onRemoveVideo = { viewModel.removeVideo(it) },
                onClearAll = { viewModel.clearAllVideos() },
                onToggleAutoPlay = { viewModel.toggleAutoPlay() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onSetScreenTimer = { viewModel.setScreenTimeLimit(it) },
                onToggleFolderEnabled = { folderId, isEnabled -> viewModel.toggleFolderEnabled(folderId, isEnabled) },
                onRemoveFolder = { folderId -> viewModel.removeTrackedFolder(folderId) },
                onRescanFolder = { folderId -> viewModel.rescanTrackedFolder(folderId) },
                onReGrantFolderPermission = { folderId, uri -> viewModel.reGrantFolderPermission(folderId, uri) },
                onDismiss = { viewModel.exitParentMode() }
            )
        }
    }
}

@Composable
fun EmptyLibraryView(
    onOpenParentSettings: () -> Unit,
    onLoadSamples: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F101A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = KidsRed.copy(alpha = 0.2f),
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Stars,
                        contentDescription = null,
                        tint = KidsYellow,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome to KidsTube! 🧸",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No offline videos loaded yet.\nParents can select a folder or load sample videos to start watching!",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onLoadSamples,
                    colors = ButtonDefaults.buttonColors(containerColor = KidsOrange),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Load Sample Videos 🎬", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenParentSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = KidsBlue),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Parent Settings ⚙️", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ScreenTimeUpView(
    onParentUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B14)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = KidsAmber.copy(alpha = 0.2f),
                modifier = Modifier.size(110.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = "Bedtime",
                        tint = KidsAmber,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Screen Time is Over! 🌙",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Great job watching today! Time to rest your eyes and play outside 🌟",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onParentUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = KidsAmber),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Parents Unlock", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
