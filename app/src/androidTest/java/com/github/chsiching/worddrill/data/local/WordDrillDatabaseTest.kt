package com.github.chsiching.worddrill.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Ticket #3 主接缝：内存版 Room，验证 5 张表 schema、外键级联、DAO CRUD、聚合查询。
 * 只测外部行为（DAO 的契约），不测 Room 内部实现。
 */
@RunWith(AndroidJUnit4::class)
class WordDrillDatabaseTest {

    private lateinit var db: WordDrillDatabase

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- 词条 CRUD ----

    @Test
    fun insertWord_assignsAutoIncrementId() = runTest {
        val wordDao = db.wordDao()
        val id = wordDao.insert(Word(text = "apple"))
        assertThat(id).isGreaterThan(0)
        assertThat(wordDao.getById(id)!!.text).isEqualTo("apple")
    }

    @Test
    fun insertWord_duplicateTextIsIgnored() = runTest {
        val wordDao = db.wordDao()
        val firstId = wordDao.insert(Word(text = "apple"))
        val secondId = wordDao.insert(Word(text = "apple"))
        // IGNORE 冲突策略：重复插入返回 -1，原始行保留
        assertThat(secondId).isEqualTo(-1L)
        assertThat(wordDao.getByText("apple")!!.wordId).isEqualTo(firstId)
    }

    @Test
    fun findIdByText_returnsNullForAbsent() = runTest {
        assertThat(db.wordDao().findIdByText("ghost")).isNull()
    }

    // ---- Ticket #14：word.phonetic（IPA 音标，可空）----

    @Test
    fun insertWord_withPhonetic_persists() = runTest {
        val wordDao = db.wordDao()
        val id = wordDao.insert(Word(text = "apple", phonetic = "/ˈæpl/"))
        assertThat(wordDao.getById(id)!!.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun insertWord_withoutPhonetic_defaultsNull() = runTest {
        val wordDao = db.wordDao()
        val id = wordDao.insert(Word(text = "run"))
        assertThat(wordDao.getById(id)!!.phonetic).isNull()
    }

    @Test
    fun getByText_returnsPhonetic() = runTest {
        val wordDao = db.wordDao()
        wordDao.insert(Word(text = "balance", phonetic = "/ˈbæləns/"))
        assertThat(wordDao.getByText("balance")!!.phonetic).isEqualTo("/ˈbæləns/")
    }

    @Test
    fun getWordsWithSensesByBook_carriesPhonetic() = runTest {
        // @Relation 回填的 Word 也应带 phonetic（验证 getWordsWithSensesByBook 的 SELECT w.*
        // 覆盖新列，而非显式列清单漏掉 phonetic）
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple", phonetic = "/ˈæpl/"))
        bookDao.linkBookWord(BookWord(bookId, wApple))

        val result = wordDao.getWordsWithSensesByBook(bookId).single()
        assertThat(result.word.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun insertSense_persistsUnderWord() = runTest {
        val wordDao = db.wordDao()
        val wordId = wordDao.insert(Word(text = "book"))
        wordDao.insertSense(Sense(wordId = wordId, pos = "n.", meaning = "书"))
        wordDao.insertSense(Sense(wordId = wordId, pos = "v.", meaning = "预订"))

        val senses = wordDao.getSensesForWord(wordId)
        assertThat(senses).hasSize(2)
        assertThat(senses.map { it.pos }).containsExactly("n.", "v.")
    }

    @Test
    fun insertSense_duplicateWordAndPosIsIgnored() = runTest {
        val wordDao = db.wordDao()
        val wordId = wordDao.insert(Word(text = "run"))
        wordDao.insertSense(Sense(wordId = wordId, pos = "v.", meaning = "跑"))
        val dup = wordDao.insertSense(Sense(wordId = wordId, pos = "v.", meaning = "运行"))
        assertThat(dup).isEqualTo(-1L)
        assertThat(wordDao.getSensesForWord(wordId)).hasSize(1)
    }

    @Test
    fun updateSense_changesPosAndMeaning() = runTest {
        val wordDao = db.wordDao()
        val wordId = wordDao.insert(Word(text = "light"))
        val senseId = wordDao.insertSense(Sense(wordId = wordId, pos = "n.", meaning = "光"))
        wordDao.updateSense(senseId, pos = "adj.", meaning = "轻的")
        val updated = wordDao.getSensesForWord(wordId).single()
        assertThat(updated.pos).isEqualTo("adj.")
        assertThat(updated.meaning).isEqualTo("轻的")
    }

    @Test
    fun deleteSense_removesOnlyThatSense() = runTest {
        val wordDao = db.wordDao()
        val wordId = wordDao.insert(Word(text = "match"))
        val s1 = wordDao.insertSense(Sense(wordId = wordId, pos = "n.", meaning = "比赛"))
        wordDao.insertSense(Sense(wordId = wordId, pos = "v.", meaning = "匹配"))
        wordDao.deleteSense(s1)
        assertThat(wordDao.getSensesForWord(wordId).map { it.pos }).containsExactly("v.")
    }

    // ---- 外键级联：删 word 级联删 sense ----

    @Test
    fun deleteWord_cascadesDeleteSenses() = runTest {
        val wordDao = db.wordDao()
        val wordId = wordDao.insert(Word(text = "temp"))
        wordDao.insertSense(Sense(wordId = wordId, pos = "n.", meaning = "临时"))
        wordDao.deleteWord(wordId)
        assertThat(wordDao.getSensesForWord(wordId)).isEmpty()
    }

    // ---- 词书 CRUD ----

    @Test
    fun insertBook_assignsIdAndDefaults() = runTest {
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "CET-4", isPreset = true))
        assertThat(bookDao.getById(id)!!.isPreset).isTrue()
    }

    @Test
    fun observeAll_ordersPresetsFirstThenByName() = runTest {
        val bookDao = db.bookDao()
        bookDao.insert(Book(name = "zebra", isPreset = false))
        bookDao.insert(Book(name = "CET-6", isPreset = true))
        bookDao.insert(Book(name = "alpha", isPreset = false))
        bookDao.insert(Book(name = "CET-4", isPreset = true))

        val books = bookDao.observeAll().first()
        // 两个预置在前（按 name 升序），两个自定义在后（按 name 升序）
        assertThat(books.map { it.name }).containsExactly("CET-4", "CET-6", "alpha", "zebra").inOrder()
    }

    @Test
    fun rename_updatesName() = runTest {
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "old", isPreset = false))
        bookDao.rename(id, "new")
        assertThat(bookDao.getById(id)!!.name).isEqualTo("new")
    }

