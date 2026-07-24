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

/**
 * Ticket #20：[ReviewBookInitializer] 的幂等创建测试（内存 Room + 真 DataStore）。
 *
 * 验收覆盖：
 * - 首次调用建一本预置「复习」词书（空）
 * - 第二次调用幂等跳过（不建第二本）
 * - 复习词书出现在词书列表里，用户能选来刷
 *
 * 注意：DataStore 文件在测试间持久（同 [com.github.chsiching.worddrill.data.settings.SettingsRepositoryThemeTest]）。
 * setUp 清掉 review_book_created 标记，保证每个测试从「未创建」干净起点开始；
 * 内存 Room 库天然每次新建为空，无需清。
 */
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
        // 清掉上轮测试写入的 review_book_created 标记，保证干净起点
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
        // 默认空（无词条）
        assertThat(db.bookDao().countWordsInBook(review.bookId)).isEqualTo(0)
        Unit
    }

    @Test
    fun ensureReviewBookCreated_isIdempotent_acrossCalls() = runBlocking {
        initializer.ensureReviewBookCreated()
        initializer.ensureReviewBookCreated() // 第二次

        // 只有一本「复习」词书
        val reviews = db.bookDao().observeAll().first().filter { it.name == "复习" }
        assertThat(reviews).hasSize(1)
        Unit
    }

    @Test
    fun ensureReviewBookCreated_appearsInBookList() = runBlocking {
        // 复习词书出现在词书列表里，用户能选来刷
        db.bookDao().insert(com.github.chsiching.worddrill.data.local.entity.Book(name = "CET-4", isPreset = true))
        initializer.ensureReviewBookCreated()

        val names = db.bookDao().observeAll().first().map { it.name }
        assertThat(names).contains("复习")
        Unit
    }
}
