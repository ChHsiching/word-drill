package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON serialization for [DatabaseSnapshot] using Android's built-in org.json.
 *
 * File-format contract: on deserialization, version defaults to 1; nickname and word.phonetic
 * default to null when absent/blank (older export files lack these fields); an absent
 * books/words/senses/bookWords/swipeLogs array throws [org.json.JSONException] (corrupted file).
 */
object DatabaseJsonSerializer {

    const val CURRENT_VERSION = 1

    fun serialize(snapshot: DatabaseSnapshot): String {
        val root = JSONObject()
        root.put("version", snapshot.version)
        val nick = snapshot.nickname?.trim()
        if (!nick.isNullOrEmpty()) root.put("nickname", nick)

        root.put("books", JSONArray().apply {
            snapshot.books.forEach { b ->
                put(JSONObject().apply {
                    put("bookId", b.bookId)
                    put("name", b.name)
                    put("isPreset", b.isPreset)
                    put("deleted", b.deleted)
                })
            }
        })
        root.put("words", JSONArray().apply {
            snapshot.words.forEach { w ->
                put(JSONObject().apply {
                    put("wordId", w.wordId)
                    put("text", w.text)
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
                    put("skipped", bw.skipped)
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
        val nickname = root.optString("nickname").trim().ifEmpty { null }

        val books = root.getJSONArray("books").toList { b ->
            Book(
                bookId = b.getLong("bookId"),
                name = b.getString("name"),
                isPreset = b.getBoolean("isPreset"),
                deleted = b.optBoolean("deleted", false),
            )
        }
        val words = root.getJSONArray("words").toList { w ->
            // Android's org.json.optString returns the string "null" on a JSON null,
            // so guard with isNull first.
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

    private inline fun <T> JSONArray.toList(block: (JSONObject) -> T): List<T> =
        buildList {
            for (i in 0 until length()) add(block(getJSONObject(i)))
        }
}
