package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preset word library import: writes a [PresetWords] into Room's global word entry pool.
 *
 * Design points (aligned with Ticket #3's schema constraints):
 * - [Word.text] is globally unique. A word appearing in multiple books is inserted once;
 *   later books reference it via linkBookWord.
 *   Here [WordDao.findIdByText] reuses the wordId on a hit; on a miss a new row is inserted.
 * - [Sense]'s (wordId, pos) is unique. Data preparation already guarantees one meaning per pos
 *   per word, so insertSense is called directly (IGNORE as a fallback so an accidental
 *   duplicate cannot fail the whole import).
 * - The whole import runs in one Room transaction ([androidx.room.withTransaction]), so it is
 *   atomic and readers see a self-consistent snapshot.
 *
 * This class only "writes the database"; it does not read assets / parse JSON, so JVM unit
 * tests can feed an in-memory Room and a constructed [PresetWords] to verify import
 * correctness and idempotency.
 */
@Singleton
class PresetImporter @Inject constructor(
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
) {
    /**
     * Write [preset] into the database. Returns the number of books imported.
     * Safe to call repeatedly: idempotent (deduplicated by unique indexes + findIdByText).
     */
    suspend fun importWords(preset: PresetWords): Int = db.withTransaction {
        var booksImported = 0
        for (bookSpec in preset.books) {
            // Book idempotency: reuse the existing bookId on a name match, so repeated imports
            // do not create duplicate book rows (BookDao.insert's REPLACE only applies to the
            // autoincrement primary key, so a duplicate-name fallback must dedupe explicitly).
            val bookId = bookDao.getByName(bookSpec.name)?.bookId
                ?: bookDao.insert(
                    com.github.chsiching.worddrill.data.local.entity.Book(
                        name = bookSpec.name, isPreset = true
                    )
                )
            booksImported++

            for (wordSpec in bookSpec.words) {
                // 1) Global word pool dedupe: reuse the wordId on a hit, create on a miss.
                // Ticket #14: on creation, carry the preset library's phonetic (IPA). Existing
                // rows are not overwritten (cross-book duplicates or repeated imports; the
                // phonetic is globally unique per text, no backfill needed).
                val wordId = wordDao.findIdByText(wordSpec.text)
                    ?: wordDao.insert(Word(text = wordSpec.text, phonetic = wordSpec.phonetic)).let { id ->
                        if (id == -1L) wordDao.findIdByText(wordSpec.text)!! else id
                    }

                // 2) Write senses (same-pos senses were already merged into one meaning; IGNORE as fallback)
                for (sense in wordSpec.senses) {
                    wordDao.insertSense(Sense(wordId = wordId, pos = sense.pos, meaning = sense.meaning))
                }

                // 3) Create the book-to-word reference (IGNORE guards against cross-book duplicates)
                bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
            }
        }
        booksImported
    }
}
