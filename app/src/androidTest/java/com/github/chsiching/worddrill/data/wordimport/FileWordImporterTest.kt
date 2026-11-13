package com.github.chsiching.worddrill.data.wordimport

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.DictionaryImporter
import com.github.chsiching.worddrill.data.DictionaryWords
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Ticket #21: end-to-end test for [FileWordImporter] (in-memory Room + a real ContentResolver file).
 *
 * JVM unit tests ([PosMeaningParserTest] / [TextTableParserTest] / [XlsxParserTest]) already cover the main seam;
 * this test verifies the parse, dictionary-lookup, and database-write orchestration (the 3 steps of the word-handling logic in issue #21).
 *
 * Coverage:
 * - If the word is in the dictionary, the test expects the dictionary's pos, meaning, and phonetic
 * - If the word is not in the dictionary, the test expects the file's col3 and col4
 * - If col3 and col4 are both empty, the test expects the word to be skipped
 * - Silent dedup: a word repeated across books reuses the same row
 * - The completion summary is correct
 *
 * PDF is not covered (needs PdfBox-Android native plus a PDF test fixture); we verified the PDF end-to-end path by hand with MCP.
 */
@RunWith(AndroidJUnit4::class)
class FileWordImporterTest {

    private lateinit var db: WordDrillDatabase
    private lateinit var importer: FileWordImporter
    private lateinit var dictImporter: DictionaryImporter
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = FileWordImporter(context, db, db.wordDao(), db.bookDao(), db.dictionaryDao())
        dictImporter = DictionaryImporter(db, db.dictionaryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- Dictionary first (issue #21 word-handling logic step 1) ----

    @Test
    fun dictHit_usesDictPosAndMeaningAndPhonetic() = runTest {
        dictImporter.importDictionary(
            DictionaryWords(
                source = "test",
                words = listOf(
                    DictionaryWords.Entry(word = "apple", phonetic = "/ˈæpl/", pos = "n.", meaning = "苹果（词典）"),
                    DictionaryWords.Entry(word = "apple", phonetic = "/ˈæpl/", pos = "v.", meaning = "堆叠（词典）"),
                ),
            )
        )

        // File col3/4 differ from the dictionary; the dictionary should win
        val file = txtFile("1,apple,/file_phonetic/,n.水果\n")
        val summary = importer.import(uriOf(file), bookName = "我的词书", filenameDecoded = "test.csv")

        assertThat(summary).isEqualTo(ImportSummary(success = 1, skipped = 0))
        val word = db.wordDao().getByText("apple")!!
        // phonetic comes from the dictionary (not the file's /file_phonetic/)
        assertThat(word.phonetic).isEqualTo("/ˈæpl/")
        // senses come from the dictionary (not the file's fruit sense)
        val senses = db.wordDao().getSensesForWord(word.wordId).map { it.pos to it.meaning }
        assertThat(senses).containsExactly(
            "n." to "苹果（词典）",
            "v." to "堆叠（词典）",
        )
    }

    // ---- File fallback (issue #21 word-handling logic step 2) ----

    @Test
    fun dictMiss_usesFileCol3Col4() = runTest {
        // "unusualsexample" is not in the dictionary; the file should serve as the fallback
        val file = txtFile("1,unusualsexample,/uf/,vt.做 vi.行动\n")
        val summary = importer.import(uriOf(file), bookName = "我的", filenameDecoded = "test.csv")

        assertThat(summary.success).isEqualTo(1)
        val word = db.wordDao().getByText("unusualsexample")!!
        assertThat(word.phonetic).isEqualTo("/uf/")
        val senses = db.wordDao().getSensesForWord(word.wordId).map { it.pos to it.meaning }
        assertThat(senses).containsExactly(
            "vt." to "做",
            "vi." to "行动",
        )
    }

    @Test
    fun dictMiss_fileOnlyPhonetic_posDefaultedToNoun_succeedsWithNoSenses() = runTest {
        // col3 has a phonetic and col4 is empty, so senses are empty, but col3+4 are not both empty, so it counts as a valid import (keep the phonetic;
        // the user can fill in the meaning later). The card front has the word + phonetic to work with.
        val file = txtFile("1,lonelyword,/lp/,\n")
        val summary = importer.import(uriOf(file), bookName = "我的", filenameDecoded = "test.csv")

        assertThat(summary).isEqualTo(ImportSummary(success = 1, skipped = 0))
        val word = db.wordDao().getByText("lonelyword")!!
        assertThat(word.phonetic).isEqualTo("/lp/")
        assertThat(db.wordDao().getSensesForWord(word.wordId)).isEmpty()
    }

    // ---- Both empty, skipped (issue #21 word-handling logic step 3) ----

    @Test
    fun col3And4Empty_skippedAsIncomplete() = runTest {
        val file = txtFile("1,lonelyword,,\n")
        val summary = importer.import(uriOf(file), bookName = "我的", filenameDecoded = "test.csv")

        assertThat(summary).isEqualTo(ImportSummary(success = 0, skipped = 1))
        assertThat(db.wordDao().getByText("lonelyword")).isNull()
    }

    @Test
    fun blankWordLine_skipped() = runTest {
        // Rows with an empty word column (col1): the parser already skips them; the importer must not count them in the summary
        val file = txtFile("1,,,n.空单词\n2,apple,,n.苹果\n")
        val summary = importer.import(uriOf(file), bookName = "我的", filenameDecoded = "test.csv")

        // TextTableParser skipped the row with the empty word column; the importer only sees the apple row
        assertThat(summary).isEqualTo(ImportSummary(success = 1, skipped = 0))
    }

    // ---- Silent dedup (issue #21: dedup without prompting) ----

    @Test
    fun duplicateWordAcrossBooks_sharesGlobalWordRow_silentDedup() = runTest {
        dictImporter.importDictionary(
            DictionaryWords(
                source = "test",
                words = listOf(DictionaryWords.Entry(word = "apple", phonetic = "/p/", pos = "n.", meaning = "苹果")),
            )
        )
        val file = txtFile("1,apple,,n.苹果\n")

        importer.import(uriOf(file), bookName = "词书1", filenameDecoded = "a.csv")
        importer.import(uriOf(file), bookName = "词书2", filenameDecoded = "b.csv")

        // Only one apple row in the word table (the global unique index as the fallback)
        val appleRows = db.wordDao().getAll().filter { it.text == "apple" }
        assertThat(appleRows).hasSize(1)
        // Both books link the same wordId
        val book1 = db.bookDao().getByName("词书1")!!
        val book2 = db.bookDao().getByName("词书2")!!
        val book1Words = db.wordDao().getWordsWithSensesByBook(book1.bookId).map { it.word.wordId }
        val book2Words = db.wordDao().getWordsWithSensesByBook(book2.bookId).map { it.word.wordId }
        assertThat(book1Words).hasSize(1)
        assertThat(book2Words).hasSize(1)
        assertThat(book1Words.single()).isEqualTo(book2Words.single())
    }

    // ---- Completion summary correct (issue #21 completion prompt) ----

    @Test
    fun summary_countsSuccessAndSkippedCorrectly() = runTest {
        dictImporter.importDictionary(
            DictionaryWords(
                source = "test",
                words = listOf(DictionaryWords.Entry(word = "apple", phonetic = "/p/", pos = "n.", meaning = "苹果")),
            )
        )
        // Rows: apple (dict hit) / missword (col3/4 fallback) / emptyword (both empty, skipped) / banana (col3/4 fallback)
        val file = txtFile(
            """
            1,apple,,n.苹果
            2,missword,/mp/,vt.做
            3,emptyword,,
            4,banana,/bp/,n.香蕉
            """.trimIndent() + "\n"
        )

        val summary = importer.import(uriOf(file), bookName = "测试", filenameDecoded = "test.csv")

        assertThat(summary).isEqualTo(ImportSummary(success = 3, skipped = 1))
        // Database check: all 3 valid words made it into the book
        val book = db.bookDao().getByName("测试")!!
        assertThat(db.bookDao().countWordsInBook(book.bookId)).isEqualTo(3)
    }

    // ---- Book creation ----

    @Test
    fun createsCustomBook_notPreset() = runTest {
        val file = txtFile("1,apple,,n.苹果\n")
        importer.import(uriOf(file), bookName = "新词书", filenameDecoded = "test.csv")

        val book = db.bookDao().getByName("新词书")!!
        assertThat(book.isPreset).isFalse()
    }

    // ---- xlsx format routing ----

    @Test
    fun xlsxFormat_routedToXlsxParser() = runTest {
        val xlsxBytes = buildMinimalXlsx(
            sharedStrings = listOf("1", "ghost", "/g/", "n.鬼"),
            rows = listOf(
                """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>""",
            ),
        )
        val file = File(context.cacheDir, "test.xlsx").apply {
            writeBytes(xlsxBytes)
        }
        val summary = importer.import(uriOf(file), bookName = "xlsx词书", filenameDecoded = "test.xlsx")

        // ghost is not in the dictionary; the col3+4 fallback applies
        assertThat(summary).isEqualTo(ImportSummary(success = 1, skipped = 0))
        val ghost = db.wordDao().getByText("ghost")!!
        assertThat(ghost.phonetic).isEqualTo("/g/")
        assertThat(db.wordDao().getSensesForWord(ghost.wordId).map { it.pos to it.meaning })
            .containsExactly("n." to "鬼")
    }

    // ---- helpers ----

    private fun txtFile(content: String): File =
        File(context.cacheDir, "test.csv").apply { writeText(content, Charsets.UTF_8) }

    private fun uriOf(file: File): Uri = Uri.fromFile(file)

    /** Builds a minimal .xlsx (same as XlsxParserTest; duplicated here to avoid referencing JVM test code across source sets). */
    private fun buildMinimalXlsx(sharedStrings: List<String>, rows: List<String>): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("xl/sharedStrings.xml"))
            val sstBody = if (sharedStrings.isEmpty()) "" else sharedStrings.joinToString("") { "<si><t>${escape(it)}</t></si>" }
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$sstBody</sst>"""
                    .toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetBody = if (rows.isEmpty()) "" else "<sheetData>${rows.joinToString("")}</sheetData>"
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$sheetBody</worksheet>"""
                    .toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
