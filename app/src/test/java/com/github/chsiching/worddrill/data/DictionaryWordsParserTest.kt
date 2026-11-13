package com.github.chsiching.worddrill.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

/**
 * Ticket #19: pure JVM tests for [DictionaryWordsParser] (secondary seam: the JSON deserialization logic).
 * Same pattern as [PresetWordsParserTest]: small JSON snippets verify the parse is correct.
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
        // Multiple parts of speech for the same word become multiple entry rows, kept as-is.
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

    // ---- phonetic is nullable (same pitfall as PresetWordsParser) ----

    @Test
    fun parse_readsPhonetic_whenPresent() {
        val parsed = DictionaryWordsParser.parse(sample)
        assertThat(parsed.words.first { it.word == "apple" }.phonetic).isEqualTo("/ˈæpl/")
    }

    @Test
    fun parse_phoneticIsNull_whenAbsent() {
        val parsed = DictionaryWordsParser.parse(sample)
        // "run" has no phonetic field, so it parses to null.
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
        // Regression guard: JSON null must parse to Kotlin null, not the string "null".
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
