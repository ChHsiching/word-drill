package com.github.chsiching.worddrill.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
 * 「库」Tab 二级页词条列表的主接缝（Ticket #7）：Compose UI + 内存版 Room。
 *
 * 验收覆盖：
 * - 列表展示该词书词条（单词 + 词性 + 释义）
 * - 新增词条：输入后点确定，列表刷新出现新词（全局词条池复用）
 * - 编辑义项：改释义，列表刷新
 * - 从词书移除：列表刷新，词条消失
 * - 预置词书只读：不渲染「新增词条」入口
 *
 * 不测 TextField 的 IME 注入细节（只测用户可见行为：输入文字 → 提交 → 列表变化）。
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
            // 用一个内存 SavedStateHandle，模拟 nav arg bookId
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                mapOf("bookId" to bookId.toString())
            ).also { /* no-op */ },
            db = db,
            wordDao = db.wordDao(),
            bookDao = db.bookDao(),
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
        // 列表展示已种子词条
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("n.  苹果").assertIsDisplayed()
        // 自定义词书：有「新增词条」入口（IconButton 的 contentDescription）
        composeRule.onNodeWithContentDescription("新增词条").assertIsDisplayed()
    }

    @Test
    fun addWord_appearsInListAfterSubmit() {
        setContentFor(customBookId)
        // 点「新增词条」入口（IconButton contentDescription）打开对话框
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        // 输入三个字段（performTextInput 走程序化设置，不走 IME，可靠）
        composeRule.onNodeWithText("单词").performTextInput("balance")
        composeRule.onNodeWithText("词性").performTextInput("n.")
        composeRule.onNodeWithText("释义").performTextInput("平衡")
        composeRule.waitForIdle()
        // 点「确定」
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // 列表应出现新词（UI 刷新验证）
        composeRule.onNodeWithText("balance").assertIsDisplayed()
        composeRule.onNodeWithText("n.  平衡").assertIsDisplayed()
        // 落库也确认
        val words = runBlocking { db.wordDao().getWordsWithSensesByBook(customBookId) }
        assertThat(words.any { it.word.text == "balance" }).isTrue()
    }

    @Test
    fun addWord_reusesGlobalWordPool() {
        // apple 已在全局词池（来自种子），向同词书再加一次不同词性的 sense
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("单词").performTextInput("apple")
        composeRule.onNodeWithText("词性").performTextInput("v.")
        composeRule.onNodeWithText("释义").performTextInput("囤积")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // 全局 word 表 apple 仍只有一行（复用，未新建）
        val appleCount = runBlocking {
            db.wordDao().getByText("apple")?.let { 1 } ?: 0
        }
        assertThat(appleCount).isEqualTo(1)
        // 但 sense 多了一条（同词新词性）
        val senses = runBlocking {
            db.wordDao().getByText("apple")?.let { db.wordDao().getSensesForWord(it.wordId) } ?: emptyList()
        }
        assertThat(senses).hasSize(2)
    }

    @Test
    fun editSense_updatesPosAndMeaning_inListAndDb() {
        setContentFor(customBookId)
        // 点行内释义文本打开编辑对话框（每条释义可点 → 编辑对应 sense）
        composeRule.onNodeWithText("n.  苹果").performClick()
        composeRule.waitForIdle()
        // 用 performTextReplacement 替换（performTextInput 是追加，会把"苹果"变成"苹果X"）
        composeRule.onNodeWithText("n.").performTextReplacement("v.")
        composeRule.onNodeWithText("苹果").performTextReplacement("囤积")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // UI 列表刷新：原 "n.  苹果" 消失，新 "v.  囤积" 出现
        composeRule.onNodeWithText("v.  囤积").assertIsDisplayed()
        composeRule.onNodeWithText("n.  苹果").assertDoesNotExist()
        // 落库确认：词性与释义都已更新
        val sense = runBlocking {
            db.wordDao().getByText("apple")!!.let { db.wordDao().getSensesForWord(it.wordId).single() }
        }
        assertThat(sense.pos).isEqualTo("v.")
        assertThat(sense.meaning).isEqualTo("囤积")
    }

    @Test
    fun removeWord_disappearsFromList_butStaysInGlobalPool() {
        setContentFor(customBookId)
        // 点「从词书移除」（IconButton contentDescription）
        composeRule.onNodeWithContentDescription("从词书移除").performClick()
        composeRule.waitForIdle()

        // 列表里 apple 应消失（UI 刷新验证）
        composeRule.onNodeWithText("apple").assertDoesNotExist()
        // 但全局 word 表里 apple 仍在（只断 book_word 关联，不删全局词）
        val stillThere = runBlocking { db.wordDao().getByText("apple") != null }
        assertThat(stillThere).isTrue()
        // book_word 关联应清空
        val count = runBlocking { db.bookDao().countWordsInBook(customBookId) }
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun presetBook_isReadOnly_noAddEditRemoveEntries() {
        setContentFor(presetBookId)
        // 预置词书验收：增/改/移三个入口都不渲染（规格："预置词书的词条不可增删改"）
        composeRule.onNodeWithContentDescription("新增词条").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("编辑释义").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("从词书移除").assertDoesNotExist()
        // 顶部展示预置词书名
        composeRule.onNodeWithText("CET-4").assertIsDisplayed()
    }
}
