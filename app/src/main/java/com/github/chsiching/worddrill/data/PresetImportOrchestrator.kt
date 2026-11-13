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
 * First-launch preset word library import orchestration: idempotency check -> read assets ->
 * parse -> write Room -> set the flag.
 *
 * The trigger (Application / first ViewModel) just calls [ensurePresetImported]; if already
 * imported, it returns immediately. The import runs on [Dispatchers.IO].
 */
@Singleton
class PresetImportOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val importer: PresetImporter,
) {
    /** Preset word library file name in assets. */
    private val assetFile = "words.json"

    /**
     * If the preset word library has not been imported yet, finish the import on a background
     * thread and set the flag; otherwise skip.
     *
     * Idempotency note: expected to be triggered once by a single caller (Application.onCreate).
     * The DataStore flag guarantees later launches do not import again; even if there were a race
     * between the flag check and setting it (there is currently no concurrent caller),
     * [PresetImporter] itself dedupes book/word/sense by name/text, so no duplicate data results.
     */
    suspend fun ensurePresetImported() = withContext(Dispatchers.IO) {
        if (settings.presetImported.first()) return@withContext
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val preset = PresetWordsParser.parse(json)
        importer.importWords(preset)
        settings.markPresetImported()
    }
}
