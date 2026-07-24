package com.github.chsiching.worddrill.ui.drill

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private fun composeDrillPager() {
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    DrillPager(
                        bookName = "测试词书",
                        cards = cards(),
                        onPageSettled = { _, _ -> },
                        locked = false,
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
        // 第一张显示「已是第一张」提示
        composeRule.onNodeWithText("已是第一张").assertIsDisplayed()
    }

    @Test
    fun lastCardShowsEndHint() {
        composeDrillPager()
        // 向右滑（左滑手势）到底：3 张卡需滑 2 次到最后一张。
        // 在根节点上滑动，避免目标词滑出屏幕后 onNodeWithText 找不到节点。
        repeat(2) {
            composeRule.onRoot().performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("circle").assertIsDisplayed()
        composeRule.onNodeWithText("已是最后一张").assertIsDisplayed()
    }
}
