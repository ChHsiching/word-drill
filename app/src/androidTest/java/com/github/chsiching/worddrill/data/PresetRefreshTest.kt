package com.github.chsiching.worddrill.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade contract of #56: an install holding v0.2-era preset books (old membership,
 * old v0.2-era content) re-imports the new bundled words.json. The refresh updates
 * books in place — book ids, link flags, swipe logs and completion counts are user data
 * keyed by ids and must survive.
 */
@RunWith(AndroidJUnit4::class)
class PresetRefreshTest {

    private lateinit var db: WordDrillDatabase
    private lateinit var importer: PresetImporter

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = PresetImporter(db, db.wordDao(), db.bookDao())
        seedOldInstall()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Old-shape state: preset books with v0.2-era membership/content, a user book
     *  sharing the "able" word row, a skipped removed-to-be word in the review book. */
    private suspend fun seedOldInstall() {
        val cet4 = db.bookDao().insert(Book(name = "CET-4", isPreset = true))
        val cet6 = db.bookDao().insert(Book(name = "CET-6", isPreset = true))
        val review = db.bookDao().insert(Book(name = REVIEW_BOOK_NAME, isPreset = true))
        val user = db.bookDao().insert(Book(name = "我的词书", isPreset = false))

        val able = db.wordDao().insert(Word(text = "able", phonetic = "/old/"))
        db.wordDao().insertSense(Sense(wordId = able, pos = "n.", meaning = "旧的释义"))
        db.wordDao().insertSense(Sense(wordId = able, pos = "adj.", meaning = "旧的形容词义"))

        val oldword = db.wordDao().insert(Word(text = "oldword"))
        db.wordDao().insertSense(Sense(wordId = oldword, pos = "n.", meaning = "旧词"))

        val myword = db.wordDao().insert(Word(text = "myword"))
        db.wordDao().insertSense(Sense(wordId = myword, pos = "n.", meaning = "用户词"))

        db.bookDao().linkBookWord(BookWord(bookId = cet4, wordId = able, skipped = true))
        db.bookDao().linkBookWord(BookWord(bookId = cet4, wordId = oldword, skipped = true))
        db.bookDao().linkBookWord(BookWord(bookId = cet6, wordId = able))
        db.bookDao().linkBookWord(BookWord(bookId = user, wordId = able))
        db.bookDao().linkBookWord(BookWord(bookId = user, wordId = myword))
        // oldword is skipped in CET-4, so the review book links it.
        db.bookDao().linkBookWord(BookWord(bookId = review, wordId = oldword))
        db.bookDao().incrementCompletionCount(cet4)
        db.bookDao().incrementCompletionCount(cet4)
        db.bookDao().incrementCompletionCount(cet4)
    }

    /** The new bundled words.json: same book names, refreshed membership and content. */
    private fun newPreset() = PresetWords(
        source = "new",
        books = listOf(
            PresetWords.Book(
                name = "CET-4",
                words = listOf(
                    PresetWords.Word(
                        text = "able",
                        phonetic = "/eɪbl/",
                        senses = listOf(
                            PresetWords.Sense("n.", "新释义"),
                            PresetWords.Sense("v.", "新动词义"),
                        ),
                    ),
                    PresetWords.Word(
                        text = "freshword",
                        phonetic = "/freʃ/",
                        senses = listOf(PresetWords.Sense("n.", "新词")),
                    ),
                ),
            ),
            PresetWords.Book(
                name = "CET-6",
                words = listOf(
                    PresetWords.Word(
                        text = "able",
                        phonetic = "/eɪbl/",
                        senses = listOf(PresetWords.Sense("n.", "新释义")),
                    ),
                ),
            ),
        ),
    )

    private suspend fun cet4Id() = db.bookDao().getByName("CET-4")!!.bookId
    private suspend fun cet6Id() = db.bookDao().getByName("CET-6")!!.bookId
    private suspend fun reviewId() = db.bookDao().getByName(REVIEW_BOOK_NAME)!!.bookId
    private suspend fun userId() = db.bookDao().getByName("我的词书")!!.bookId

    @Test
    fun refresh_updatesWordContentInPlace() = runTest {
        importer.importWords(newPreset())

        val able = db.wordDao().getByText("able")!!
        assertThat(able.phonetic).isEqualTo("/eɪbl/")
        // Same-pos senses update their meaning; new pos insert; stale pos (adj.) is gone.
        val senses = db.wordDao().getSensesForWord(able.wordId)
        assertThat(senses.map { it.pos to it.meaning }).containsExactly(
            "n." to "新释义",
            "v." to "新动词义",
        )
    }

    @Test
    fun refresh_replacesStaleSenseOfSamePos() = runTest {
        importer.importWords(newPreset())

        val able = db.wordDao().getByText("able")!!
        val senses = db.wordDao().getSensesForWord(able.wordId)
        // The old v0.2-era meaning must not linger next to the new one.
        assertThat(senses.map { it.meaning }).containsNoDuplicates()
        assertThat(senses.map { it.meaning }).containsNoneOf("旧的释义", "旧的形容词义")
    }

