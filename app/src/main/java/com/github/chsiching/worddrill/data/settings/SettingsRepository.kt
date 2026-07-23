package com.github.chsiching.worddrill.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val presetImportedKey = booleanPreferencesKey("preset_imported")
    private val currentBookIdKey = longPreferencesKey("current_book_id")

    /** 预置词库是否已导入完成。未读过时默认 false。 */
    val presetImported: Flow<Boolean> = context.appDataStore.data.map { it[presetImportedKey] ?: false }

    /** 标记预置词库导入完成。导入流程成功后调用一次，后续启动跳过导入。 */
    suspend fun markPresetImported() {
        context.appDataStore.edit { it[presetImportedKey] = true }
    }

    /** 当前选中的词书 id。未设置过时为 null（首次启动，调用方应默认选第一本）。 */
    val currentBookId: Flow<Long?> = context.appDataStore.data.map { it[currentBookIdKey] }

    /** 记住当前词书，下次打开 App 直接刷该词书。 */
    suspend fun setCurrentBookId(id: Long) {
        context.appDataStore.edit { it[currentBookIdKey] = id }
    }
}
