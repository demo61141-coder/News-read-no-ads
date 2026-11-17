package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_articles",
    indices = [Index(value = ["cleanUrl"], unique = true)]
)
data class SavedArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cleanUrl: String,
    val originalUrl: String,
    val title: String,
    val sourceDomain: String,
    val siteName: String,
    val author: String,
    val publishedDate: String,
    val leadImageUrl: String,
    val contentText: String,
    val aiSummary: String = "",
    val readingTimeMinutes: Int = 1,
    val trackersStripped: Int = 0,
    val isBookmarked: Boolean = false,
    val savedAtMillis: Long = System.currentTimeMillis()
)