    @Test
    fun refresh_addsNewMembershipWords() = runTest {
        importer.importWords(newPreset())

        val fresh = db.wordDao().getByText("freshword")!!
        // Membership counts every link; the visible deck is one smaller because
        // "able" keeps the skip flag the old install had set.
        assertThat(db.bookDao().getWordIdsInBook(cet4Id())).hasSize(2)
        assertThat(db.bookDao().countWordsInBook(cet4Id())).isEqualTo(1)
        assertThat(db.wordDao().getSensesForWord(fresh.wordId).map { it.meaning }).containsExactly("新词")
    }

    @Test
    fun refresh_unlinksRemovedWords_fromPresetBookOnly() = runTest {
        val oldwordId = db.wordDao().getByText("oldword")!!.wordId

        importer.importWords(newPreset())

        // Removed from CET-4 membership...
        assertThat(db.bookDao().getSkipped(cet4Id(), oldwordId)).isNull()
        // ...but the word row itself survives (cumulative stats key on word ids).
        assertThat(db.wordDao().getById(oldwordId)).isNotNull()
    }

    @Test
    fun refresh_dropsReviewLink_ofRemovedUnskippedWord() = runTest {
        val oldwordId = db.wordDao().getByText("oldword")!!.wordId
        assertThat(db.bookDao().getSkipped(reviewId(), oldwordId)).isNotNull()

        importer.importWords(newPreset())

        // oldword left every book's membership, so it is no longer "skipped anywhere"
        // and must not linger in the review book (GLOSSARY.md, review book).
        assertThat(db.bookDao().getSkipped(reviewId(), oldwordId)).isNull()
    }

    @Test
    fun refresh_keepsReviewLink_ofStillSkippedWord() = runTest {
        // "able" stays skipped in CET-4 after the refresh, so the review book keeps it.
        val ableId = db.wordDao().getByText("able")!!.wordId
        val review = db.bookDao().getByName(REVIEW_BOOK_NAME)!!
        db.bookDao().linkBookWord(BookWord(bookId = review.bookId, wordId = ableId))

        importer.importWords(newPreset())

        assertThat(db.bookDao().getSkipped(reviewId(), ableId)).isNotNull()
    }

    @Test
    fun refresh_preservesPresetBookIdsSkipFlagsAndCompletionCount() = runTest {
        val cet4Before = db.bookDao().getByName("CET-4")!!
        val ableId = db.wordDao().getByText("able")!!.wordId
        assertThat(db.bookDao().getSkipped(cet4Before.bookId, ableId)).isTrue()

        importer.importWords(newPreset())

        val cet4After = db.bookDao().getByName("CET-4")!!
        assertThat(cet4After.bookId).isEqualTo(cet4Before.bookId)
        assertThat(cet4After.completionCount).isEqualTo(3)
        assertThat(db.bookDao().getSkipped(cet4Id(), ableId)).isTrue()
    }

    @Test
    fun refresh_preservesUserBooksAndTheirLinks() = runTest {
        importer.importWords(newPreset())

        val user = db.bookDao().getByName("我的词书")!!
        assertThat(user.isPreset).isFalse()
        val userWords = db.wordDao().getWordsWithSensesByBook(user.bookId).map { it.word.text }
        assertThat(userWords).containsExactly("able", "myword")
        // The user's own sense content is untouched.
        val myword = db.wordDao().getByText("myword")!!
        assertThat(db.wordDao().getSensesForWord(myword.wordId).map { it.meaning }).containsExactly("用户词")
    }

    @Test
    fun refresh_doesNotDuplicateWordsSharedWithUserBooks() = runTest {
        importer.importWords(newPreset())

        // findOrInsertWord semantics: one "able" row serves the preset books and the
        // user book that imported the same word earlier.
        val allAbles = db.wordDao().getAll().filter { it.text == "able" }
        assertThat(allAbles).hasSize(1)
    }

    @Test
    fun refresh_isIdempotent() = runTest {
        importer.importWords(newPreset())
        importer.importWords(newPreset())

        val cet4 = db.bookDao().getByName("CET-4")!!
        assertThat(db.bookDao().getWordIdsInBook(cet4.bookId)).hasSize(2)
        val able = db.wordDao().getByText("able")!!
        assertThat(db.wordDao().getSensesForWord(able.wordId)).hasSize(2)
        assertThat(db.wordDao().getAll().filter { it.text == "able" }).hasSize(1)
    }

    @Test
    fun refresh_freshInstall_createsBooksAsPreset() = runTest {
        val freshDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            WordDrillDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val freshImporter = PresetImporter(freshDb, freshDb.wordDao(), freshDb.bookDao())
            freshImporter.importWords(newPreset())

            val books = freshDb.bookDao().getAll()
            assertThat(books.map { it.name }).containsExactly("CET-4", "CET-6")
            assertThat(books.all { it.isPreset }).isTrue()
            assertThat(freshDb.bookDao().countWordsInBook(books.first { it.name == "CET-4" }.bookId))
                .isEqualTo(2)
        } finally {
            freshDb.close()
        }
    }
}
