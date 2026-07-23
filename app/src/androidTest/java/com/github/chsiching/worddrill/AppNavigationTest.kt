package com.github.chsiching.worddrill

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
 * 「库」Tab 内容自 Ticket #6 起为词书列表（顶部标题「词书」）。
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
        // 「库」Tab 内容自 Ticket #6 起渲染词书列表（顶部标题「词书」），
        // 故「库」字仍只在导航栏出现 1 次（内容区是「词书」/具体词书名）。
        composeRule.onAllNodesWithText("库").assertCountEquals(1)
        // 列表标题「词书」证明页面正常渲染
        composeRule.onNodeWithText("词书").assertIsDisplayed()
    }

    @Test
    fun clickingMeTabDoesNotCrash() {
        composeRule.onAllNodesWithText("我的")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("我的").assertCountEquals(2)
    }
}
