package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import org.json.JSONArray
import org.json.JSONObject

/**
 * [DatabaseSnapshot] 的 JSON 序列化/反序列化（副接缝）。
 *
 * 与 [com.github.chsiching.worddrill.data.PresetWordsParser] 同样用 Android 自带的 org.json，
 * 无新增依赖。两个函数都是纯函数（输入数据 → 输出字符串 / 输入字符串 → 输出数据），
 * 不依赖 Android 文件系统，便于在 JVM 单测里喂入数据与字符串验证往返。
 *
 * JSON 结构：
 * ```
 * {
 *   "version": 1,
 *   "nickname": "可选昵称",            // 空白/省略视为无昵称
 *   "books":   [ { "bookId":1, "name":"CET-4", "isPreset":true } ],
 *   "words":   [ { "wordId":10, "text":"apple", "phonetic":"/ˈæpl/" } ],
 *   "senses":  [ { "senseId":100, "wordId":10, "pos":"n.", "meaning":"书" } ],
 *   "bookWords":[ { "bookId":1, "wordId":10, "skipped":false } ],
 *   "swipeLogs":[ { "logId":1000, "bookId":2, "wordId":10, "timestamp":1700000000000 } ]
 * }
 * ```
 *
 * 反序列化对缺省字段做安全回退：version 缺省为 1，nickname 缺省/空白为 null；
 * word.phonetic 缺省/空白为 null（兼容 Ticket #14 之前的导出文件）；
 * 但 books/words/senses/bookWords/swipeLogs 数组缺省时抛 [org.json.JSONException]
 *（与 [com.github.chsiching.worddrill.data.PresetWordsParser] 的策略一致：缺关键字段视为损坏文件）。
 */
object DatabaseJsonSerializer {

    /** 当前 schema 版本号，导出时写入。 */
    const val CURRENT_VERSION = 1

    fun serialize(snapshot: DatabaseSnapshot): String {
        val root = JSONObject()
        root.put("version", snapshot.version)
        // 空白昵称不写入（导出文件里不留空标签）
        val nick = snapshot.nickname?.trim()
        if (!nick.isNullOrEmpty()) root.put("nickname", nick)

        root.put("books", JSONArray().apply {
            snapshot.books.forEach { b ->
                put(JSONObject().apply {
                    put("bookId", b.bookId)
                    put("name", b.name)
                    put("isPreset", b.isPreset)
                    // Ticket #22：软删标记写入导出（true = 已软删，重导回保留回收站状态）。
                    // 与 bookWord.skipped/deleted 同策略：字段齐全写，反序列化无须猜测缺省。
                    put("deleted", b.deleted)
                })
            }
        })
        root.put("words", JSONArray().apply {
            snapshot.words.forEach { w ->
                put(JSONObject().apply {
                    put("wordId", w.wordId)
                    put("text", w.text)
                    // Ticket #14：phonetic 为空不写入（与 nickname 同策略：导出文件不留空标签，
                    // 反序列化时 optString 默认 "" → null）。
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
                    // Ticket #20：跳过标记写入导出（true = 已跳过，重导回不丢跳过状态）。
                    // 默认 false 的行也写，保持字段齐全，反序列化无须猜测缺省。
                    put("skipped", bw.skipped)
                    // Ticket #22：软删标记写入导出（true = 已软删，重导回保留回收站状态）。
                    // 与 skipped 同策略：字段齐全写，反序列化无须猜测缺省。
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
        // 空白昵称视为无昵称
        val nickname = root.optString("nickname").trim().ifEmpty { null }

        val books = root.getJSONArray("books").toList { b ->
            // Ticket #22：deleted 可空，兼容 #22 之前的导出文件（缺省 → false，未软删）。
            Book(
                bookId = b.getLong("bookId"),
                name = b.getString("name"),
                isPreset = b.getBoolean("isPreset"),
                deleted = b.optBoolean("deleted", false),
            )
        }
        val words = root.getJSONArray("words").toList { w ->
            // Ticket #14：phonetic 可空。
            // ⚠️ w.isNull 先挡 JSON null（见 PresetWordsParser 同款坑的说明）：
            // Android org.json 的 optString 在值为 JSON null 时返回字符串 "null"。
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
            // Ticket #20：skipped 可空，兼容 #20 之前的导出文件（缺省 → false，未跳过）。
            // Ticket #22：deleted 可空，兼容 #22 之前的导出文件（缺省 → false，未软删）。
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

    /** 把 JSONArray 映射成 List<T>，块里拿到的是已解包的 JSONObject。 */
    private inline fun <T> JSONArray.toList(block: (JSONObject) -> T): List<T> =
        buildList {
            for (i in 0 until length()) add(block(getJSONObject(i)))
        }
}
