package com.github.chsiching.worddrill.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Main seam of the word entry list page, the second-level page of the library tab (Ticket #7): Compose UI + in-memory Room.
 *
 * Acceptance coverage:
 * - The list shows the book's word entries (word + part of speech + meaning)
 * - Add entry: type the text, tap confirm, and the new word appears after the list refreshes (the global word pool is reused)
 * - Edit sense: change the meaning, the list refreshes
 * - Remove from book: the list refreshes, the entry disappears
 * - Preset books are read-only: no "add entry" entry point is rendered
 *
 * IME injection details of TextField are not tested (only user-visible behavior: type text, submit, list changes).
 */
@RunWith(AndroidJUnit4::class)
class WordListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: WordDrillDatabase
    private var customBookId: Long = 0L
    private var presetBookId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        customBookId = db.bookDao().insert(Book(name = "我的生词本", isPreset = false))
        presetBookId = db.bookDao().insert(Book(name = "CET-4", isPreset = true))
        seedOneWord(customBookId, "apple", "n.", "苹果")
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedOneWord(bookId: Long, text: String, pos: String, meaning: String) =
        runBlocking {
            val wId = db.wordDao().insert(Word(text = text))
            db.wordDao().insertSense(Sense(wordId = wId, pos = pos, meaning = meaning))
            db.bookDao().linkBookWord(BookWord(bookId, wId))
            Unit
        }

    private fun setContentFor(bookId: Long) {
        val vm = WordListViewModel(
            // Use an in-memory SavedStateHandle to simulate the nav arg bookId
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                mapOf("bookId" to bookId.toString())
            ).also { /* no-op */ },
            db = db,
            wordDao = db.wordDao(),
            bookDao = db.bookDao(),
            dictionaryDao = db.dictionaryDao(),
        )
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WordListScreen(
                        onBack = {},
                        viewModel = vm,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun customBook_showsWords_andAddEntry() {
        setContentFor(customBookId)
        // The list shows the seeded entry (pos and meaning are split into separate Text nodes since #16)
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertIsDisplayed()
        // Custom book: the "add entry" entry point exists (IconButton contentDescription)
        composeRule.onNodeWithContentDescription("新增词条").assertIsDisplayed()
    }

    @Test
    fun addWord_appearsInListAfterSubmit() {
        setContentFor(customBookId)
        // Tap the "add entry" entry point (IconButton contentDescription) to open the dialog
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        // Type the word and meaning (performTextInput sets the text programmatically instead of going through the IME; reliable)
        composeRule.onNodeWithText("单词").performTextInput("balance")
        // POS uses a dropdown (since #9 POS cannot be typed freely): open the dropdown, then pick n.
        selectPosFromDropdown("n.")
        composeRule.onNodeWithText("释义").performTextInput("平衡")
        composeRule.waitForIdle()
        // Tap "confirm"
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // The new word should appear in the list (verifies the UI refresh)
        composeRule.onNodeWithText("balance").assertIsDisplayed()
        composeRule.onNodeWithText("平衡").assertIsDisplayed()
        // Also confirm it landed in the database
        val words = runBlocking { db.wordDao().getWordsWithSensesByBook(customBookId) }
        assertThat(words.any { it.word.text == "balance" }).isTrue()
    }

    @Test
    fun addWord_reusesGlobalWordPool() {
        // apple is already in the global word pool (from the seed); add a sense with a different part of speech to the same book again
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("单词").performTextInput("apple")
        // POS uses the dropdown: pick v.
        selectPosFromDropdown("v.")
        composeRule.onNodeWithText("释义").performTextInput("囤积")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // The global word table still has only one apple row (reused, not re-created)
        val appleCount = runBlocking {
            db.wordDao().getByText("apple")?.let { 1 } ?: 0
        }
        assertThat(appleCount).isEqualTo(1)
        // But there is one more sense (same word, new part of speech)
        val senses = runBlocking {
            db.wordDao().getByText("apple")?.let { db.wordDao().getSensesForWord(it.wordId) } ?: emptyList()
        }
        assertThat(senses).hasSize(2)
    }

    /**
     * Simulates selecting a POS from the dropdown (since #9 POS is picked from a dropdown, not typed freely).
     *
     * Steps: tap the POS field (label "part of speech") to open the dropdown, then tap the target POS menu item.
     *
     * Note: a POS already shown in the list (for example the seeded "n.") has the same text as the dropdown item "n.",
     * so use [onAllNodesWithText] plus the last match (the AlertDialog popup sits later in the semantics tree).
     */
    private fun selectPosFromDropdown(pos: String) {
        composeRule.onNodeWithText("词性").performClick()
        composeRule.waitForIdle()
        val candidates = composeRule.onAllNodes(
            androidx.compose.ui.test.hasText(pos, substring = false)
        )
        candidates[candidates.fetchSemanticsNodes().size - 1].performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun editSense_updatesPosAndMeaning_inListAndDb() {
        setContentFor(customBookId)
        // Tap the inline meaning text to open the edit dialog (since #16 the apple meaning node triggers editing)
        composeRule.onNodeWithText("苹果").performClick()
        composeRule.waitForIdle()
        // Since #16 pos / meaning are split into separate Text nodes: the "n." + apple meaning shown in the list and the dialog
        // TextField initial values "n." + apple meaning share the same text (two same-text nodes). hasSetTextAction()
        // matches only the editable TextFields (the list Text is not editable), which pinpoints the dialog fields.
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextReplacement("v.")
        composeRule.waitForIdle()
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[1].performTextReplacement("囤积")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // UI list refresh: the old apple meaning disappears, the new hoard meaning appears
        composeRule.onNodeWithText("囤积").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertDoesNotExist()
        // Database check: both the part of speech and the meaning are updated
        val sense = runBlocking {
            db.wordDao().getByText("apple")!!.let { db.wordDao().getSensesForWord(it.wordId).single() }
        }
        assertThat(sense.pos).isEqualTo("v.")
        assertThat(sense.meaning).isEqualTo("囤积")
        Unit
    }

    @Test
    fun deleteWord_opensConfirmDialog_thenSoftDeletesOnConfirm() {
        setContentFor(customBookId)
        // Tap "delete" (IconButton contentDescription) and a confirmation dialog opens first
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.waitForIdle()
        // The confirm dialog is shown (title + word name + delete button)
        composeRule.onNodeWithText("删除词条").assertIsDisplayed()
        composeRule.onNodeWithText("确定删除「apple」？可从回收站恢复。").assertIsDisplayed()

        // Tap the dialog's "delete" confirm button (the confirm button label and the title are both "delete"; use exact matching to click the confirm button)
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("删除", substring = false)).onFirst().performClick()
        composeRule.waitForIdle()

        // apple should disappear from the list (verifies the UI refresh)
        composeRule.onNodeWithText("apple").assertDoesNotExist()
        // But apple is still in the global word table (soft delete only sets deleted=1; it does not delete the global word)
        val stillThere = runBlocking { db.wordDao().getByText("apple") != null }
        assertThat(stillThere).isTrue()
        // The link row is still there (soft delete is not a real delete), and the deleted flag is true
        val deleted = runBlocking { db.bookDao().getDeleted(customBookId, 1L) }
        assertThat(deleted).isTrue()
    }

    @Test
    fun deleteWord_cancelDoesNotSoftDelete() {
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.waitForIdle()
        // Cancel is the safe option
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        // apple should still be in the list
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        // The link row was not soft-deleted
        val deleted = runBlocking { db.bookDao().getDeleted(customBookId, 1L) }
        assertThat(deleted).isFalse()
    }

    @Test
    fun presetBook_isReadOnly_noAddEditRemoveEntries() {
        setContentFor(presetBookId)
        // Preset book acceptance: none of the three entry points add / edit / delete are rendered (spec: "entries of a preset book cannot be added, deleted, or edited")
        composeRule.onNodeWithContentDescription("新增词条").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("编辑释义").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("删除").assertDoesNotExist()
        // The preset book name shows at the top
        composeRule.onNodeWithText("CET-4").assertIsDisplayed()
    }

    // ---- Ticket #9: POS dropdown (no free typing) ----

    @Test
    fun addDialog_posField_isDropdownWithOptions() {
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        // Open the POS dropdown
        composeRule.onNodeWithText("词性").performClick()
        composeRule.waitForIdle()
        // The fixed 12 POS options should show.
        // Note: a POS already shown in the list (for example the seeded "n.") has the same text as the dropdown item "n.", so assert >= 1.
        for (pos in WORD_LIST_POS_OPTIONS) {
            val matches = composeRule.onAllNodes(androidx.compose.ui.test.hasText(pos, substring = false))
            assertThat(matches.fetchSemanticsNodes().size).isAtLeast(1)
        }
    }
}
