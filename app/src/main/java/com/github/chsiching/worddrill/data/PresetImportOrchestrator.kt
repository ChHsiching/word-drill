package com.github.chsiching.worddrill.data

import android.content.Context
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetImportOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val importer: PresetImporter,
) {
    private val assetFile = "words.json"

    suspend fun ensurePresetImported() = withContext(Dispatchers.IO) {
        val stored = settings.presetVersion.first()
        if (stored >= PRESET_VERSION) return@withContext
        refreshPresets()
        settings.markPresetVersion(PRESET_VERSION)
    }

    /** Brings the exam books in line with the bundled words.json. importWords refreshes
     *  in place, so a version bump over an old install reconciles membership and content
     *  without touching user data. */
    suspend fun refreshPresets() = withContext(Dispatchers.IO) {
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val preset = PresetWordsParser.parse(json)
        importer.importWords(preset)
    }

    companion object {
        /** Increment whenever words.json changes; a stale stored version triggers an
         *  in-place refresh on launch. Installs from the pre-versioning era (boolean
         *  flag only) report 0 and refresh too. */
        const val PRESET_VERSION: Long = 2L
    }
}
