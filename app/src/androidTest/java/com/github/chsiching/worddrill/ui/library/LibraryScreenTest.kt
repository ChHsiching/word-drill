package com.github.chsiching.worddrill.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.settings.SettingsRepository
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
 * Main seam of the book-list delete flow on the library tab (Ticket #18): Compose UI + in-memory Room.
 *
 * Acceptance coverage (maps to Issue #18 acceptance criteria):
 * - Tap "delete" and a confirm dialog opens (title + irreversible warning + cancel/delete buttons)
 * - Cancel: the book is kept (UI + database)
 * - Confirm delete: the book disappears (UI + database)
 * - Preset books do not render the "delete" entry point (no regression of the existing isPreset guard)
 *
 * Follows the [WordListScreenTest] template: createComposeRule + in-memory Room + directly constructed ViewModel.
 * SettingsRepository uses the real Context (the DataStore file lives in the test app's private dir; this test only reads
 * the currentBookId default and never writes, so there is no cross-test pollution).
 *
 * The dialog confirm button label and the list entry point are both "delete", so after the popup the semantics tree has two same-text nodes.
 * This follows the repo's existing pattern (see [WordListScreenTest], which uses onAllNodes(...)[index] for same-text nodes):
 * the list entry comes first and the AlertDialog popup after, so take the last match.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private var customBookId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        customBookId = db.bookDao().insert(Book(name = "我的生词本", isPreset = false))
        db.bookDao().insert(Book(name = "CET-4", isPreset = true))
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setContent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fileImporter = com.github.chsiching.worddrill.data.wordimport.FileWordImporter(
            context = context,
            db = db,
            wordDao = db.wordDao(),
            bookDao = db.bookDao(),
            dictionaryDao = db.dictionaryDao(),
        )
        val vm = LibraryViewModel(bookDao = db.bookDao(), settings = settings, fileImporter = fileImporter)
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    LibraryScreen(onOpenBook = {}, viewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun deleteClick_opensConfirmDialog() {
        setContent()
        // Tap the inline "delete" TextButton and the confirm dialog opens
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()

        // The dialog title plus the recoverable-from-recycle-bin warning containing the book name appear (Ticket #22: soft delete, so the copy changed to "recoverable from the recycle bin")
        composeRule.onNodeWithText("删除词书").assertIsDisplayed()
        composeRule.onNodeWithText("确定删除「我的生词本」？可从回收站恢复。").assertIsDisplayed()
        // The cancel button exists (evidence the dialog rendered)
        composeRule.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test
    fun cancelDelete_keepsBook_inListAndDb() {
        setContent()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        // The book is still in the list (substring=false matches the exact book name, avoiding hits on the subtitle format string)
        composeRule.onNodeWithText("我的生词本", substring = false).assertIsDisplayed()
        // Still there in the database too
        val stillThere = runBlocking {
            db.bookDao().observeAllWithCounts().first().any { it.bookId == customBookId }
        }
        assertThat(stillThere).isTrue()
    }

    @Test
    fun confirmDelete_softDeletesBook_fromListAndDb() {
        setContent()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()
        // After the popup there are 2 "delete" nodes (list entry + dialog confirm); take the popup one (the last)
        // hasText defaults to substring=false (unlike onNodeWithText), matching the exact label "delete",
        // which avoids wrongly matching texts that contain "delete", such as the dialog title for deleting a book
        val deleteNodes = composeRule.onAllNodes(hasText("删除", substring = false))
        assertThat(deleteNodes.fetchSemanticsNodes().size).isEqualTo(2)
        deleteNodes[1].performClick()
        composeRule.waitForIdle()

        // The book disappears from the list (Ticket #22: soft delete sets deleted=1 and observeAllWithCounts filters it out)
        composeRule.onNodeWithText("我的生词本", substring = false).assertDoesNotExist()
        // Also gone from the visible list (deleted=0)
        val gone = runBlocking {
            db.bookDao().observeAllWithCounts().first().none { it.bookId == customBookId }
        }
        assertThat(gone).isTrue()
        // Soft delete is not a real delete: the book row is still there with deleted true (recoverable from the recycle bin)
        val softDeleted = runBlocking { db.bookDao().getById(customBookId)?.deleted }
        assertThat(softDeleted).isTrue()
    }

    @Test
    fun presetBook_hasNoDeleteEntry() {
        setContent()
        // Preset book acceptance: the CET-4 row shows no delete entry, so the count of "delete" TextButtons in the whole list == the number of custom books (1)
        val deleteButtons = composeRule.onAllNodes(hasText("删除", substring = false))
        assertThat(deleteButtons.fetchSemanticsNodes().size).isEqualTo(1)
    }

    // ---- Ticket #21: file import entry point ----

    @Test
    fun importButton_isDisplayed() {
        setContent()
        // The "import from file" button sits below the new-book button and should show
        composeRule.onNodeWithText("从文件导入").assertIsDisplayed()
    }

    @Test
    fun importButton_click_opensImportDialog() {
        setContent()
        composeRule.onNodeWithText("从文件导入").performClick()
        composeRule.waitForIdle()
        // The dialog title + pick-file button + hint text should all show
        composeRule.onNodeWithText("从文件导入词书").assertIsDisplayed()
        composeRule.onNodeWithText("选择文件").assertIsDisplayed()
    }
}
