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
 *
 * Ticket #23：用版本号 [DICTIONARY_VERSION] 取代"已导入即不再导"语义。每次发布
 * 修复过 dictionary.json 的版本就 +1；老用户升级后存储版本 < 期望版本 → 清表重导，
 * 解决 INSERT IGNORE 无法覆盖旧行的问题。
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
     * 内置词典数据版本。每次发布修复过 [assetFile] 内容的版本就 +1：
     * - v1：Ticket #19 首发（ECDICT top 10 万词，parse_translation 续接策略）。
     * - v2：Ticket #23 修复（POS_PREFIXES 补 adj./interj.；无 POS 行不再续接）。
     * orchestrator 把它写入 DataStore，启动时比对，落后则清表重导。
     */
    val version: Long = DICTIONARY_VERSION

    /**
     * 若内置词典尚未导入或落后于 [DICTIONARY_VERSION]，则完成导入并置标记；已对齐则跳过。
     *
     * 幂等说明：预期由单一调用方（Application.onCreate）触发一次。DataStore 版本标记
     * 保证后续启动不再重导；落后版本时先 [DictionaryImporter.clear] 再 insert，覆盖旧行。
     *
     * clearFirst 判定：stored == 0 且 旧布尔 [SettingsRepository.dictionaryImported] 为 true
     * → 从 #19 老版本升级，dictionary 表有旧行，必须 clear；stored > 0 → 跨版本升级，
     * 同样 clear；仅首启（stored == 0 且布尔为 false）不需要 clear（表本就空）。
     */
    suspend fun ensureDictionaryImported() = withContext(Dispatchers.IO) {
        val stored = settings.dictionaryVersion.first()
        if (stored >= DICTIONARY_VERSION) return@withContext
        val json = context.assets.open(assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
        val data = DictionaryWordsParser.parse(json)
        // stored==0 时仍可能已有旧数据（#19 老布尔标记过 true 但版本字段未写入）；
        // 用旧布尔判定是否需要先清表，否则 INSERT IGNORE 无法覆盖污染行。
        val hadOldData = stored > 0L || settings.dictionaryImported.first()
        importer.importDictionary(data, clearFirst = hadOldData)
        settings.markDictionaryVersion(DICTIONARY_VERSION)
    }

    companion object {
        /** 当前内置词典数据版本。见 [version] 的递增说明。 */
        const val DICTIONARY_VERSION: Long = 2L
    }
}
