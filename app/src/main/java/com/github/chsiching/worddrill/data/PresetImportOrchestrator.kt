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
 * 首启预置词库导入编排：幂等检查 → 读 assets → 解析 → 写 Room → 置标记。
 *
 * 触发方（Application / 首个 ViewModel）调用 [ensurePresetImported] 即可，
 * 已导入会立即返回。导入在 [Dispatchers.IO] 上执行。
 */
@Singleton
class PresetImportOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val importer: PresetImporter,
) {
    /** assets 中预置词库文件名。 */
    private val assetFile = "words.json"

    /**
     * 若预置词库尚未导入，则在后台线程完成导入并置标记；已导入则跳过。
     *
     * 幂等说明：预期由单一调用方（Application.onCreate）触发一次。DataStore 标记
     * 保证后续启动不再导入；即便标记检查与置位之间存在并发（当前无并发调用方），
     * [PresetImporter] 自身对 book/word/sense 也按 name/text 去重，不会产生重复数据。
     */
    suspend fun ensurePresetImported() = withContext(Dispatchers.IO) {
        if (settings.presetImported.first()) return@withContext
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val preset = PresetWordsParser.parse(json)
        importer.importWords(preset)
        settings.markPresetImported()
    }
}
