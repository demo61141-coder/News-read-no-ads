package com.example.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SavedArticleEntity
import com.example.data.reader.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _bookmarkedOnly = MutableStateFlow(false)
    val bookmarkedOnly: StateFlow<Boolean> = _bookmarkedOnly.asStateFlow()

    val articles: StateFlow<List<SavedArticleEntity>> = combine(
        articleRepository.allArticles,
        _searchQuery,
        _bookmarkedOnly
    ) { list, query, onlyBookmarked ->
        val trimmed = query.trim()
        list.filter { item ->
            val matchesBookmark = !onlyBookmarked || item.isBookmarked
            val matchesQuery = trimmed.isEmpty() ||
                item.title.contains(trimmed, ignoreCase = true) ||
                item.sourceDomain.contains(trimmed, ignoreCase = true) ||
                item.contentText.contains(trimmed, ignoreCase = true)
            matchesBookmark && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setBookmarkedOnly(onlyBookmarked: Boolean) {
        _bookmarkedOnly.value = onlyBookmarked
    }

    fun toggleBookmark(cleanUrl: String) {
        viewModelScope.launch {
            articleRepository.toggleBookmark(cleanUrl)
        }
    }

    fun deleteArticle(id: Long) {
        viewModelScope.launch {
            articleRepository.deleteArticle(id)
        }
    }

    class Factory(
        private val articleRepository: ArticleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(articleRepository) as T
        }
    }
}
