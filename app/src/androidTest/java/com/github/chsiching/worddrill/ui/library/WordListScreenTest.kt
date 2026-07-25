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
        // 列表展示已种子词条（#16 起 pos 与 meaning 拆成独立 Text 节点）
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertIsDisplayed()
        // 自定义词书：有「新增词条」入口（IconButton 的 contentDescription）
        composeRule.onNodeWithContentDescription("新增词条").assertIsDisplayed()
    }

    @Test
    fun addWord_appearsInListAfterSubmit() {
        setContentFor(customBookId)
        // 点「新增词条」入口（IconButton contentDescription）打开对话框
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        // 输入单词与释义（performTextInput 走程序化设置，不走 IME，可靠）
        composeRule.onNodeWithText("单词").performTextInput("balance")
        // POS 走下拉（#9 起 POS 不能自由输入）：点开下拉 → 选 n.
        selectPosFromDropdown("n.")
        composeRule.onNodeWithText("释义").performTextInput("平衡")
        composeRule.waitForIdle()
        // 点「确定」
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // 列表应出现新词（UI 刷新验证）
        composeRule.onNodeWithText("balance").assertIsDisplayed()
        composeRule.onNodeWithText("平衡").assertIsDisplayed()
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
        // POS 走下拉：选 v.
        selectPosFromDropdown("v.")
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

    /**
     * 模拟 POS 下拉选择（#9 起 POS 从下拉选，不自由输入）。
     *
     * 步骤：点 POS 字段（label "词性"）展开下拉 → 点目标 POS 菜单项。
     *
     * 注意：列表里已展示的词条 POS（如种子 "n."）会与下拉项 "n." 同文本，
     * 用 [onAllNodesWithText] + 最后一个匹配（AlertDialog popup 在语义树后部）。
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
        // 点行内释义文本打开编辑对话框（#16 起点 meaning 节点「苹果」触发编辑）
        composeRule.onNodeWithText("苹果").performClick()
        composeRule.waitForIdle()
        // #16 起 pos / meaning 拆成独立 Text 节点：列表显示的 "n." + "苹果" 与对话框
        // TextField 的初始值 "n." + "苹果" 同名（两个同名节点）。用 hasSetTextAction()
        // 只匹配可编辑的 TextField（列表 Text 不可编辑），精确定位对话框字段。
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextReplacement("v.")
        composeRule.waitForIdle()
        composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[1].performTextReplacement("囤积")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        // UI 列表刷新：原 meaning "苹果" 消失，新 meaning "囤积" 出现
        composeRule.onNodeWithText("囤积").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertDoesNotExist()
        // 落库确认：词性与释义都已更新
        val sense = runBlocking {
            db.wordDao().getByText("apple")!!.let { db.wordDao().getSensesForWord(it.wordId).single() }
        }
        assertThat(sense.pos).isEqualTo("v.")
        assertThat(sense.meaning).isEqualTo("囤积")
        Unit
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

    // ---- Ticket #9：POS 下拉（不能自由输入）----

    @Test
    fun addDialog_posField_isDropdownWithOptions() {
        setContentFor(customBookId)
        composeRule.onNodeWithContentDescription("新增词条").performClick()
        composeRule.waitForIdle()
        // 点开 POS 下拉
        composeRule.onNodeWithText("词性").performClick()
        composeRule.waitForIdle()
        // 固定 12 个 POS 选项应展示。
        // 注：列表里已展示的 POS（如种子 "n."）与下拉项 "n." 同文本，用 >= 1 断言。
        for (pos in WORD_LIST_POS_OPTIONS) {
            val matches = composeRule.onAllNodes(androidx.compose.ui.test.hasText(pos, substring = false))
            assertThat(matches.fetchSemanticsNodes().size).isAtLeast(1)
        }
    }
}
