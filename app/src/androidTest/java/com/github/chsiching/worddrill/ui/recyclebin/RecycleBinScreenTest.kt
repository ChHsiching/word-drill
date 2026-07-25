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
 * 回收站页面主接缝（Ticket #22）：Compose UI + 内存版 Room。
 *
 * 验收覆盖：
 * - 空态：无软删条目时显示「回收站为空」
 * - 列表展示软删条目（词名 + 「（从 X 词书移除）」副标题）
 * - 恢复：点「恢复」→ 条目从列表消失 + 关联 deleted 置 0
 * - 永久删除：点「永久删除」→ 二次确认 → 确认 → 条目消失 + 关联真删
 * - 永久删除取消：点「永久删除」→ 取消 → 条目仍在
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

    /** 种一个词并软删它（进回收站）。返回 wordId。 */
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
        // 词名 + 副标题「apple（从 我的生词本 移除）」都展示
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

        // 回收站列表里 apple 消失
        composeRule.onNodeWithText("apple").assertDoesNotExist()
        // 关联 deleted 标记置 0
        assertThat(db.bookDao().getDeleted(bookId, wId)).isFalse()
        // 回收站空了
        assertThat(db.bookDao().observeDeletedEntries().first()).isEmpty()
        Unit
    }

    @Test
    fun purge_opensConfirmDialog_andDeletesOnConfirm() = runBlocking {
        val wId = seedDeletedWord("apple")
        setContent()

        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.waitForIdle()
        // 二次确认对话框展示
        composeRule.onNodeWithText("永久删除「apple」？此操作不可撤销。").assertIsDisplayed()

        // 点对话框确认按钮「永久删除」。
        // 「永久删除」节点有 3 个：列表项 TextButton + 对话框标题 Text + 对话框确认按钮。
        // 用 hasClickAction 过滤掉标题 Text（不可点击），剩 2 个可点击节点（列表项 + 对话框确认）；
        // 取 popup（最后一个）—— 与 LibraryScreenTest.confirmDelete 同模式。
        val purgeNodes = composeRule.onAllNodes(
            androidx.compose.ui.test.hasText("永久删除", substring = false) and
                androidx.compose.ui.test.hasClickAction()
        )
        assertThat(purgeNodes.fetchSemanticsNodes().size).isEqualTo(2)
        purgeNodes[1].performClick()
        composeRule.waitForIdle()

        // 列表里 apple 消失
        composeRule.onNodeWithText("apple").assertDoesNotExist()
        // 关联真删了（getDeleted 返回 null）
        assertThat(db.bookDao().getDeleted(bookId, wId)).isNull()
        // 全局 word 池里 apple 仍在（永久删除只删关联，不删全局词）
        assertThat(db.wordDao().getByText("apple")).isNotNull()
        Unit
    }

    @Test
    fun purge_cancelKeepsEntry() = runBlocking {
        val wId = seedDeletedWord("apple")
        setContent()

        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.waitForIdle()
        // 取消是安全选项
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        // 条目仍在回收站
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        assertThat(db.bookDao().getDeleted(bookId, wId)).isTrue()
        Unit
    }
}
