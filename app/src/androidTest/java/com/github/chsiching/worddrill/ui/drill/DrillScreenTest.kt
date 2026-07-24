package com.github.chsiching.worddrill.ui.drill

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「刷」Tab 主接缝：Compose UI 集成测试 + 内存版 Room。
 *
 * 验证规格验收项（用户可见行为）：
 * - 卡片同屏显示单词（含词性）+ 中文释义，大字号
 * - 顶部展示当前词书名（纯文字）
 * - 到头显示提示文案（已是第一张 / 已是最后一张）
 *
 * 滑动计数决策逻辑（向右+1 / 向左+0 / 到头+0）由纯函数 JVM 单测
 * [SwipeDirectionTest] 覆盖；端到端「滑动→写 swipe_log」在模拟器上手验过
 * （MCP adb 查 swipe_log：向右 2 次 = 2 行，向左/到头不增加）。
 *
 * 不测 HorizontalPager 的手势内部（脆弱且与实现耦合），只测用户可见行为。
 */
@RunWith(AndroidJUnit4::class)
class DrillScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: WordDrillDatabase

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedData()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 3 张卡的词书，便于验证首/中/末三态。 */
    private fun seedData() = runBlocking {
        val bookId = db.bookDao().insert(Book(name = "测试词书", isPreset = false))
        val w1 = db.wordDao().insert(Word(text = "apple"))
        val w2 = db.wordDao().insert(Word(text = "balance"))
        val w3 = db.wordDao().insert(Word(text = "circle"))
        // apple 多词性：验证一张卡同屏显示全部 sense
        db.wordDao().insertSense(Sense(wordId = w1, pos = "n.", meaning = "苹果"))
        db.wordDao().insertSense(Sense(wordId = w1, pos = "v.", meaning = "囤积"))
        db.wordDao().insertSense(Sense(wordId = w2, pos = "n.", meaning = "平衡"))
        db.wordDao().insertSense(Sense(wordId = w3, pos = "n.", meaning = "圆圈"))
        db.bookDao().linkBookWord(BookWord(bookId, w1))
        db.bookDao().linkBookWord(BookWord(bookId, w2))
        db.bookDao().linkBookWord(BookWord(bookId, w3))
        Unit
    }

    private fun cards() = runBlocking {
        val bookId = db.bookDao().getByName("测试词书")!!.bookId
        db.wordDao().getWordsWithSensesByBook(bookId)
    }

    private fun composeDrillPager(
        locked: Boolean = false,
        onSkip: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    DrillPager(
                        bookName = "测试词书",
                        cards = cards(),
                        onPageSettled = { _, _ -> },
                        onSkip = onSkip,
                        locked = locked,
                        onToggleLock = {},
                        hidePhonetic = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun showsBookNameAndFirstCardContent() {
        composeDrillPager()

        // 顶部词书名
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
        // 第一张卡：英文单词 + 两个词性的 pos / 中文释义都同屏（#16 起拆成独立 Text 节点）
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("n.").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertIsDisplayed()
        composeRule.onNodeWithText("v.").assertIsDisplayed()
        composeRule.onNodeWithText("囤积").assertIsDisplayed()
        // 顶部计数器（审核反馈：去掉边界提示后，计数器「1 / 3」表达位置）
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun lastCardCounterShowsLastIndex() {
        composeDrillPager()
        // 向右滑（左滑手势）到底：3 张卡需滑 2 次到最后一张。
        repeat(2) {
            composeRule.onRoot().performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("circle").assertIsDisplayed()
        // 计数器显示 3 / 3（替代原「已是最后一张」边界提示）
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
    }

    /**
     * #17 (审核反馈 #3) + #20：锁定状态下跳过按钮仍可点击触发回调。
     * 回归：早期实现里 onSkip 内含 `!locked` 守卫，锁定后跳过无效。验收要求锁定只隐藏
     * 导航栏，不影响跳过。Ticket #20 改为回调式 onSkip(page)，这里验证锁定态回调仍触发。
     */
    @Test
    fun skipButtonWorksWhileLocked() {
        var skippedPage: Int? = null
        composeDrillPager(locked = true, onSkip = { page -> skippedPage = page })

        // 起始：第 1 张（page=0）
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        // 锁定状态下点击「跳过」仍触发回调
        composeRule.onNodeWithText("跳过").performClick()
        composeRule.waitForIdle()
        assertThat(skippedPage).isEqualTo(0)
    }

    /**
     * Ticket #20：跳过按钮回调会拿到当前页码（pagerState.currentPage）。
     * 这是 ViewModel.skipCurrentWord 定位要跳过哪个 word 的依据。用捕获回调验证：
     * 在第 1 张点跳过 → 回调收到 page=0。
     */
    @Test
    fun skipButton_passesCurrentPageToCallback() {
        var skippedPage: Int? = null
        composeDrillPager(onSkip = { page -> skippedPage = page })

        // 第 1 张（page=0）点跳过
        composeRule.onNodeWithText("跳过").performClick()
        composeRule.waitForIdle()
        assertThat(skippedPage).isEqualTo(0)
    }
}
