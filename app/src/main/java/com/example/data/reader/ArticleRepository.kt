package com.example.data.reader

import com.example.data.local.ArticleDao
import com.example.data.local.SavedArticleEntity
import kotlinx.coroutines.flow.Flow

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val extractor: ArticleExtractor = ArticleExtractor()
) {
    val allArticles: Flow<List<SavedArticleEntity>> = articleDao.observeAllArticles()
    val bookmarkedArticles: Flow<List<SavedArticleEntity>> = articleDao.observeBookmarkedArticles()

    suspend fun openArticle(rawInput: String, forceRefresh: Boolean = false): ExtractedArticle {
        val cleaned = UrlCleaner.extractAndClean(rawInput)
            ?: throw IllegalArgumentException("Please enter a valid http or https news link.")

        val existing = articleDao.getByCleanUrl(cleaned.cleanUrl)
        if (!forceRefresh && existing != null && existing.contentText.isNotBlank()) {
            return existing.toExtractedArticle(cleaned.strippedTrackersCount)
        }

        val extracted = try {
            extractor.fetchAndExtract(rawInput)
        } catch (e: Exception) {
            if (existing != null && existing.contentText.isNotBlank()) {
                return existing.toExtractedArticle(cleaned.strippedTrackersCount)
            }
            throw e
        }

        val entity = SavedArticleEntity(
            id = existing?.id ?: 0L,
            cleanUrl = extracted.cleanUrl,
            originalUrl = extracted.originalUrl,
            title = extracted.title,
            sourceDomain = extracted.sourceDomain,
            siteName = extracted.siteName,
            author = extracted.author,
            publishedDate = extracted.publishedDate,
            leadImageUrl = extracted.leadImageUrl,
            contentText = extracted.paragraphs.joinToString("\n\n"),
            aiSummary = existing?.aiSummary.orEmpty(),
            readingTimeMinutes = extracted.readingTimeMinutes,
            trackersStripped = extracted.trackersStripped,
            isBookmarked = existing?.isBookmarked ?: false,
            savedAtMillis = System.currentTimeMillis()
        )
        articleDao.upsertArticle(entity)
        return extracted
    }

    suspend fun getSavedSummary(cleanUrl: String): String {
        return articleDao.getByCleanUrl(cleanUrl)?.aiSummary.orEmpty()
    }

    suspend fun isBookmarked(cleanUrl: String): Boolean {
        return articleDao.getByCleanUrl(cleanUrl)?.isBookmarked ?: false
    }

    suspend fun toggleBookmark(cleanUrl: String): Boolean {
        val current = articleDao.getByCleanUrl(cleanUrl)?.isBookmarked ?: false
        val next = !current
        articleDao.updateBookmarkStatus(cleanUrl, next)
        return next
    }

    suspend fun saveAiSummary(cleanUrl: String, summary: String) {
        articleDao.updateAiSummary(cleanUrl, summary)
    }

    suspend fun deleteArticle(id: Long) {
        articleDao.deleteById(id)
    }

    private fun SavedArticleEntity.toExtractedArticle(extraStripped: Int): ExtractedArticle {
        return ExtractedArticle(
            cleanUrl = cleanUrl,
            originalUrl = originalUrl,
            title = title,
            sourceDomain = sourceDomain,
            siteName = siteName,
            author = author,
            publishedDate = publishedDate,
            leadImageUrl = leadImageUrl,
            paragraphs = contentText.split("\n\n").filter { it.isNotBlank() },
            readingTimeMinutes = readingTimeMinutes,
            trackersStripped = maxOf(trackersStripped, extraStripped)
        )
    }
}
