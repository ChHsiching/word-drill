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

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationShowsAllTabs() {
        composeRule.onAllNodesWithText("刷").assertCountEquals(1)
        composeRule.onAllNodesWithText("库").assertCountEquals(1)
        composeRule.onAllNodesWithText("我的").assertCountEquals(1)
    }

    @Test
    fun clickingLibraryTabDoesNotCrash() {
        composeRule.onAllNodesWithText("库")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("库").assertCountEquals(1)
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithText("CET-4").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("词库").assertIsDisplayed()
    }

    @Test
    fun clickingMeTabDoesNotCrash() {
        composeRule.onAllNodesWithText("我的")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("今日刷卡").assertIsDisplayed()
    }
}
