package com.github.chsiching.worddrill.ui.me

import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.backup.BackupService
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.min
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private var bookId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        bookId = db.bookDao().insert(Book(name = "测试词书", isPreset = false))
        val w1 = db.wordDao().insert(Word(text = "apple"))
        val w2 = db.wordDao().insert(Word(text = "balance"))
        val w3 = db.wordDao().insert(Word(text = "circle"))
        db.bookDao().linkBookWord(BookWord(bookId, w1))
        db.bookDao().linkBookWord(BookWord(bookId, w2))
        db.bookDao().linkBookWord(BookWord(bookId, w3))
        settings.setCurrentBookId(bookId)
        Unit
    }

    @After
    fun tearDown() {
        db.close()
        runBlocking { settings.setDwellInterval(5f) }
    }

    private fun seedSwipe(wordId: Long, timestamp: Long, bid: Long = bookId) = runBlocking {
        db.swipeLogDao().insert(SwipeLog(bookId = bid, wordId = wordId, timestamp = timestamp))
        Unit
    }

    private fun vm(): MeViewModel = MeViewModel(
        swipeLogDao = db.swipeLogDao(),
        bookDao = db.bookDao(),
        settings = settings,
    )

    private fun settingsVm(): SettingsViewModel = SettingsViewModel(settings)

    private fun exportImportVm(): ExportImportViewModel {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = ctx.applicationContext as android.app.Application
        return ExportImportViewModel(app, BackupService(db, db.bookDao(), db.wordDao(), db.swipeLogDao()))
    }

    private suspend fun stableState(viewModel: MeViewModel): MeStatsUiState =
        viewModel.uiState.first { it.bookName.isNotEmpty() }

    private fun renderWithVm(
        viewModel: MeViewModel,
        settingsViewModel: SettingsViewModel = settingsVm(),
        exportImportViewModel: ExportImportViewModel = exportImportVm(),
    ) {
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    MeScreen(
                        onNavigateToRecycleBin = {},
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        exportImportViewModel = exportImportViewModel,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun showsThreeStatsSections_initially() {
        renderWithVm(vm())
        composeRule.onNodeWithText("今日刷卡").assertIsDisplayed()
        composeRule.onNodeWithText("累计刷卡").assertIsDisplayed()
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
    }

    @Test
    fun viewModel_aggregatesStatsCorrectly_afterSwipes() = runBlocking {
        val now = System.currentTimeMillis()
        seedSwipe(wordId = 1, timestamp = now - 1000)
        seedSwipe(wordId = 2, timestamp = now - 500)
        seedSwipe(wordId = 1, timestamp = now - 200) // repeated word
        seedSwipe(wordId = 3, timestamp = now - 100_000_000L) // counts toward the total but not today

        val state = stableState(vm())
        assertThat(state.totalCount).isEqualTo(4)
        assertThat(state.todayCount).isEqualTo(3)
        assertThat(state.brushed).isEqualTo(3)
        assertThat(state.total).isEqualTo(3)
        assertThat(state.percent).isEqualTo(100)
    }

    @Test
    fun progressLine_showsBookNameAndRatio_afterSwipes() {
        runBlocking {
            val now = System.currentTimeMillis()
            seedSwipe(wordId = 1, timestamp = now)
            seedSwipe(wordId = 2, timestamp = now)
        }
        renderWithVm(vm())
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        composeRule.onNodeWithText("66%").assertIsDisplayed()
        composeRule.onNodeWithText("今日刷卡").assertIsDisplayed()
        composeRule.onNodeWithText("累计刷卡").assertIsDisplayed()
    }

    @Test
    fun progressLine_showsZeroPercent_whenBookEmpty() {
        runBlocking {
            val emptyBookId = db.bookDao().insert(Book(name = "空词书", isPreset = false))
            settings.setCurrentBookId(emptyBookId)
        }
        renderWithVm(vm())
        composeRule.onNodeWithText("空词书").assertIsDisplayed()
        composeRule.onNodeWithText("0 / 0").assertIsDisplayed()
        composeRule.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun progress_followsNewCurrentBook_afterSwitch() = runBlocking {
        val now = System.currentTimeMillis()
        seedSwipe(wordId = 1, timestamp = now)
        seedSwipe(wordId = 2, timestamp = now)
        assertThat(stableState(vm()).percent).isEqualTo(66)

        val other = db.bookDao().insert(Book(name = "另一本", isPreset = false))
        val ow1 = db.wordDao().insert(Word(text = "dog"))
        val ow2 = db.wordDao().insert(Word(text = "egg"))
        db.bookDao().linkBookWord(BookWord(other, ow1))
        db.bookDao().linkBookWord(BookWord(other, ow2))
        settings.setCurrentBookId(other)
        seedSwipe(wordId = ow1, timestamp = now, bid = other)

        val state = stableState(vm())
        assertThat(state.brushed).isEqualTo(1)
        assertThat(state.total).isEqualTo(2)
        assertThat(state.percent).isEqualTo(50)
        assertThat(state.bookName).isEqualTo("另一本")
    }

    @Test
    fun settingsGroups_showDisplayNavigationGeneralLabels() {
        renderWithVm(vm())
        composeRule.onNodeWithText("显示").assertIsDisplayed()
        composeRule.onNodeWithText("导航").assertIsDisplayed()
        composeRule.onNodeWithText("通用", substring = false)
        composeRule.onNodeWithText("隐藏音标").assertIsDisplayed()
        // The CI emulator viewport is shorter: assert the top-bar icon before any
        // performScrollTo moves it off screen, and scroll below-fold rows into view.
        composeRule.onNodeWithContentDescription("主题").assertIsDisplayed()
        composeRule.onNodeWithText("导航栏风格").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("简约导航").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("导出数据")
        composeRule.onNodeWithText("导入数据")
        composeRule.onNodeWithText("关于", substring = false)
    }

    @Test
    fun themeIcon_lightCyclesToDark() = runBlocking {
        runBlocking { settings.setTheme(ThemeMode.LIGHT) }
        val svm = settingsVm()
        renderWithVm(vm(), settingsViewModel = svm)
        svm.themeMode.first { it == ThemeMode.LIGHT }
        composeRule.onNodeWithContentDescription("主题").performClick()
        composeRule.waitForIdle()
        val mode = settings.themePreference.first { it != ThemeMode.LIGHT }
        assertThat(mode).isEqualTo(ThemeMode.DARK)
        Unit
    }

    @Test
    fun themeIcon_darkCyclesToSystem() = runBlocking {
        runBlocking { settings.setTheme(ThemeMode.DARK) }
        val svm = settingsVm()
        renderWithVm(vm(), settingsViewModel = svm)
        svm.themeMode.first { it == ThemeMode.DARK }
        composeRule.onNodeWithContentDescription("主题").performClick()
        composeRule.waitForIdle()
        val mode = settings.themePreference.first { it != ThemeMode.DARK }
        assertThat(mode).isEqualTo(ThemeMode.SYSTEM)
        Unit
    }

    @Test
    fun aboutDialog_showsAppNameAndVersion_whenOpened() {
        renderWithVm(vm())
        composeRule.onNodeWithText("关于", substring = false).performScrollTo()
        composeRule.onNodeWithText("关于", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("关于 WordDrill").assertIsDisplayed()
        composeRule.onNodeWithText("WordDrill", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("版本 0.2.1").assertIsDisplayed()
    }

    @Test
    fun exportDialog_showsNicknameField_whenExportClicked() {
        renderWithVm(vm())
        composeRule.onNodeWithText("导出数据").performScrollTo()
        composeRule.onNodeWithText("导出数据").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("将整库（词条池、词书、刷卡日志）导出为 JSON 文件，用于换机迁移。").assertIsDisplayed()
        composeRule.onNodeWithText("可选昵称标签").assertIsDisplayed()
        composeRule.onNodeWithText("取消", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("将整库（词条池、词书、刷卡日志）导出为 JSON 文件，用于换机迁移。").assertIsNotDisplayed()
    }

    @Test
    fun importDialog_showsOverwriteWarning_whenImportClicked() {
        renderWithVm(vm())
        composeRule.onNodeWithText("导入数据").performScrollTo()
        composeRule.onNodeWithText("导入数据").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确认导入").assertIsDisplayed()
        composeRule.onNodeWithText("导入将覆盖当前所有数据，此操作不可撤销。").assertIsDisplayed()
        composeRule.onNodeWithText("覆盖导入").assertIsDisplayed()
        composeRule.onNodeWithText("取消", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确认导入").assertIsNotDisplayed()
    }

    @Test
    fun dwellRow_showsCurrentValue_withUnitLabel() = runBlocking {
        settings.setDwellInterval(12.5f)
        renderWithVm(vm())
        composeRule.onNodeWithText("自动连播间隔").performScrollTo().assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("12.5 秒").fetchSemanticsNodes().isNotEmpty()
        }
        Unit
    }

    @Test
    fun dwellRow_tapExpandsSliderWithCaption_tapAgainCollapses() {
        renderWithVm(vm())
        composeRule.onNodeWithText("自动连播间隔").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Expanding grows content below the tapped row; scroll the caption into view
        // so the display assertion holds on the CI emulator's shorter viewport.
        composeRule.onNodeWithText("在学习页双击卡片开始或暂停").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1s").assertIsDisplayed()
        composeRule.onNodeWithText("30s").assertIsDisplayed()

        composeRule.onNodeWithText("自动连播间隔").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("在学习页双击卡片开始或暂停").assertIsNotDisplayed()
    }

    @Test
    fun dwellSlider_dragUpdatesValueLive_andPersistsAfterRecreatingScreen() {
        runBlocking { settings.setDwellInterval(5f) }
        renderWithVm(vm())
        composeRule.onNodeWithText("自动连播间隔").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("在学习页双击卡片开始或暂停").performScrollTo().assertIsDisplayed()

        // Drag the thumb to the far right edge: the value updates live and clamps at 30 s.
        composeRule.onNodeWithTag(DwellSliderTestTag).performScrollTo().performTouchInput {
            down(center)
            val endX = right - 1f
            var x = centerX
            while (x < endX) {
                x = min(x + 40f, endX)
                moveTo(Offset(x, centerY))
                advanceEventTime(16L)
            }
            up()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("30.0 秒").fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(runBlocking { settings.dwellInterval.first() }).isEqualTo(30f)

        // Re-create the screen with a fresh settings ViewModel: the persisted value loads.
        renderWithVm(vm(), settingsViewModel = settingsVm())
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("30.0 秒").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
