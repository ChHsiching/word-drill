package com.github.chsiching.worddrill.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "worddrill.preferences")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val presetImportedKey = booleanPreferencesKey("preset_imported")
    private val dictionaryImportedKey = booleanPreferencesKey("dictionary_imported")
    private val dictionaryVersionKey = longPreferencesKey("dictionary_version")
    private val reviewBookCreatedKey = booleanPreferencesKey("review_book_created")
    private val currentBookIdKey = longPreferencesKey("current_book_id")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val hidePhoneticKey = booleanPreferencesKey("hide_phonetic")
    private val navStyleKey = stringPreferencesKey("nav_style")
    private val compactNavKey = booleanPreferencesKey("compact_nav")
    private val dwellIntervalKey = floatPreferencesKey("dwell_interval_seconds")

    val presetImported: Flow<Boolean> = context.appDataStore.data.map { it[presetImportedKey] ?: false }

    suspend fun markPresetImported() {
        context.appDataStore.edit { it[presetImportedKey] = true }
    }

    /** Legacy pre-versioning flag: true only means the dictionary table may already hold rows
     *  (used to decide whether to clear before re-import). */
    val dictionaryImported: Flow<Boolean> =
        context.appDataStore.data.map { it[dictionaryImportedKey] ?: false }

    val dictionaryVersion: Flow<Long> =
        context.appDataStore.data.map { it[dictionaryVersionKey] ?: 0L }

    suspend fun markDictionaryVersion(version: Long) {
        context.appDataStore.edit {
            it[dictionaryVersionKey] = version
            it[dictionaryImportedKey] = true
        }
    }

    val reviewBookCreated: Flow<Boolean> =
        context.appDataStore.data.map { it[reviewBookCreatedKey] ?: false }

    suspend fun markReviewBookCreated() {
        context.appDataStore.edit { it[reviewBookCreatedKey] = true }
    }

    suspend fun clearReviewBookCreated() {
        context.appDataStore.edit { it[reviewBookCreatedKey] = false }
    }

    val currentBookId: Flow<Long?> = context.appDataStore.data.map { it[currentBookIdKey] }

    suspend fun setCurrentBookId(id: Long) {
        context.appDataStore.edit { it[currentBookIdKey] = id }
    }

    /** Resume position (GLOSSARY.md): per book, the id of the word last drilled.
     *  Stored under a dynamic key derived from the book id, never as a deck index,
     *  because skipping shifts indices. */
    private fun resumeWordKey(bookId: Long) = longPreferencesKey("resume_word_$bookId")

    suspend fun resumeWordId(bookId: Long): Long? =
        context.appDataStore.data.first()[resumeWordKey(bookId)]

    suspend fun setResumeWordId(bookId: Long, wordId: Long) {
        context.appDataStore.edit { it[resumeWordKey(bookId)] = wordId }
    }

    val themePreference: Flow<ThemeMode> =
        context.appDataStore.data.map { ThemeMode.fromStorageName(it[themeKey]) }

    suspend fun setTheme(mode: ThemeMode) {
        context.appDataStore.edit { it[themeKey] = mode.name }
    }

    val hidePhonetic: Flow<Boolean> = context.appDataStore.data.map { it[hidePhoneticKey] ?: false }

    suspend fun setHidePhonetic(value: Boolean) {
        context.appDataStore.edit { it[hidePhoneticKey] = value }
    }

    val navStyle: Flow<NavStyle> =
        context.appDataStore.data.map { NavStyle.fromStorageName(it[navStyleKey]) }

    suspend fun setNavStyle(style: NavStyle) {
        context.appDataStore.edit { it[navStyleKey] = style.name }
    }

    val compactNav: Flow<Boolean> = context.appDataStore.data.map { it[compactNavKey] ?: false }

    suspend fun setCompactNav(value: Boolean) {
        context.appDataStore.edit { it[compactNavKey] = value }
    }

    val dwellInterval: Flow<Float> =
        context.appDataStore.data.map { it[dwellIntervalKey] ?: DwellInterval.DEFAULT_SECONDS }

    suspend fun setDwellInterval(seconds: Float) {
        context.appDataStore.edit { it[dwellIntervalKey] = DwellInterval.normalize(seconds) }
    }
}