    @Test
    fun rename_refusesPresetBook() = runTest {
        // Ticket #11：预置词书不可重命名（DAO 层兜底，与 deleteCustom 的 isPreset=0 同模式）
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val affected = bookDao.rename(id, "hacked")
        // WHERE isPreset = 0 不匹配，受影响行数为 0，原行保留
        assertThat(affected).isEqualTo(0)
        assertThat(bookDao.getById(id)!!.name).isEqualTo("CET-4")
    }

    // Ticket #22：deleteCustom 现为软删（deleted=1），完整覆盖见下方 #22 段的
    // deleteCustom_softDeletesInsteadOfPhysicalDelete / deleteCustom_refusesPresetBook。

    // ---- 词书-词条 关联 ----

    @Test
    fun linkBookWord_enablesCount() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "a"))
        val w2 = wordDao.insert(Word(text = "b"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w2))
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(2)
    }

    @Test
    fun linkBookWord_duplicatePairIsIgnored() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "a"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w1)) // 重复
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(1)
    }

    @Test
    fun unlinkBookWord_removesPair() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "a"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.unlinkBookWord(bookId, w1)
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(0)
    }

    @Test
    fun softDeleteBook_preservesBookWordLinks() = runTest {
        // Ticket #22：词书软删（deleteCustom）不真删行，也不动其下 book_word 关联。
        // CASCADE 只在永久删除（purgeBook）时触发，见 purgeBook_physicallyDeletesAndCascades。
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1", isPreset = false))
        val w1 = wordDao.insert(Word(text = "a"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(1)

        bookDao.deleteCustom(bookId) // 软删

        // 关联行仍在（软删词书不动 book_word），countWordsInBook 仍能查到
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(1)
        // 但词书从可见列表消失（observeAllWithCounts 过滤 deleted=0）
        assertThat(bookDao.observeAllWithCounts().first().none { it.bookId == bookId }).isTrue()
    }

    // ---- 按词书查词条（含 sense）----

    @Test
    fun getWordsWithSensesByBook_returnsWordsWithTheirSenses() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wBanana = wordDao.insert(Word(text = "banana"))
        wordDao.insertSense(Sense(wordId = wApple, pos = "n.", meaning = "苹果"))
        wordDao.insertSense(Sense(wordId = wBanana, pos = "n.", meaning = "香蕉"))
        // 另一个词书里的词不应出现
        val otherBook = bookDao.insert(Book(name = "b2"))
        val wCherry = wordDao.insert(Word(text = "cherry"))
        wordDao.insertSense(Sense(wordId = wCherry, pos = "n.", meaning = "樱桃"))
        bookDao.linkBookWord(BookWord(bookId, wApple))
        bookDao.linkBookWord(BookWord(bookId, wBanana))
        bookDao.linkBookWord(BookWord(otherBook, wCherry))

        val result = db.wordDao().getWordsWithSensesByBook(bookId)
        assertThat(result.map { it.word.text }).containsExactly("apple", "banana")
        assertThat(result.first { it.word.text == "apple" }.senses).hasSize(1)
    }

    @Test
    fun getWordsWithSensesByBook_oneWordMultipleSenses() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wId = wordDao.insert(Word(text = "light"))
        wordDao.insertSense(Sense(wordId = wId, pos = "n.", meaning = "光"))
        wordDao.insertSense(Sense(wordId = wId, pos = "adj.", meaning = "轻的"))
        wordDao.insertSense(Sense(wordId = wId, pos = "v.", meaning = "点燃"))
        bookDao.linkBookWord(BookWord(bookId, wId))

        val result = wordDao.getWordsWithSensesByBook(bookId).single()
        assertThat(result.senses).hasSize(3)
    }

    // ---- Ticket #7: observe 词书词条（响应式，列表页订阅）----

    @Test
    fun observeWordsWithSensesByBook_emitsCurrentList() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        wordDao.insertSense(Sense(wordId = w1, pos = "n.", meaning = "苹果"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        val result = wordDao.observeWordsWithSensesByBook(bookId).first()
        assertThat(result.map { it.word.text }).containsExactly("apple")
        assertThat(result.single().senses).hasSize(1)
    }

    @Test
    fun observeWordsWithSensesByBook_isolatesByBook() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val b1 = bookDao.insert(Book(name = "b1"))
        val b2 = bookDao.insert(Book(name = "b2"))
        val w1 = wordDao.insert(Word(text = "apple"))
        val w2 = wordDao.insert(Word(text = "banana"))
        bookDao.linkBookWord(BookWord(b1, w1))
        bookDao.linkBookWord(BookWord(b2, w2))

        assertThat(wordDao.observeWordsWithSensesByBook(b1).first().map { it.word.text })
            .containsExactly("apple")
        assertThat(wordDao.observeWordsWithSensesByBook(b2).first().map { it.word.text })
            .containsExactly("banana")
    }

    // ---- 刷卡日志聚合 ----

    @Test
    fun swipeLog_totalCount_countsAllEvents() = runTest {
        val logDao = db.swipeLogDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 1000L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 2000L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 3, timestamp = 3000L))
        assertThat(logDao.totalCount()).isEqualTo(3)
    }

    @Test
    fun swipeLog_countSince_filtersByTimestamp() = runTest {
        val logDao = db.swipeLogDao()
        val bookId = db.bookDao().insert(Book(name = "b1"))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 500L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 1500L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 3, timestamp = 2500L))
        // 今日起点 = 1000L：应只计 2 条
        assertThat(logDao.countSince(1000L)).isEqualTo(2)
    }

    @Test
    fun swipeLog_distinctWordCountForBook_countsUniqueWords() = runTest {
        val logDao = db.swipeLogDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        // 同一词书内，wordId 1 被刷 3 次，wordId 2 被刷 1 次 → distinct = 2
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 1L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 2L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 3L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 4L))
        assertThat(logDao.distinctWordCountForBook(bookId)).isEqualTo(2)
    }

    @Test
    fun swipeLog_distinctWordCountForBook_isolationBetweenBooks() = runTest {
        val logDao = db.swipeLogDao()
        val bookDao = db.bookDao()
        val b1 = bookDao.insert(Book(name = "b1"))
        val b2 = bookDao.insert(Book(name = "b2"))
        logDao.insert(SwipeLog(bookId = b1, wordId = 1, timestamp = 1L))
        logDao.insert(SwipeLog(bookId = b1, wordId = 2, timestamp = 2L))
        logDao.insert(SwipeLog(bookId = b2, wordId = 1, timestamp = 3L))
        assertThat(logDao.distinctWordCountForBook(b1)).isEqualTo(2)
        assertThat(logDao.distinctWordCountForBook(b2)).isEqualTo(1)
    }

    // ---- Ticket #8: observe 版本（响应式，「我的」Tab 统计随刷卡刷新）----

    @Test
    fun observeTotalCount_emitsCurrentCount() = runTest {
        val logDao = db.swipeLogDao()
        val bookId = db.bookDao().insert(Book(name = "b1"))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 1L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 2L))
        assertThat(logDao.observeTotalCount().first()).isEqualTo(2)
    }

    @Test
    fun observeCountSince_emitsFilteredByTimestamp() = runTest {
        val logDao = db.swipeLogDao()
        val bookId = db.bookDao().insert(Book(name = "b1"))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 500L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 1500L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 3, timestamp = 2500L))
        assertThat(logDao.observeCountSince(1000L).first()).isEqualTo(2)
    }

    @Test
    fun observeDistinctWordCountForBook_emitsUniqueCount() = runTest {
        val logDao = db.swipeLogDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 1L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 2L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 3L))
        assertThat(logDao.observeDistinctWordCountForBook(bookId).first()).isEqualTo(2)
    }

    @Test
    fun observeTotalCount_reflectsNewInserts() = runTest {
        // 响应式核心：插入后再次 .first() 应读到新值
        val logDao = db.swipeLogDao()
        val bookId = db.bookDao().insert(Book(name = "b1"))
        assertThat(logDao.observeTotalCount().first()).isEqualTo(0)
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 1L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 2L))
        assertThat(logDao.observeTotalCount().first()).isEqualTo(2)
    }

    // ---- Ticket #20：跳过标记（book_word.skipped）----

    @Test
    fun insertBookWord_defaultsNotSkipped() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        assertThat(bookDao.getSkipped(bookId, w1)).isFalse()
    }

    @Test
    fun setSkipped_marksOnlyThatBookWordLink() = runTest {
        // 词书级独立：CET-4 跳过 apple 只标记 CET-4 的关联，不影响 CET-6
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val cet4 = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val cet6 = bookDao.insert(Book(name = "CET-6", isPreset = true))
        val wApple = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(cet4, wApple))
        bookDao.linkBookWord(BookWord(cet6, wApple))

        bookDao.setSkipped(cet4, wApple, skipped = true)

        // CET-4 的关联被标记跳过，CET-6 的同词关联仍为未跳过
        assertThat(bookDao.getSkipped(cet4, wApple)).isTrue()
        assertThat(bookDao.getSkipped(cet6, wApple)).isFalse()
    }

    @Test
    fun setSkipped_isReversible() = runTest {
        // 跳过可恢复：setSkipped(false) 置回 0，词重新进刷卡列表
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        bookDao.setSkipped(bookId, w1, skipped = true)
        assertThat(bookDao.getSkipped(bookId, w1)).isTrue()
        bookDao.setSkipped(bookId, w1, skipped = false)
        assertThat(bookDao.getSkipped(bookId, w1)).isFalse()
    }

    @Test
    fun getWordsWithSensesByBook_excludesSkipped() = runTest {
        // 刷卡只显示 skipped=0 的词 —— 跳过的词从卡片列表消失
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wRun = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(bookId, wApple))
        bookDao.linkBookWord(BookWord(bookId, wRun))
        bookDao.setSkipped(bookId, wApple, skipped = true)

        val words = wordDao.getWordsWithSensesByBook(bookId).map { it.word.text }
        assertThat(words).containsExactly("run")
    }

    @Test
    fun getWordsWithSensesByBook_includesAllAfterUnskip() = runTest {
        // 恢复跳过后词重新进刷卡列表
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wRun = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(bookId, wApple))
        bookDao.linkBookWord(BookWord(bookId, wRun))

        bookDao.setSkipped(bookId, wApple, skipped = true)
        assertThat(wordDao.getWordsWithSensesByBook(bookId).map { it.word.text })
            .containsExactly("run")

        bookDao.setSkipped(bookId, wApple, skipped = false)
        assertThat(wordDao.getWordsWithSensesByBook(bookId).map { it.word.text })
            .containsExactly("apple", "run")
    }

    @Test
    fun countWordsInBook_excludesSkipped() = runTest {
        // 词书列表副标题词数只计未跳过
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "a"))
        val w2 = wordDao.insert(Word(text = "b"))
        val w3 = wordDao.insert(Word(text = "c"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w2))
        bookDao.linkBookWord(BookWord(bookId, w3))

        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(3)
        bookDao.setSkipped(bookId, w1, skipped = true)
        bookDao.setSkipped(bookId, w2, skipped = true)
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(1)
    }

    @Test
    fun observeAllWithCounts_excludesSkippedFromCount() = runTest {
        // 词库列表副标题的「X 词」只计未跳过
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "a"))
        val w2 = wordDao.insert(Word(text = "b"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w2))
        bookDao.setSkipped(bookId, w1, skipped = true)

        val book = bookDao.observeAllWithCounts().first().single()
        assertThat(book.wordCount).isEqualTo(1)
    }

    @Test
    fun unskipWordEverywhere_clearsSkippedAcrossAllBooks() = runTest {
        // 恢复语义(issue #1):一个词可能在 CET-4 和 CET-6 都被跳过,恢复要全清
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val cet4 = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val cet6 = bookDao.insert(Book(name = "CET-6", isPreset = true))
        val wApple = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(cet4, wApple))
        bookDao.linkBookWord(BookWord(cet6, wApple))
        // 两本词书都跳过 apple
        bookDao.setSkipped(cet4, wApple, skipped = true)
        bookDao.setSkipped(cet6, wApple, skipped = true)

        bookDao.unskipWordEverywhere(wApple)

        // 两本词书的 apple 关联都恢复未跳过
        assertThat(bookDao.getSkipped(cet4, wApple)).isFalse()
        assertThat(bookDao.getSkipped(cet6, wApple)).isFalse()
    }

    @Test
    fun unskipWordEverywhere_doesNotAffectOtherWords() = runTest {
        // 只清目标词,不动其他词的 skipped 状态
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wRun = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(bookId, wApple))
        bookDao.linkBookWord(BookWord(bookId, wRun))
        bookDao.setSkipped(bookId, wApple, skipped = true)
        bookDao.setSkipped(bookId, wRun, skipped = true)

        bookDao.unskipWordEverywhere(wApple)

        assertThat(bookDao.getSkipped(bookId, wApple)).isFalse()
        // run 仍处于跳过态,不被波及
        assertThat(bookDao.getSkipped(bookId, wRun)).isTrue()
    }

    // ---- Ticket #22：软删除（book_word.deleted，词书级独立，进回收站）----

    @Test
    fun insertBookWord_defaultsNotDeleted() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        assertThat(bookDao.getDeleted(bookId, w1)).isFalse()
    }

    @Test
    fun setDeleted_marksOnlyThatBookWordLink() = runTest {
        // 词书级独立：CET-4 删 apple 只标记 CET-4 的关联，不影响 CET-6
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val cet4 = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val cet6 = bookDao.insert(Book(name = "CET-6", isPreset = true))
        val wApple = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(cet4, wApple))
        bookDao.linkBookWord(BookWord(cet6, wApple))

        bookDao.setDeleted(cet4, wApple, deleted = true)

        // CET-4 的关联被标记软删，CET-6 的同词关联仍为未删
        assertThat(bookDao.getDeleted(cet4, wApple)).isTrue()
        assertThat(bookDao.getDeleted(cet6, wApple)).isFalse()
    }

    @Test
    fun setDeleted_isReversible() = runTest {
        // 软删可恢复：setDeleted(false) 置回 0，词回到词书列表
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        bookDao.setDeleted(bookId, w1, deleted = true)
        assertThat(bookDao.getDeleted(bookId, w1)).isTrue()
        bookDao.setDeleted(bookId, w1, deleted = false)
        assertThat(bookDao.getDeleted(bookId, w1)).isFalse()
    }

    @Test
    fun getWordsWithSensesByBook_excludesDeleted() = runTest {
        // 刷卡只显示 deleted=0 的词 —— 软删的词从卡片列表消失
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wRun = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(bookId, wApple))
        bookDao.linkBookWord(BookWord(bookId, wRun))
        bookDao.setDeleted(bookId, wApple, deleted = true)

        val words = wordDao.getWordsWithSensesByBook(bookId).map { it.word.text }
        assertThat(words).containsExactly("run")
    }

    @Test
    fun getWordsWithSensesByBook_includesAllAfterRestore() = runTest {
        // 恢复后词重新进刷卡列表
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wRun = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(bookId, wApple))
        bookDao.linkBookWord(BookWord(bookId, wRun))

        bookDao.setDeleted(bookId, wApple, deleted = true)
        assertThat(wordDao.getWordsWithSensesByBook(bookId).map { it.word.text })
            .containsExactly("run")

        bookDao.setDeleted(bookId, wApple, deleted = false)
        assertThat(wordDao.getWordsWithSensesByBook(bookId).map { it.word.text })
            .containsExactly("apple", "run")
    }

    @Test
    fun countWordsInBook_excludesDeleted() = runTest {
        // 词书列表副标题词数只计未软删
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "a"))
        val w2 = wordDao.insert(Word(text = "b"))
        val w3 = wordDao.insert(Word(text = "c"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w2))
        bookDao.linkBookWord(BookWord(bookId, w3))

        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(3)
        bookDao.setDeleted(bookId, w1, deleted = true)
        bookDao.setDeleted(bookId, w2, deleted = true)
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(1)
    }

    @Test
    fun observeAllWithCounts_excludesDeletedFromCount() = runTest {
        // 词库列表副标题的「X 词」只计未软删
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "a"))
        val w2 = wordDao.insert(Word(text = "b"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w2))
        bookDao.setDeleted(bookId, w1, deleted = true)

        val book = bookDao.observeAllWithCounts().first().single()
        assertThat(book.wordCount).isEqualTo(1)
    }

    @Test
    fun deletedAndSkippedAreIndependent() = runTest {
        // 两个标记互不影响：跳过的词不等于删除，删除的词不等于跳过
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        bookDao.setSkipped(bookId, w1, skipped = true)
        assertThat(bookDao.getDeleted(bookId, w1)).isFalse() // 跳过不等于删除

        bookDao.setDeleted(bookId, w1, deleted = true)
        assertThat(bookDao.getSkipped(bookId, w1)).isTrue() // 删除不改变跳过态
    }

    @Test
    fun observeDeletedEntries_listsOnlyDeleted() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val cet4 = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wRun = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(cet4, wApple))
        bookDao.linkBookWord(BookWord(cet4, wRun))

        // 初始无软删 → 回收站空
        assertThat(bookDao.observeDeletedEntries().first()).isEmpty()

        bookDao.setDeleted(cet4, wApple, deleted = true)
        val entries = bookDao.observeDeletedEntries().first()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().wordText).isEqualTo("apple")
        assertThat(entries.single().bookName).isEqualTo("CET-4")
        assertThat(entries.single().bookId).isEqualTo(cet4)
        assertThat(entries.single().wordId).isEqualTo(wApple)
    }

    @Test
    fun observeDeletedEntries_isEmptyAfterRestore() = runTest {
        // 恢复后条目从回收站消失
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.setDeleted(bookId, w1, deleted = true)
        assertThat(bookDao.observeDeletedEntries().first()).hasSize(1)

        bookDao.setDeleted(bookId, w1, deleted = false)
        assertThat(bookDao.observeDeletedEntries().first()).isEmpty()
    }

    @Test
    fun purgeDeleted_actuallyDeletesRow() = runTest {
        // 永久删除 = 真 DELETE 关联行（不可恢复）
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.setDeleted(bookId, w1, deleted = true)

        bookDao.purgeDeleted(bookId, w1)

        // 关联行真删了，getDeleted 返回 null
        assertThat(bookDao.getDeleted(bookId, w1)).isNull()
        // 回收站也空了
        assertThat(bookDao.observeDeletedEntries().first()).isEmpty()
        // 但全局 word 池里 apple 还在（永久删除只删关联，不删全局词）
        assertThat(wordDao.getByText("apple")).isNotNull()
    }

    @Test
    fun purgeDeleted_refusesNonDeletedLink() = runTest {
        // deleted=1 兜底：未软删的关联不会被 purgeDeleted 误删
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        bookDao.purgeDeleted(bookId, w1) // 关联 deleted=0，兜底不删

        assertThat(bookDao.getDeleted(bookId, w1)).isFalse() // 关联还在
    }

    // ---- Ticket #22：词书软删除（book.deleted）----

    @Test
    fun deleteCustom_softDeletesInsteadOfPhysicalDelete() = runTest {
        // 删除自定义词书 = 软删（deleted=1），行还在，可恢复
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "mybook", isPreset = false))
        val affected = bookDao.deleteCustom(id)
        assertThat(affected).isEqualTo(1)
        // 行还在（软删），getById 能读到
        val book = bookDao.getById(id)
        assertThat(book).isNotNull()
        assertThat(book!!.deleted).isTrue()
    }

    @Test
    fun deleteCustom_refusesPresetBook() = runTest {
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val affected = bookDao.deleteCustom(id)
        assertThat(affected).isEqualTo(0)
        assertThat(bookDao.getById(id)!!.deleted).isFalse()
    }

    @Test
    fun observeAll_excludesSoftDeletedBooks() = runTest {
        val bookDao = db.bookDao()
        val visible = bookDao.insert(Book(name = "visible", isPreset = false))
        val hidden = bookDao.insert(Book(name = "hidden", isPreset = false))
        bookDao.deleteCustom(hidden)

        val names = bookDao.observeAll().first().map { it.name }
        assertThat(names).contains("visible")
        assertThat(names).doesNotContain("hidden")
    }

    @Test
    fun observeAllWithCounts_excludesSoftDeletedBooks() = runTest {
        val bookDao = db.bookDao()
        val visible = bookDao.insert(Book(name = "visible", isPreset = false))
        val hidden = bookDao.insert(Book(name = "hidden", isPreset = false))
        bookDao.deleteCustom(hidden)

        val ids = bookDao.observeAllWithCounts().first().map { it.bookId }
        assertThat(ids).contains(visible)
        assertThat(ids).doesNotContain(hidden)
    }

    @Test
    fun restoreBook_clearsDeletedFlag() = runTest {
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "b1", isPreset = false))
        bookDao.deleteCustom(id)
        assertThat(bookDao.getById(id)!!.deleted).isTrue()

        bookDao.restoreBook(id)
        assertThat(bookDao.getById(id)!!.deleted).isFalse()
        // 恢复后回到可见列表
        assertThat(bookDao.observeAll().first().map { it.name }).contains("b1")
    }

    @Test
    fun restoreBook_preservesBookWordLinks() = runTest {
        // 软删/恢复词书不动其下 book_word 关联（含各自 deleted/skipped 状态）
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1", isPreset = false))
        val w1 = wordDao.insert(Word(text = "apple"))
        val w2 = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w2))
        bookDao.setDeleted(bookId, w1, deleted = true) // w1 关联软删
        bookDao.setSkipped(bookId, w2, skipped = true) // w2 关联跳过

        bookDao.deleteCustom(bookId) // 软删整本书
        bookDao.restoreBook(bookId) // 恢复

        // 关联行原样保留：w1 仍软删，w2 仍跳过
        assertThat(bookDao.getDeleted(bookId, w1)).isTrue()
        assertThat(bookDao.getSkipped(bookId, w2)).isTrue()
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(0) // w1 软删、w2 跳过，都不计
    }

    @Test
    fun observeDeletedBooks_listsOnlySoftDeleted() = runTest {
        val bookDao = db.bookDao()
        val visible = bookDao.insert(Book(name = "visible", isPreset = false))
        val hidden = bookDao.insert(Book(name = "hidden", isPreset = false))

        assertThat(bookDao.observeDeletedBooks().first()).isEmpty()

        bookDao.deleteCustom(hidden)
        val deleted = bookDao.observeDeletedBooks().first()
        assertThat(deleted).hasSize(1)
        assertThat(deleted.single().name).isEqualTo("hidden")
        assertThat(deleted.single().bookId).isEqualTo(hidden)
    }

    @Test
    fun purgeBook_physicallyDeletesAndCascades() = runTest {
        // 永久删除词书 = 真 DELETE book，CASCADE 清其下 book_word 关联
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1", isPreset = false))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.deleteCustom(bookId) // 先软删进回收站

        bookDao.purgeBook(bookId)

        // 词书行真删了
        assertThat(bookDao.getById(bookId)).isNull()
        assertThat(bookDao.observeDeletedBooks().first()).isEmpty()
        // CASCADE 清了关联行
        assertThat(bookDao.getDeleted(bookId, w1)).isNull()
        // 全局 word 池里 apple 还在（永久删词书不删全局词）
        assertThat(wordDao.getByText("apple")).isNotNull()
    }

    @Test
    fun purgeBook_refusesNonDeletedBook() = runTest {
        // deleted=1 兜底：未软删的词书不会被 purgeBook 误删
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "b1", isPreset = false))

        bookDao.purgeBook(id) // 词书 deleted=0，兜底不删

        assertThat(bookDao.getById(id)).isNotNull() // 词书还在
    }
}
