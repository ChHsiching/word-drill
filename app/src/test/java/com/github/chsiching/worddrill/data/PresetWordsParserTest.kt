package com.github.chsiching.worddrill.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

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
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.map { it.text }).contains("apple")
        assertThat(parsed.books[1].words.map { it.text }).contains("apple")
    }

    @Test
    fun parse_readsPhonetic_whenPresent() {
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.first { it.text == "apple" }.phonetic)
            .isEqualTo("/ˈæpl/")
    }

    @Test
    fun parse_phoneticIsNull_whenAbsent() {
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.first { it.text == "run" }.phonetic).isNull()
    }

    @Test
    fun parse_phoneticIsNull_whenBlank() {
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
        val json = """
            {"source":"x","books":[{"name":"b","words":[
              {"text":"a","phonetic":null,"senses":[{"pos":"n.","meaning":"甲"}]}
            ]}]}
        """.trimIndent()
        val parsed = PresetWordsParser.parse(json)
        // Regression: Android's bundled org.json optString returns the string "null" on a JSON
        // null, which once wrote "null" into the database. This JVM test runs on org.json:json,
        // whose behavior may differ; the real backstop is PresetImporterTest (connectedAndroidTest).
        assertThat(parsed.books[0].words[0].phonetic).isNull()
    }

    @Test(expected = JSONException::class)
    fun parse_missingWordsArrayThrows() {
        PresetWordsParser.parse("""{"source":"x","books":[{"name":"b"}]}""")
    }
}
