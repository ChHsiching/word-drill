package com.github.chsiching.worddrill.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.DictionaryImporter
import com.github.chsiching.worddrill.data.DictionaryWords
import com.github.chsiching.worddrill.data.DictionaryWordsParser
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryDaoTest {

    private lateinit var db: WordDrillDatabase
    private lateinit var dao: DictionaryDao
    private lateinit var importer: DictionaryImporter

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dictionaryDao()
        importer = DictionaryImporter(db, dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun count_startsAtZero() = runTest {
        assertThat(dao.count()).isEqualTo(0)
    }

    @Test
    fun insertAll_persistsEntries() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntry(word = "apple", phonetic = "/ˈæpl/", pos = "n.", meaning = "苹果"),
                DictionaryEntry(word = "run", pos = "v.", meaning = "跑"),
            )
        )
        assertThat(dao.count()).isEqualTo(2)
    }

    @Test
    fun findByWord_returnsEmptyForAbsent() = runTest {
        assertThat(dao.findByWord("ghost")).isEmpty()
    }

    @Test
    fun findByWord_returnsMatchingEntries() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntry(word = "light", phonetic = "/laɪt/", pos = "n.", meaning = "光"),
                DictionaryEntry(word = "light", phonetic = "/laɪt/", pos = "adj.", meaning = "轻的"),
                DictionaryEntry(word = "light", phonetic = "/laɪt/", pos = "v.", meaning = "点燃"),
            )
        )
        val result = dao.findByWord("light")
        assertThat(result).hasSize(3)
        assertThat(result.map { it.pos }).containsExactly("n.", "adj.", "v.")
    }

    @Test
    fun findByWord_isCaseSensitive() = runTest {
        dao.insertAll(
            listOf(DictionaryEntry(word = "apple", pos = "n.", meaning = "苹果"))
        )
        assertThat(dao.findByWord("apple")).hasSize(1)
        assertThat(dao.findByWord("Apple")).isEmpty()
    }

    @Test
    fun findByWord_carriesPhoneticAndMeaning() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntry(word = "apple", phonetic = "/ˈæpl/", pos = "n.", meaning = "苹果")
            )
        )
        val e = dao.findByWord("apple").single()
        assertThat(e.phonetic).isEqualTo("/ˈæpl/")
        assertThat(e.meaning).isEqualTo("苹果")
    }

    @Test
    fun findByWord_phoneticIsNull_whenAbsent() = runTest {
        dao.insertAll(
            listOf(DictionaryEntry(word = "run", pos = "v.", meaning = "跑"))
        )
        assertThat(dao.findByWord("run").single().phonetic).isNull()
    }

    @Test
    fun insertAll_ignoresDuplicateWordPos() = runTest {
        val e1 = DictionaryEntry(word = "apple", phonetic = "/p1/", pos = "n.", meaning = "苹果")
        val e2 = DictionaryEntry(word = "apple", phonetic = "/p2/", pos = "n.", meaning = "苹果（重复）")
        dao.insertAll(listOf(e1, e2))
        assertThat(dao.count()).isEqualTo(1)
        assertThat(dao.findByWord("apple").single().phonetic).isEqualTo("/p1/")
    }

    @Test
    fun insertAll_allowsSameWordDifferentPos() = runTest {
        dao.insertAll(
            listOf(
                DictionaryEntry(word = "match", pos = "n.", meaning = "比赛"),
                DictionaryEntry(word = "match", pos = "v.", meaning = "匹配"),
            )
        )
        assertThat(dao.findByWord("match")).hasSize(2)
    }

    @Test
    fun importer_insertsAllEntries() = runTest {
        val data = sampleDictionary()
        val inserted = importer.importDictionary(data)
        assertThat(inserted).isEqualTo(data.words.size)
        assertThat(dao.count()).isEqualTo(data.words.size)
    }

    @Test
    fun importer_queryByWordReturnsAllPos() = runTest {
        importer.importDictionary(sampleDictionary())
        val result = dao.findByWord("apple")
        assertThat(result.map { it.pos }).containsExactly("n.", "v.")
    }

    @Test
    fun importer_isIdempotent_noDuplicate() = runTest {
        val data = sampleDictionary()
        importer.importDictionary(data)
        val secondRun = importer.importDictionary(data)
        assertThat(secondRun).isEqualTo(0)
        assertThat(dao.count()).isEqualTo(data.words.size)
    }

    @Test
    fun importer_phoneticIsNull_notStringNull_whenJsonNull() = runTest {
        val json = """
            {"source":"x","words":[
              {"word":"ghost","phonetic":null,"pos":"n.","meaning":"鬼"}
            ]}
        """.trimIndent()
        importer.importDictionary(DictionaryWordsParser.parse(json))
        val ghost = dao.findByWord("ghost").single()
        assertThat(ghost.phonetic).isNull()
    }

    private fun sampleDictionary(): DictionaryWords = DictionaryWords(
        source = "test",
        words = listOf(
            DictionaryWords.Entry(word = "apple", phonetic = "/ˈæpl/", pos = "n.", meaning = "苹果"),
            DictionaryWords.Entry(word = "apple", phonetic = "/ˈæpl/", pos = "v.", meaning = "..."),
            DictionaryWords.Entry(word = "run", pos = "v.", meaning = "跑"),
        ),
    )
}
