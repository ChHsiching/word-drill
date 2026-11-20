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
    private val presetVersionKey = longPreferencesKey("preset_version")
    private val dictionaryImportedKey = booleanPreferencesKey("dictionary_imported")
    private val dictionaryVersionKey = longPreferencesKey("dictionary_version")
    private val reviewBookCreatedKey = booleanPreferencesKey("review_book_created")
    private val currentBookIdKey = longPreferencesKey("current_book_id")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val hidePhoneticKey = booleanPreferencesKey("hide_phonetic")
    private val phoneticPreferenceKey = stringPreferencesKey("phonetic_preference")
    private val navStyleKey = stringPreferencesKey("nav_style")
    private val compactNavKey = booleanPreferencesKey("compact_nav")
    private val dwellIntervalKey = floatPreferencesKey("dwell_interval_seconds")
    private val deckOrderingKey = stringPreferencesKey("deck_ordering")

    /** Version of the bundled preset data an install last imported; 0 means never (or a
     *  pre-versioning boolean-flag install, whose books need the refresh pass). */
    val presetVersion: Flow<Long> =
        context.appDataStore.data.map { it[presetVersionKey] ?: 0L }

    suspend fun markPresetVersion(version: Long) {
        context.appDataStore.edit { it[presetVersionKey] = version }
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

    /** Live view of the resume word (issue #60): the word-list progress header
     *  follows every drill settle without re-sampling. */
    fun resumeWordIdFlow(bookId: Long): Flow<Long?> =
        context.appDataStore.data.map { it[resumeWordKey(bookId)] }

    suspend fun setResumeWordId(bookId: Long, wordId: Long) {
        context.appDataStore.edit { it[resumeWordKey(bookId)] = wordId }
    }

    /** Progress reset: removing the key makes the book open at its first card
     *  (the drill falls back to index 0 when no resume word is recorded). */
    suspend fun clearResumeWordId(bookId: Long) {
        context.appDataStore.edit { it.remove(resumeWordKey(bookId)) }
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

    /** Phonetic preference (GLOSSARY.md, issue #58): which variant(s) the drill card
     *  shows. Default US (美式); hiding (hidePhonetic) always wins over it. */
    val phoneticPreference: Flow<PhoneticPreference> =
        context.appDataStore.data.map { PhoneticPreference.fromStorageName(it[phoneticPreferenceKey]) }

    suspend fun setPhoneticPreference(preference: PhoneticPreference) {
        context.appDataStore.edit { it[phoneticPreferenceKey] = preference.name }
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

    /** Deck ordering (GLOSSARY.md, ADR 0002): one global mode for all books.
     *  Sampled once per book open; a shuffled sequence itself is never stored. */
    val deckOrdering: Flow<DeckOrdering> =
        context.appDataStore.data.map { DeckOrdering.fromStorageName(it[deckOrderingKey]) }

    suspend fun setDeckOrdering(mode: DeckOrdering) {
        context.appDataStore.edit { it[deckOrderingKey] = mode.name }
    }
}
