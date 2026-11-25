package com.github.chsiching.worddrill.ui.me

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.PresetImportOrchestrator
import com.github.chsiching.worddrill.data.PresetImporter
import com.github.chsiching.worddrill.data.PresetWordsParser
import com.github.chsiching.worddrill.data.backup.BackupService
import com.github.chsiching.worddrill.data.backup.DatabaseJsonSerializer
import com.github.chsiching.worddrill.data.backup.DatabaseSnapshot
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportImportViewModelTest {

    private lateinit var db: WordDrillDatabase
    private lateinit var context: android.content.Context
    private lateinit var orchestrator: PresetImportOrchestrator
    private val store = ViewModelStore()
    private val backupFiles = mutableListOf<File>()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        orchestrator = PresetImportOrchestrator(
            context, SettingsRepository(context), PresetImporter(db, db.wordDao(), db.bookDao()),
        )
        Unit
    }

    @After
    fun tearDown() {
        store.clear()
        db.close()
        backupFiles.forEach { it.delete() }
    }

    @Test
    fun import_backupFromAnOldVersion_examBooksTakeTheBundledContent_userDataKept() = runBlocking {
        // The shape of a backup written by 0.2.1: the exam books hold that version's
        // word list and definitions. The user skipped "a" in CET-4 and swiped it once,
        // and has a book of their own.
        val snapshot = DatabaseSnapshot(
            nickname = null,
            books = listOf(
                Book(bookId = 1, name = REVIEW_BOOK_NAME, isPreset = true),
                Book(bookId = 2, name = "CET-4", isPreset = true),
                Book(bookId = 3, name = "CET-6", isPreset = true),
                Book(bookId = 4, name = "考研英语", isPreset = true),
                Book(bookId = 5, name = "我的生词本", isPreset = false, completionCount = 3),
            ),
            words = listOf(
                Word(wordId = 1, text = "a", phonetic = "/ei/"),
                Word(wordId = 2, text = "Africa", phonetic = "/ˈæfrikə/"),
                Word(wordId = 3, text = "myword"),
            ),
            senses = listOf(
                Sense(senseId = 1, wordId = 1, pos = "art.", meaning = "一；任一"),
                Sense(senseId = 2, wordId = 2, pos = "n.", meaning = "非洲"),
                Sense(senseId = 3, wordId = 3, pos = "n.", meaning = "我的词"),
            ),
            bookWords = listOf(
                BookWord(bookId = 2, wordId = 1, skipped = true),
                BookWord(bookId = 2, wordId = 2),
                BookWord(bookId = 1, wordId = 1),
                BookWord(bookId = 5, wordId = 3),
            ),
            swipeLogs = listOf(SwipeLog(logId = 1, bookId = 2, wordId = 1, timestamp = 1L)),
        )
        val viewModel = newViewModel()
        viewModel.importFrom(writeBackup(snapshot))

        assertThat(viewModel.status.first { it is ExportImportStatus.Done || it is ExportImportStatus.Failed })
            .isEqualTo(ExportImportStatus.Done(R.string.me_import_done))

        val cet4Spec = bundled().books.single { it.name == "CET-4" }
        val cet4 = db.bookDao().getByName("CET-4")!!
        assertThat(cet4.bookId).isEqualTo(2L)
        assertThat(db.bookDao().getWordIdsInBook(cet4.bookId)).hasSize(cet4Spec.words.size)
        // "a" is in both word lists. Its row is kept, so the skip flag, the review link
        // and the swipe log still point at it, and its content follows the bundled data.
        val aSpec = cet4Spec.words.single { it.text == "a" }
        val a = db.wordDao().getByText("a")!!
        assertThat(a.wordId).isEqualTo(1L)
        assertThat(a.phonetic).isEqualTo(aSpec.phonetic)
        assertThat(db.wordDao().getSensesForWord(a.wordId).associate { it.pos to it.meaning })
            .isEqualTo(aSpec.senses.associate { it.pos to it.meaning })
        assertThat(db.bookDao().getSkipped(cet4.bookId, a.wordId)).isTrue()
        assertThat(db.bookDao().getWordIdsInBook(1L)).containsExactly(a.wordId)
        // Africa left CET-4's word list. Its link goes, its row stays for the statistics.
        assertThat(db.bookDao().getWordIdsInBook(cet4.bookId)).doesNotContain(2L)
        assertThat(db.wordDao().getByText("Africa")).isNotNull()
        // The user's own data is as restored.
        assertThat(db.bookDao().getByName("我的生词本")!!.completionCount).isEqualTo(3)
        assertThat(db.wordDao().getSensesForWord(3).map { it.meaning }).containsExactly("我的词")
        assertThat(db.bookDao().getWordIdsInBook(5L)).containsExactly(3L)
        assertThat(db.swipeLogDao().getAll()).hasSize(1)
    }

    @Test
    fun import_ownBackup_restoresEverythingUnchanged() = runBlocking {
        // A fresh install's exam books and review book, plus the user's own data.
        orchestrator.refreshPresets()
        val reviewBook = db.bookDao().insert(Book(name = REVIEW_BOOK_NAME, isPreset = true))
        val userBook = db.bookDao().insert(Book(name = "我的生词本", isPreset = false))
        val myword = db.wordDao().insert(Word(text = "myword", phonetic = null))
        db.wordDao().insertSense(Sense(wordId = myword, pos = "n.", meaning = "我的词"))
        db.bookDao().linkBookWord(BookWord(bookId = userBook, wordId = myword))
        val a = db.wordDao().getByText("a")!!
        val cet4 = db.bookDao().getByName("CET-4")!!
        db.bookDao().setSkipped(cet4.bookId, a.wordId, true)
        db.bookDao().linkBookWord(BookWord(bookId = reviewBook, wordId = a.wordId))
        db.bookDao().incrementCompletionCount(userBook)
        db.swipeLogDao().insert(SwipeLog(bookId = cet4.bookId, wordId = a.wordId, timestamp = 42L))
        val service = BackupService(db, db.bookDao(), db.wordDao(), db.swipeLogDao())
        val before = service.export(nickname = null)

        val viewModel = newViewModel()
        viewModel.importFrom(writeBackup(before))

        assertThat(viewModel.status.first { it is ExportImportStatus.Done || it is ExportImportStatus.Failed })
            .isEqualTo(ExportImportStatus.Done(R.string.me_import_done))
        assertThat(service.export(nickname = null)).isEqualTo(before)
    }

    private fun newViewModel(): ExportImportViewModel {
        val app = context.applicationContext as Application
        val viewModel = ExportImportViewModel(
            app, BackupService(db, db.bookDao(), db.wordDao(), db.swipeLogDao()), orchestrator,
        )
        store.put("exportImport", viewModel)
        return viewModel
    }

    /** The bundled words.json, parsed the way the app parses it. */
    private fun bundled() = context.assets.open("words.json").use {
        PresetWordsParser.parse(it.readBytes().toString(Charsets.UTF_8))
    }

    /** Writes a backup file where the app can read it and returns its Uri. */
    private fun writeBackup(snapshot: DatabaseSnapshot): Uri {
        val file = File.createTempFile("backup", ".json", context.cacheDir)
        file.writeText(DatabaseJsonSerializer.serialize(snapshot))
        backupFiles.add(file)
        return Uri.fromFile(file)
    }
}
