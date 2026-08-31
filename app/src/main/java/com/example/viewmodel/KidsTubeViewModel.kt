package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.VideoItem
import com.example.repository.VideoRepository
import com.example.util.ThumbnailHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class KidsTubeUiState(
    val videos: List<VideoItem> = emptyList(),
    val currentVideo: VideoItem? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
    val isParentMode: Boolean = false,
    val showParentLockDialog: Boolean = false,
    val parentMathQuestion: String = "",
    val parentMathAnswer: Int = 0,
    val isToddlerLockActive: Boolean = false,
    val selectedFolder: String? = null,
    val folders: List<String> = emptyList(),
    val screenTimerMinutes: Int? = null,
    val screenTimerRemainingSeconds: Long? = null,
    val isScreenTimeUp: Boolean = false,
    val isAutoPlayNext: Boolean = true,
    val isShuffleMode: Boolean = true,
    val retryAttempt: Int = 0,
    val toastMessage: String? = null,
    val playTrigger: Long = 0L,
    val seekRequestMs: Long? = null,
    val displayPlaylist: List<VideoItem> = emptyList()
)

class KidsTubeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)

    private val _uiState = MutableStateFlow(KidsTubeUiState())
    val uiState: StateFlow<KidsTubeUiState> = _uiState.asStateFlow()

    private var screenTimerJob: Job? = null
    private val maxRetries = 2

    // History for previous button in shuffle mode
    private val historyStack = ArrayDeque<String>()
    // Set of played video IDs in current shuffle cycle to prevent repeats
    private val playedVideoIds = mutableSetOf<String>()

    init {
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val list = repository.getSavedVideos()
            val folders = list.map { it.folderName }.distinct()
            val initialVideo = list.firstOrNull()

            val initialPlaylist = if (list.isNotEmpty()) {
                if (_uiState.value.isShuffleMode) list.shuffled() else list
            } else emptyList()

            _uiState.update {
                it.copy(
                    videos = list,
                    folders = folders,
                    currentVideo = initialVideo,
                    displayPlaylist = initialPlaylist,
                    isLoading = false
                )
            }
            initialVideo?.let { playedVideoIds.add(it.id) }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), list)
            }
        }
    }

    fun playVideo(video: VideoItem, isRetry: Boolean = false, recordHistory: Boolean = true, reshufflePlaylist: Boolean = true) {
        if (!isRetry) {
            _uiState.update { it.copy(retryAttempt = 0, errorMessage = null) }
        }
        val current = _uiState.value.currentVideo
        if (recordHistory && current != null && current.id != video.id) {
            historyStack.addLast(current.id)
            if (historyStack.size > 50) {
                historyStack.removeFirst()
            }
        }
        playedVideoIds.add(video.id)

        // Only reshuffle the display playlist when user manually selects a video (not during autoplay)
        val newDisplayList = if (reshufflePlaylist) {
            val filtered = getFilteredVideos(selectedFolder = _uiState.value.selectedFolder)
            if (_uiState.value.isShuffleMode) {
                val remaining = filtered.filter { it.id != video.id }.shuffled()
                listOf(video) + remaining
            } else {
                val idx = filtered.indexOfFirst { it.id == video.id }
                if (idx >= 0) {
                    filtered.subList(idx, filtered.size) + filtered.subList(0, idx)
                } else {
                    filtered
                }
            }
        } else {
            // Keep existing playlist order stable, don't reshuffle
            _uiState.value.displayPlaylist
        }

        _uiState.update {
            it.copy(
                currentVideo = video,
                isPlaying = true,
                currentPositionMs = 0L,
                playTrigger = System.currentTimeMillis(),
                displayPlaylist = newDisplayList
            )
        }
    }

    fun onPlayerPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        _uiState.update {
            it.copy(
                isPlaying = isPlaying,
                isBuffering = isBuffering
            )
        }
    }

    fun onProgressUpdate(currentPos: Long, totalDuration: Long) {
        _uiState.update {
            it.copy(
                currentPositionMs = currentPos,
                durationMs = if (totalDuration > 0) totalDuration else it.durationMs
            )
        }
    }

    fun seekTo(positionMs: Long) {
        _uiState.update {
            it.copy(
                currentPositionMs = positionMs,
                seekRequestMs = positionMs
            )
        }
    }

    fun onSeekHandled() {
        _uiState.update { it.copy(seekRequestMs = null) }
    }

    fun onPlayerError(errorDescription: String) {
        val current = _uiState.value.currentVideo ?: return
        // Mark failed item so it doesn't repeat in shuffle cycle
        playedVideoIds.add(current.id)
        _uiState.update {
            it.copy(retryAttempt = 0, errorMessage = null)
        }
        // MX Player behavior: Instantly and silently skip to the next playable video with zero toast or lag
        playNextVideo()
    }

    fun onVideoFinished() {
        if (_uiState.value.isAutoPlayNext) {
            playNextVideo()
        }
    }

    fun playNextVideo() {
        val filtered = getFilteredVideos()
        if (filtered.isEmpty()) return

        val currentId = _uiState.value.currentVideo?.id

        if (_uiState.value.isShuffleMode) {
            // SHUFFLE MODE: Pick non-repeating random video from filtered playlist
            val unplayedCandidates = filtered.filter { it.id !in playedVideoIds && it.id != currentId }
            val nextVideo = when {
                unplayedCandidates.isNotEmpty() -> unplayedCandidates.random()
                filtered.size > 1 -> {
                    // Reset played cycle, exclude only current video
                    playedVideoIds.clear()
                    currentId?.let { playedVideoIds.add(it) }
                    filtered.filter { it.id != currentId }.random()
                }
                else -> filtered.first()
            }
            playVideo(nextVideo, reshufflePlaylist = false)
        } else {
            // SEQUENTIAL MODE
            val currentIndex = filtered.indexOfFirst { it.id == currentId }
            val nextIndex = if (currentIndex >= 0 && currentIndex + 1 < filtered.size) currentIndex + 1 else 0
            playVideo(filtered[nextIndex], reshufflePlaylist = false)
        }
    }

    fun playPreviousVideo() {
        val filtered = getFilteredVideos()
        if (filtered.isEmpty()) return

        if (_uiState.value.isShuffleMode && historyStack.isNotEmpty()) {
            val previousId = historyStack.removeLast()
            val prevVideo = filtered.find { it.id == previousId } ?: filtered.first()
            playVideo(prevVideo, recordHistory = false, reshufflePlaylist = false)
        } else {
            val currentIndex = filtered.indexOfFirst { it.id == _uiState.value.currentVideo?.id }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else filtered.size - 1
            playVideo(filtered[prevIndex], recordHistory = false, reshufflePlaylist = false)
        }
    }

    fun toggleShuffle() {
        _uiState.update {
            val nextState = !it.isShuffleMode
            val filtered = getFilteredVideos(it.selectedFolder)
            val current = it.currentVideo
            val newPlaylist = if (nextState) {
                if (current != null) {
                    listOf(current) + filtered.filter { v -> v.id != current.id }.shuffled()
                } else filtered.shuffled()
            } else {
                filtered
            }
            it.copy(
                isShuffleMode = nextState,
                displayPlaylist = newPlaylist,
                toastMessage = if (nextState) "Random Shuffle Mode ON 🔀" else "Sequential Mode 🔁"
            )
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isScreenTimeUp) return
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun requestParentMode() {
        if (_uiState.value.isParentMode) {
            _uiState.update { it.copy(isParentMode = false) }
            return
        }
        val a = Random.nextInt(3, 9)
        val b = Random.nextInt(3, 9)
        _uiState.update {
            it.copy(
                showParentLockDialog = true,
                parentMathQuestion = "$a × $b",
                parentMathAnswer = a * b
            )
        }
    }

    fun dismissParentLockDialog() {
        _uiState.update { it.copy(showParentLockDialog = false) }
    }

    fun verifyParentAnswer(userAnswer: Int): Boolean {
        if (userAnswer == _uiState.value.parentMathAnswer) {
            _uiState.update {
                it.copy(
                    isParentMode = true,
                    showParentLockDialog = false,
                    isToddlerLockActive = false,
                    toastMessage = "Parent controls unlocked"
                )
            }
            return true
        }
        return false
    }

    fun exitParentMode() {
        _uiState.update { it.copy(isParentMode = false) }
    }

    fun toggleToddlerLock() {
        _uiState.update {
            val newLock = !it.isToddlerLockActive
            it.copy(
                isToddlerLockActive = newLock,
                toastMessage = if (newLock) "Screen locked! Long-press lock to unlock" else "Screen unlocked"
            )
        }
    }

    fun unlockToddlerLock() {
        _uiState.update {
            it.copy(
                isToddlerLockActive = false,
                toastMessage = "Screen unlocked"
            )
        }
    }

    fun filterByFolder(folder: String?) {
        val filtered = getFilteredVideos(selectedFolder = folder)
        val current = _uiState.value.currentVideo
        val isCurrentInFiltered = current != null && filtered.any { it.id == current.id }

        val newPlaylist = if (filtered.isNotEmpty()) {
            if (_uiState.value.isShuffleMode) filtered.shuffled() else filtered
        } else emptyList()

        _uiState.update {
            it.copy(
                selectedFolder = folder,
                displayPlaylist = newPlaylist
            )
        }

        if (filtered.isNotEmpty() && !isCurrentInFiltered) {
            playVideo(filtered.first())
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val newVideos = repository.scanDocumentTree(treeUri)
            if (newVideos.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        toastMessage = "No compatible videos found in folder"
                    )
                }
                return@launch
            }

            val existing = _uiState.value.videos.toMutableList()
            val existingUris = existing.map { it.uriString }.toSet()
            val toAdd = newVideos.filterNot { it.uriString in existingUris }

            existing.addAll(0, toAdd)
            repository.saveVideos(existing)
            val folders = existing.map { it.folderName }.distinct()

            _uiState.update {
                it.copy(
                    videos = existing,
                    folders = folders,
                    isLoading = false,
                    toastMessage = "${toAdd.size} videos added from folder!"
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), existing)
            }
            if (toAdd.isNotEmpty() && _uiState.value.currentVideo == null) {
                playVideo(toAdd.first())
            }
        }
    }

    fun importUris(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val newItems = uris.map { repository.createVideoFromUri(it, context) }
            val existing = _uiState.value.videos.toMutableList()
            val existingUris = existing.map { it.uriString }.toSet()
            val toAdd = newItems.filterNot { it.uriString in existingUris }

            existing.addAll(0, toAdd)
            repository.saveVideos(existing)
            val folders = existing.map { it.folderName }.distinct()

            _uiState.update {
                it.copy(
                    videos = existing,
                    folders = folders,
                    isLoading = false,
                    toastMessage = "${toAdd.size} video(s) added!"
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), existing)
            }
            if (toAdd.isNotEmpty() && _uiState.value.currentVideo == null) {
                playVideo(toAdd.first())
            }
        }
    }

    fun scanDeviceVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val scanned = repository.scanDeviceVideos()
            if (scanned.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        toastMessage = "No videos detected on device storage"
                    )
                }
                return@launch
            }

            val existing = _uiState.value.videos.toMutableList()
            val existingUris = existing.map { it.uriString }.toSet()
            val toAdd = scanned.filterNot { it.uriString in existingUris }

            existing.addAll(toAdd)
            repository.saveVideos(existing)
            val folders = existing.map { it.folderName }.distinct()

            val msg = if (toAdd.isNotEmpty()) {
                "Found ${toAdd.size} new video(s) on device!"
            } else {
                "All ${scanned.size} device videos are already in library"
            }

            _uiState.update {
                it.copy(
                    videos = existing,
                    folders = folders,
                    isLoading = false,
                    toastMessage = msg
                )
            }
            if (_uiState.value.currentVideo == null && existing.isNotEmpty()) {
                playVideo(existing.first())
            }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), existing)
            }
        }
    }

    fun restoreSampleVideos() {
        viewModelScope.launch {
            val samples = VideoRepository.SAMPLE_VIDEOS
            val existing = _uiState.value.videos.toMutableList()
            val existingUris = existing.map { it.uriString }.toSet()
            val toAdd = samples.filterNot { it.uriString in existingUris }

            existing.addAll(toAdd)
            repository.saveVideos(existing)
            val folders = existing.map { it.folderName }.distinct()

            _uiState.update {
                it.copy(
                    videos = existing,
                    folders = folders,
                    toastMessage = "Added ${toAdd.size} kid sample videos!"
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), existing)
            }
            if (_uiState.value.currentVideo == null && existing.isNotEmpty()) {
                playVideo(existing.first())
            }
        }
    }

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            val updated = _uiState.value.videos.filterNot { it.id == videoId }
            repository.saveVideos(updated)
            val folders = updated.map { it.folderName }.distinct()

            var newCurrent = _uiState.value.currentVideo
            if (newCurrent?.id == videoId) {
                newCurrent = updated.firstOrNull()
            }

            _uiState.update {
                it.copy(
                    videos = updated,
                    folders = folders,
                    currentVideo = newCurrent,
                    toastMessage = "Video removed"
                )
            }
        }
    }

    fun clearAllVideos() {
        viewModelScope.launch {
            repository.saveVideos(emptyList())
            _uiState.update {
                it.copy(
                    videos = emptyList(),
                    folders = emptyList(),
                    currentVideo = null,
                    isPlaying = false,
                    toastMessage = "Video library cleared"
                )
            }
        }
    }

    fun setScreenTimeLimit(minutes: Int?) {
        screenTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _uiState.update {
                it.copy(
                    screenTimerMinutes = null,
                    screenTimerRemainingSeconds = null,
                    isScreenTimeUp = false,
                    toastMessage = "Timer turned off"
                )
            }
            return
        }

        val totalSeconds = minutes * 60L
        _uiState.update {
            it.copy(
                screenTimerMinutes = minutes,
                screenTimerRemainingSeconds = totalSeconds,
                isScreenTimeUp = false,
                toastMessage = "Timer set for $minutes minutes ⏱️"
            )
        }

        screenTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(screenTimerRemainingSeconds = remaining) }
            }
            // Timer expired!
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isScreenTimeUp = true,
                    toastMessage = "Screen time is up! Time for a break 🌟"
                )
            }
        }
    }

    fun dismissScreenTimeUp() {
        _uiState.update { it.copy(isScreenTimeUp = false) }
    }

    fun toggleAutoPlay() {
        _uiState.update { it.copy(isAutoPlayNext = !it.isAutoPlayNext) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun getFilteredVideos(selectedFolder: String? = _uiState.value.selectedFolder): List<VideoItem> {
        val all = _uiState.value.videos
        if (selectedFolder == null) return all
        return all.filter {
            it.folderName == selectedFolder || it.folderName.startsWith("$selectedFolder /")
        }
    }
}
