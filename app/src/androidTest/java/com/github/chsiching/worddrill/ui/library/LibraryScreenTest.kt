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
 * 「库」Tab 词书列表删除流程的主接缝（Ticket #18）：Compose UI + 内存版 Room。
 *
 * 验收覆盖（对应 Issue #18 acceptance criteria）：
 * - 点「删除」→ 弹出确认对话框（标题 + 不可撤销提示 + 取消/删除按钮）
 * - 取消：词书保留（UI + 落库）
 * - 确认删除：词书消失（UI + 落库）
 * - 预置词书不渲染「删除」入口（既有 isPreset 守卫不回归）
 *
 * 模板套 [WordListScreenTest]：createComposeRule + 内存 Room + 直接构造 ViewModel。
 * SettingsRepository 用真实 Context（DataStore 文件在测试 app 私有目录；本测试只读
 * currentBookId 默认值，不写，无跨测试污染）。
 *
 * 对话框确认按钮文案与列表入口同为「删除」，弹窗后语义树存在两个同文本节点。
 * 沿用 repo 既有模式（见 [WordListScreenTest] 用 onAllNodes(...)[index] 处理同名节点）：
 * 列表入口在前、AlertDialog popup 在后，取最后一个匹配。
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
        // 点行内「删除」TextButton → 弹确认对话框
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()

        // 对话框标题 + 含书名的可恢复提示出现（Ticket #22：软删，文案改为「可从回收站恢复」）
        composeRule.onNodeWithText("删除词书").assertIsDisplayed()
        composeRule.onNodeWithText("确定删除「我的生词本」？可从回收站恢复。").assertIsDisplayed()
        // 取消按钮存在（对话框渲染证据）
        composeRule.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test
    fun cancelDelete_keepsBook_inListAndDb() {
        setContent()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        // 词书仍在列表（substring=false 精确匹配书名，避免命中副标题格式串）
        composeRule.onNodeWithText("我的生词本", substring = false).assertIsDisplayed()
        // 落库也仍在
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
        // 弹窗后「删除」节点 == 2（列表入口 + 对话框确认）；取 popup（最后一个）
        // hasText 默认 substring=false（与 onNodeWithText 不同），精确匹配"删除"二字，
        // 避免误匹配对话框标题"删除词书"等含"删除"的文本
        val deleteNodes = composeRule.onAllNodes(hasText("删除", substring = false))
        assertThat(deleteNodes.fetchSemanticsNodes().size).isEqualTo(2)
        deleteNodes[1].performClick()
        composeRule.waitForIdle()

        // 列表里词书消失（Ticket #22：软删 deleted=1，observeAllWithCounts 过滤掉）
        composeRule.onNodeWithText("我的生词本", substring = false).assertDoesNotExist()
        // 可见列表（deleted=0）里也消失
        val gone = runBlocking {
            db.bookDao().observeAllWithCounts().first().none { it.bookId == customBookId }
        }
        assertThat(gone).isTrue()
        // 软删 ≠ 真删：词书行还在，deleted 标记为 true（可从回收站恢复）
        val softDeleted = runBlocking { db.bookDao().getById(customBookId)?.deleted }
        assertThat(softDeleted).isTrue()
    }

    @Test
    fun presetBook_hasNoDeleteEntry() {
        setContent()
        // 预置词书验收：CET-4 行不显示删除入口 → 全列表「删除」TextButton 数 == 自定义词书数(1)
        val deleteButtons = composeRule.onAllNodes(hasText("删除", substring = false))
        assertThat(deleteButtons.fetchSemanticsNodes().size).isEqualTo(1)
    }

    // ---- Ticket #21：文件导入入口 ----

    @Test
    fun importButton_isDisplayed() {
        setContent()
        // 「从文件导入」按钮在新建词书按钮下方，应展示
        composeRule.onNodeWithText("从文件导入").assertIsDisplayed()
    }

    @Test
    fun importButton_click_opensImportDialog() {
        setContent()
        composeRule.onNodeWithText("从文件导入").performClick()
        composeRule.waitForIdle()
        // 对话框标题 + 选择文件按钮 + 说明文案 都应展示
        composeRule.onNodeWithText("从文件导入词书").assertIsDisplayed()
        composeRule.onNodeWithText("选择文件").assertIsDisplayed()
    }
}
