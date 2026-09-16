package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ParentProtectedAction
import com.example.model.PlaybackConstants
import com.example.model.TrackedFolderItem
import com.example.model.VideoItem
import com.example.model.calculateResumePosition
import com.example.model.isPlaybackCompleted
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
    val displayPlaylist: List<VideoItem> = emptyList(),
    val selectedVideoId: String? = null,
    val selectedVideo: VideoItem? = null,
    val currentVideo: VideoItem? = null,
    val isSelectionAreaVisible: Boolean = true,
    val isLoadingVideo: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val isPlaybackError: Boolean = false,
    val playbackErrorMessage: String? = null,
    val isLoading: Boolean = true,
    val isParentMode: Boolean = false,
    val isFolderManagerMode: Boolean = false,
    val pendingParentAction: ParentProtectedAction? = null,
    val showParentLockDialog: Boolean = false,
    val parentMathQuestion: String = "",
    val parentMathAnswer: Int = 0,
    val isToddlerLockActive: Boolean = false,
    val selectedFolder: String? = null,
    val folders: List<String> = emptyList(),
    val trackedFolders: List<TrackedFolderItem> = emptyList(),
    val screenTimerMinutes: Int? = null,
    val screenTimerRemainingSeconds: Long? = null,
    val isScreenTimeUp: Boolean = false,
    val isAutoPlayNext: Boolean = true,
    val isShuffleMode: Boolean = false,
    val retryAttempt: Int = 0,
    val toastMessage: String? = null,
    val playTrigger: Long = 0L,
    val seekRequestMs: Long? = null,
    val pendingResumePositionMs: Long? = null
)

class KidsTubeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)

    private val _uiState = MutableStateFlow(KidsTubeUiState())
    val uiState: StateFlow<KidsTubeUiState> = _uiState.asStateFlow()

    private var screenTimerJob: Job? = null

    // Navigation history stack for Previous button
    private val historyStack = ArrayDeque<String>()
    // Set of played video IDs in current shuffle cycle to prevent repeats
    private val playedVideoIds = mutableSetOf<String>()
    private var lastSaveProgressTimestamp: Long = 0L

    init {
        observeTrackedFolders()
        observeActiveVideosFlow()
        loadVideos()
    }

    private fun observeTrackedFolders() {
        viewModelScope.launch {
            repository.getTrackedFolderItemsFlow().collect { folderItems ->
                _uiState.update { it.copy(trackedFolders = folderItems) }
            }
        }
    }

    private fun observeActiveVideosFlow() {
        viewModelScope.launch {
            repository.getActiveVideosFlow().collect { activeList ->
                if (activeList.isEmpty() && _uiState.value.videos.isEmpty()) return@collect

                val currentSelectedId = _uiState.value.selectedVideoId ?: _uiState.value.currentVideo?.id
                val preserved = if (currentSelectedId != null) activeList.find { it.id == currentSelectedId } else null
                val targetVideo = preserved ?: activeList.firstOrNull()
                val folders = activeList.map { it.folderName }.distinct().sorted()
                val filtered = getFilteredVideos(selectedFolder = _uiState.value.selectedFolder, allVideos = activeList)

                _uiState.update { current ->
                    current.copy(
                        videos = activeList,
                        displayPlaylist = filtered,
                        folders = folders,
                        selectedVideoId = targetVideo?.id,
                        selectedVideo = targetVideo,
                        currentVideo = if (current.currentVideo != null && preserved == null && activeList.isNotEmpty()) targetVideo else (preserved ?: current.currentVideo ?: targetVideo)
                    )
                }
            }
        }
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.videos.isEmpty(), isPlaybackError = false, playbackErrorMessage = null) }
            repository.checkAndReconcilePermissions()
            val list = repository.getSavedVideos()
            val folders = list.map { it.folderName }.distinct().sorted()

            // Preserve existing selection if valid
            val currentSelectedId = _uiState.value.selectedVideoId ?: _uiState.value.currentVideo?.id
            val preservedVideo = if (currentSelectedId != null) list.find { it.id == currentSelectedId } else null
            val targetVideo = preservedVideo ?: list.firstOrNull()
            val filtered = getFilteredVideos(selectedFolder = _uiState.value.selectedFolder, allVideos = list)

            _uiState.update { current ->
                current.copy(
                    videos = list,
                    displayPlaylist = filtered,
                    folders = folders,
                    selectedVideoId = targetVideo?.id,
                    selectedVideo = targetVideo,
                    currentVideo = preservedVideo ?: current.currentVideo ?: targetVideo,
                    isLoadingVideo = (preservedVideo ?: current.currentVideo ?: targetVideo) != null && current.isPlaying,
                    isBuffering = false,
                    isLoading = false
                )
            }
            targetVideo?.let { playedVideoIds.add(it.id) }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), list)
            }
        }
    }

    fun playVideo(video: VideoItem, isRetry: Boolean = false, recordHistory: Boolean = true) {
        val current = _uiState.value.currentVideo
        if (current != null && current.id != video.id) {
            val currentPos = _uiState.value.currentPositionMs
            val duration = _uiState.value.durationMs
            if (currentPos > 0 && duration > 0) {
                val completed = isPlaybackCompleted(currentPos, duration)
                persistPlaybackProgress(current.id, currentPos, duration, completed)
            }
            if (recordHistory) {
                historyStack.addLast(current.id)
                if (historyStack.size > 50) {
                    historyStack.removeFirst()
                }
            }
        }
        playedVideoIds.add(video.id)

        // Calculate resume position deterministically
        val resumePos = calculateResumePosition(
            positionMs = video.playbackPositionMs,
            durationMs = video.durationMs,
            isCompleted = video.isCompleted
        )

        // Maintain stable display playlist matching currently active folder
        val filtered = getFilteredVideos(_uiState.value.selectedFolder)

        _uiState.update {
            it.copy(
                selectedVideoId = video.id,
                selectedVideo = video,
                currentVideo = video,
                displayPlaylist = filtered,
                isLoadingVideo = true,
                isBuffering = true,
                isPlaying = false,
                isPlaybackError = false,
                playbackErrorMessage = null,
                currentPositionMs = resumePos,
                durationMs = video.durationMs,
                pendingResumePositionMs = if (resumePos > 0) resumePos else null,
                isSelectionAreaVisible = true, // Ensure selection area remains usable
                playTrigger = System.currentTimeMillis(),
                retryAttempt = if (isRetry) it.retryAttempt + 1 else 0
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val freshProgress = repository.getPlaybackProgress(video.id)
            if (freshProgress != null) {
                val freshResume = calculateResumePosition(
                    positionMs = freshProgress.positionMs,
                    durationMs = freshProgress.durationMs,
                    isCompleted = freshProgress.isCompleted
                )
                if (freshResume > 0) {
                    _uiState.update { it.copy(pendingResumePositionMs = freshResume) }
                }
            }
        }
    }

    fun retryCurrentVideo() {
        val current = _uiState.value.currentVideo ?: return
        playVideo(current, isRetry = true)
    }

    fun onPlayerPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        val resumeMs = _uiState.value.pendingResumePositionMs
        if (isPlaying && resumeMs != null && resumeMs > 0) {
            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isLoadingVideo = false,
                    isPlaybackError = false,
                    pendingResumePositionMs = null
                )
            }
            seekTo(resumeMs)
        } else {
            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isLoadingVideo = if (isPlaying) false else it.isLoadingVideo,
                    isPlaybackError = if (isPlaying) false else it.isPlaybackError
                )
            }
        }

        if (!isPlaying) {
            flushCurrentProgress()
        }
    }

    fun onProgressUpdate(currentPos: Long, totalDuration: Long) {
        val duration = if (totalDuration > 0) totalDuration else _uiState.value.durationMs
        _uiState.update {
            it.copy(
                currentPositionMs = currentPos,
                durationMs = duration,
                isLoadingVideo = false
            )
        }

        val now = System.currentTimeMillis()
        if (now - lastSaveProgressTimestamp >= PlaybackConstants.SAVE_PROGRESS_INTERVAL_MS) {
            lastSaveProgressTimestamp = now
            val current = _uiState.value.currentVideo
            if (current != null && currentPos > 0 && duration > 0) {
                val isCompleted = isPlaybackCompleted(currentPos, duration)
                persistPlaybackProgress(current.id, currentPos, duration, isCompleted)
            }
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
        val current = _uiState.value.currentVideo
        Log.e(
            "KidsTubePlayer",
            "Playback Error: $errorDescription | Video: ${current?.title} | Uri: ${current?.uriString}"
        )

        _uiState.update {
            it.copy(
                isLoadingVideo = false,
                isPlaying = false,
                isBuffering = false,
                isPlaybackError = true,
                playbackErrorMessage = errorDescription
            )
        }
    }

    fun onVideoFinished() {
        val current = _uiState.value.currentVideo
        if (current != null) {
            val duration = _uiState.value.durationMs
            persistPlaybackProgress(current.id, 0L, duration, isCompleted = true)
            viewModelScope.launch(Dispatchers.IO) {
                repository.recordWatchHistory(current.id, duration)
            }
        }

        if (_uiState.value.isAutoPlayNext) {
            // MX Player Behavior: Seamlessly & silently load next video without any intrusive toast
            playNextVideo()
        }
    }

    fun playNextVideo() {
        val filtered = getFilteredVideos()
        if (filtered.isEmpty()) return

        val currentId = _uiState.value.currentVideo?.id

        if (_uiState.value.isShuffleMode) {
            // SHUFFLE MODE: Pick non-repeating random video from candidates
            val unplayedCandidates = filtered.filter { it.id !in playedVideoIds && it.id != currentId }
            val nextVideo = when {
                unplayedCandidates.isNotEmpty() -> unplayedCandidates.random()
                filtered.size > 1 -> {
                    // Reset played cycle, exclude current video
                    playedVideoIds.clear()
                    currentId?.let { playedVideoIds.add(it) }
                    filtered.filter { it.id != currentId }.random()
                }
                else -> filtered.first()
            }
            playVideo(nextVideo)
        } else {
            // SEQUENTIAL MODE: Strict deterministic index navigation (MX Player standard)
            val currentIndex = filtered.indexOfFirst { it.id == currentId }
            val nextIndex = if (currentIndex >= 0 && currentIndex + 1 < filtered.size) currentIndex + 1 else 0
            playVideo(filtered[nextIndex])
        }
    }

    fun playPreviousVideo() {
        val filtered = getFilteredVideos()
        if (filtered.isEmpty()) return

        if (_uiState.value.isShuffleMode && historyStack.isNotEmpty()) {
            val previousId = historyStack.removeLast()
            val prevVideo = filtered.find { it.id == previousId } ?: filtered.first()
            playVideo(prevVideo, recordHistory = false)
        } else {
            val currentIndex = filtered.indexOfFirst { it.id == _uiState.value.currentVideo?.id }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else filtered.size - 1
            playVideo(filtered[prevIndex], recordHistory = false)
        }
    }

    fun toggleShuffle() {
        _uiState.update {
            val nextState = !it.isShuffleMode
            it.copy(
                isShuffleMode = nextState,
                toastMessage = if (nextState) "Shuffle Mode ON 🔀" else "Sequential Mode 🔁"
            )
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isScreenTimeUp) return
        if (_uiState.value.isPlaybackError) {
            retryCurrentVideo()
            return
        }
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun requestProtectedAction(action: ParentProtectedAction) {
        if (_uiState.value.isParentMode) {
            executeProtectedAction(action)
            return
        }
        val a = Random.nextInt(3, 9)
        val b = Random.nextInt(3, 9)
        _uiState.update {
            it.copy(
                pendingParentAction = action,
                showParentLockDialog = true,
                parentMathQuestion = "$a × $b",
                parentMathAnswer = a * b
            )
        }
    }

    fun requestParentMode() {
        requestProtectedAction(ParentProtectedAction.OpenParentDashboard)
    }

    fun requestFolderManagement() {
        requestProtectedAction(ParentProtectedAction.OpenFolderManager)
    }

    fun dismissParentLockDialog() {
        _uiState.update {
            it.copy(
                showParentLockDialog = false,
                pendingParentAction = null
            )
        }
    }

    fun verifyParentAnswer(userAnswer: Int): Boolean {
        if (userAnswer == _uiState.value.parentMathAnswer) {
            val action = _uiState.value.pendingParentAction ?: ParentProtectedAction.OpenParentDashboard
            _uiState.update {
                it.copy(
                    showParentLockDialog = false,
                    pendingParentAction = null,
                    isToddlerLockActive = false
                )
            }
            executeProtectedAction(action)
            return true
        }
        return false
    }

    private fun executeProtectedAction(action: ParentProtectedAction) {
        when (action) {
            ParentProtectedAction.OpenFolderManager -> {
                _uiState.update {
                    it.copy(
                        isParentMode = true,
                        isFolderManagerMode = true,
                        toastMessage = "Folder Management opened 📁"
                    )
                }
            }
            ParentProtectedAction.OpenParentDashboard -> {
                _uiState.update {
                    it.copy(
                        isParentMode = true,
                        isFolderManagerMode = false,
                        toastMessage = "Parent controls unlocked 🛡️"
                    )
                }
            }
        }
    }

    fun exitParentMode() {
        _uiState.update {
            it.copy(
                isParentMode = false,
                isFolderManagerMode = false
            )
        }
    }

    fun setSelectionAreaVisible(visible: Boolean) {
        _uiState.update { it.copy(isSelectionAreaVisible = visible) }
    }

    fun toggleSelectionArea() {
        _uiState.update { it.copy(isSelectionAreaVisible = !it.isSelectionAreaVisible) }
    }

    fun flushCurrentProgress() {
        val current = _uiState.value.currentVideo ?: return
        val pos = _uiState.value.currentPositionMs
        val duration = _uiState.value.durationMs
        if (pos > 0 && duration > 0) {
            val completed = isPlaybackCompleted(pos, duration)
            persistPlaybackProgress(current.id, pos, duration, completed)
        }
    }

    private fun persistPlaybackProgress(videoId: String, positionMs: Long, durationMs: Long, isCompleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePlaybackProgress(videoId, positionMs, durationMs, isCompleted)
        }
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

        _uiState.update {
            it.copy(
                selectedFolder = folder,
                displayPlaylist = filtered
            )
        }

        if (filtered.isNotEmpty() && !isCurrentInFiltered) {
            playVideo(filtered.first())
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val newVideos = repository.addOrUpdateTrackedFolder(treeUri)
            loadVideos()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    toastMessage = if (newVideos.isNotEmpty()) "${newVideos.size} active video(s) ready!" else "Folder indexed, but no compatible videos found"
                )
            }
        }
    }

    fun toggleFolderEnabled(folderId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.setFolderEnabled(folderId, isEnabled)
            loadVideos()
            _uiState.update {
                it.copy(toastMessage = if (isEnabled) "Folder enabled for kids" else "Folder hidden from kids")
            }
        }
    }

    fun removeTrackedFolder(folderId: String) {
        viewModelScope.launch {
            repository.removeTrackedFolder(folderId)
            loadVideos()
            _uiState.update {
                it.copy(toastMessage = "Folder removed from library (storage files kept safe)")
            }
        }
    }

    fun rescanTrackedFolder(folderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val updatedVideos = repository.rescanTrackedFolder(folderId)
            loadVideos()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    toastMessage = "Folder rescanned (${updatedVideos.size} active videos)"
                )
            }
        }
    }

    fun reGrantFolderPermission(folderId: String, newUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val updated = repository.reGrantFolderPermission(folderId, newUri)
            loadVideos()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    toastMessage = "Permission restored! (${updated.size} active videos)"
                )
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

            existing.addAll(toAdd)
            val sorted = repository.sortVideosDeterministically(existing)
            repository.saveVideos(sorted)
            val folders = sorted.map { it.folderName }.distinct().sorted()

            val activePlaylist = if (_uiState.value.selectedFolder == null) {
                sorted
            } else {
                sorted.filter { it.folderName == _uiState.value.selectedFolder || it.folderName.startsWith("${_uiState.value.selectedFolder} /") }
            }

            _uiState.update {
                it.copy(
                    videos = sorted,
                    displayPlaylist = activePlaylist,
                    folders = folders,
                    isLoading = false,
                    toastMessage = "${toAdd.size} video(s) added!"
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), sorted)
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
            val sorted = repository.sortVideosDeterministically(existing)
            repository.saveVideos(sorted)
            val folders = sorted.map { it.folderName }.distinct().sorted()

            val activePlaylist = if (_uiState.value.selectedFolder == null) {
                sorted
            } else {
                sorted.filter { it.folderName == _uiState.value.selectedFolder || it.folderName.startsWith("${_uiState.value.selectedFolder} /") }
            }

            val msg = if (toAdd.isNotEmpty()) {
                "Found ${toAdd.size} new video(s) on device!"
            } else {
                "All ${scanned.size} device videos are already in library"
            }

            _uiState.update {
                it.copy(
                    videos = sorted,
                    displayPlaylist = activePlaylist,
                    folders = folders,
                    isLoading = false,
                    toastMessage = msg
                )
            }
            if (_uiState.value.currentVideo == null && sorted.isNotEmpty()) {
                playVideo(sorted.first())
            }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), sorted)
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
            val sorted = repository.sortVideosDeterministically(existing)
            repository.saveVideos(sorted)
            val folders = sorted.map { it.folderName }.distinct().sorted()

            val activePlaylist = if (_uiState.value.selectedFolder == null) {
                sorted
            } else {
                sorted.filter { it.folderName == _uiState.value.selectedFolder || it.folderName.startsWith("${_uiState.value.selectedFolder} /") }
            }

            _uiState.update {
                it.copy(
                    videos = sorted,
                    displayPlaylist = activePlaylist,
                    folders = folders,
                    toastMessage = "Added ${toAdd.size} kid sample videos!"
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                ThumbnailHelper.preloadThumbnails(getApplication(), sorted)
            }
            if (_uiState.value.currentVideo == null && sorted.isNotEmpty()) {
                playVideo(sorted.first())
            }
        }
    }

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            val updated = _uiState.value.videos.filterNot { it.id == videoId }
            val sorted = repository.sortVideosDeterministically(updated)
            repository.saveVideos(sorted)
            val folders = sorted.map { it.folderName }.distinct().sorted()

            var newCurrent = _uiState.value.currentVideo
            if (newCurrent?.id == videoId) {
                newCurrent = sorted.firstOrNull()
            }

            val activePlaylist = if (_uiState.value.selectedFolder == null) {
                sorted
            } else {
                sorted.filter { it.folderName == _uiState.value.selectedFolder || it.folderName.startsWith("${_uiState.value.selectedFolder} /") }
            }

            _uiState.update {
                it.copy(
                    videos = sorted,
                    displayPlaylist = activePlaylist,
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
                    displayPlaylist = emptyList(),
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

    fun getFilteredVideos(
        selectedFolder: String? = _uiState.value.selectedFolder,
        allVideos: List<VideoItem> = _uiState.value.videos
    ): List<VideoItem> {
        if (selectedFolder == null) return allVideos
        return allVideos.filter {
            it.folderName == selectedFolder || it.folderName.startsWith("$selectedFolder /")
        }
    }

    override fun onCleared() {
        super.onCleared()
        flushCurrentProgress()
    }
}
