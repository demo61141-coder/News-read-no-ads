package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedArticleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CleanReadDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile
        private var instance: CleanReadDatabase? = null

        fun getInstance(context: Context): CleanReadDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CleanReadDatabase::class.java,
                    "cleanread_articles.db"
                ).build().also { instance = it }
            }
        }
    }
}
