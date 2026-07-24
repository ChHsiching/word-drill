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
 * Ticket #19：内置词典首启导入编排。幂等检查 → 读 assets → 解析 → 写 Room → 置标记。
 *
 * 与 [PresetImportOrchestrator] 同模式。触发方（Application.onCreate）调用
 * [ensureDictionaryImported] 即可，已导入会立即返回。导入在 [Dispatchers.IO] 上执行。
 *
 * 与预置词库导入独立标记：词典失败不影响用户已有词书（dictionary 只读参考数据）。
 */
@Singleton
class DictionaryImportOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val importer: DictionaryImporter,
) {
    /** assets 中内置词典文件名。 */
    private val assetFile = "dictionary.json"

    /**
     * 若内置词典尚未导入，则在后台线程完成导入并置标记；已导入则跳过。
     *
     * 幂等说明：预期由单一调用方（Application.onCreate）触发一次。DataStore 标记
     * 保证后续启动不再导入；[DictionaryImporter] 自身对 (word,pos) 去重，安全。
     */
    suspend fun ensureDictionaryImported() = withContext(Dispatchers.IO) {
        if (settings.dictionaryImported.first()) return@withContext
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val data = DictionaryWordsParser.parse(json)
        importer.importDictionary(data)
        settings.markDictionaryImported()
    }
}
