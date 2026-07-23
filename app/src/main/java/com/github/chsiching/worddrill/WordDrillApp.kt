package com.github.chsiching.worddrill

import android.app.Application
import com.github.chsiching.worddrill.data.PresetImportOrchestrator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 的代码生成，生成 ApplicationComponent。
 * 所有后续 ViewModel / Repository 的依赖注入都挂在这个根组件上。
 *
 * 注意：本项目未启用 Hilt Gradle Plugin（它要求 AGP 9，与规格的 AGP 8.13.x 冲突）。
 * 因此按 Dagger 官方文档，@HiltAndroidApp 需显式指定 base class，
 * 且被注解的类要 extend Hilt 生成的 Hilt_<类名>。
 */
@HiltAndroidApp(Application::class)
class WordDrillApp : Hilt_WordDrillApp() {

    @Inject lateinit var presetImportOrchestrator: PresetImportOrchestrator

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 首启在后台幂等导入预置词库；已导入则立即返回，无重复开销。
        appScope.launch { presetImportOrchestrator.ensurePresetImported() }
    }
}
