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
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                mapOf("bookId" to bookId.toString())
            ).also { },
            db = db,
            wordDao = db.wordDao(),
            bookDao = db.bookDao(),
            dictionaryDao = db.dictionaryDao(),
            progressResetter = com.github.chsiching.worddrill.data.ProgressResetter(
                db, db.bookDao(),
                com.github.chsiching.worddrill.data.settings.SettingsRepository(
                    ApplicationProvider.getApplicationContext()
                ),
            ),
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
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("新增词条").assertIsDisplayed()
    }

    @Test
    fun addWord_appearsInListAfterSubmit() {
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("单词").performTextInput("balance")
        selectPosFromDropdown("n.")
        composeRule.onNodeWithText("释义").performTextInput("平衡")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("balance").assertIsDisplayed()
        composeRule.onNodeWithText("平衡").assertIsDisplayed()
        val words = runBlocking { db.wordDao().getWordsWithSensesByBook(customBookId) }
        assertThat(words.any { it.word.text == "balance" }).isTrue()
    }

    @Test
    fun addWord_reusesGlobalWordPool() {
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("单词").performTextInput("apple")
        selectPosFromDropdown("v.")
        composeRule.onNodeWithText("释义").performTextInput("囤积")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        val appleCount = runBlocking {
            db.wordDao().getByText("apple")?.let { 1 } ?: 0
        }
        assertThat(appleCount).isEqualTo(1)
        val senses = runBlocking {
            db.wordDao().getByText("apple")?.let { db.wordDao().getSensesForWord(it.wordId) } ?: emptyList()
        }
        assertThat(senses).hasSize(2)
    }

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
        composeRule.onNodeWithText("苹果").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextReplacement("v.")
        composeRule.waitForIdle()
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[1].performTextReplacement("囤积")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("囤积").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertDoesNotExist()
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
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("删除词条").assertIsDisplayed()
        composeRule.onNodeWithText("确定删除「apple」？可从回收站恢复。").assertIsDisplayed()

        composeRule.onAllNodes(androidx.compose.ui.test.hasText("删除", substring = false)).onFirst().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("apple").assertDoesNotExist()
        val stillThere = runBlocking { db.wordDao().getByText("apple") != null }
        assertThat(stillThere).isTrue()
        val deleted = runBlocking { db.bookDao().getDeleted(customBookId, 1L) }
        assertThat(deleted).isTrue()
    }

    @Test
    fun deleteWord_cancelDoesNotSoftDelete() {
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("apple").assertIsDisplayed()
        val deleted = runBlocking { db.bookDao().getDeleted(customBookId, 1L) }
        assertThat(deleted).isFalse()
    }

    @Test
    fun presetBook_isReadOnly_noAddEditRemoveEntries() {
        setContentFor(presetBookId)
        composeRule.onNodeWithContentDescription("新增词条").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("编辑释义").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("删除").assertDoesNotExist()
        composeRule.onNodeWithText("CET-4").assertIsDisplayed()
    }

    @Test
    fun addDialog_posField_isDropdownWithOptions() {
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("词性").performClick()
        composeRule.waitForIdle()
        for (pos in WORD_LIST_POS_OPTIONS) {
            val matches = composeRule.onAllNodes(androidx.compose.ui.test.hasText(pos, substring = false))
            assertThat(matches.fetchSemanticsNodes().size).isAtLeast(1)
        }
    }
}
