package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_preferences"
)

enum class ReaderSurfaceMode(val label: String) {
    PAPER("Warm Paper"),
    CRISP("Crisp White"),
    NIGHT("Ink Night")
}

data class ReaderPreferences(
    val fontSizeSp: Float = 18f,
    val lineSpacingMultiplier: Float = 1.6f,
    val useSerifFont: Boolean = true,
    val surfaceMode: ReaderSurfaceMode = ReaderSurfaceMode.PAPER
)

class ReaderPreferencesRepository(private val context: Context) {
    private object Keys {
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val LINE_SPACING = floatPreferencesKey("line_spacing_multiplier")
        val USE_SERIF = booleanPreferencesKey("use_serif_font")
        val SURFACE_MODE = stringPreferencesKey("surface_mode")
    }

    val preferences: Flow<ReaderPreferences> = context.readerPrefsDataStore.data.map { prefs ->
        val modeName = prefs[Keys.SURFACE_MODE] ?: ReaderSurfaceMode.PAPER.name
        val mode = ReaderSurfaceMode.entries.firstOrNull { it.name == modeName }
            ?: ReaderSurfaceMode.PAPER
        ReaderPreferences(
            fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 18f).coerceIn(14f, 26f),
            lineSpacingMultiplier = (prefs[Keys.LINE_SPACING] ?: 1.6f).coerceIn(1.3f, 2.0f),
            useSerifFont = prefs[Keys.USE_SERIF] ?: true,
            surfaceMode = mode
        )
    }

    suspend fun setFontSize(sizeSp: Float) {
        context.readerPrefsDataStore.edit { it[Keys.FONT_SIZE_SP] = sizeSp.coerceIn(14f, 26f) }
    }

    suspend fun setLineSpacing(multiplier: Float) {
        context.readerPrefsDataStore.edit { it[Keys.LINE_SPACING] = multiplier.coerceIn(1.3f, 2.0f) }
    }

    suspend fun setUseSerif(useSerif: Boolean) {
        context.readerPrefsDataStore.edit { it[Keys.USE_SERIF] = useSerif }
    }

    suspend fun setSurfaceMode(mode: ReaderSurfaceMode) {
        context.readerPrefsDataStore.edit { it[Keys.SURFACE_MODE] = mode.name }
    }
}
