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
 * 「刷」Tab 内容自 Ticket #5 起为真实卡片浏览（不再渲染占位文案 "刷"），
 * 故三个 Tab 文案均仅出现在导航栏（各 1 个节点）。
 * 「刷」Tab 的卡片渲染由 DrillScreenTest 覆盖。
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationShowsAllTabs() {
        // 三个 Tab 文案各自仅出现在底部导航栏（各 1 个节点）
        composeRule.onAllNodesWithText("刷").assertCountEquals(1)
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
