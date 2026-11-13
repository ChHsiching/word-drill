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
 * Ticket #3 main seam: in-memory Room, verifying the 5-table schema, foreign-key cascades, DAO CRUD, and aggregate queries.
 * Only external behavior is tested (the DAO contract), not Room internals.
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

    // ---- Word entry CRUD ----

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
        // IGNORE conflict strategy: a duplicate insert returns -1 and the original row is kept
        assertThat(secondId).isEqualTo(-1L)
        assertThat(wordDao.getByText("apple")!!.wordId).isEqualTo(firstId)
    }

    @Test
    fun findIdByText_returnsNullForAbsent() = runTest {
        assertThat(db.wordDao().findIdByText("ghost")).isNull()
    }

    // ---- Ticket #14: word.phonetic (IPA transcription, nullable) ----

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
        // The Word backfilled by @Relation should also carry phonetic (verifies that getWordsWithSensesByBook's SELECT w.*
        // covers the new column, instead of an explicit column list that would miss phonetic)
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

    // ---- Foreign-key cascade: deleting a word cascades to its senses ----

    @Test
    fun deleteWord_cascadesDeleteSenses() = runTest {
        val wordDao = db.wordDao()
        val wordId = wordDao.insert(Word(text = "temp"))
        wordDao.insertSense(Sense(wordId = wordId, pos = "n.", meaning = "临时"))
        wordDao.deleteWord(wordId)
        assertThat(wordDao.getSensesForWord(wordId)).isEmpty()
    }

    // ---- Book CRUD ----

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
        // The two preset books come first (by name ascending), the two custom ones after (by name ascending)
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
        // Ticket #11: preset books cannot be renamed (DAO-level guard, same pattern as deleteCustom's isPreset=0)
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val affected = bookDao.rename(id, "hacked")
        // WHERE isPreset = 0 does not match, 0 rows affected, the original row is kept
        assertThat(affected).isEqualTo(0)
        assertThat(bookDao.getById(id)!!.name).isEqualTo("CET-4")
    }

    // Ticket #22: deleteCustom is now a soft delete (deleted=1); full coverage is in the #22 section below,
    // deleteCustom_softDeletesInsteadOfPhysicalDelete / deleteCustom_refusesPresetBook.

    // ---- Book-word links ----

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
        bookDao.linkBookWord(BookWord(bookId, w1)) // duplicate
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
        // Ticket #22: soft-deleting a book (deleteCustom) does not really delete the row and does not touch its book_word links.
        // CASCADE fires only on permanent delete (purgeBook); see purgeBook_physicallyDeletesAndCascades.
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1", isPreset = false))
        val w1 = wordDao.insert(Word(text = "a"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(1)

        bookDao.deleteCustom(bookId) // soft delete

        // The link rows are still there (soft-deleting the book does not touch book_word); countWordsInBook still finds them
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(1)
        // But the book disappears from the visible list (observeAllWithCounts filters to deleted=0)
        assertThat(bookDao.observeAllWithCounts().first().none { it.bookId == bookId }).isTrue()
    }

    // ---- Query words by book (with senses) ----

    @Test
    fun getWordsWithSensesByBook_returnsWordsWithTheirSenses() = runTest {
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val wApple = wordDao.insert(Word(text = "apple"))
        val wBanana = wordDao.insert(Word(text = "banana"))
        wordDao.insertSense(Sense(wordId = wApple, pos = "n.", meaning = "苹果"))
        wordDao.insertSense(Sense(wordId = wBanana, pos = "n.", meaning = "香蕉"))
        // Words from another book must not appear
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

    // ---- Ticket #7: observe book words (reactive, subscribed by the list page) ----

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

    // ---- Swipe log aggregation ----

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
        // Today's start = 1000L: only 2 entries should count
        assertThat(logDao.countSince(1000L)).isEqualTo(2)
    }

    @Test
    fun swipeLog_distinctWordCountForBook_countsUniqueWords() = runTest {
        val logDao = db.swipeLogDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        // Within one book, wordId 1 is swiped 3 times and wordId 2 once, so distinct = 2
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

    // ---- Ticket #8: observe variants (reactive; the Me tab stats refresh on swipes) ----

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
        // Reactive core: calling .first() again after inserts should read the new value
        val logDao = db.swipeLogDao()
        val bookId = db.bookDao().insert(Book(name = "b1"))
        assertThat(logDao.observeTotalCount().first()).isEqualTo(0)
        logDao.insert(SwipeLog(bookId = bookId, wordId = 1, timestamp = 1L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = 2, timestamp = 2L))
        assertThat(logDao.observeTotalCount().first()).isEqualTo(2)
    }

    // ---- Ticket #20: skip flag (book_word.skipped) ----

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
        // Book-level independence: skipping apple in CET-4 marks only CET-4's link, not CET-6
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val cet4 = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val cet6 = bookDao.insert(Book(name = "CET-6", isPreset = true))
        val wApple = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(cet4, wApple))
        bookDao.linkBookWord(BookWord(cet6, wApple))

        bookDao.setSkipped(cet4, wApple, skipped = true)

        // CET-4's link is marked skipped; CET-6's link to the same word stays not skipped
        assertThat(bookDao.getSkipped(cet4, wApple)).isTrue()
        assertThat(bookDao.getSkipped(cet6, wApple)).isFalse()
    }

    @Test
    fun setSkipped_isReversible() = runTest {
        // Skipping is reversible: setSkipped(false) sets it back to 0 and the word re-enters the drill list
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
        // Drilling shows only skipped=0 words; skipped words disappear from the card list
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
        // After unskipping, the word re-enters the drill list
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
        // The book-list subtitle word count counts only unskipped words
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
        // The library-list subtitle "X words" counts only unskipped words
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
        // Restore semantics (issue #1): a word may be skipped in both CET-4 and CET-6; restore must clear all of them
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val cet4 = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val cet6 = bookDao.insert(Book(name = "CET-6", isPreset = true))
        val wApple = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(cet4, wApple))
        bookDao.linkBookWord(BookWord(cet6, wApple))
        // Skip apple in both books
        bookDao.setSkipped(cet4, wApple, skipped = true)
        bookDao.setSkipped(cet6, wApple, skipped = true)

        bookDao.unskipWordEverywhere(wApple)

        // apple's links in both books are restored to not skipped
        assertThat(bookDao.getSkipped(cet4, wApple)).isFalse()
        assertThat(bookDao.getSkipped(cet6, wApple)).isFalse()
    }

    @Test
    fun unskipWordEverywhere_doesNotAffectOtherWords() = runTest {
        // Clear only the target word; other words' skipped state is untouched
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
        // run stays skipped, unaffected
        assertThat(bookDao.getSkipped(bookId, wRun)).isTrue()
    }

    // ---- Ticket #22: soft delete (book_word.deleted, book-level independent, into the recycle bin) ----

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
        // Book-level independence: deleting apple in CET-4 marks only CET-4's link, not CET-6
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val cet4 = bookDao.insert(Book(name = "CET-4", isPreset = true))
        val cet6 = bookDao.insert(Book(name = "CET-6", isPreset = true))
        val wApple = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(cet4, wApple))
        bookDao.linkBookWord(BookWord(cet6, wApple))

        bookDao.setDeleted(cet4, wApple, deleted = true)

        // CET-4's link is marked soft-deleted; CET-6's link to the same word stays not deleted
        assertThat(bookDao.getDeleted(cet4, wApple)).isTrue()
        assertThat(bookDao.getDeleted(cet6, wApple)).isFalse()
    }

    @Test
    fun setDeleted_isReversible() = runTest {
        // Soft delete is reversible: setDeleted(false) sets it back to 0 and the word returns to the book list
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
        // Drilling shows only deleted=0 words; soft-deleted words disappear from the card list
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
        // After restore, the word re-enters the drill list
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
        // The book-list subtitle word count counts only non-soft-deleted words
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
        // The library-list subtitle "X words" counts only non-soft-deleted words
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
        // The two flags are independent: skipped does not mean deleted, and deleted does not mean skipped
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        bookDao.setSkipped(bookId, w1, skipped = true)
        assertThat(bookDao.getDeleted(bookId, w1)).isFalse() // skipping does not mean deleting

        bookDao.setDeleted(bookId, w1, deleted = true)
        assertThat(bookDao.getSkipped(bookId, w1)).isTrue() // deleting does not change the skipped state
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

        // Initially nothing soft-deleted, so the recycle bin is empty
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
        // After restore, the entry disappears from the recycle bin
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
        // Permanent delete = a real DELETE of the link row (irreversible)
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.setDeleted(bookId, w1, deleted = true)

        bookDao.purgeDeleted(bookId, w1)

        // The link row is really deleted; getDeleted returns null
        assertThat(bookDao.getDeleted(bookId, w1)).isNull()
        // The recycle bin is empty too
        assertThat(bookDao.observeDeletedEntries().first()).isEmpty()
        // But apple is still in the global word pool (permanent delete removes only the link, not the global word)
        assertThat(wordDao.getByText("apple")).isNotNull()
    }

    @Test
    fun purgeDeleted_refusesNonDeletedLink() = runTest {
        // deleted=1 guard: a link that is not soft-deleted cannot be wrongly deleted by purgeDeleted
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1"))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))

        bookDao.purgeDeleted(bookId, w1) // link deleted=0; the guard prevents deletion

        assertThat(bookDao.getDeleted(bookId, w1)).isFalse() // the link is still there
    }

    // ---- Ticket #22: book soft delete (book.deleted) ----

    @Test
    fun deleteCustom_softDeletesInsteadOfPhysicalDelete() = runTest {
        // Deleting a custom book = soft delete (deleted=1); the row stays and is recoverable
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "mybook", isPreset = false))
        val affected = bookDao.deleteCustom(id)
        assertThat(affected).isEqualTo(1)
        // The row is still there (soft-deleted); getById can read it
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
        // After restore the book returns to the visible list
        assertThat(bookDao.observeAll().first().map { it.name }).contains("b1")
    }

    @Test
    fun restoreBook_preservesBookWordLinks() = runTest {
        // Soft-deleting/restoring a book does not touch its book_word links (including their own deleted/skipped state)
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1", isPreset = false))
        val w1 = wordDao.insert(Word(text = "apple"))
        val w2 = wordDao.insert(Word(text = "run"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.linkBookWord(BookWord(bookId, w2))
        bookDao.setDeleted(bookId, w1, deleted = true) // w1's link soft-deleted
        bookDao.setSkipped(bookId, w2, skipped = true) // w2's link skipped

        bookDao.deleteCustom(bookId) // soft-delete the whole book
        bookDao.restoreBook(bookId) // restore

        // The link rows are preserved as-is: w1 stays soft-deleted, w2 stays skipped
        assertThat(bookDao.getDeleted(bookId, w1)).isTrue()
        assertThat(bookDao.getSkipped(bookId, w2)).isTrue()
        assertThat(bookDao.countWordsInBook(bookId)).isEqualTo(0) // w1 is soft-deleted and w2 skipped; neither counts
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
        // Permanently deleting a book = a real DELETE of the book row; CASCADE clears its book_word links
        val wordDao = db.wordDao()
        val bookDao = db.bookDao()
        val bookId = bookDao.insert(Book(name = "b1", isPreset = false))
        val w1 = wordDao.insert(Word(text = "apple"))
        bookDao.linkBookWord(BookWord(bookId, w1))
        bookDao.deleteCustom(bookId) // soft-delete into the recycle bin first

        bookDao.purgeBook(bookId)

        // The book row is really deleted
        assertThat(bookDao.getById(bookId)).isNull()
        assertThat(bookDao.observeDeletedBooks().first()).isEmpty()
        // CASCADE cleared the link rows
        assertThat(bookDao.getDeleted(bookId, w1)).isNull()
        // apple is still in the global word pool (permanently deleting the book does not delete global words)
        assertThat(wordDao.getByText("apple")).isNotNull()
    }

    @Test
    fun purgeBook_refusesNonDeletedBook() = runTest {
        // deleted=1 guard: a book that is not soft-deleted cannot be wrongly deleted by purgeBook
        val bookDao = db.bookDao()
        val id = bookDao.insert(Book(name = "b1", isPreset = false))

        bookDao.purgeBook(id) // book deleted=0; the guard prevents deletion

        assertThat(bookDao.getById(id)).isNotNull() // the book is still there
    }
}
