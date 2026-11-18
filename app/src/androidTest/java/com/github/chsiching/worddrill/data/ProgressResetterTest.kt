package com.github.chsiching.worddrill.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressResetterTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var resetter: ProgressResetter

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        resetter = ProgressResetter(db, db.bookDao(), settings)
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        Unit
    }

    /** Seeds a book under an explicit id with linked words; ids are unique per test
     *  because the process-wide DataStore resume keys survive the per-test in-memory
     *  database. Skips [skippedTexts] through the same write path as the drill
     *  (mark the book link + link the review book). Returns the review book id. */
    private suspend fun seedDrilledBook(
        bookId: Long,
        name: String,
        wordTexts: List<String>,
        skippedTexts: List<String>,
        resumeText: String? = null,
    ): Long {
        db.bookDao().insert(Book(bookId = bookId, name = name, isPreset = false))
        val reviewId = db.bookDao().insert(Book(name = REVIEW_BOOK_NAME, isPreset = true))
        wordTexts.forEach { text ->
            val wordId = db.wordDao().findOrInsertWord(text, null)
            db.bookDao().linkBookWord(BookWord(bookId = bookId, wordId = wordId))
        }
        skippedTexts.forEach { text ->
            val wordId = db.wordDao().findIdByText(text)!!
            db.bookDao().setSkipped(bookId, wordId, skipped = true)
            db.bookDao().linkBookWord(BookWord(bookId = reviewId, wordId = wordId))
        }
        resumeText?.let { settings.setResumeWordId(bookId, db.wordDao().findIdByText(it)!!) }
        return reviewId
    }

    @Test
    fun reset_unskipsEveryWordOfTheBook_andClearsResume() = runBlocking {
        val bookId = 800_101L
        seedDrilledBook(
            bookId = bookId,
            name = "重置A",
            wordTexts = listOf("apple", "balance", "circle"),
            skippedTexts = listOf("apple", "circle"),
            resumeText = "balance",
        )

        resetter.reset(bookId)

        assertThat(db.bookDao().getSkipped(bookId, db.wordDao().findIdByText("apple")!!)).isFalse()
        assertThat(db.bookDao().getSkipped(bookId, db.wordDao().findIdByText("circle")!!)).isFalse()
        assertThat(settings.resumeWordId(bookId)).isNull()
        assertThat(db.wordDao().getWordsWithSensesByBook(bookId).map { it.word.text })
            .containsExactly("apple", "balance", "circle")
        Unit
    }

    @Test
    fun reset_removesReviewLinks_ofUnskippedWords() = runBlocking {
        val bookId = 800_201L
        val reviewId = seedDrilledBook(
            bookId = bookId,
            name = "重置B",
            wordTexts = listOf("apple", "balance", "circle"),
            skippedTexts = listOf("apple", "circle"),
        )

        resetter.reset(bookId)

        assertThat(db.wordDao().getWordsWithSensesByBook(reviewId)).isEmpty()
        Unit
    }

    @Test
    fun reset_keepsReviewLink_whenWordIsStillSkippedInAnotherBook() = runBlocking {
        val bookId = 800_301L
        val otherId = 800_302L
        val reviewId = seedDrilledBook(
            bookId = bookId,
            name = "重置C",
            wordTexts = listOf("apple", "balance"),
            skippedTexts = listOf("apple", "balance"),
        )
        // "apple" also lives skipped in another book; only "balance" is reset away.
        db.bookDao().insert(Book(bookId = otherId, name = "别处", isPreset = false))
        db.bookDao().linkBookWord(BookWord(bookId = otherId, wordId = db.wordDao().findIdByText("apple")!!))
        db.bookDao().setSkipped(otherId, db.wordDao().findIdByText("apple")!!, skipped = true)

        resetter.reset(bookId)

        val reviewTexts = db.wordDao().getWordsWithSensesByBook(reviewId).map { it.word.text }
        assertThat(reviewTexts).containsExactly("apple")
        assertThat(db.bookDao().getSkipped(otherId, db.wordDao().findIdByText("apple")!!)).isTrue()
        Unit
    }

    @Test
    fun reset_leavesSwipeLogsAndOtherBooksUntouched() = runBlocking {
        val bookId = 800_401L
        val otherId = 800_402L
        seedDrilledBook(
            bookId = bookId,
            name = "重置D",
            wordTexts = listOf("apple", "balance", "circle"),
            skippedTexts = listOf("circle"),
        )
        db.bookDao().insert(Book(bookId = otherId, name = "旁书", isPreset = false))
        val otherWord = db.wordDao().findOrInsertWord("duty", null)
        db.bookDao().linkBookWord(BookWord(bookId = otherId, wordId = otherWord))
        db.bookDao().setSkipped(otherId, otherWord, skipped = true)
        val logDao = db.swipeLogDao()
        logDao.insert(SwipeLog(bookId = bookId, wordId = db.wordDao().findIdByText("apple")!!, timestamp = 1_000L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = db.wordDao().findIdByText("balance")!!, timestamp = 2_000L))
        val logsBefore = logDao.getAll()

        resetter.reset(bookId)

        assertThat(logDao.getAll()).isEqualTo(logsBefore)
        assertThat(logDao.totalCount()).isEqualTo(2)
        assertThat(logDao.countSince(0L)).isEqualTo(2)
        assertThat(db.bookDao().getSkipped(otherId, otherWord)).isTrue()
        assertThat(settings.resumeWordId(otherId)).isNull()
        Unit
    }

    @Test
    fun reset_emitsResetBookId_onSharedFlow() = runBlocking {
        val bookId = 800_501L
        seedDrilledBook(
            bookId = bookId,
            name = "重置E",
            wordTexts = listOf("apple"),
            skippedTexts = emptyList(),
        )
        val emitted = mutableListOf<Long>()
        val job = launch {
            resetter.resetBookIds.collect { emitted.add(it) }
        }
        delay(100)

        resetter.reset(bookId)
        delay(100)
        job.cancel()

        assertThat(emitted).containsExactly(bookId)
        Unit
    }
}
