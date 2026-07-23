package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word

/**
 * 整库快照（导出/导入的内存模型）。
 *
 * 导入策略为**覆盖**（Ticket #10 默认）：按原始主键恢复，整库清空再批量重插。
 * 这样跨表的外键引用关系（book_word / sense → word，swipe_log.bookId → book）完整保留，
 * 尤其是孤立的 swipe_log（其 bookId/wordId 可能引用已删除的词书/词条，仍需保留用于累计统计）。
 *
 * 字段对齐 Room 表，无 Android 依赖，便于在 JVM 单测里构造并验证 JSON 往返。
 *
 * @param version JSON schema 版本号；当前为 1。向后兼容：缺省时默认 1。
 * @param nickname 可选昵称标签。导出文件的用户可见标识，非全局设置；空白视为 null（不写入）。
 */
data class DatabaseSnapshot(
    val version: Int = 1,
    val nickname: String?,
    val books: List<Book> = emptyList(),
    val words: List<Word> = emptyList(),
    val senses: List<Sense> = emptyList(),
    val bookWords: List<BookWord> = emptyList(),
    val swipeLogs: List<SwipeLog> = emptyList(),
)
