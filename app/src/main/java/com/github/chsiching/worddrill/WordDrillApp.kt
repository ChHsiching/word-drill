package com.github.chsiching.worddrill

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import com.github.chsiching.worddrill.data.DictionaryImportOrchestrator
import com.github.chsiching.worddrill.data.PresetImportOrchestrator
import com.github.chsiching.worddrill.data.ReviewBookInitializer
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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
        // Ticket #24：根据用户主题偏好启用对应的 launcher activity-alias。
        // 系统 SplashScreen 读 manifest 里**启用**的 alias 的 theme 渲染背景色。
        // 3 个 alias 各带不同 splash 主题（System/Light/Dark），这里同步读
        // SharedPreferences 镜像（DataStore 异步来不及），启用偏好对应的那一个、
        // 禁用另外两个 → 下次冷启动系统 splash 颜色匹配应用偏好。
        // 首次安装无偏好 → 默认 LauncherSystem（跟随系统夜间模式）。
        val prefs = getSharedPreferences("splash_theme_prefs", MODE_PRIVATE)
        val themeMode = ThemeMode.fromStorageName(prefs.getString("theme_mode", null))
        val targetAlias = when (themeMode) {
            ThemeMode.LIGHT -> LAUNCHER_ALIAS_LIGHT
            ThemeMode.DARK -> LAUNCHER_ALIAS_DARK
            ThemeMode.SYSTEM -> LAUNCHER_ALIAS_SYSTEM
        }
        val pm = packageManager
        LAUNCHER_ALIASES.forEach { alias ->
            val state = if (alias == targetAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(this, alias),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }

        super.onCreate()
        // Ticket #21：PdfBox-Android 的 native 库（libpdfbox.so）需在用前 init。
        // 文件导入 PDF 时 [com.github.chsiching.worddrill.data.wordimport.PdfTableParser]
        // 依赖它；这里在 Application 启动时一次性初始化，幂等（PDFBoxResourceLoader 内部已防重）。
        PDFBoxResourceLoader.init(this)

        // 首启在后台幂等导入预置词库；已导入则立即返回，无重复开销。
        appScope.launch { presetImportOrchestrator.ensurePresetImported() }
        // Ticket #19：首启后台幂等导入内置词典（ECDICT 10 万词，只读参考数据）。
        // 与预置词库导入并行；dictionary 失败不影响用户词书使用。
        appScope.launch { dictionaryImportOrchestrator.ensureDictionaryImported() }
        // Ticket #20：首启幂等创建预置「复习」词书（跳过的词汇入此处，默认空）。
        // 独立协程，与词库/词典导入并行；建书本身只一行 INSERT，开销可忽略。
        appScope.launch { reviewBookInitializer.ensureReviewBookCreated() }
    }

    private companion object {
        const val LAUNCHER_ALIAS_SYSTEM = "com.github.chsiching.worddrill.LauncherSystem"
        const val LAUNCHER_ALIAS_LIGHT = "com.github.chsiching.worddrill.LauncherLight"
        const val LAUNCHER_ALIAS_DARK = "com.github.chsiching.worddrill.LauncherDark"
        val LAUNCHER_ALIASES = listOf(LAUNCHER_ALIAS_SYSTEM, LAUNCHER_ALIAS_LIGHT, LAUNCHER_ALIAS_DARK)
    }
}
