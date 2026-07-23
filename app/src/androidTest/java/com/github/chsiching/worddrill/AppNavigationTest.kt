package com.github.chsiching.worddrill

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主接缝：Compose UI 集成测试。
 * 验证三 Tab 存在、可切换、切换不崩溃。
 * 同时隐式验证 Hilt @HiltAndroidApp + @AndroidEntryPoint 启动不崩溃。
 *
 * 骨架阶段三个 Tab 均为空白占位页面，内容区文案与导航 label 同名，
 * 故用 onAllNodesWithText + assertCountEquals 断言：
 * 当前 Tab 的文案在导航栏 + 内容区各一处（共 2 个节点）。
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationShowsAllTabs() {
        // 默认「刷」选中：内容区 + 导航栏 = 2 个节点
        composeRule.onAllNodesWithText("刷").assertCountEquals(2)
        // 未选中 Tab 只有导航栏 1 个节点
        composeRule.onAllNodesWithText("库").assertCountEquals(1)
        composeRule.onAllNodesWithText("我的").assertCountEquals(1)
    }

    @Test
    fun clickingLibraryTabDoesNotCrash() {
        composeRule.onAllNodesWithText("库")[0].performClick()
        composeRule.waitForIdle()
        // 切换到「库」后：内容区 + 导航栏 = 2 个节点，证明 App 未崩溃
        composeRule.onAllNodesWithText("库").assertCountEquals(2)
    }

    @Test
    fun clickingMeTabDoesNotCrash() {
        composeRule.onAllNodesWithText("我的")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("我的").assertCountEquals(2)
    }
}
