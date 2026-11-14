package com.github.chsiching.worddrill.data.wordimport

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class FileNotOpenableException(val uri: String) : Exception("file not openable: $uri")

@Singleton
class FileWordImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
    private val dictionaryDao: DictionaryDao,
) {

    /**
     * @param filenameDecoded Display name for [uri], queried by the caller (SAF does not expose
     *   the file name on all Android versions); used to detect the format by extension.
     */
    suspend fun import(uri: Uri, bookName: String, filenameDecoded: String): ImportSummary =
        withContext(Dispatchers.IO) {
            val format = ImportFormat.byExtension(filenameDecoded)
            val rows = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (format) {
                    ImportFormat.XLSX -> XlsxParser.parse(stream)
                    ImportFormat.TXT -> TextTableParser.parse(stream, delimiter = null)
                    ImportFormat.CSV -> TextTableParser.parse(stream, delimiter = ',')
                    ImportFormat.PDF -> PdfTableParser.parse(stream)
                }
            } ?: throw FileNotOpenableException(uri.toString())

            db.withTransaction {
                val bookId = bookDao.insert(Book(name = bookName.trim(), isPreset = false))
                var success = 0
                var skipped = 0
                for (row in rows) {
                    if (row.word.isBlank()) {
                        skipped++
                        continue
                    }
                    val dictEntries = dictionaryDao.findByWord(row.word.lowercase())
                    if (dictEntries.isNotEmpty()) {
                        val phonetic = dictEntries.first().phonetic
                        val wordId = upsertWord(row.word, phonetic)
                        for (entry in dictEntries) {
                            wordDao.insertSense(Sense(wordId = wordId, pos = entry.pos, meaning = entry.meaning))
                        }
                        bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
                        success++
                        continue
                    }
                    val phonetic = row.phonetic?.takeIf { it.isNotBlank() }
                    val senses = PosMeaningParser.parse(row.posMeaning ?: "")
                    if (phonetic == null && senses.isEmpty()) {
                        skipped++
                        continue
                    }
                    val wordId = upsertWord(row.word, phonetic)
                    for ((pos, meaning) in senses) {
                        wordDao.insertSense(Sense(wordId = wordId, pos = pos, meaning = meaning))
                    }
                    bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
                    success++
                }
                ImportSummary(success = success, skipped = skipped)
            }
        }

    private suspend fun upsertWord(text: String, phonetic: String?): Long =
        wordDao.findIdByText(text)
            ?: wordDao.insert(Word(text = text, phonetic = phonetic)).let { id ->
                if (id == -1L) wordDao.findIdByText(text)!! else id
            }
}
