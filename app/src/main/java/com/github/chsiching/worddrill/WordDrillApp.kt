package com.github.chsiching.worddrill

import android.app.Application
import com.github.chsiching.worddrill.data.DictionaryImportOrchestrator
import com.github.chsiching.worddrill.data.PresetImportOrchestrator
import com.github.chsiching.worddrill.data.ReviewBookInitializer
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
    @Inject lateinit var dictionaryImportOrchestrator: DictionaryImportOrchestrator
    @Inject lateinit var reviewBookInitializer: ReviewBookInitializer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 首启在后台幂等导入预置词库；已导入则立即返回，无重复开销。
        appScope.launch { presetImportOrchestrator.ensurePresetImported() }
        // Ticket #19：首启后台幂等导入内置词典（ECDICT 10 万词，只读参考数据）。
        // 与预置词库导入并行；dictionary 失败不影响用户词书使用。
        appScope.launch { dictionaryImportOrchestrator.ensureDictionaryImported() }
        // Ticket #20：首启幂等创建预置「复习」词书（跳过的词汇入此处，默认空）。
        // 独立协程，与词库/词典导入并行；建书本身只一行 INSERT，开销可忽略。
        appScope.launch { reviewBookInitializer.ensureReviewBookCreated() }
    }
}
