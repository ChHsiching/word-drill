package com.github.chsiching.worddrill.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("删除词书").assertIsDisplayed()
        composeRule.onNodeWithText("确定删除「我的生词本」？可从回收站恢复。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test
    fun cancelDelete_keepsBook_inListAndDb() {
        setContent()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("我的生词本", substring = false).assertIsDisplayed()
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
        val deleteNodes = composeRule.onAllNodes(hasText("删除", substring = false))
        assertThat(deleteNodes.fetchSemanticsNodes().size).isEqualTo(2)
        deleteNodes[1].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("我的生词本", substring = false).assertDoesNotExist()
        val gone = runBlocking {
            db.bookDao().observeAllWithCounts().first().none { it.bookId == customBookId }
        }
        assertThat(gone).isTrue()
        val softDeleted = runBlocking { db.bookDao().getById(customBookId)?.deleted }
        assertThat(softDeleted).isTrue()
    }

    @Test
    fun presetBook_hasNoDeleteEntry() {
        setContent()
        val deleteButtons = composeRule.onAllNodes(hasText("删除", substring = false))
        assertThat(deleteButtons.fetchSemanticsNodes().size).isEqualTo(1)
    }

    @Test
    fun importButton_isDisplayed() {
        setContent()
        composeRule.onNodeWithText("从文件导入").assertIsDisplayed()
    }

    @Test
    fun createDialog_confirmDisabledUntilNameEntered() {
        setContent()
        composeRule.onNodeWithText("新建词书").performClick()
        composeRule.onNodeWithText("确定").assertIsNotEnabled()
        composeRule.onNode(hasText("词书名称")).performTextInput("X")
        composeRule.onNodeWithText("确定").assertIsEnabled()
        composeRule.onNodeWithText("取消").performClick()
    }

    @Test
    fun importButton_click_opensImportDialog() {
        setContent()
        composeRule.onNodeWithText("从文件导入").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("从文件导入词书").assertIsDisplayed()
        composeRule.onNodeWithText("选择文件").assertIsDisplayed()
    }
}
