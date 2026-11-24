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

        composeRule.onNodeWithText("apple").assertDoesNotExist()
        assertThat(db.bookDao().getDeleted(bookId, wId)).isFalse()
        assertThat(db.bookDao().observeDeletedEntries().first()).isEmpty()
        Unit
    }

    @Test
    fun purge_opensConfirmDialog_andDeletesOnConfirm() = runBlocking {
        val wId = seedDeletedWord("apple")
        setContent()

        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("无法恢复").assertIsDisplayed()

        val purgeNodes = composeRule.onAllNodes(
            androidx.compose.ui.test.hasText("永久删除", substring = false) and
                androidx.compose.ui.test.hasClickAction()
        )
        assertThat(purgeNodes.fetchSemanticsNodes().size).isEqualTo(2)
        purgeNodes[1].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("apple").assertDoesNotExist()
        assertThat(db.bookDao().getDeleted(bookId, wId)).isNull()
        assertThat(db.wordDao().getByText("apple")).isNotNull()
        Unit
    }

    @Test
    fun purge_cancelKeepsEntry() = runBlocking {
        val wId = seedDeletedWord("apple")
        setContent()

        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("apple").assertIsDisplayed()
        assertThat(db.bookDao().getDeleted(bookId, wId)).isTrue()
        Unit
    }
}
