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
 * Ticket #21：[FileWordImporter] 端到端测试（内存 Room + 真实 ContentResolver 文件）。
 *
 * 主接缝已在 JVM 单测覆盖（[PosMeaningParserTest] / [TextTableParserTest] / [XlsxParserTest]），
 * 本测试验证"解析 → 词典查词 → 写库"的编排正确性（issue #21 单词处理逻辑 3 步）。
 *
 * 覆盖：
 * - 词典有词 → 用词典的 pos+meaning+phonetic
 * - 词典没词 → 用文件 col3+4
 * - col3+4 全空 → skipped
 * - 去重静默：跨词书重复 word 复用同一行
 * - 完成统计正确
 *
 * 不覆盖 PDF（需 PdfBox-Android native，且要构造 PDF 测试 fixture）：PDF 端到端走 MCP 手验。
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

    // ---- 词典优先（issue #21 单词处理逻辑 step 1）----

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

        // 文件 col3/4 与词典不同 —— 词典应胜出
        val file = txtFile("1,apple,/file_phonetic/,n.水果\n")
        val summary = importer.import(uriOf(file), bookName = "我的词书", filenameDecoded = "test.csv")

        assertThat(summary).isEqualTo(ImportSummary(success = 1, skipped = 0))
        val word = db.wordDao().getByText("apple")!!
        // phonetic 来自词典（不取文件 /file_phonetic/）
        assertThat(word.phonetic).isEqualTo("/ˈæpl/")
        // senses 来自词典（不取文件 n.水果）
        val senses = db.wordDao().getSensesForWord(word.wordId).map { it.pos to it.meaning }
        assertThat(senses).containsExactly(
            "n." to "苹果（词典）",
            "v." to "堆叠（词典）",
        )
    }

    // ---- 文件兜底（issue #21 单词处理逻辑 step 2）----

    @Test
    fun dictMiss_usesFileCol3Col4() = runTest {
        // 词典无 "unusualsexample"，文件应兜底
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
        // col3 有音标，col4 空 → senses 空，但 col3+4 并非全空 → 视为有效导入（保留音标，
        // 用户可后续手填释义）。卡片正面有单词 + 音标可用。
        val file = txtFile("1,lonelyword,/lp/,\n")
        val summary = importer.import(uriOf(file), bookName = "我的", filenameDecoded = "test.csv")

        assertThat(summary).isEqualTo(ImportSummary(success = 1, skipped = 0))
        val word = db.wordDao().getByText("lonelyword")!!
        assertThat(word.phonetic).isEqualTo("/lp/")
        assertThat(db.wordDao().getSensesForWord(word.wordId)).isEmpty()
    }

    // ---- 全空跳过（issue #21 单词处理逻辑 step 3）----

    @Test
    fun col3And4Empty_skippedAsIncomplete() = runTest {
        val file = txtFile("1,lonelyword,,\n")
        val summary = importer.import(uriOf(file), bookName = "我的", filenameDecoded = "test.csv")

        assertThat(summary).isEqualTo(ImportSummary(success = 0, skipped = 1))
        assertThat(db.wordDao().getByText("lonelyword")).isNull()
    }

    @Test
    fun blankWordLine_skipped() = runTest {
        // word 列（col1）空的行：解析器已跳过，importer 不应计入 summary
        val file = txtFile("1,,,n.空单词\n2,apple,,n.苹果\n")
        val summary = importer.import(uriOf(file), bookName = "我的", filenameDecoded = "test.csv")

        // TextTableParser 跳过了 word 列空的行，importer 只看到一行 apple
        assertThat(summary).isEqualTo(ImportSummary(success = 1, skipped = 0))
    }

    // ---- 去重静默（issue #21 去重不提示）----

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

        // word 表只一行 apple（全局唯一索引兜底）
        val appleRows = db.wordDao().getAll().filter { it.text == "apple" }
        assertThat(appleRows).hasSize(1)
        // 两本词书各自 link 同一 wordId
        val book1 = db.bookDao().getByName("词书1")!!
        val book2 = db.bookDao().getByName("词书2")!!
        val book1Words = db.wordDao().getWordsWithSensesByBook(book1.bookId).map { it.word.wordId }
        val book2Words = db.wordDao().getWordsWithSensesByBook(book2.bookId).map { it.word.wordId }
        assertThat(book1Words).hasSize(1)
        assertThat(book2Words).hasSize(1)
        assertThat(book1Words.single()).isEqualTo(book2Words.single())
    }

    // ---- 完成统计正确（issue #21 完成提示）----

    @Test
    fun summary_countsSuccessAndSkippedCorrectly() = runTest {
        dictImporter.importDictionary(
            DictionaryWords(
                source = "test",
                words = listOf(DictionaryWords.Entry(word = "apple", phonetic = "/p/", pos = "n.", meaning = "苹果")),
            )
        )
        // 行：apple（dict 命中） / missword（col3/4 兜底） / emptyword（全空跳过） / banana（col3/4 兜底）
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
        // 写库验证：3 个有效词都进了词书
        val book = db.bookDao().getByName("测试")!!
        assertThat(db.bookDao().countWordsInBook(book.bookId)).isEqualTo(3)
    }

    // ---- 词书创建 ----

    @Test
    fun createsCustomBook_notPreset() = runTest {
        val file = txtFile("1,apple,,n.苹果\n")
        importer.import(uriOf(file), bookName = "新词书", filenameDecoded = "test.csv")

        val book = db.bookDao().getByName("新词书")!!
        assertThat(book.isPreset).isFalse()
    }

    // ---- xlsx 格式路由 ----

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

        // ghost 不在词典，走 col3+4 兜底
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

    /** 构造最小 .xlsx（与 XlsxParserTest 同款，重复在这里避免 JVM 测试代码跨 source set 引用）。 */
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
