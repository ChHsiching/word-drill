package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrillViewModelTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private val viewModels = mutableListOf<DrillViewModel>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        Unit
    }

    @After
    fun tearDown() {
        // Each ViewModel's init collector stays subscribed to the shared DataStore for
        // as long as its scope lives; without cancelling it, a later DataStore write in
        // the same process would resume the collector against the closed database.
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
    }

    private fun newViewModel(): DrillViewModel =
        DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
        ).also { viewModels.add(it) }

    private suspend fun insertBookWithWord(name: String, isPreset: Boolean, wordText: String): Long {
        val bookId = db.bookDao().insert(Book(name = name, isPreset = isPreset))
        val wordId = db.wordDao().insert(Word(text = wordText))
        db.wordDao().insertSense(Sense(wordId = wordId, pos = "n.", meaning = "测试释义"))
        db.bookDao().linkBookWord(BookWord(bookId = bookId, wordId = wordId))
        return bookId
    }

    /**
     * Tests in this process share the app's DataStore, and a written current_book_id
     * cannot be removed, so "no recorded book" is emulated with an id that matches no
     * row: the loader falls through to default selection, the same path a fresh
     * install takes.
     */
    private suspend fun recordNoBook() {
        settings.setCurrentBookId(NO_BOOK_ID)
    }

    /** The state the loader settled on within [timeoutMs], or null while it is still waiting. */
    private suspend fun statePastLoadingOrNull(vm: DrillViewModel, timeoutMs: Long): DrillUiState? =
        withTimeoutOrNull(timeoutMs) { vm.uiState.first { it != DrillUiState.Loading } }

    @Test
    fun freshInstall_waitsForPresetBooks_thenSelectsFirstNonReview() = runBlocking {
        recordNoBook()
        val vm = newViewModel()

        // Issue #30: on an empty book table the loader must wait for the first-launch
        // import instead of settling on the empty state after one sample.
        assertThat(statePastLoadingOrNull(vm, 1_000)).isNull()

        // The review book is created early by its own initializer; it must not win
        // the default slot.
        db.bookDao().insert(Book(name = REVIEW_BOOK_NAME, isPreset = true))
        assertThat(statePastLoadingOrNull(vm, 1_000)).isNull()

        // Both presets in one transaction, like the real import: the library ordering
        // decides and CET-4 is first.
        val cet4 = db.withTransaction {
            db.bookDao().insert(Book(name = "CET-6", isPreset = true))
            insertBookWithWord(name = "CET-4", isPreset = true, wordText = "apple")
        }
        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.bookName).isEqualTo("CET-4")
        assertThat(ready.cards).hasSize(1)

        // The default book is recorded, so the next launch resolves it immediately.
        assertThat(settings.currentBookId.first { it == cet4 }).isEqualTo(cet4)
        Unit
    }

    @Test
    fun recordedCurrentBook_winsImmediately_overDefaultBook() = runBlocking {
        val mine = insertBookWithWord(name = "我的词书", isPreset = false, wordText = "apple")
        db.bookDao().insert(Book(name = "CET-4", isPreset = true))
        settings.setCurrentBookId(mine)
        val vm = newViewModel()

        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.bookName).isEqualTo("我的词书")
        assertThat(settings.currentBookId.first { it == mine }).isEqualTo(mine)
        Unit
    }

    private companion object {
        const val NO_BOOK_ID = 999_999L
    }
}
