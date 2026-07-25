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

/** 应用级 DataStore（单例）。文件名 worddrill.preferences。 */
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "worddrill.preferences")

/**
 * 应用级设置与状态标记。
 * - 预置词库是否已首次导入（Ticket #4 首启幂等导入）
 * - 当前选中的词书 book_id（Ticket #5 「刷」Tab 记住上次刷的词书）
 * - 主题偏好（Ticket #9 深色/浅色/跟随系统，重启保持）
 * - UI 设置（Ticket #16）：隐藏音标 / 导航栏风格 / 简约导航
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val presetImportedKey = booleanPreferencesKey("preset_imported")
    private val dictionaryImportedKey = booleanPreferencesKey("dictionary_imported")
    /**
     * Ticket #23：内置词典的导入版本（整数）。当前每发布一次修复过 dictionary.json 的版本就 +1，
     * orchestrator 比对存储版本 < 期望版本时清表重导，保证老用户在 app 升级后能拿到新数据。
     * 取代了纯布尔 [dictionaryImportedKey] 的"已导入即不再导"语义。
     */
    private val dictionaryVersionKey = longPreferencesKey("dictionary_version")
    private val reviewBookCreatedKey = booleanPreferencesKey("review_book_created")
    private val currentBookIdKey = longPreferencesKey("current_book_id")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val hidePhoneticKey = booleanPreferencesKey("hide_phonetic")
    private val navStyleKey = stringPreferencesKey("nav_style")
    private val compactNavKey = booleanPreferencesKey("compact_nav")

    /** 预置词库是否已导入完成。未读过时默认 false。 */
    val presetImported: Flow<Boolean> = context.appDataStore.data.map { it[presetImportedKey] ?: false }

    /** 标记预置词库导入完成。导入流程成功后调用一次，后续启动跳过导入。 */
    suspend fun markPresetImported() {
        context.appDataStore.edit { it[presetImportedKey] = true }
    }

    // ---- Ticket #19：内置词典首启导入幂等标记 ----

    /**
     * 内置词典（ECDICT）是否曾经成功导入过。Ticket #19 的原始幂等标记，
     * Ticket #23 引入版本号后只作为"表里可能已有数据"的信号，供 [com.github.chsiching.worddrill.data.DictionaryImportOrchestrator]
     * 判断是否需要先 [com.github.chsiching.worddrill.data.local.dao.DictionaryDao.clear] 再 insert。
     */
    val dictionaryImported: Flow<Boolean> =
        context.appDataStore.data.map { it[dictionaryImportedKey] ?: false }

    /**
     * Ticket #23：内置词典当前已导入的版本号。未读过时默认 0（视为"从未导入"）。
     * orchestrator 用它与 [com.github.chsiching.worddrill.data.DictionaryImportOrchestrator.DICTIONARY_VERSION]
     * 比对：小于期望版本 → 清表重导。
     */
    val dictionaryVersion: Flow<Long> =
        context.appDataStore.data.map { it[dictionaryVersionKey] ?: 0L }

    /** 标记当前已导入到 [version]。重导完成后调用。同时把旧布尔标记置 true（兼容诊断）。 */
    suspend fun markDictionaryVersion(version: Long) {
        context.appDataStore.edit {
            it[dictionaryVersionKey] = version
            it[dictionaryImportedKey] = true
        }
    }

    // ---- Ticket #20：预置复习词书首启创建幂等标记 ----

    /** 预置「复习」词书是否已创建。未读过时默认 false。 */
    val reviewBookCreated: Flow<Boolean> =
        context.appDataStore.data.map { it[reviewBookCreatedKey] ?: false }

    /** 标记复习词书已创建。首次创建后调用一次，后续启动跳过。 */
    suspend fun markReviewBookCreated() {
        context.appDataStore.edit { it[reviewBookCreatedKey] = true }
    }

    /**
     * 清除复习词书创建标记（置 false）。供测试隔离用：内存 Room 库每次新建为空，
     * 配合清标记让 [com.github.chsiching.worddrill.data.ReviewBookInitializer] 从干净起点执行。
     */
    suspend fun clearReviewBookCreated() {
        context.appDataStore.edit { it[reviewBookCreatedKey] = false }
    }

    /** 当前选中的词书 id。未设置过时为 null（首次启动，调用方应默认选第一本）。 */
    val currentBookId: Flow<Long?> = context.appDataStore.data.map { it[currentBookIdKey] }

    /** 记住当前词书，下次打开 App 直接刷该词书。 */
    suspend fun setCurrentBookId(id: Long) {
        context.appDataStore.edit { it[currentBookIdKey] = id }
    }

    /**
     * 用户主题偏好（Ticket #9）。未设置过时默认 [ThemeMode.SYSTEM]。
     * 损坏/未知值通过 [ThemeMode.fromStorageName] 安全回退，不抛异常。
     */
    val themePreference: Flow<ThemeMode> =
        context.appDataStore.data.map { ThemeMode.fromStorageName(it[themeKey]) }

    /** 写入主题偏好，重启后保持。 */
    suspend fun setTheme(mode: ThemeMode) {
        context.appDataStore.edit { it[themeKey] = mode.name }
        // Ticket #24：同步镜像到 SharedPreferences。
        // 系统 SplashScreen 在 Application.onCreate 之前渲染，那时 DataStore 还没准备好
        // （异步读取），但 SharedPreferences 可同步读。镜像让 Application 能立刻拿到
        // 用户偏好并 setTheme() 选对应 splash 变体（LIGHT/DARK），避免「白闪 2 秒」。
        context.getSharedPreferences("splash_theme_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode.name).commit()
    }

    // ---- Ticket #16：UI 设置（隐藏音标 / 导航栏风格 / 简约导航）----

    /** 是否隐藏刷卡页音标。未设置过时默认 false。 */
    val hidePhonetic: Flow<Boolean> = context.appDataStore.data.map { it[hidePhoneticKey] ?: false }

    suspend fun setHidePhonetic(value: Boolean) {
        context.appDataStore.edit { it[hidePhoneticKey] = value }
    }

    /** 导航栏风格。未设置过或损坏值默认 [NavStyle.PILL]。 */
    val navStyle: Flow<NavStyle> =
        context.appDataStore.data.map { NavStyle.fromStorageName(it[navStyleKey]) }

    suspend fun setNavStyle(style: NavStyle) {
        context.appDataStore.edit { it[navStyleKey] = style.name }
    }

    /** 是否启用简约导航（仅图标、无文字标签）。未设置过时默认 false。 */
    val compactNav: Flow<Boolean> = context.appDataStore.data.map { it[compactNavKey] ?: false }

    suspend fun setCompactNav(value: Boolean) {
        context.appDataStore.edit { it[compactNavKey] = value }
    }
}
