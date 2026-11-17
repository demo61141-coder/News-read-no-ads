package com.example.ui.medialab

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AudioRecorderHelper
import com.example.data.ai.GeminiMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MediaLabViewModel"

enum class MediaToolTab(val label: String) {
    PHOTO("Photo Scan"),
    VIDEO("Video Brief"),
    AUDIO("Voice Note")
}

data class SelectedVideoInfo(
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray
)

sealed interface MediaLabUiState {
    data object Idle : MediaLabUiState
    data class Loading(val statusLabel: String) : MediaLabUiState
    data class Success(val outputText: String, val modelTag: String) : MediaLabUiState
    data class Error(val message: String) : MediaLabUiState
}

class MediaLabViewModel(
    private val geminiRepository: GeminiMediaRepository,
    private val audioRecorderHelper: AudioRecorderHelper
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(MediaToolTab.PHOTO)
    val selectedTab: StateFlow<MediaToolTab> = _selectedTab.asStateFlow()

    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    private val _selectedVideo = MutableStateFlow<SelectedVideoInfo?>(null)
    val selectedVideo: StateFlow<SelectedVideoInfo?> = _selectedVideo.asStateFlow()

    private val _customPrompt = MutableStateFlow("")
    val customPrompt: StateFlow<String> = _customPrompt.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _uiState = MutableStateFlow<MediaLabUiState>(MediaLabUiState.Idle)
    val uiState: StateFlow<MediaLabUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun selectTab(tab: MediaToolTab) {
        _selectedTab.value = tab
    }

    fun onCustomPromptChange(prompt: String) {
        _customPrompt.value = prompt
    }

    fun setPickedImage(bitmap: Bitmap?) {
        _selectedBitmap.value = bitmap
        if (bitmap != null) {
            analyzeCurrentImage()
        }
    }

    fun analyzeCurrentImage(overridePrompt: String? = null) {
        val bitmap = _selectedBitmap.value ?: return
        val promptToUse = overridePrompt ?: _customPrompt.value
        viewModelScope.launch {
            _uiState.value = MediaLabUiState.Loading("Analyzing photo with Gemini 4 Argon...")
            try {
                val result = geminiRepository.analyzeImage(bitmap, promptToUse)
                _uiState.value = MediaLabUiState.Success(
                    outputText = result,
                    modelTag = "gemini-4-argon"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image analysis timed out", e)
                _uiState.value = MediaLabUiState.Error("Image analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image analysis failed", e)
                _uiState.value = MediaLabUiState.Error(
                    e.localizedMessage ?: "Failed to analyze image."
                )
            }
        }
    }

    fun setPickedVideo(videoInfo: SelectedVideoInfo?) {
        _selectedVideo.value = videoInfo
        if (videoInfo != null) {
            analyzeCurrentVideo()
        }
    }

    fun analyzeCurrentVideo(overridePrompt: String? = null) {
        val video = _selectedVideo.value ?: return
        val promptToUse = overridePrompt ?: _customPrompt.value
        viewModelScope.launch {
            _uiState.value = MediaLabUiState.Loading("Analyzing video with Gemini 4 Argon...")
            try {
                val result = geminiRepository.analyzeVideo(
                    videoBytes = video.bytes,
                    mimeType = video.mimeType,
                    prompt = promptToUse
                )
                _uiState.value = MediaLabUiState.Success(
                    outputText = result,
                    modelTag = "gemini-4-argon"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Video analysis timed out", e)
                _uiState.value = MediaLabUiState.Error("Video analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video analysis failed", e)
                _uiState.value = MediaLabUiState.Error(
                    e.localizedMessage ?: "Failed to analyze video."
                )
            }
        }
    }

    fun startRecording() {
        try {
            audioRecorderHelper.startRecording()
            _isRecording.value = true
            _recordingSeconds.value = 0
            _uiState.value = MediaLabUiState.Idle
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000L)
                    _recordingSeconds.value = _recordingSeconds.value + 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            _isRecording.value = false
            _uiState.value = MediaLabUiState.Error(
                e.localizedMessage ?: "Could not start microphone recording."
            )
        }
    }

    fun stopRecordingAndTranscribe() {
        timerJob?.cancel()
        _isRecording.value = false
        val audioBytes = try {
            audioRecorderHelper.stopAndReadBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recorder", e)
            _uiState.value = MediaLabUiState.Error(
                e.localizedMessage ?: "Recording stopped unexpectedly. Please hold/record at least 1 second."
            )
            return
        }

        if (audioBytes == null || audioBytes.isEmpty()) {
            _uiState.value = MediaLabUiState.Error("No audio captured. Please try recording again.")
            return
        }

        viewModelScope.launch {
            _uiState.value = MediaLabUiState.Loading("Transcribing speech with Gemini 3.5 Transcribe...")
            try {
                val result = geminiRepository.transcribeAudio(
                    audioBytes = audioBytes,
                    mimeType = "audio/mp4",
                    prompt = _customPrompt.value
                )
                _uiState.value = MediaLabUiState.Success(
                    outputText = result,
                    modelTag = "gemini-3.5-transcribe"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Audio transcription timed out", e)
                _uiState.value = MediaLabUiState.Error("Transcription timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio transcription failed", e)
                _uiState.value = MediaLabUiState.Error(
                    e.localizedMessage ?: "Failed to transcribe audio."
                )
            }
        }
    }

    fun clearOutput() {
        _uiState.value = MediaLabUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        audioRecorderHelper.stopAndRelease()
    }

    class Factory(
        private val geminiRepository: GeminiMediaRepository,
        private val audioRecorderHelper: AudioRecorderHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MediaLabViewModel(geminiRepository, audioRecorderHelper) as T
        }
    }
}
