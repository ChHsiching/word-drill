package com.github.chsiching.worddrill.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

/**
 * Ticket #19：[DictionaryWordsParser] 的纯 JVM 单测（副接缝：JSON 反序列化逻辑）。
 * 与 [PresetWordsParserTest] 同模式：用小段 JSON 验证解析正确性。
 */
class DictionaryWordsParserTest {

    private val sample = """
        {
          "source": "ECDICT (MIT License, https://github.com/skywind3000/ECDICT)",
          "words": [
            { "word": "apple", "phonetic": "/ˈæpl/", "pos": "n.", "meaning": "苹果" },
            { "word": "apple", "phonetic": "/ˈæpl/", "pos": "v.", "meaning": "..." },
            { "word": "run", "pos": "v.", "meaning": "跑" }
          ]
        }
    """.trimIndent()

    @Test
    fun parse_readsSource() {
        val parsed = DictionaryWordsParser.parse(sample)
        assertThat(parsed.source).isEqualTo("ECDICT (MIT License, https://github.com/skywind3000/ECDICT)")
    }

    @Test
    fun parse_readsAllEntries() {
        val parsed = DictionaryWordsParser.parse(sample)
        // 同一 word 的多词性 = 多行 entries，按原样保留
        assertThat(parsed.words).hasSize(3)
    }

    @Test
    fun parse_readsWordPosMeaning() {
        val parsed = DictionaryWordsParser.parse(sample)
        val first = parsed.words[0]
        assertThat(first.word).isEqualTo("apple")
        assertThat(first.pos).isEqualTo("n.")
        assertThat(first.meaning).isEqualTo("苹果")
    }

    @Test
    fun parse_keepsMultiplePosForSameWordAsSeparateEntries() {
        val parsed = DictionaryWordsParser.parse(sample)
        val apples = parsed.words.filter { it.word == "apple" }
        assertThat(apples).hasSize(2)
        assertThat(apples.map { it.pos }).containsExactly("n.", "v.")
    }

    // ---- phonetic 可空（与 PresetWordsParser 同坑）----

    @Test
    fun parse_readsPhonetic_whenPresent() {
        val parsed = DictionaryWordsParser.parse(sample)
        assertThat(parsed.words.first { it.word == "apple" }.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun parse_phoneticIsNull_whenAbsent() {
        val parsed = DictionaryWordsParser.parse(sample)
        // run 没有 phonetic 字段 → null
        assertThat(parsed.words.first { it.word == "run" }.phonetic).isNull()
    }

    @Test
    fun parse_phoneticIsNull_whenBlank() {
        val json = """
            {"source":"x","words":[
              {"word":"a","phonetic":"   ","pos":"n.","meaning":"甲"}
            ]}
        """.trimIndent()
        val parsed = DictionaryWordsParser.parse(json)
        assertThat(parsed.words[0].phonetic).isNull()
    }

    @Test
    fun parse_phoneticIsNull_whenJsonNull() {
        // 回归防护：JSON null 必须解析为 Kotlin null，而非字符串 "null"
        val json = """
            {"source":"x","words":[
              {"word":"a","phonetic":null,"pos":"n.","meaning":"甲"}
            ]}
        """.trimIndent()
        val parsed = DictionaryWordsParser.parse(json)
        assertThat(parsed.words[0].phonetic).isNull()
    }

    @Test(expected = JSONException::class)
    fun parse_missingWordsArrayThrows() {
        DictionaryWordsParser.parse("""{"source":"x"}""")
    }
}
