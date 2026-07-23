package com.github.chsiching.worddrill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.chsiching.worddrill.ui.navigation.WordDrillRoot
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 入口。承载 Compose 根 UI 与底部导航。
 * @AndroidEntryPoint 让 Hilt 能向此 Activity 及其挂载的 Composable 注入依赖。
 *
 * 注意：本项目未启用 Hilt Gradle Plugin（它要求 AGP 9，与规格的 AGP 8.13.x 冲突）。
 * 因此 @AndroidEntryPoint 需显式指定 base class，且 extend Hilt 生成的 Hilt_<类名>。
 */
@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WordDrillTheme {
                WordDrillRoot()
            }
        }
    }
}
