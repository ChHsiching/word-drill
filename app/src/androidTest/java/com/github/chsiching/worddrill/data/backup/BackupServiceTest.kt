package com.github.chsiching.worddrill.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BackupService 的 instrumented 测试（内存版 Room）。
 *
 * 验收覆盖（规格 AC「导入后刷卡统计、词书、词条均正确恢复」）：
 * - export 读全量 5 张表（含孤立 swipe_log）
 * - import 覆盖：导入新快照后旧数据被清空、新数据按原始主键落库
 * - 导入后刷卡统计、词书、词条、义项、关联均正确
 * - 序列化往返（export → serialize → deserialize → import）端到端
 *
 * 与 [DatabaseJsonSerializerTest]（JVM 副接缝）互补：本测试覆盖"表 ↔ 快照"映射与事务编排。
 */
@RunWith(AndroidJUnit4::class)
class BackupServiceTest {

    private lateinit var db: WordDrillDatabase
    private lateinit var backup: BackupService

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = BackupService(db, db.bookDao(), db.wordDao(), db.swipeLogDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun export_readsAllFiveTables_includingOrphanSwipeLogs() = runTest {
        seedFixtures()
        // 制造一个孤立 swipe_log（引用不存在的 bookId）
        db.swipeLogDao().insert(SwipeLog(logId = 9999, bookId = 999, wordId = 10, timestamp = 123L))

        val snapshot = backup.export(nickname = "测试")

        assertThat(snapshot.nickname).isEqualTo("测试")
        assertThat(snapshot.books.map { it.name }).containsExactly("CET-4", "我的")
        assertThat(snapshot.words.map { it.text }).containsExactly("apple", "run")
        assertThat(snapshot.senses.map { it.pos }).containsExactly("n.", "vi.", "vt.")
        assertThat(snapshot.bookWords).hasSize(3)
        // 孤立日志必须读到（累计统计不可丢）
        assertThat(snapshot.swipeLogs.map { it.logId }).contains(9999L)
    }

    @Test
    fun import_overwritesOldData_andRestoresByOriginalIds() = runTest {
        seedFixtures()
        // 原始：2 词书 2 词
        assertThat(db.bookDao().getAll()).hasSize(2)
        assertThat(db.wordDao().getAll()).hasSize(2)

        // 导入一份全新的快照（覆盖）
        val snapshot = DatabaseSnapshot(
            version = 1,
            nickname = null,
            books = listOf(Book(bookId = 50, name = "新词书", isPreset = false)),
            words = listOf(Word(wordId = 60, text = "newword")),
            senses = listOf(Sense(senseId = 61, wordId = 60, pos = "n.", meaning = "新词")),
            bookWords = listOf(BookWord(bookId = 50, wordId = 60)),
            swipeLogs = listOf(SwipeLog(logId = 70, bookId = 50, wordId = 60, timestamp = 999L)),
        )
        backup.import(snapshot)

        // 旧数据被清空
        assertThat(db.bookDao().getAll().map { it.name }).containsExactly("新词书")
        assertThat(db.wordDao().getAll().map { it.text }).containsExactly("newword")
        // 按原始主键落库
        assertThat(db.bookDao().getById(50)?.name).isEqualTo("新词书")
        assertThat(db.wordDao().getById(60)?.text).isEqualTo("newword")
        assertThat(db.wordDao().getSensesForWord(60).map { it.pos }).containsExactly("n.")
        // 关联与日志恢复
        assertThat(db.bookDao().countWordsInBook(50)).isEqualTo(1)
        assertThat(db.swipeLogDao().totalCount()).isEqualTo(1)
    }

    @Test
    fun import_clearsAllSwipeLogs_beforeRestore() = runTest {
        seedFixtures()
        assertThat(db.swipeLogDao().totalCount()).isGreaterThan(0)

        backup.import(DatabaseSnapshot(version = 1, nickname = null))

        // 空快照导入后日志被清空
        assertThat(db.swipeLogDao().totalCount()).isEqualTo(0)
        assertThat(db.bookDao().getAll()).isEmpty()
    }

    @Test
    fun roundTrip_exportSerializeDeserializeImport_preservesAllData() = runTest {
        seedFixtures()
        val original = backup.export(nickname = "往返")
        val json = DatabaseJsonSerializer.serialize(original)
        // 清空后用反序列化的快照恢复
        backup.import(DatabaseJsonSerializer.deserialize(json))

        val restored = backup.export(nickname = null)
        // nickname 在 import 时由快照覆盖，第二次 export 不传 → null；比较其余字段
        assertThat(restored.books).isEqualTo(original.books)
        assertThat(restored.words).isEqualTo(original.words)
        assertThat(restored.senses).isEqualTo(original.senses)
        assertThat(restored.bookWords).isEqualTo(original.bookWords)
        assertThat(restored.swipeLogs).isEqualTo(original.swipeLogs)
    }

    @Test
    fun import_thenStats_recoverCorrectly() = runTest {
        // 规格 AC「导入后刷卡统计、词书、词条均正确恢复」
        // 词书 1（10 词，刷 3 distinct），词书 2（5 词，刷 2 distinct），累计 5 条日志
        val book1Words = (1..10).map { Word(wordId = it.toLong(), text = "w$it") }
        val book2Words = (11..15).map { Word(wordId = it.toLong(), text = "w$it") }
        val book1Links = (1..10).map { BookWord(bookId = 1, wordId = it.toLong()) }
        val book2Links = (11..15).map { BookWord(bookId = 2, wordId = it.toLong()) }
        val logs = listOf(
            SwipeLog(logId = 100, bookId = 1, wordId = 1, timestamp = 1),
            SwipeLog(logId = 101, bookId = 1, wordId = 2, timestamp = 2),
            SwipeLog(logId = 102, bookId = 1, wordId = 3, timestamp = 3),
            SwipeLog(logId = 103, bookId = 2, wordId = 11, timestamp = 4),
            SwipeLog(logId = 104, bookId = 2, wordId = 12, timestamp = 5),
        )
        backup.import(
            DatabaseSnapshot(
                version = 1,
                nickname = null,
                books = listOf(
                    Book(bookId = 1, name = "B1", isPreset = false),
                    Book(bookId = 2, name = "B2", isPreset = false),
                ),
                words = book1Words + book2Words,
                senses = emptyList(),
                bookWords = book1Links + book2Links,
                swipeLogs = logs,
            )
        )

        // 累计 5 条
        assertThat(db.swipeLogDao().totalCount()).isEqualTo(5)
        // B1 已刷 3 distinct / 总 10；B2 已刷 2 distinct / 总 5
        assertThat(db.swipeLogDao().distinctWordCountForBook(1)).isEqualTo(3)
        assertThat(db.bookDao().countWordsInBook(1)).isEqualTo(10)
        assertThat(db.swipeLogDao().distinctWordCountForBook(2)).isEqualTo(2)
        assertThat(db.bookDao().countWordsInBook(2)).isEqualTo(5)
        // 词书列表顺序：预置优先（无），再按名
        assertThat(db.bookDao().observeAll().first().map { it.name }).containsExactly("B1", "B2")
    }

    /** 种入标准测试数据：2 词书（CET-4 预置 + 我的自定义）、2 词、3 义、3 关联、2 日志。 */
    private suspend fun seedFixtures() {
        db.bookDao().insertAll(
            listOf(
                Book(bookId = 1, name = "CET-4", isPreset = true),
                Book(bookId = 2, name = "我的", isPreset = false),
            )
        )
        db.wordDao().insertAll(
            listOf(
                Word(wordId = 10, text = "apple"),
                Word(wordId = 20, text = "run"),
            )
        )
        db.wordDao().insertAllSenses(
            listOf(
                Sense(senseId = 100, wordId = 10, pos = "n.", meaning = "苹果"),
                Sense(senseId = 101, wordId = 20, pos = "vi.", meaning = "跑"),
                Sense(senseId = 102, wordId = 20, pos = "vt.", meaning = "经营"),
            )
        )
        db.bookDao().linkAll(
            listOf(
                BookWord(bookId = 1, wordId = 10),
                BookWord(bookId = 1, wordId = 20),
                BookWord(bookId = 2, wordId = 10),
            )
        )
        db.swipeLogDao().insertAll(
            listOf(
                SwipeLog(logId = 1000, bookId = 1, wordId = 10, timestamp = 1_700_000_000_000L),
                SwipeLog(logId = 1001, bookId = 2, wordId = 10, timestamp = 1_700_000_001_000L),
            )
        )
    }
}
