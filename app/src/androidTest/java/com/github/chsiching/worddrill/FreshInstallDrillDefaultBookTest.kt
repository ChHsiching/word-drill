package com.github.chsiching.worddrill

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Asserts the user-visible acceptance criterion of #33: a launch with no recorded
 * book shows the default book's card on the drill tab without any library
 * interaction. The wait-vs-sample race itself is pinned deterministically by
 * DrillViewModelTest; this end-to-end test only loses the race when the harness
 * reaches the activity before the import finishes (cold devices), because the
 * instrumentation setup can take longer than the whole preset import.
 */
@RunWith(AndroidJUnit4::class)
class FreshInstallDrillDefaultBookTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // RuleChain, not two @Rule fields: the reset must complete before the compose rule
    // launches the activity, so the drill loader sees the reset state, not whatever an
    // earlier test in this process recorded.
    @get:Rule
    val ruleChain: TestRule = RuleChain
        .outerRule(NoRecordedBookRule())
        .around(composeRule)

    @Test
    fun freshInstall_drillShowsDefaultBookCard() {
        // The drill tab is the start destination; the test never visits the Library.
        // First launch imports the 100k-word dictionary concurrently, so CET-4 can
        // take well over 10 seconds to appear; 60 seconds is the established budget.
        composeRule.waitUntil(60_000L) {
            composeRule.onAllNodesWithText("CET-4").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("CET-4").assertIsDisplayed()
        // The lock button only exists in the Ready pager, so a card is on screen,
        // not merely the book name.
        composeRule.onNodeWithContentDescription("锁定").assertIsDisplayed()
    }
}

/**
 * Tests in this instrumentation process share the app's DataStore, and once any test
 * has written current_book_id the key cannot be removed. Pointing it at an id that
 * matches no book makes the launch resolve no recorded book, so the drill loader runs
 * the same default-selection path as a fresh install.
 */
private class NoRecordedBookRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                runBlocking {
                    SettingsRepository(ApplicationProvider.getApplicationContext())
                        .setCurrentBookId(NO_BOOK_ID)
                }
                base.evaluate()
            }
        }
}

private const val NO_BOOK_ID = 999_999L
