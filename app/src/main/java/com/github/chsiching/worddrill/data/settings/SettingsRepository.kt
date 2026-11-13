package com.github.chsiching.worddrill.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-level DataStore (singleton). File name worddrill.preferences. */
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "worddrill.preferences")

/**
 * App-level settings and state flags.
 * - Whether preset books have been imported once (Ticket #4, idempotent first-launch import)
 * - The currently selected book's book_id (Ticket #5, the "Drill" tab remembers the last book)
 * - Theme preference (Ticket #9, dark/light/follow-system, persists across restarts)
 * - UI settings (Ticket #16): hide phonetics / nav bar style / compact nav
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val presetImportedKey = booleanPreferencesKey("preset_imported")
    private val dictionaryImportedKey = booleanPreferencesKey("dictionary_imported")
    /**
     * Ticket #23: imported version of the built-in dictionary (integer). Increment it each time
     * a release ships a fixed dictionary.json. The orchestrator compares the stored version
     * against the expected version, clears the table and re-imports when it is behind, so
     * existing users get the new data after upgrading the app.
     * Replaces the plain boolean [dictionaryImportedKey] "imported once, never again" semantics.
     */
    private val dictionaryVersionKey = longPreferencesKey("dictionary_version")
    private val reviewBookCreatedKey = booleanPreferencesKey("review_book_created")
    private val currentBookIdKey = longPreferencesKey("current_book_id")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val hidePhoneticKey = booleanPreferencesKey("hide_phonetic")
    private val navStyleKey = stringPreferencesKey("nav_style")
    private val compactNavKey = booleanPreferencesKey("compact_nav")

    /** Whether the preset books have finished importing. Defaults to false when never read. */
    val presetImported: Flow<Boolean> = context.appDataStore.data.map { it[presetImportedKey] ?: false }

    /** Mark the preset book import as done. Called once after a successful import; later launches skip importing. */
    suspend fun markPresetImported() {
        context.appDataStore.edit { it[presetImportedKey] = true }
    }

    // ---- Ticket #19: idempotent first-launch import flag for the built-in dictionary ----

    /**
     * Whether the built-in dictionary (ECDICT) has ever been imported successfully. The original
     * idempotency flag from Ticket #19; after Ticket #23 introduced the version number it only
     * signals "the table may already hold data", used by
     * [com.github.chsiching.worddrill.data.DictionaryImportOrchestrator]
     * to decide whether to run [com.github.chsiching.worddrill.data.local.dao.DictionaryDao.clear] before inserting.
     */
    val dictionaryImported: Flow<Boolean> =
        context.appDataStore.data.map { it[dictionaryImportedKey] ?: false }

    /**
     * Ticket #23: currently imported version of the built-in dictionary. Defaults to 0 when
     * never read (treated as "never imported").
     * The orchestrator compares it against
     * [com.github.chsiching.worddrill.data.DictionaryImportOrchestrator.DICTIONARY_VERSION].
     * When the stored version is smaller than the expected version, the orchestrator clears the
     * table and re-imports.
     */
    val dictionaryVersion: Flow<Long> =
        context.appDataStore.data.map { it[dictionaryVersionKey] ?: 0L }

    /** Mark the current import as being at [version]. Called after a re-import finishes. Also sets the old boolean flag to true (for diagnostic compatibility). */
    suspend fun markDictionaryVersion(version: Long) {
        context.appDataStore.edit {
            it[dictionaryVersionKey] = version
            it[dictionaryImportedKey] = true
        }
    }

    // ---- Ticket #20: idempotent first-launch creation flag for the preset review book ----

    /** Whether the preset "Review" book has been created. Defaults to false when never read. */
    val reviewBookCreated: Flow<Boolean> =
        context.appDataStore.data.map { it[reviewBookCreatedKey] ?: false }

    /** Mark the review book as created. Called once after first creation; later launches skip it. */
    suspend fun markReviewBookCreated() {
        context.appDataStore.edit { it[reviewBookCreatedKey] = true }
    }

    /**
     * Clear the review book creation flag (set to false). This exists for test isolation. An
     * in-memory Room database starts empty each time, and clearing the flag lets
     * [com.github.chsiching.worddrill.data.ReviewBookInitializer] run from a clean starting point.
     */
    suspend fun clearReviewBookCreated() {
        context.appDataStore.edit { it[reviewBookCreatedKey] = false }
    }

    /** Currently selected book id. null when never set (first launch; callers should default to the first book). */
    val currentBookId: Flow<Long?> = context.appDataStore.data.map { it[currentBookIdKey] }

    /** Remember the current book, so the next app launch drills that book directly. */
    suspend fun setCurrentBookId(id: Long) {
        context.appDataStore.edit { it[currentBookIdKey] = id }
    }

    /**
     * User theme preference (Ticket #9). Defaults to [ThemeMode.SYSTEM] when never set.
     * Corrupted/unknown values fall back safely via [ThemeMode.fromStorageName], no exception thrown.
     */
    val themePreference: Flow<ThemeMode> =
        context.appDataStore.data.map { ThemeMode.fromStorageName(it[themeKey]) }

    /** Write the theme preference; persists across restarts. */
    suspend fun setTheme(mode: ThemeMode) {
        context.appDataStore.edit { it[themeKey] = mode.name }
    }

    // ---- Ticket #16: UI settings (hide phonetics / nav bar style / compact nav) ----

    /** Whether to hide phonetics on the drill page. Defaults to false when never set. */
    val hidePhonetic: Flow<Boolean> = context.appDataStore.data.map { it[hidePhoneticKey] ?: false }

    suspend fun setHidePhonetic(value: Boolean) {
        context.appDataStore.edit { it[hidePhoneticKey] = value }
    }

    /** Nav bar style. Defaults to [NavStyle.PILL] when never set or the value is corrupted. */
    val navStyle: Flow<NavStyle> =
        context.appDataStore.data.map { NavStyle.fromStorageName(it[navStyleKey]) }

    suspend fun setNavStyle(style: NavStyle) {
        context.appDataStore.edit { it[navStyleKey] = style.name }
    }

    /** Whether compact nav is enabled (icons only, no text labels). Defaults to false when never set. */
    val compactNav: Flow<Boolean> = context.appDataStore.data.map { it[compactNavKey] ?: false }

    suspend fun setCompactNav(value: Boolean) {
        context.appDataStore.edit { it[compactNavKey] = value }
    }
}
