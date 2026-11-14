package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word

data class DatabaseSnapshot(
    val version: Int = 1,
    val nickname: String?,
    val books: List<Book> = emptyList(),
    val words: List<Word> = emptyList(),
    val senses: List<Sense> = emptyList(),
    val bookWords: List<BookWord> = emptyList(),
    val swipeLogs: List<SwipeLog> = emptyList(),
)
