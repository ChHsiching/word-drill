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
        if (settings.presetImported.first()) return@withContext
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val preset = PresetWordsParser.parse(json)
        importer.importWords(preset)
        settings.markPresetImported()
    }
}
