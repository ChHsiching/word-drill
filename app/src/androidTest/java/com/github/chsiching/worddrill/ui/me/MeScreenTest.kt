package com.github.chsiching.worddrill.ui.me

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Main seam of the Me tab (Ticket #8, rewritten in #16): Compose UI + in-memory Room + real ViewModel.
 *
 * Acceptance coverage (spec ACs):
 * - Shows the three stats: today / total / progress
 * - The numbers are correct after swiping N cards (aggregation correctness)
 * - After switching the current book, the progress tracks the new book
 *
 * Since Ticket #16:
 * - Settings are split into three groups (display / navigation / general); the "app settings" and "data" headings are gone
 * - The three theme options moved into the theme picker dialog (opened by tapping the "theme" row)
 * - The "about" entry is in the general group
 * - Export / import are in the general group, labeled "export data" and "import data"
 *
 * Seam: injects a real MeViewModel through MeScreen(viewModel = ...) (in-memory Room DAO +
 * real SettingsRepository); collectAsStateWithLifecycle drives the UI; UI text asserts the aggregated results.
 * Uses Truth assertThat (repo convention, pitfall H: do not use kotlin.assert).
 */
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
        // 3 words in the book (progress denominator = 3)
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

    /**
     * Waits for the ViewModel to settle the upstream combine, then takes the first resolved value.
     * The initial value of stateIn(WhileSubscribed) is the empty MeStatsUiState() (bookName="");
     * every test has already called setCurrentBookId, so the resolved state always has a non-empty bookName, which is the ready signal.
     */
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
        // Current progress area: both the book name and the progress are there
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
    }

    @Test
    fun viewModel_aggregatesStatsCorrectly_afterSwipes() = runBlocking {
        val now = System.currentTimeMillis()
        // Today 3 cards swiped (wordId 1 twice + wordId 2 once); wordId 3 swiped once long ago
        seedSwipe(wordId = 1, timestamp = now - 1000)
        seedSwipe(wordId = 2, timestamp = now - 500)
        seedSwipe(wordId = 1, timestamp = now - 200) // repeated word
        seedSwipe(wordId = 3, timestamp = now - 100_000_000L) // counts toward the total but not today

        val state = stableState(vm())
        // Total = 4 events; today = 3 (near now);
        // Progress = distinct words / total words: wordIds 1/2/3 in the book were all swiped, so 3 distinct / 3 total = 100%
        // (distinctWordCountForBook counts by bookId over all time, independent of the today window)
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
        // Since #16 the progress area is split into: book name (its own Text) + "done / total" (its own Text) + "percent%" (its own Text)
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        composeRule.onNodeWithText("66%").assertIsDisplayed()
        // The two stats labels
        composeRule.onNodeWithText("今日刷卡").assertIsDisplayed()
        composeRule.onNodeWithText("累计刷卡").assertIsDisplayed()
    }

    @Test
    fun progressLine_showsZeroPercent_whenBookEmpty() {
        runBlocking {
            // Empty book: denominator 0; progress shows 0% without crashing
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
        // Current book bookId (3 words) has 2 distinct swiped, so 66%
        val now = System.currentTimeMillis()
        seedSwipe(wordId = 1, timestamp = now)
        seedSwipe(wordId = 2, timestamp = now)
        assertThat(stableState(vm()).percent).isEqualTo(66)

        // Create another book (2 words), switch to it as current, swipe 1, so 50%
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

    // ---- Ticket #16: settings in three groups + theme picker dialog + about ----

    @Test
    fun settingsGroups_showDisplayNavigationGeneralLabels() {
        renderWithVm(vm())
        // The three group labels (since #16: display / navigation / general)
        composeRule.onNodeWithText("显示").assertIsDisplayed()
        composeRule.onNodeWithText("导航").assertIsDisplayed()
        // The general group is off-screen (verticalScroll + bottom padding); verify existence with onNodeWithText (it throws when not found)
        composeRule.onNodeWithText("通用", substring = false)
        // Display group: hide phonetics
        composeRule.onNodeWithText("隐藏音标").assertIsDisplayed()
        // Navigation group: nav bar style + minimal navigation
        composeRule.onNodeWithText("导航栏风格").assertIsDisplayed()
        composeRule.onNodeWithText("简约导航").assertIsDisplayed()
        // General group: export + import + about (review feedback 5: theme moved to the top-right icon, no longer in the general group)
        composeRule.onNodeWithText("导出数据")
        composeRule.onNodeWithText("导入数据")
        composeRule.onNodeWithText("关于", substring = false)
        // Top-right theme icon (contentDescription = "theme")
        composeRule.onNodeWithContentDescription("主题").assertIsDisplayed()
    }

    @Test
    fun themeIcon_togglesToOppositeMode() = runBlocking {
        // Review feedback 5: tapping the top-right icon toggles dark/light (currently LIGHT, so it switches to DARK).
        runBlocking { settings.setTheme(ThemeMode.LIGHT) }
        renderWithVm(vm())
        composeRule.onNodeWithContentDescription("主题").performClick()
        composeRule.waitForIdle()
        val mode = settings.themePreference.first { it == ThemeMode.DARK }
        assertThat(mode).isEqualTo(ThemeMode.DARK)
        Unit
    }

    @Test
    fun themeIcon_darkToLight_togglesBack() = runBlocking {
        // Review feedback 5: currently DARK, so it switches to LIGHT.
        runBlocking { settings.setTheme(ThemeMode.DARK) }
        renderWithVm(vm())
        composeRule.onNodeWithContentDescription("主题").performClick()
        composeRule.waitForIdle()
        val mode = settings.themePreference.first { it == ThemeMode.LIGHT }
        assertThat(mode).isEqualTo(ThemeMode.LIGHT)
        Unit
    }

    @Test
    fun aboutDialog_showsAppNameAndVersion_whenOpened() {
        renderWithVm(vm())
        // The "about" entry is off-screen; scroll to it first, then tap
        composeRule.onNodeWithText("关于", substring = false).performScrollTo()
        composeRule.onNodeWithText("关于", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("关于 WordDrill").assertIsDisplayed()
        // Pitfall G: onNodeWithText does substring matching by default, so "WordDrill" would match both the "About WordDrill" title
        // and the body text "WordDrill". Use substring=false to exactly match the single body node.
        composeRule.onNodeWithText("WordDrill", substring = false).assertIsDisplayed()
        // Version line: matches versionName in build.gradle.kts; update it in sync when bumping.
        composeRule.onNodeWithText("版本 0.2.1").assertIsDisplayed()
    }

    // ---- Ticket #10/#16: data export / import entries (two rows under the general group) ----

    @Test
    fun exportDialog_showsNicknameField_whenExportClicked() {
        renderWithVm(vm())
        // The "export data" entry is off-screen; scroll to it first, then tap
        composeRule.onNodeWithText("导出数据").performScrollTo()
        composeRule.onNodeWithText("导出数据").performClick()
        composeRule.waitForIdle()
        // Export dialog: hint + nickname field + confirm/cancel (the title shares the text "export data" with the row, so it is skipped)
        composeRule.onNodeWithText("将整库（词条池、词书、刷卡日志）导出为 JSON 文件，用于换机迁移。").assertIsDisplayed()
        composeRule.onNodeWithText("可选昵称标签").assertIsDisplayed()
        // Tap cancel and the dialog closes (pitfall M: to avoid duplicate words, use substring=false exact matching)
        composeRule.onNodeWithText("取消", substring = false).performClick()
        composeRule.waitForIdle()
        // After closing: the hint text is gone (how we detect the dialog closed)
        composeRule.onNodeWithText("将整库（词条池、词书、刷卡日志）导出为 JSON 文件，用于换机迁移。").assertIsNotDisplayed()
    }

    @Test
    fun importDialog_showsOverwriteWarning_whenImportClicked() {
        renderWithVm(vm())
        // The "import data" entry is off-screen; scroll to it first, then tap
        composeRule.onNodeWithText("导入数据").performScrollTo()
        composeRule.onNodeWithText("导入数据").performClick()
        composeRule.waitForIdle()
        // Import dialog: title + overwrite warning + confirm/cancel
        composeRule.onNodeWithText("确认导入").assertIsDisplayed()
        composeRule.onNodeWithText("导入将覆盖当前所有数据，此操作不可撤销。").assertIsDisplayed()
        composeRule.onNodeWithText("覆盖导入").assertIsDisplayed()
        // Tap cancel and the dialog closes
        composeRule.onNodeWithText("取消", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确认导入").assertIsNotDisplayed()
    }
}
