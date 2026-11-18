package com.github.chsiching.worddrill

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
        composeRule.waitUntil(60_000L) {
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

    @Test
    fun wordListSearch_persistsAcrossTabSwitch() {
        composeRule.onAllNodesWithText("库")[0].performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(60_000L) {
            composeRule.onAllNodesWithText("CET-4").fetchSemanticsNodes().isNotEmpty()
        }
        // The book row body only selects the current book; the word list opens
        // through its arrow button. CET-4 is the first book, so the first match.
        composeRule.onAllNodesWithContentDescription("词书").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(60_000L) {
            composeRule.onAllNodesWithText("搜索单词").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("搜索单词").performTextInput("aband")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("aband", substring = false).assertIsDisplayed()

        // Tab away and back: saveState/restoreState keeps the word list's
        // ViewModel alive, so the query (and the filtered list) survives.
        composeRule.onAllNodesWithText("我的")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("库")[0].performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(60_000L) {
            composeRule.onAllNodesWithText("aband", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        // Still on the word list with the query intact, not a fresh list: the
        // placeholder only shows while the field is empty.
        assertThat(
            composeRule.onAllNodesWithText("搜索单词").fetchSemanticsNodes().isEmpty()
        ).isTrue()

        // Leave the word list via back so the Library tab ends on its root; a
        // saved word-list stack would change what a later test sees on that tab.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}
