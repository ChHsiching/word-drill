package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON serialization/deserialization for [DatabaseSnapshot] (secondary seam).
 *
 * Like [com.github.chsiching.worddrill.data.PresetWordsParser], uses Android's built-in org.json,
 * with no new dependencies. Both functions are pure (input data → output string /
 * input string → output data) and do not depend on the Android file system, making it easy to
 * feed data and strings in JVM unit tests and verify round-trips.
 *
 * JSON structure:
 * ```
 * {
 *   "version": 1,
 *   "nickname": "optional nickname",    // blank/omitted means no nickname
 *   "books":   [ { "bookId":1, "name":"CET-4", "isPreset":true } ],
 *   "words":   [ { "wordId":10, "text":"apple", "phonetic":"/ˈæpl/" } ],
 *   "senses":  [ { "senseId":100, "wordId":10, "pos":"n.", "meaning":"book" } ],
 *   "bookWords":[ { "bookId":1, "wordId":10, "skipped":false } ],
 *   "swipeLogs":[ { "logId":1000, "bookId":2, "wordId":10, "timestamp":1700000000000 } ]
 * }
 * ```
 *
 * Deserialization applies safe fallbacks for absent fields: version defaults to 1,
 * nickname defaults to null when absent/blank; word.phonetic defaults to null when
 * absent/blank (compatible with export files from before Ticket #14); but if the
 * books/words/senses/bookWords/swipeLogs arrays are absent, an [org.json.JSONException]
 * is thrown (same strategy as [com.github.chsiching.worddrill.data.PresetWordsParser]:
 * missing key fields are treated as a corrupted file).
 */
object DatabaseJsonSerializer {

    /** Current schema version number, written on export. */
    const val CURRENT_VERSION = 1

    fun serialize(snapshot: DatabaseSnapshot): String {
        val root = JSONObject()
        root.put("version", snapshot.version)
        // Blank nicknames are not written (no empty labels left in the exported file)
        val nick = snapshot.nickname?.trim()
        if (!nick.isNullOrEmpty()) root.put("nickname", nick)

        root.put("books", JSONArray().apply {
            snapshot.books.forEach { b ->
                put(JSONObject().apply {
                    put("bookId", b.bookId)
                    put("name", b.name)
                    put("isPreset", b.isPreset)
                    // Ticket #22: soft-delete flag is written to the export (true = soft-deleted,
                    // re-importing keeps the recycle bin state).
                    // Same strategy as bookWord.skipped/deleted: always write the field so
                    // deserialization never has to guess the default.
                    put("deleted", b.deleted)
                })
            }
        })
        root.put("words", JSONArray().apply {
            snapshot.words.forEach { w ->
                put(JSONObject().apply {
                    put("wordId", w.wordId)
                    put("text", w.text)
                    // Ticket #14: empty phonetic is not written (same strategy as nickname:
                    // no empty labels in the exported file; on deserialization
                    // optString defaults to "" → null).
                    if (!w.phonetic.isNullOrBlank()) put("phonetic", w.phonetic)
                })
            }
        })
        root.put("senses", JSONArray().apply {
            snapshot.senses.forEach { s ->
                put(JSONObject().apply {
                    put("senseId", s.senseId)
                    put("wordId", s.wordId)
                    put("pos", s.pos)
                    put("meaning", s.meaning)
                })
            }
        })
        root.put("bookWords", JSONArray().apply {
            snapshot.bookWords.forEach { bw ->
                put(JSONObject().apply {
                    put("bookId", bw.bookId)
                    put("wordId", bw.wordId)
                    // Ticket #20: skip flag is written to the export (true = skipped,
                    // re-importing does not lose the skipped state).
                    // Rows with the default false are also written to keep fields complete,
                    // so deserialization never has to guess the default.
                    put("skipped", bw.skipped)
                    // Ticket #22: soft-delete flag is written to the export (true = soft-deleted,
                    // re-importing keeps the recycle bin state).
                    // Same strategy as skipped: always write the field so deserialization
                    // never has to guess the default.
                    put("deleted", bw.deleted)
                })
            }
        })
        root.put("swipeLogs", JSONArray().apply {
            snapshot.swipeLogs.forEach { l ->
                put(JSONObject().apply {
                    put("logId", l.logId)
                    put("bookId", l.bookId)
                    put("wordId", l.wordId)
                    put("timestamp", l.timestamp)
                })
            }
        })
        return root.toString()
    }

    fun deserialize(json: String): DatabaseSnapshot {
        val root = JSONObject(json)
        val version = if (root.has("version")) root.getInt("version") else 1
        // Blank nickname is treated as no nickname
        val nickname = root.optString("nickname").trim().ifEmpty { null }

        val books = root.getJSONArray("books").toList { b ->
            // Ticket #22: deleted is nullable, compatible with export files from before #22
            // (absent → false, not soft-deleted).
            Book(
                bookId = b.getLong("bookId"),
                name = b.getString("name"),
                isPreset = b.getBoolean("isPreset"),
                deleted = b.optBoolean("deleted", false),
            )
        }
        val words = root.getJSONArray("words").toList { w ->
            // Ticket #14: phonetic is nullable.
            // ⚠️ w.isNull first guards against JSON null (see the note about the same pitfall
            // in PresetWordsParser): Android org.json's optString returns the string "null"
            // when the value is JSON null.
            Word(
                wordId = w.getLong("wordId"),
                text = w.getString("text"),
                phonetic = if (w.isNull("phonetic")) null
                else w.optString("phonetic", "").ifBlank { null },
            )
        }
        val senses = root.getJSONArray("senses").toList { s ->
            Sense(
                senseId = s.getLong("senseId"),
                wordId = s.getLong("wordId"),
                pos = s.getString("pos"),
                meaning = s.getString("meaning"),
            )
        }
        val bookWords = root.getJSONArray("bookWords").toList { bw ->
            // Ticket #20: skipped is nullable, compatible with export files from before #20
            // (absent → false, not skipped).
            // Ticket #22: deleted is nullable, compatible with export files from before #22
            // (absent → false, not soft-deleted).
            BookWord(
                bookId = bw.getLong("bookId"),
                wordId = bw.getLong("wordId"),
                skipped = bw.optBoolean("skipped", false),
                deleted = bw.optBoolean("deleted", false),
            )
        }
        val swipeLogs = root.getJSONArray("swipeLogs").toList { l ->
            SwipeLog(
                logId = l.getLong("logId"),
                bookId = l.getLong("bookId"),
                wordId = l.getLong("wordId"),
                timestamp = l.getLong("timestamp"),
            )
        }
        return DatabaseSnapshot(
            version = version,
            nickname = nickname,
            books = books,
            words = words,
            senses = senses,
            bookWords = bookWords,
            swipeLogs = swipeLogs,
        )
    }

    /** Maps a JSONArray to List<T>; the block receives an already-unwrapped JSONObject. */
    private inline fun <T> JSONArray.toList(block: (JSONObject) -> T): List<T> =
        buildList {
            for (i in 0 until length()) add(block(getJSONObject(i)))
        }
}
