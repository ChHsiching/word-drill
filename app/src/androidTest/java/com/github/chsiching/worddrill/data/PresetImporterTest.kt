package com.github.chsiching.worddrill.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetImporterTest {

    private lateinit var db: WordDrillDatabase
    private lateinit var importer: PresetImporter

    private val sample = PresetWords(
        source = "test",
        books = listOf(
            PresetWords.Book(
                name = "CET-4",
                words = listOf(
                    PresetWords.Word(
                        text = "apple",
                        phonetic = "/ˈæpl/",
                        senses = listOf(PresetWords.Sense("n.", "苹果")),
                    ),
                    PresetWords.Word(
                        text = "run",
                        senses = listOf(
                            PresetWords.Sense("vi.", "跑"),
                            PresetWords.Sense("vt.", "经营"),
                        ),
                    ),
                ),
            ),
            PresetWords.Book(
                name = "CET-6",
                words = listOf(
                    PresetWords.Word(
                        text = "apple",
                        phonetic = "/ˈæpl/",
                        senses = listOf(PresetWords.Sense("n.", "苹果")),
                    ),
                    PresetWords.Word(
                        text = "balance",
                        phonetic = "/ˈbæləns/",
                        senses = listOf(PresetWords.Sense("n.", "平衡")),
                    ),
                ),
            ),
        ),
    )

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = PresetImporter(db, db.wordDao(), db.bookDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun import_createsBooksAsPreset() = runTest {
        importer.importWords(sample)
        val books = db.bookDao().observeAll().first()
        assertThat(books.map { it.name }).containsExactly("CET-4", "CET-6")
        assertThat(books.all { it.isPreset }).isTrue()
    }

    @Test
    fun import_dedupesWordAcrossBooks() = runTest {
        importer.importWords(sample)
        val apple = db.wordDao().getByText("apple")
        assertThat(apple).isNotNull()
        val cet4 = db.bookDao().getByName("CET-4")!!
        val cet6 = db.bookDao().getByName("CET-6")!!
        val cet4Words = db.wordDao().getWordsWithSensesByBook(cet4.bookId).map { it.word.wordId }
        val cet6Words = db.wordDao().getWordsWithSensesByBook(cet6.bookId).map { it.word.wordId }
        assertThat(cet4Words).contains(apple!!.wordId)
        assertThat(cet6Words).contains(apple.wordId)
        assertThat(cet4Words.intersect(cet6Words.toSet())).contains(apple.wordId)
    }

    @Test
    fun import_linksBookWordForBothBooks() = runTest {
        importer.importWords(sample)
        val cet4 = db.bookDao().observeAll().first().first { it.name == "CET-4" }
        val cet6 = db.bookDao().observeAll().first().first { it.name == "CET-6" }
        assertThat(db.bookDao().countWordsInBook(cet4.bookId)).isEqualTo(2)
        assertThat(db.bookDao().countWordsInBook(cet6.bookId)).isEqualTo(2)
    }

    @Test
    fun import_writesMultipleSensesForOneWord() = runTest {
        importer.importWords(sample)
        val runId = db.wordDao().getByText("run")!!.wordId
        val senses = db.wordDao().getSensesForWord(runId)
        assertThat(senses.map { it.pos }).containsExactly("vi.", "vt.")
    }

    @Test
    fun import_isIdempotent_noDuplicateRows() = runTest {
        importer.importWords(sample)
        importer.importWords(sample)

        val books = db.bookDao().observeAll().first()
        assertThat(books).hasSize(2)
        assertThat(books.map { it.name }).containsExactly("CET-4", "CET-6")

        val cet4 = books.first { it.name == "CET-4" }
        val cet6 = books.first { it.name == "CET-6" }
        assertThat(db.bookDao().countWordsInBook(cet4.bookId)).isEqualTo(2)
        assertThat(db.bookDao().countWordsInBook(cet6.bookId)).isEqualTo(2)

        val apple = db.wordDao().getByText("apple")
        val run = db.wordDao().getByText("run")
        val balance = db.wordDao().getByText("balance")
        assertThat(listOf(apple, run, balance).filterNotNull()).hasSize(3)

        val runSenses = db.wordDao().getSensesForWord(run!!.wordId)
        assertThat(runSenses).hasSize(2)
    }

    @Test
    fun import_queryByBookReturnsWordsWithSenses() = runTest {
        importer.importWords(sample)
        val cet4 = db.bookDao().observeAll().first().first { it.name == "CET-4" }
        val words = db.wordDao().getWordsWithSensesByBook(cet4.bookId)
        assertThat(words.map { it.word.text }).containsExactly("apple", "run")
        assertThat(words.first { it.word.text == "apple" }.senses).hasSize(1)
        assertThat(words.first { it.word.text == "run" }.senses).hasSize(2)
    }

    @Test
    fun import_writesPhonetic_whenPresent() = runTest {
        importer.importWords(sample)
        val apple = db.wordDao().getByText("apple")
        assertThat(apple!!.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun import_phoneticIsNull_whenAbsent() = runTest {
        importer.importWords(sample)
        val run = db.wordDao().getByText("run")
        assertThat(run!!.phonetic).isNull()
    }

    @Test
    fun import_getByIdReturnsPhonetic() = runTest {
        importer.importWords(sample)
        val apple = db.wordDao().getByText("apple")!!
        val byId = db.wordDao().getById(apple.wordId)!!
        assertThat(byId.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun import_phoneticSurvivesIdempotentReimport() = runTest {
        importer.importWords(sample)
        importer.importWords(sample)
        val apple = db.wordDao().getByText("apple")
        assertThat(apple!!.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun import_phoneticIsNull_notStringNull_whenJsonNull() = runTest {
        // Real-device regression: Android's bundled org.json optString returns the string
        // "null" for a JSON null (unlike the org.json:json artifact used in JVM unit tests),
        // which once stored the "null" string in the database.
        val json = """
            {"source":"x","books":[{"name":"b","words":[
              {"text":"ghost","phonetic":null,"senses":[{"pos":"n.","meaning":"鬼"}]}
            ]}]}
        """.trimIndent()
        importer.importWords(PresetWordsParser.parse(json))
        val ghost = db.wordDao().getByText("ghost")
        assertThat(ghost!!.phonetic).isNull()
    }
}
