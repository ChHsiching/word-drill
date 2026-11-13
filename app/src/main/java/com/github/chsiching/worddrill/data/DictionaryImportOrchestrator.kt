package com.github.chsiching.worddrill.data

import android.content.Context
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ticket #19: built-in dictionary first-launch import orchestration. Idempotency check ->
 * read assets -> parse -> write Room -> set the flag.
 *
 * Same pattern as [PresetImportOrchestrator]. The trigger (Application.onCreate) just calls
 * [ensureDictionaryImported]; if already imported, it returns immediately. The import runs on
 * [Dispatchers.IO].
 *
 * Uses a flag independent from the preset library import: a dictionary failure does not affect
 * the user's existing books (the dictionary is read-only reference data).
 *
 * Ticket #23: the version number [DICTIONARY_VERSION] replaces the "imported once, never
 * again" semantics. Incremented for every release that fixes dictionary.json; after upgrading,
 * a stored version < the expected version -> clear the table and re-import, solving the
 * problem that INSERT IGNORE cannot overwrite old rows.
 */
@Singleton
class DictionaryImportOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val importer: DictionaryImporter,
) {
    /** Built-in dictionary file name in assets. */
    private val assetFile = "dictionary.json"

    /**
     * Built-in dictionary data version. Incremented for every release that fixes [assetFile]:
     * - v1: Ticket #19 initial release (ECDICT top 100k words, parse_translation continuation strategy).
     * - v2: Ticket #23 fix (POS_PREFIXES gained adj./interj.; rows without a POS no longer continue).
     * The orchestrator writes it to DataStore and compares it on startup; when stale, clear the
     * table and re-import.
     */
    val version: Long = DICTIONARY_VERSION

    /**
     * If the built-in dictionary has not been imported, or is behind [DICTIONARY_VERSION],
     * finish the import and set the flag; skip when already up to date.
     *
     * Idempotency note: expected to be triggered once by a single caller (Application.onCreate).
     * The DataStore version flag guarantees later launches do not re-import; when the version is
     * behind, [DictionaryImporter.clear] runs before the insert, overwriting old rows.
     *
     * clearFirst decision: stored == 0 and the old boolean [SettingsRepository.dictionaryImported]
     * is true -> upgrading from the old #19 version, the dictionary table holds old rows, must
     * clear; stored > 0 -> cross-version upgrade, also clear; only a true first launch
     * (stored == 0 and the boolean false) needs no clear (the table is already empty).
     */
    suspend fun ensureDictionaryImported() = withContext(Dispatchers.IO) {
        val stored = settings.dictionaryVersion.first()
        if (stored >= DICTIONARY_VERSION) return@withContext
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val data = DictionaryWordsParser.parse(json)
        // With stored==0 there may still be old data (#19's old boolean flag was true but the
        // version field was never written); use the old boolean to decide whether to clear the
        // table first, otherwise INSERT IGNORE cannot overwrite polluted rows.
        val hadOldData = stored > 0L || settings.dictionaryImported.first()
        importer.importDictionary(data, clearFirst = hadOldData)
        settings.markDictionaryVersion(DICTIONARY_VERSION)
    }

    companion object {
        /** Current built-in dictionary data version. See [version] for the increment rules. */
        const val DICTIONARY_VERSION: Long = 2L
    }
}
