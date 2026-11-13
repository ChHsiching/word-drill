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
 * Main seam: Compose UI integration test.
 * Verifies the three tabs exist, are switchable, and that switching does not crash.
 * Also implicitly verifies that Hilt @HiltAndroidApp + @AndroidEntryPoint startup does not crash.
 *
 * Since Ticket #5 the drill tab content is real card browsing (it no longer renders the placeholder label),
 * and since Ticket #6 the library tab content is the word book list (with the "word book" heading at the top).
 * So each of the three tab labels appears only in the navigation bar (1 node each).
 * DrillScreenTest covers card rendering on the drill tab.
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationShowsAllTabs() {
        // Each of the three tab labels appears only in the bottom navigation bar (1 node each)
        composeRule.onAllNodesWithText("刷").assertCountEquals(1)
        composeRule.onAllNodesWithText("库").assertCountEquals(1)
        composeRule.onAllNodesWithText("我的").assertCountEquals(1)
    }

    @Test
    fun clickingLibraryTabDoesNotCrash() {
        composeRule.onAllNodesWithText("库")[0].performClick()
        composeRule.waitForIdle()
        // Since Ticket #16 the library tab renders the large "word library" heading plus the word book list,
        // so the library tab's single-character label still appears only once, in the navigation bar (the content area shows "word library" or concrete word book names).
        composeRule.onAllNodesWithText("库").assertCountEquals(1)
        // waitUntil covers the async import of the preset library on first launch: before the import finishes books is empty, but the large "word library" heading
        // appears on the first frame. Once the preset word book CET-4 appears, the import has finished and the list is non-empty.
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithText("CET-4").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("词库").assertIsDisplayed()
    }

    @Test
    fun clickingMeTabDoesNotCrash() {
        composeRule.onAllNodesWithText("我的")[0].performClick()
        composeRule.waitForIdle()
        // Since Ticket #16 the Me tab has both the large "Me" heading and the bottom navigation "Me" label at the same time (2 nodes).
        // Asserting the stats label for today's card swipes is enough to prove the page rendered.
        composeRule.onNodeWithText("今日刷卡").assertIsDisplayed()
    }
}
