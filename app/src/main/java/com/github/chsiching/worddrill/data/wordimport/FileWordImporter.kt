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

/** SAF cannot open the given Uri (permission revoked / file deleted, etc.). The VM layer converts it to a user-visible message. */
class FileNotOpenableException(val uri: String) : Exception("file not openable: $uri")

/**
 * Ticket #21: file-to-book import orchestrator (@Singleton).
 *
 * Flow (issue #21):
 * 1. The user picks a file (SAF; the ViewModel passes the Uri) and enters a new book name, then calls [import]
 * 2. Detect the format from the Uri suffix ([ImportFormat.byExtension]), then route to the matching parser
 * 3. Parse into an [ImportRow] list
 * 4. Process word by word:
 *    - **Dictionary lookup first** (issue #21 word processing step 1): on a hit, use the dictionary's pos, meaning, and phonetic
 *    - **No dictionary entry** means the file's col3 phonetic and col4 pos/meaning are used ([PosMeaningParser]
 *      parses col4; the issue uses 1-based column numbers: col1=index / col2=word / col3=phonetic / col4=pos+meaning)
 *    - **col3+4 also empty** means the row is skipped, counted as "incomplete data" ([ImportSummary.skipped])
 * 5. Silent dedupe: word table `text` unique index (IGNORE fallback) + sense `(wordId,pos)` unique
 *    index + book_word `(bookId,wordId)` composite primary key. Duplicate words across files /
 *    books automatically reuse the same word row; the issue explicitly says "dedupe without prompting".
 * 6. Write the word pool and book_word associations in one Room transaction
 *    ([androidx.room.withTransaction]), so it is atomic; a failure rolls back, leaving no half-imported state.
 * 7. Return [ImportSummary] (success X / skipped Y).
 *
 * Same pattern as [com.github.chsiching.worddrill.data.PresetImporter]: upsert word (reuse or
 * create) + insertSense + linkBookWord. Difference: this class also queries the dictionary to
 * decide the data source.
 *
 * All I/O runs on [Dispatchers.IO]; the Context is only used to get a ContentResolver to open the Uri.
 */
@Singleton
class FileWordImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
    private val dictionaryDao: DictionaryDao,
) {

    /**
     * @param uri File Uri returned by SAF
     * @param bookName New book name (already validated: non-empty, no duplicate)
     * @param filenameDecoded File name for the Uri (used to detect the format by extension).
     *   SAF on some Android versions does not expose the file name directly; the caller should
     *   query DISPLAY_NAME from a Cursor and pass it in. This class does not query Uri metadata,
     *   keeping the single responsibility of "read the stream only".
     * @return Import completion statistics
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
                    // 1) Dictionary lookup first (by lowercase spelling; ECDICT's word column is mostly lowercase base forms)
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
                    // 2) No dictionary entry: use the file's col3 (phonetic) and col4 (pos/meaning, needs POS parsing)
                    val phonetic = row.phonetic?.takeIf { it.isNotBlank() }
                    val senses = PosMeaningParser.parse(row.posMeaning ?: "")
                    if (phonetic == null && senses.isEmpty()) {
                        // 3) All empty: skip, counted as incomplete data
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

    /**
     * Global word pool upsert: on a hit, reuse the wordId; on a miss, insert (IGNORE guards
     * against races, -1 triggers a follow-up lookup).
     * Same approach as [com.github.chsiching.worddrill.data.PresetImporter] / [WordListViewModel.submitAdd].
     */
    private suspend fun upsertWord(text: String, phonetic: String?): Long =
        wordDao.findIdByText(text)
            ?: wordDao.insert(Word(text = text, phonetic = phonetic)).let { id ->
                if (id == -1L) wordDao.findIdByText(text)!! else id
            }
}
