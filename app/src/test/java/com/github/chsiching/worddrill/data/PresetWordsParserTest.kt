package com.github.chsiching.worddrill.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

/**
 * Pure JVM tests for PresetWordsParser (secondary seam: the JSON deserialization logic).
 * The spec lists serialization/deserialization as a secondary seam, so small JSON snippets verify the parse is correct.
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
        // "apple" is listed in both CET-4 and CET-6. The parser keeps it as listed. Deduplication happens in the import logic.
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.map { it.text }).contains("apple")
        assertThat(parsed.books[1].words.map { it.text }).contains("apple")
    }

    // ---- Ticket #14: phonetic (IPA transcription, nullable) ----

    @Test
    fun parse_readsPhonetic_whenPresent() {
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.first { it.text == "apple" }.phonetic)
            .isEqualTo("/ˈæpl/")
    }

    @Test
    fun parse_phoneticIsNull_whenAbsent() {
        // "run" has no phonetic field, so it parses to null. Not an empty string, which would create two "no value" semantics.
        val parsed = PresetWordsParser.parse(sample)
        assertThat(parsed.books[0].words.first { it.text == "run" }.phonetic).isNull()
    }

    @Test
    fun parse_phoneticIsNull_whenBlank() {
        // A blank phonetic counts as no transcription and is normalized to null.
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
        // ⚠️ Regression guard: an explicit "phonetic": null in the JSON must parse to Kotlin null,
        // not the string "null". Android's bundled org.json optString(key, "") returns the string
        // "null" when the value is JSON null, which wrote "null" into the database (hit on a real device).
        // (This JVM test uses org.json:json, whose behavior may differ. The real backstop is connectedAndroidTest
        //  and PresetImporterTest's import_phoneticIsNull_whenAbsent.)
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
