package com.github.chsiching.worddrill.data

import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ticket #20：预置「复习」词书的幂等创建。
 *
 * 跳过的词会汇入这本词书（[com.github.chsiching.worddrill.ui.drill.DrillViewModel.skipWord]
 * 把当前 book_word.skipped 置 1 + 把该 word 挂到复习词书）。复习词书与 CET-4/CET-6 一样
 * 是预置词书（isPreset=true，不可重命名/删除），但默认空，由用户跳过行为填充。
 *
 * 幂等：DataStore 标记 `review_book_created` 保证只建一次；BookDao.getByName 兜底，
 * 即便标记缺失（重装/清缓存）也不会建出两本同名词书。与 [PresetImportOrchestrator]
 * 同模式：标记检查 + 数据层去重双保险。
 */
@Singleton
class ReviewBookInitializer @Inject constructor(
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) {
    /** 「复习」词书名。预置词书不可重命名，name 稳定可作幂等键。 */
    // 与 CET-4 / CET-6 / 考研 来自 assets 数据一致：词书名是数据值，不走 R.string。
    private val reviewBookName = "复习"

    /** 若复习词书尚未创建，则建一本空的预置词书；已建则跳过。 */
    suspend fun ensureReviewBookCreated() {
        if (settings.reviewBookCreated.first()) return
        if (bookDao.getByName(reviewBookName) == null) {
            bookDao.insert(Book(name = reviewBookName, isPreset = true))
        }
        settings.markReviewBookCreated()
    }
}
