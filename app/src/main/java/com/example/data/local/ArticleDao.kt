package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM saved_articles ORDER BY savedAtMillis DESC")
    fun observeAllArticles(): Flow<List<SavedArticleEntity>>

    @Query("SELECT * FROM saved_articles WHERE isBookmarked = 1 ORDER BY savedAtMillis DESC")
    fun observeBookmarkedArticles(): Flow<List<SavedArticleEntity>>

    @Query("SELECT * FROM saved_articles WHERE cleanUrl = :cleanUrl LIMIT 1")
    suspend fun getByCleanUrl(cleanUrl: String): SavedArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(article: SavedArticleEntity): Long

    @Query("UPDATE saved_articles SET isBookmarked = :bookmarked WHERE cleanUrl = :cleanUrl")
    suspend fun updateBookmarkStatus(cleanUrl: String, bookmarked: Boolean)

    @Query("UPDATE saved_articles SET aiSummary = :summary WHERE cleanUrl = :cleanUrl")
    suspend fun updateAiSummary(cleanUrl: String, summary: String)

    @Query("DELETE FROM saved_articles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
