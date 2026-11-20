package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import javax.inject.Inject
import javax.inject.Singleton

/** SQLite's per-statement bind limit is 999 on the oldest supported devices, so bulk
 *  IN (:list) statements are chunked below this size. */
private const val SQL_BIND_CHUNK = 500

@Singleton
class PresetImporter @Inject constructor(
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
) {
    /**
     * Idempotent and safe over an existing install: books are updated in place (their
     * ids, link flags and completion counts are user data keyed by ids), word rows are
     * shared with user books (one row per spelling), and membership is reconciled —
     * words the new asset drops are unlinked from the preset book (their rows survive
     * for cumulative stats), words it adds are linked.
     */
    suspend fun importWords(preset: PresetWords): Int = db.withTransaction {
        // Word content is global (one row per spelling serves every book), so each
        // distinct word is reconciled once against the first book's spec for it; a
        // shared word whose books carry different sense lists keeps that first list
        // instead of the last book processed deleting the others' senses.
        val specsByText = LinkedHashMap<String, PresetWords.Word>()
        for (bookSpec in preset.books) {
            for (wordSpec in bookSpec.words) specsByText.putIfAbsent(wordSpec.text, wordSpec)
        }
        val wordIds = HashMap<String, Long>(specsByText.size)
        for (wordSpec in specsByText.values) {
            wordIds[wordSpec.text] = reconcileWord(wordSpec)
        }

        var booksImported = 0
        for (bookSpec in preset.books) {
            val existing = bookDao.getByName(bookSpec.name)
            val bookId = if (existing != null && existing.isPreset) {
                existing.bookId
            } else {
                bookDao.insert(Book(name = bookSpec.name, isPreset = true))
            }
            booksImported++

            val desiredIds = mutableSetOf<Long>()
            for (wordSpec in bookSpec.words) {
                val wordId = wordIds.getValue(wordSpec.text)
                // IGNORE keeps an existing link with its skipped/deleted flags untouched.
                bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
                desiredIds.add(wordId)
            }

            val removed = bookDao.getWordIdsInBook(bookId).filter { it !in desiredIds }
            if (removed.isNotEmpty()) {
                bookDao.unlinkWords(bookId, removed)
                // The review book only holds words skipped somewhere (GLOSSARY.md); a word
                // that left every book's membership must leave the review book too. Runs
                // after the unlinks so the NOT-skipped subquery sees the final state.
                bookDao.getByName(REVIEW_BOOK_NAME)?.let { review ->
                    removed.chunked(SQL_BIND_CHUNK).forEach {
                        bookDao.removeReviewLinksOfWords(review.bookId, it)
                    }
                }
            }
        }
        booksImported
    }

    /** Brings one word in line with the bundled asset. A word unknown to the database
     *  takes the plain insert path (fresh installs pay no per-sense reconciliation);
     *  an existing word has its phonetic updated and senses reconciled per pos — same
     *  pos updates in place, new pos inserts, stale pos deletes. */
    private suspend fun reconcileWord(wordSpec: PresetWords.Word): Long {
        val existing = wordDao.getByText(wordSpec.text)
        if (existing == null) {
            val wordId = wordDao.insert(Word(text = wordSpec.text, phonetic = wordSpec.phonetic))
            for (sense in wordSpec.senses) {
                wordDao.insertSense(Sense(wordId = wordId, pos = sense.pos, meaning = sense.meaning))
            }
            return wordId
        }
        if (existing.phonetic != wordSpec.phonetic) {
            wordDao.updatePhonetic(existing.wordId, wordSpec.phonetic)
        }
        val existingByPos = wordDao.getSensesForWord(existing.wordId).associateBy { it.pos }
        val desiredPos = mutableSetOf<String>()
        for (sense in wordSpec.senses) {
            desiredPos.add(sense.pos)
            val current = existingByPos[sense.pos]
            when {
                current == null -> wordDao.insertSense(
                    Sense(wordId = existing.wordId, pos = sense.pos, meaning = sense.meaning)
                )
                current.meaning != sense.meaning -> wordDao.updateSense(
                    current.senseId, sense.pos, sense.meaning
                )
            }
        }
        existingByPos.values
            .filter { it.pos !in desiredPos }
            .forEach { wordDao.deleteSense(it.senseId) }
        return existing.wordId
    }
}
