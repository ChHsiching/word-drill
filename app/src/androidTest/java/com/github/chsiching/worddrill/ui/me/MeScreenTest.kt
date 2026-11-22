package com.github.chsiching.worddrill.ui.me

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.backup.BackupService
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import com.github.chsiching.worddrill.data.settings.PhoneticPreference
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import com.github.chsiching.worddrill.update.ApkInstaller
import com.github.chsiching.worddrill.update.ReleaseInfo
import com.github.chsiching.worddrill.update.UpdateCheckCache
import com.github.chsiching.worddrill.update.UpdateChecker
import com.github.chsiching.worddrill.update.UpdateCheckResult
import com.github.chsiching.worddrill.update.UpdateViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    /** ViewModels built for this test; no ViewModelStore ever clears them, so their
     *  stateIn(WhileSubscribed) collectors would outlive the test against the closed
     *  database. */
    private val viewModels = mutableListOf<ViewModel>()

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
    fun tearDown() = runBlocking {
        // Cancel every ViewModel's scope and drain in-flight Room queries before the
        // pool closes (the 8c3567a convention): this process's DataStore is shared,
        // and a later test's settings write would otherwise resume a leaked
        // collector's query against this closed database, throwing
        // "connection pool has been closed" into that later test.
        viewModels.forEach { it.viewModelScope.cancel() }
        delay(200)
        db.close()
        settings.setDwellInterval(5f)
        // Deck ordering is process-global; other classes in this process assume
        // the alphabetical default.
        settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        // So is the phonetic preference (US default).
        settings.setPhoneticPreference(PhoneticPreference.US)
        Unit
    }

    private fun seedSwipe(wordId: Long, timestamp: Long, bid: Long = bookId) = runBlocking {
        db.swipeLogDao().insert(SwipeLog(bookId = bid, wordId = wordId, timestamp = timestamp))
        Unit
    }

    private fun vm(): MeViewModel = MeViewModel(
        swipeLogDao = db.swipeLogDao(),
        bookDao = db.bookDao(),
        settings = settings,
    ).also { viewModels.add(it) }

    private fun settingsVm(): SettingsViewModel = SettingsViewModel(settings).also { viewModels.add(it) }

    private fun exportImportVm(): ExportImportViewModel {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = ctx.applicationContext as android.app.Application
        return ExportImportViewModel(app, BackupService(db, db.bookDao(), db.wordDao(), db.swipeLogDao()))
            .also { viewModels.add(it) }
    }

    /** Update-flow fakes mirror UpdateFlowTest: no network, no DownloadManager. */
    private class FakeUpdateChecker(var result: UpdateCheckResult) : UpdateChecker {
        var calls = 0
        override suspend fun checkForUpdate(): UpdateCheckResult {
            calls++
            return result
        }
    }

    private class FakeUpdateInstaller : ApkInstaller {
        override fun canInstallApk() = true
        override fun downloadAndInstall(apkUrl: String, version: String, onFailed: () -> Unit) = Unit
    }

    private fun updateVm(
        checker: FakeUpdateChecker = FakeUpdateChecker(UpdateCheckResult.UpToDate),
        cache: UpdateCheckCache = UpdateCheckCache(),
    ): UpdateViewModel = UpdateViewModel(checker, FakeUpdateInstaller(), cache)
        .also { viewModels.add(it) }

    private suspend fun stableState(viewModel: MeViewModel): MeStatsUiState =
        viewModel.uiState.first { it.bookName.isNotEmpty() }

    private fun renderWithVm(
        viewModel: MeViewModel,
        settingsViewModel: SettingsViewModel = settingsVm(),
        exportImportViewModel: ExportImportViewModel = exportImportVm(),
        updateViewModel: UpdateViewModel = updateVm(),
    ) {
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    MeScreen(
                        onNavigateToRecycleBin = {},
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        exportImportViewModel = exportImportViewModel,
                        updateViewModel = updateViewModel,
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
        // A book with no finished pass yet shows no completion line.
        composeRule.onNodeWithText("已刷完 0 遍").assertDoesNotExist()
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
        assertThat(state.percent).isEqualTo(100.0)
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
        composeRule.onNodeWithText("66.7%").assertIsDisplayed()
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
        assertThat(stableState(vm()).percent).isEqualTo(66.7)

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
        assertThat(state.percent).isEqualTo(50.0)
        assertThat(state.bookName).isEqualTo("另一本")
    }

    // --- Completion count in the stats card (#57) ---

    @Test
    fun statsCard_showsCompletionCount_ofCurrentBook() = runBlocking {
        repeat(2) { db.bookDao().incrementCompletionCount(bookId) }

        val state = stableState(vm())
        assertThat(state.completionCount).isEqualTo(2)

        renderWithVm(vm())
        composeRule.onNodeWithText("已刷完 2 遍").assertIsDisplayed()
        Unit
    }

    @Test
    fun settingsGroups_showDisplayNavigationGeneralLabels() {
        renderWithVm(vm())
        composeRule.onNodeWithText("显示").assertIsDisplayed()
        composeRule.onNodeWithText("导航").assertIsDisplayed()
        composeRule.onNodeWithText("通用", substring = false)
        composeRule.onNodeWithText("隐藏音标").assertIsDisplayed()
        // The phonetic preference row (issue #58) renders in the display group too.
        composeRule.onNodeWithText("音标偏好").performScrollTo().assertIsDisplayed()
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
    fun deckOrderingRow_inDisplayGroup_defaultsToAlphabetical_thenCyclesToShuffled() = runBlocking {
        settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        renderWithVm(vm())
        composeRule.onNodeWithText("单词顺序").performScrollTo().assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("字母顺序", substring = false).fetchSemanticsNodes().isNotEmpty()
        }

        // Tapping cycles to shuffled and persists the global setting (ADR 0002).
        composeRule.onNodeWithText("单词顺序").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("随机顺序", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(runBlocking { settings.deckOrdering.first() }).isEqualTo(DeckOrdering.SHUFFLED)
        Unit
    }

    @Test
    fun phoneticPreferenceRow_defaultsToAmerican_thenCyclesAndPersists() = runBlocking {
        settings.setPhoneticPreference(PhoneticPreference.US)
        renderWithVm(vm())
        composeRule.onNodeWithText("音标偏好").performScrollTo().assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("美式", substring = false).fetchSemanticsNodes().isNotEmpty()
        }

        // Tapping cycles US -> BOTH -> UK and persists the global setting (issue #58).
        composeRule.onNodeWithText("音标偏好").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("英美", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(runBlocking { settings.phoneticPreference.first() })
            .isEqualTo(PhoneticPreference.BOTH)

        composeRule.onNodeWithText("音标偏好").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("英式", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(runBlocking { settings.phoneticPreference.first() })
            .isEqualTo(PhoneticPreference.UK)
        Unit
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
        composeRule.onNodeWithText("版本 0.2.1").assertIsDisplayed()
    }

    @Test
    fun aboutDialog_showsStructuredFacts_notProseParagraph() {
        renderWithVm(vm())
        composeRule.onNodeWithText("关于", substring = false).performScrollTo()
        composeRule.onNodeWithText("关于", substring = false).performClick()
        composeRule.waitForIdle()
        // Three label/value fact pairs replace the old provenance paragraph.
        composeRule.onNodeWithText("词表来源", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("教育部 2016 版四六级考试大纲", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("词形说明", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("同一单词的不同拼写均已收录", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("词数说明", substring = false).assertIsDisplayed()
        // The count value is manually broken into three short lines, each its own
        // Text node.
        composeRule.onNodeWithText("由于官方词表对拼写相同的词重复计数", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("因此词数略少于官方数字", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("但收录词汇与考试大纲完全一致", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("词表来自教育部考试中心", substring = true).assertDoesNotExist()
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
        composeRule.onNodeWithText("1s", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("30s", substring = false).assertIsDisplayed()

        composeRule.onNodeWithText("自动连播间隔").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("在学习页双击卡片开始或暂停").assertIsNotDisplayed()
    }

    @Test
    fun dwellSlider_dragUpdatesValueLive_andPersistsAfterRecreatingScreen() {
        runBlocking { settings.setDwellInterval(5f) }
        // setContent may run only once per activity, so this test renders once and
        // reads the settings ViewModel through a swappable holder. The "re-create"
        // step below swaps in a fresh SettingsViewModel over the same DataStore and
        // recomposition replays the load, which is what persistence must prove.
        val settingsHolder = mutableStateOf(settingsVm())
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    MeScreen(
                        onNavigateToRecycleBin = {},
                        viewModel = vm(),
                        settingsViewModel = settingsHolder.value,
                        exportImportViewModel = exportImportVm(),
                        updateViewModel = updateVm(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
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

        // Re-create the screen: swap a fresh settings ViewModel, recomposition reloads.
        composeRule.runOnUiThread { settingsHolder.value = settingsVm() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("30.0 秒").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // --- Manual check-update row (#59) ---

    private fun awaitShown(text: String, substring: Boolean = true) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }

    private fun awaitGone(text: String, substring: Boolean = true) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(text, substring = substring).assertDoesNotExist()
    }

    @Test
    fun checkUpdateRow_rendersInGeneralGroup() {
        renderWithVm(vm())
        composeRule.onNodeWithText("检查更新", substring = false).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun checkUpdateRow_noUpdate_showsUpToDateDialog() {
        renderWithVm(vm(), updateViewModel = updateVm(FakeUpdateChecker(UpdateCheckResult.UpToDate)))

        composeRule.onNodeWithText("检查更新", substring = false).performScrollTo().performClick()

        // The result lands in the standard dialog: message plus a single OK button.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("已是最新版本", substring = false)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("已是最新版本", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
        composeRule.onNodeWithText("确定", substring = false).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("已是最新版本", substring = false)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun checkUpdateRow_updateAvailable_opensUpdateDialog() {
        val release = ReleaseInfo(
            version = "0.3.0",
            notes = "修复与改进",
            apkUrl = "https://example.com/worddrill-v0.3.0.apk",
        )
        renderWithVm(
            vm(),
            updateViewModel = updateVm(FakeUpdateChecker(UpdateCheckResult.UpdateAvailable(release))),
        )

        composeRule.onNodeWithText("检查更新", substring = false).performScrollTo().performClick()

        awaitShown("发现新版本", substring = false)
        awaitShown("版本 0.3.0", substring = false)
        awaitShown("立即更新", substring = false)
        composeRule.onNodeWithText("暂不更新", substring = false).performClick()
        awaitGone("发现新版本", substring = false)
    }

    @Test
    fun checkUpdateRow_failure_showsErrorDialog() {
        renderWithVm(
            vm(),
            updateViewModel = updateVm(
                FakeUpdateChecker(UpdateCheckResult.CheckFailed(R.string.update_error_generic)),
            ),
        )

        composeRule.onNodeWithText("检查更新", substring = false).performScrollTo().performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("无法连接 GitHub", substring = false)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("无法连接 GitHub", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
        composeRule.onNodeWithText("确定", substring = false).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("无法连接 GitHub", substring = false)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun checkUpdateRow_tapAfterLaunchCheck_refetches() = runBlocking {
        val checker = FakeUpdateChecker(UpdateCheckResult.UpToDate)
        val cache = UpdateCheckCache()
        // The activity-scoped launch ViewModel fetched first and cached its result;
        // the manual row still bypasses the cache and fetches again (reopened #59).
        updateVm(checker, cache).checkForUpdate()
        renderWithVm(vm(), updateViewModel = updateVm(checker, cache))

        composeRule.onNodeWithText("检查更新", substring = false).performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("已是最新版本", substring = false)
                .fetchSemanticsNodes().isNotEmpty()
        }

        assertThat(checker.calls).isEqualTo(2)
        Unit
    }
}
