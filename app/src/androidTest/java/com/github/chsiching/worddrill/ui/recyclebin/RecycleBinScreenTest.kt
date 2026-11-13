package com.github.chsiching.worddrill.ui.recyclebin

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Main seam of the recycle bin screen (Ticket #22): Compose UI + in-memory Room.
 *
 * Acceptance coverage:
 * - Empty state: with no soft-deleted entries, "recycle bin is empty" shows
 * - The list shows soft-deleted entries (word name + "(removed from word book X)" subtitle)
 * - Restore: tap "restore" and the entry disappears from the list and the link's deleted flag is set back to 0
 * - Permanently delete: tap "permanently delete", a confirmation dialog opens, confirm, and the entry disappears and the link is really deleted
 * - Permanently delete cancelled: tap "permanently delete", cancel, and the entry stays
 */
@RunWith(AndroidJUnit4::class)
class RecycleBinScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: WordDrillDatabase
    private var bookId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookId = db.bookDao().insert(Book(name = "我的生词本", isPreset = false))
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Seeds one word and soft-deletes it (into the recycle bin). Returns the wordId. */
    private suspend fun seedDeletedWord(text: String): Long {
        val wId = db.wordDao().insert(Word(text = text))
        db.bookDao().linkBookWord(BookWord(bookId, wId))
        db.bookDao().setDeleted(bookId, wId, deleted = true)
        return wId
    }

    private fun setContent() {
        val vm = RecycleBinViewModel(bookDao = db.bookDao())
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    RecycleBinScreen(onBack = {}, viewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun emptyState_showsWhenNoDeletedEntries() {
        setContent()
        composeRule.onNodeWithText("回收站为空").assertIsDisplayed()
    }

    @Test
    fun listsDeletedEntries_withBookNameSubtitle() = runBlocking {
        seedDeletedWord("apple")
        setContent()
        // Both the word name and the subtitle "apple (removed from My word book)" are displayed
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("apple（从 我的生词本 移除）").assertIsDisplayed()
        Unit
    }

    @Test
    fun restore_removesEntryAndClearsDeletedFlag() = runBlocking {
        val wId = seedDeletedWord("apple")
        setContent()
        assertThat(db.bookDao().observeDeletedEntries().first()).hasSize(1)

        composeRule.onNodeWithText("恢复").performClick()
        composeRule.waitForIdle()

        // apple disappears from the recycle bin list
        composeRule.onNodeWithText("apple").assertDoesNotExist()
        // The link's deleted flag is set to 0
        assertThat(db.bookDao().getDeleted(bookId, wId)).isFalse()
        // The recycle bin is empty now
        assertThat(db.bookDao().observeDeletedEntries().first()).isEmpty()
        Unit
    }

    @Test
    fun purge_opensConfirmDialog_andDeletesOnConfirm() = runBlocking {
        val wId = seedDeletedWord("apple")
        setContent()

        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.waitForIdle()
        // The confirmation dialog is shown
        composeRule.onNodeWithText("永久删除「apple」？此操作不可撤销。").assertIsDisplayed()

        // Click the dialog's confirm button "permanently delete".
        // There are 3 nodes with the text "permanently delete": the list item TextButton + the dialog title Text + the dialog confirm button.
        // hasClickAction filters out the title Text (not clickable), leaving 2 clickable nodes (list item + dialog confirm);
        // take the popup one (the last), the same pattern as LibraryScreenTest.confirmDelete.
        val purgeNodes = composeRule.onAllNodes(
            androidx.compose.ui.test.hasText("永久删除", substring = false) and
                androidx.compose.ui.test.hasClickAction()
        )
        assertThat(purgeNodes.fetchSemanticsNodes().size).isEqualTo(2)
        purgeNodes[1].performClick()
        composeRule.waitForIdle()

        // apple disappears from the list
        composeRule.onNodeWithText("apple").assertDoesNotExist()
        // The link was really deleted (getDeleted returns null)
        assertThat(db.bookDao().getDeleted(bookId, wId)).isNull()
        // apple is still in the global word pool (permanent delete removes only the link, not the global word)
        assertThat(db.wordDao().getByText("apple")).isNotNull()
        Unit
    }

    @Test
    fun purge_cancelKeepsEntry() = runBlocking {
        val wId = seedDeletedWord("apple")
        setContent()

        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.waitForIdle()
        // Cancel is the safe option
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        // The entry is still in the recycle bin
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        assertThat(db.bookDao().getDeleted(bookId, wId)).isTrue()
        Unit
    }
}
