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
class DictionaryImportOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val importer: DictionaryImporter,
) {
    private val assetFile = "dictionary.json"

    val version: Long = DICTIONARY_VERSION

    suspend fun ensureDictionaryImported() = withContext(Dispatchers.IO) {
        val stored = settings.dictionaryVersion.first()
        if (stored >= DICTIONARY_VERSION) return@withContext
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val data = DictionaryWordsParser.parse(json)
        // Legacy installs set dictionaryImported before the version field existed; their rows
        // must be cleared or INSERT IGNORE keeps the stale data.
        val hadOldData = stored > 0L || settings.dictionaryImported.first()
        importer.importDictionary(data, clearFirst = hadOldData)
        settings.markDictionaryVersion(DICTIONARY_VERSION)
    }

    companion object {
        /** Increment whenever dictionary.json changes; a stale stored version triggers a clear + re-import on launch. */
        const val DICTIONARY_VERSION: Long = 2L
    }
}
