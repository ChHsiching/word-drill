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
 * 「我的」Tab 主接缝（Ticket #8 + #16 重写）：Compose UI + 内存 Room + 真 ViewModel。
 *
 * 验收覆盖（规格 AC）：
 * - 展示今日/累计/进度三个统计
 * - 刷 N 张后数字正确（聚合正确性）
 * - 切换当前词书后，进度对应新词书
 *
 * Ticket #16 后：
 * - 设置分三组（显示/导航/通用），不再有「软件设置」「数据」标题
 * - 主题三选项移到主题选择对话框（点「主题」行触发）
 * - 「关于」入口在通用组
 * - 导出/导入在通用组，文案「导出数据」「导入数据」
 *
 * 接缝：通过 MeScreen(viewModel = ...) 注入真 MeViewModel（内存 Room DAO +
 * 真 SettingsRepository），collectAsStateWithLifecycle 驱动 UI；UI 文案断言聚合结果。
 * 用 Truth assertThat（repo 约定，坑 H：勿用 kotlin.assert）。
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
        // 词书内 3 个词（进度分母 = 3）
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
     * 等待 ViewModel 把上游 combine 解析稳定后取首条解析值。
     * stateIn(WhileSubscribed) 首值是空 MeStatsUiState()（bookName=""）；
     * 所有测试都已 setCurrentBookId，解析后的状态 bookName 必非空，作为就绪信号。
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
        // 当前进度区域：书名 + 进度都在
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
    }

    @Test
    fun viewModel_aggregatesStatsCorrectly_afterSwipes() = runBlocking {
        val now = System.currentTimeMillis()
        // 今日刷 3 张（wordId 1 两次 + wordId 2 一次），很久前刷 wordId 3 一次
        seedSwipe(wordId = 1, timestamp = now - 1000)
        seedSwipe(wordId = 2, timestamp = now - 500)
        seedSwipe(wordId = 1, timestamp = now - 200) // 重复词
        seedSwipe(wordId = 3, timestamp = now - 100_000_000L) // 累计但不计入今日

        val state = stableState(vm())
        // 累计 = 4 条事件；今日 = 3 条（now 附近）；
        // 进度 = distinct 词数 / 总词数：词书内 wordId 1/2/3 都刷过 → 3 distinct / 3 总 = 100%
        // （distinctWordCountForBook 跨全时段按 bookId 统计，与今日窗口无关）
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
        // #16 起进度区拆为：书名（独立 Text）+ "done / total"（独立 Text）+ "percent%"（独立 Text）
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        composeRule.onNodeWithText("66%").assertIsDisplayed()
        // 两个统计标签
        composeRule.onNodeWithText("今日刷卡").assertIsDisplayed()
        composeRule.onNodeWithText("累计刷卡").assertIsDisplayed()
    }

    @Test
    fun progressLine_showsZeroPercent_whenBookEmpty() {
        runBlocking {
            // 空词书：分母 0，进度显示 0% 不崩溃
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
        // 当前词书 bookId（3 词）刷了 2 distinct → 66%
        val now = System.currentTimeMillis()
        seedSwipe(wordId = 1, timestamp = now)
        seedSwipe(wordId = 2, timestamp = now)
        assertThat(stableState(vm()).percent).isEqualTo(66)

        // 新建另一词书（2 词），切为当前，刷 1 个 → 50%
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

    // ---- Ticket #16：设置分三组 + 主题选择对话框 + 关于 ----

    @Test
    fun settingsGroups_showDisplayNavigationGeneralLabels() {
        renderWithVm(vm())
        // 三个分组标签（#16 起：显示 / 导航 / 通用）
        composeRule.onNodeWithText("显示").assertIsDisplayed()
        composeRule.onNodeWithText("导航").assertIsDisplayed()
        // 通用组在屏外（verticalScroll + 底部 padding），用 onNodeWithText 验证存在（找不到会抛）
        composeRule.onNodeWithText("通用", substring = false)
        // 显示组：隐藏音标
        composeRule.onNodeWithText("隐藏音标").assertIsDisplayed()
        // 导航组：导航栏风格 + 简约导航
        composeRule.onNodeWithText("导航栏风格").assertIsDisplayed()
        composeRule.onNodeWithText("简约导航").assertIsDisplayed()
        // 通用组：导出 + 导入 + 关于（审核反馈 5：主题移到右上角 icon，不在通用组）
        composeRule.onNodeWithText("导出数据")
        composeRule.onNodeWithText("导入数据")
        composeRule.onNodeWithText("关于", substring = false)
        // 右上角主题 icon（contentDescription = "主题"）
        composeRule.onNodeWithContentDescription("主题").assertIsDisplayed()
    }

    @Test
    fun themeIcon_togglesToOppositeMode() = runBlocking {
        // 审核反馈 5：右上角 icon 点击切深/浅（当前 LIGHT → 切 DARK）。
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
        // 审核反馈 5：当前 DARK → 切 LIGHT。
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
        // 「关于」入口在屏外，先 scroll 到它再点
        composeRule.onNodeWithText("关于", substring = false).performScrollTo()
        composeRule.onNodeWithText("关于", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("关于 WordDrill").assertIsDisplayed()
        // 坑 G：onNodeWithText 默认子串匹配，"WordDrill" 会同时命中标题"关于 WordDrill"
        // 与正文"WordDrill"。正文用 substring=false 精确匹配唯一节点。
        composeRule.onNodeWithText("WordDrill", substring = false).assertIsDisplayed()
        // 版本号行：与 build.gradle.kts 的 versionName 一致；bump 时同步更新。
        composeRule.onNodeWithText("版本 0.1.0-dev27").assertIsDisplayed()
    }

    // ---- Ticket #10/#16：数据导出/导入入口（通用组下两行）----

    @Test
    fun exportDialog_showsNicknameField_whenExportClicked() {
        renderWithVm(vm())
        // 「导出数据」入口在屏外，先 scroll 到它再点
        composeRule.onNodeWithText("导出数据").performScrollTo()
        composeRule.onNodeWithText("导出数据").performClick()
        composeRule.waitForIdle()
        // 导出弹窗：说明 + 昵称输入框 + 确认/取消（标题与行同名「导出数据」故跳过）
        composeRule.onNodeWithText("将整库（词条池、词书、刷卡日志）导出为 JSON 文件，用于换机迁移。").assertIsDisplayed()
        composeRule.onNodeWithText("可选昵称标签").assertIsDisplayed()
        // 点取消后弹窗关闭（坑 M：避重复词，用 substring=false 精确匹配）
        composeRule.onNodeWithText("取消", substring = false).performClick()
        composeRule.waitForIdle()
        // 关闭后：说明文本消失（弹窗关闭判定）
        composeRule.onNodeWithText("将整库（词条池、词书、刷卡日志）导出为 JSON 文件，用于换机迁移。").assertIsNotDisplayed()
    }

    @Test
    fun importDialog_showsOverwriteWarning_whenImportClicked() {
        renderWithVm(vm())
        // 「导入数据」入口在屏外，先 scroll 到它再点
        composeRule.onNodeWithText("导入数据").performScrollTo()
        composeRule.onNodeWithText("导入数据").performClick()
        composeRule.waitForIdle()
        // 导入弹窗：标题 + 覆盖警告 + 确认/取消
        composeRule.onNodeWithText("确认导入").assertIsDisplayed()
        composeRule.onNodeWithText("导入将覆盖当前所有数据，此操作不可撤销。").assertIsDisplayed()
        composeRule.onNodeWithText("覆盖导入").assertIsDisplayed()
        // 点取消后弹窗关闭
        composeRule.onNodeWithText("取消", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("确认导入").assertIsNotDisplayed()
    }
}
