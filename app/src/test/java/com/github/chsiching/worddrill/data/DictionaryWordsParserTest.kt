package com.github.chsiching.worddrill.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

class DictionaryWordsParserTest {

    private val sample = """
        {
          "source": "Youdao (dict.youdao.com web API, crawled 2026-11-21)",
          "words": [
            { "word": "apple", "phonetic": "/ˈæpl/", "phonetic_us": "/ˈæpl/", "pos": "n.", "meaning": "苹果" },
            { "word": "apple", "phonetic": "/ˈæpl/", "pos": "v.", "meaning": "..." },
            { "word": "run", "pos": "v.", "meaning": "跑" }
          ]
        }
    """.trimIndent()

    @Test
    fun parse_readsSource() {
        val parsed = DictionaryWordsParser.parse(sample)
        assertThat(parsed.source).isEqualTo("Youdao (dict.youdao.com web API, crawled 2026-11-21)")
    }

    @Test
    fun parse_readsAllEntries() {
        val parsed = DictionaryWordsParser.parse(sample)
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

    @Test
    fun parse_readsPhonetic_whenPresent() {
        val parsed = DictionaryWordsParser.parse(sample)
        assertThat(parsed.words.first { it.word == "apple" }.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun parse_phoneticIsNull_whenAbsent() {
        val parsed = DictionaryWordsParser.parse(sample)
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
        val json = """
            {"source":"x","words":[
              {"word":"a","phonetic":null,"pos":"n.","meaning":"甲"}
            ]}
        """.trimIndent()
        val parsed = DictionaryWordsParser.parse(json)
        assertThat(parsed.words[0].phonetic).isNull()
    }

    @Test
    fun parse_readsPhoneticUs_whenPresent() {
        val parsed = DictionaryWordsParser.parse(sample)
        assertThat(parsed.words.first { it.word == "apple" && it.pos == "n." }.phoneticUs)
            .isEqualTo("/ˈæpl/")
    }

    @Test
    fun parse_phoneticUsIsNull_whenAbsent() {
        val parsed = DictionaryWordsParser.parse(sample)
        assertThat(parsed.words.first { it.word == "apple" && it.pos == "v." }.phoneticUs).isNull()
    }

    @Test
    fun parse_phoneticUsIsNull_whenJsonNull() {
        val json = """
            {"source":"x","words":[
              {"word":"a","phonetic":"/p/","phonetic_us":null,"pos":"n.","meaning":"甲"}
            ]}
        """.trimIndent()
        val parsed = DictionaryWordsParser.parse(json)
        assertThat(parsed.words[0].phoneticUs).isNull()
    }

    @Test
    fun parse_phoneticUsIsNull_whenBlank() {
        val json = """
            {"source":"x","words":[
              {"word":"a","phonetic_us":"  ","pos":"n.","meaning":"甲"}
            ]}
        """.trimIndent()
        val parsed = DictionaryWordsParser.parse(json)
        assertThat(parsed.words[0].phoneticUs).isNull()
    }

    @Test(expected = JSONException::class)
    fun parse_missingWordsArrayThrows() {
        DictionaryWordsParser.parse("""{"source":"x"}""")
    }
}
