package com.github.chsiching.worddrill.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

/**
 * PresetWordsParser 的纯 JVM 单测（副接缝：JSON 反序列化逻辑）。
 * 规格把序列化/反序列化列为副接缝，用小段 JSON 验证解析正确性。
 */
class PresetWordsParserTest {

    private val sample = """
        {
          "source": "ECDICT (MIT)",
          "books": [
            { "name": "CET-4", "words": [
              { "text": "apple", "phonetic": "/ˈæpl/", "senses": [ {"pos":"n.","meaning":"苹果"} ] },
              { "text": "run", "senses": [
                  {"pos":"vi.","meaning":"跑"},
                  {"pos":"vt.","meaning":"经营"}
              ]}
            ]},
            { "name": "CET-6", "words": [
              { "text": "apple", "phonetic": "/ˈæpl/", "senses": [ {"pos":"n.","meaning":"苹果"} ] }
            ]}
          ]
        }
    """.trimIndent()

    @Test
    fun parse_readsSource() {
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.source).isEqualTo("ECDICT (MIT)")
    }

    @Test
    fun parse_readsAllBooks() {
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books.map { it.name }).containsExactly("CET-4", "CET-6")
    }

    @Test
    fun parse_readsWordAndSenses() {
        val parsed = PresetWordsParser.parse(sample)
        val cet4 = parsed.books[0]
        assertThat(cet4.words.map { it.text }).containsExactly("apple", "run")
        val run = cet4.words.first { it.text == "run" }
        assertThat(run.senses).hasSize(2)
        assertThat(run.senses.map { it.pos }).containsExactly("vi.", "vt.")
        assertThat(run.senses.first { it.pos == "vt." }.meaning).isEqualTo("经营")
    }

    @Test
    fun parse_keepsDuplicateAcrossBooksAsListed() {
        // apple 在 CET-4 和 CET-6 都列出 —— 解析按原样保留（去重在导入逻辑做）
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.map { it.text }).contains("apple")
        assertThat(parsed.books[1].words.map { it.text }).contains("apple")
    }

    // ---- Ticket #14: phonetic（IPA 音标，可空）----

    @Test
    fun parse_readsPhonetic_whenPresent() {
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.first { it.text == "apple" }.phonetic)
            .isEqualTo("/ˈæpl/")
    }

    @Test
    fun parse_phoneticIsNull_whenAbsent() {
        // run 没有 phonetic 字段 → null（而非空字符串，避免两种"无值"语义）
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.first { it.text == "run" }.phonetic).isNull()
    }

    @Test
    fun parse_phoneticIsNull_whenBlank() {
        // 空白 phonetic 视为无音标，统一为 null
        val json = """
            {"source":"x","books":[{"name":"b","words":[
              {"text":"a","phonetic":"   ","senses":[{"pos":"n.","meaning":"甲"}]}
            ]}]}
        """.trimIndent()
        val parsed = PresetWordsParser.parse(json)
        assertThat(parsed.books[0].words[0].phonetic).isNull()
    }

    @Test
    fun parse_phoneticIsNull_whenJsonNull() {
        // ⚠️ 回归防护：JSON 里显式 "phonetic": null 必须解析为 Kotlin null，
        // 而非字符串 "null"。Android 自带 org.json 的 optString(key, "") 在值为
        // JSON null 时会返回 "null" 字符串，导致把 "null" 写进数据库（实机踩到过）。
        // （本 JVM 单测用的是 org.json:json，行为可能不同；真正兜底看 connectedAndroidTest
        //  和 PresetImporterTest 的 import_phoneticIsNull_whenAbsent。）
        val json = """
            {"source":"x","books":[{"name":"b","words":[
              {"text":"a","phonetic":null,"senses":[{"pos":"n.","meaning":"甲"}]}
            ]}]}
        """.trimIndent()
        val parsed = PresetWordsParser.parse(json)
        assertThat(parsed.books[0].words[0].phonetic).isNull()
    }

    @Test(expected = JSONException::class)
    fun parse_missingWordsArrayThrows() {
        PresetWordsParser.parse("""{"source":"x","books":[{"name":"b"}]}""")
    }
}
