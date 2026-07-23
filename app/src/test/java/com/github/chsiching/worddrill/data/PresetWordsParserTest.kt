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
              { "text": "apple", "senses": [ {"pos":"n.","meaning":"苹果"} ] },
              { "text": "run", "senses": [
                  {"pos":"vi.","meaning":"跑"},
                  {"pos":"vt.","meaning":"经营"}
              ]}
            ]},
            { "name": "CET-6", "words": [
              { "text": "apple", "senses": [ {"pos":"n.","meaning":"苹果"} ] }
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

    @Test(expected = JSONException::class)
    fun parse_missingWordsArrayThrows() {
        PresetWordsParser.parse("""{"source":"x","books":[{"name":"b"}]}""")
    }
}
