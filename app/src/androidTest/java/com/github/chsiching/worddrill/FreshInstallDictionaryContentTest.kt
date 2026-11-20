package com.github.chsiching.worddrill

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runner.RunWith

/**
 * Fresh-install acceptance criteria of #56, at the UI seam: cards carry the shipped
 * dictionary's phonetics, the preset books hold exactly the official syllabus counts,
 * and the bundled definitions include Youdao's domain-labeled senses. The 100k-word
 * first-launch import runs concurrently, so content waits use the 60-second budget.
 */
@RunWith(AndroidJUnit4::class)
class FreshInstallDictionaryContentTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // See FreshInstallDrillDefaultBookTest: the reset must land before the activity
    // launches, and the shared-process DataStore cannot drop the key once written.
    @get:Rule
    val ruleChain: TestRule = RuleChain
        .outerRule(NoRecordedDictionaryBookRule())
        .around(composeRule)

    /** Settles the drill on a Ready deck: the lock icon (a contentDescription, not
     *  text — see docs/agents/android-mcp-notes.md) only exists once the default
     *  book resolved and the preset import committed. Waiting on it also waits out
     *  the launch splash, so tab taps after it always land on the real nav bar.
     *
     *  The budget is deliberately generous: on a cold instrumented process the
     *  preset import's transaction queues behind the 100k-row dictionary import
     *  (SQLite has one writer), which can take minutes on a slow emulator. Warm
     *  runs exit immediately — waitUntil polls, it does not sleep. */
    private fun awaitDrillReady() {
        composeRule.waitUntil(300_000L) {
            composeRule.onAllNodesWithContentDescription("锁定").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Opens the Library tab, re-tapping until its title (R.string.library_large_title,
     *  present on the library
     *  screen only) appears: the launch splash is a full-screen Canvas with no
     *  semantics, and a tap issued while it is still up is swallowed by it even though
     *  the drill beneath is already queryable. */
    private fun openLibrary() {
        composeRule.waitUntil(20_000L) {
            if (composeRule.onAllNodesWithText("词库").fetchSemanticsNodes().isEmpty()) {
                composeRule.onAllNodesWithText("库").onFirst().performClick()
                composeRule.waitForIdle()
            }
            composeRule.onAllNodesWithText("词库").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Opens a book's word list through its row arrow (contentDescription
     *  R.string.library_title) and
     *  waits for the list's search field. */
    private fun openFirstBookWordList() {
        composeRule.waitUntil(20_000L) {
            if (composeRule.onAllNodesWithText("搜索单词").fetchSemanticsNodes().isEmpty()) {
                composeRule.onAllNodesWithContentDescription("词书").onFirst().performClick()
                composeRule.waitForIdle()
            }
            composeRule.onAllNodesWithText("搜索单词").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun freshInstall_cardsShowPhoneticsFromShippedDictionary() {
        awaitDrillReady()
        composeRule.onNodeWithContentDescription("锁定").assertIsDisplayed()
        // The default book is CET-4 and the default deck ordering is alphabetical, so
        // the visible card is "a" — whose shipped Youdao phonetic is IPA, not the old
        // dataset's ASCII "/ei/".
        composeRule.onNodeWithText("/ə; eɪ/").assertIsDisplayed()
    }

    @Test
    fun freshInstall_presetBooksHoldExactSyllabusCounts() {
        awaitDrillReady()
        openLibrary()

        composeRule.onNodeWithText("CET-4").assertIsDisplayed()
        // substring=true is load-bearing: a book row is one merged node whose text
        // collection is ["CET-4", "4035 words (Chinese row subtitle)"], and the default exact matcher
        // only hits the first item.
        composeRule.onNodeWithText("4035 词", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("5298 词", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("5658 词", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("考研英语").assertIsDisplayed()
    }

    @Test
    fun freshInstall_definitionsCarryYoudaoDomainLabels() {
        awaitDrillReady()
        openLibrary()
        // The first book row's open arrow belongs to CET-4 (preset books sort first).
        openFirstBookWordList()

        composeRule.onNodeWithText("搜索单词").performTextInput("Celsius")
        composeRule.waitForIdle()

        // Celsius ships a domain-labeled sense in the bundled asset:
        // a Chinese part-of-speech tag with region labels and a person-name gloss. The sense text is not the
        // first item of the merged row, so the match must be substring-based.
        composeRule.onNodeWithText("摄尔西乌斯", substring = true).assertIsDisplayed()
    }
}

/**
 * Tests in this instrumentation process share the app's DataStore, and once any test
 * has written current_book_id the key cannot be removed. Pointing it at an id that
 * matches no book makes the launch resolve no recorded book, so the drill loader runs
 * the same default-selection path as a fresh install.
 */
private class NoRecordedDictionaryBookRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                runBlocking {
                    SettingsRepository(ApplicationProvider.getApplicationContext())
                        .setCurrentBookId(NO_DICTIONARY_BOOK_ID)
                }
                base.evaluate()
            }
        }
}

private const val NO_DICTIONARY_BOOK_ID = 999_998L
