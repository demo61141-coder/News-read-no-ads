package com.example.ui.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.BriefMode
import com.example.data.ai.GeminiMediaRepository
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderPreferencesRepository
import com.example.data.local.ReaderSurfaceMode
import com.example.data.reader.ArticleRepository
import com.example.data.reader.ExtractedArticle
import com.example.data.reader.UrlCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ReaderViewModel"

sealed interface ReaderUiState {
    data object Empty : ReaderUiState
    data class Loading(val url: String) : ReaderUiState
    data class Success(
        val article: ExtractedArticle,
        val isBookmarked: Boolean
    ) : ReaderUiState
    data class Error(
        val message: String,
        val attemptedUrl: String
    ) : ReaderUiState
}

sealed interface BriefUiState {
    data object Idle : BriefUiState
    data class Loading(val mode: BriefMode) : BriefUiState
    data class Streaming(val mode: BriefMode, val text: String) : BriefUiState
    data class Success(val mode: BriefMode, val text: String) : BriefUiState
    data class Error(val message: String) : BriefUiState
}

class ReaderViewModel(
    private val articleRepository: ArticleRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val geminiRepository: GeminiMediaRepository
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _readerState = MutableStateFlow<ReaderUiState>(ReaderUiState.Empty)
    val readerState: StateFlow<ReaderUiState> = _readerState.asStateFlow()

    private val _briefState = MutableStateFlow<BriefUiState>(BriefUiState.Idle)
    val briefState: StateFlow<BriefUiState> = _briefState.asStateFlow()

    val readerPreferences: StateFlow<ReaderPreferences> = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderPreferences()
        )

    private var loadArticleJob: Job? = null
    private var briefJob: Job? = null

    fun onUrlInputChange(newValue: String) {
        _urlInput.value = newValue
    }

    fun clearReader() {
        loadArticleJob?.cancel()
        briefJob?.cancel()
        _urlInput.value = ""
        _readerState.value = ReaderUiState.Empty
        _briefState.value = BriefUiState.Idle
    }

    fun openArticle(rawInput: String = _urlInput.value, forceRefresh: Boolean = false) {
        val cleaned = UrlCleaner.extractAndClean(rawInput)
        if (cleaned == null) {
            _readerState.value = ReaderUiState.Error(
                message = "Please enter a valid http or https news URL.",
                attemptedUrl = rawInput
            )
            return
        }

        _urlInput.value = cleaned.cleanUrl
        loadArticleJob?.cancel()
        briefJob?.cancel()
        _briefState.value = BriefUiState.Idle

        loadArticleJob = viewModelScope.launch {
            _readerState.value = ReaderUiState.Loading(cleaned.cleanUrl)
            try {
                val article = articleRepository.openArticle(rawInput, forceRefresh = forceRefresh)
                val bookmarked = articleRepository.isBookmarked(article.cleanUrl)
                val savedSummary = articleRepository.getSavedSummary(article.cleanUrl)
                _readerState.value = ReaderUiState.Success(
                    article = article,
                    isBookmarked = bookmarked
                )
                if (savedSummary.isNotBlank()) {
                    _briefState.value = BriefUiState.Success(BriefMode.SUMMARY_EN, savedSummary)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract article from ${cleaned.cleanUrl}", e)
                _readerState.value = ReaderUiState.Error(
                    message = e.localizedMessage ?: "Unable to load this news link.",
                    attemptedUrl = cleaned.cleanUrl
                )
            }
        }
    }

    fun toggleBookmark() {
        val current = _readerState.value as? ReaderUiState.Success ?: return
        viewModelScope.launch {
            val next = articleRepository.toggleBookmark(current.article.cleanUrl)
            _readerState.value = current.copy(isBookmarked = next)
        }
    }

    fun generateQuickBrief(mode: BriefMode) {
        val current = _readerState.value as? ReaderUiState.Success ?: return
        briefJob?.cancel()
        briefJob = viewModelScope.launch {
            _briefState.value = BriefUiState.Loading(mode)
            var latestText = ""
            try {
                val fullBody = current.article.paragraphs.joinToString("\n\n")
                geminiRepository.streamArticleBrief(
                    title = current.article.title,
                    articleText = fullBody,
                    mode = mode
                ).collect { partial ->
                    latestText = partial
                    _briefState.value = BriefUiState.Streaming(mode, partial)
                }
                if (latestText.isNotBlank()) {
                    _briefState.value = BriefUiState.Success(mode, latestText)
                    articleRepository.saveAiSummary(current.article.cleanUrl, latestText)
                } else {
                    _briefState.value = BriefUiState.Error("Gemini returned an empty brief.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Quick brief timed out", e)
                _briefState.value = BriefUiState.Error("Request timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Quick brief failed", e)
                _briefState.value = BriefUiState.Error(
                    e.localizedMessage ?: "Could not generate AI brief."
                )
            }
        }
    }

    fun dismissBrief() {
        briefJob?.cancel()
        _briefState.value = BriefUiState.Idle
    }

    fun setFontSize(sizeSp: Float) {
        viewModelScope.launch { preferencesRepository.setFontSize(sizeSp) }
    }

    fun setLineSpacing(multiplier: Float) {
        viewModelScope.launch { preferencesRepository.setLineSpacing(multiplier) }
    }

    fun setUseSerif(useSerif: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseSerif(useSerif) }
    }

    fun setSurfaceMode(mode: ReaderSurfaceMode) {
        viewModelScope.launch { preferencesRepository.setSurfaceMode(mode) }
    }

    fun formatArticleForCopy(article: ExtractedArticle): String {
        val header = buildString {
            appendLine(article.title)
            val meta = listOfNotNull(
                article.siteName.takeIf { it.isNotBlank() },
                article.author.takeIf { it.isNotBlank() },
                article.publishedDate.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (meta.isNotBlank()) appendLine(meta)
            appendLine(article.cleanUrl)
        }
        return "$header\n${article.paragraphs.joinToString("\n\n")}"
    }

    class Factory(
        private val articleRepository: ArticleRepository,
        private val preferencesRepository: ReaderPreferencesRepository,
        private val geminiRepository: GeminiMediaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(articleRepository, preferencesRepository, geminiRepository) as T
        }
    }
}
