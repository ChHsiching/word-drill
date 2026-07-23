package com.github.chsiching.worddrill.ui.me

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
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
 * 「我的」Tab 主接缝（Ticket #8）：Compose UI + 内存 Room + 真 ViewModel。
 *
 * 验收覆盖（规格 AC）：
 * - 展示今日/累计/进度三个统计
 * - 刷 N 张后数字正确（聚合正确性）
 * - 切换当前词书后，进度对应新词书
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

    /**
     * 等待 ViewModel 把上游 combine 解析稳定后取首条解析值。
     * stateIn(WhileSubscribed) 首值是空 MeStatsUiState()（bookName=""）；
     * 所有测试都已 setCurrentBookId，解析后的状态 bookName 必非空，作为就绪信号。
     */
    private suspend fun stableState(viewModel: MeViewModel): MeStatsUiState =
        viewModel.uiState.first { it.bookName.isNotEmpty() }

    private fun renderWithVm(viewModel: MeViewModel) {
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    MeScreen(viewModel = viewModel)
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
        composeRule.onNodeWithText("当前词书进度").assertIsDisplayed()
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
        // 进度行：书名：已刷 / 总（百分比）（坑 G：用 substring 精确长文案避免误匹配）
        composeRule.onNodeWithText("测试词书：2 / 3（66%）").assertIsDisplayed()
        // 三个统计区标签都在
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
        composeRule.onNodeWithText("空词书：0 / 0（0%）").assertIsDisplayed()
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
}
