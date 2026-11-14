package com.github.chsiching.worddrill.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewBookInitializerTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var initializer: ReviewBookInitializer

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        initializer = ReviewBookInitializer(db.bookDao(), settings)
        settings.clearReviewBookCreated()
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        settings.clearReviewBookCreated()
        Unit
    }

    @Test
    fun ensureReviewBookCreated_createsPresetReviewBook() = runBlocking {
        initializer.ensureReviewBookCreated()

        val review = db.bookDao().getByName("复习")
        assertThat(review).isNotNull()
        assertThat(review!!.isPreset).isTrue()
        assertThat(db.bookDao().countWordsInBook(review.bookId)).isEqualTo(0)
        Unit
    }

    @Test
    fun ensureReviewBookCreated_isIdempotent_acrossCalls() = runBlocking {
        initializer.ensureReviewBookCreated()
        initializer.ensureReviewBookCreated()

        val reviews = db.bookDao().observeAll().first().filter { it.name == "复习" }
        assertThat(reviews).hasSize(1)
        Unit
    }

    @Test
    fun ensureReviewBookCreated_appearsInBookList() = runBlocking {
        db.bookDao().insert(com.github.chsiching.worddrill.data.local.entity.Book(name = "CET-4", isPreset = true))
        initializer.ensureReviewBookCreated()

        val names = db.bookDao().observeAll().first().map { it.name }
        assertThat(names).contains("复习")
        Unit
    }
}
