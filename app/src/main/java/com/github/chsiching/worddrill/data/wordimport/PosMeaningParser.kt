package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21：词性缩写 + 中文释义解析（纯函数）。
 *
 * 列4 文本（如 `"a.平坦的；平淡的 n.公寓；平面"`）→ `List<Pair<pos, meaning>>`。
 *
 * 固定缩写表（issue #21 字面）：每个缩写以半角 `.` 结尾。
 *   n. v. vt. vi. aux.v. adj. a. adv. ad. prep. conj. pron. art. num. int. interj. abbr.
 *
 * 解析策略：
 * 1. 用正则找出文本中所有"已知缩写出现的位置"，按位置排序作为切段锚点。
 *    缩写匹配按**长度降序**（`aux.v.` 优先于 `aux.`、`adj.` 优先于 `a.`），避免短前缀误切。
 * 2. 每段 = 锚点缩写 + 紧跟到下一锚点（或串尾）的释义文本，trim 后产出 (pos, meaning)。
 * 3. 第一个锚点之前的文本（如有）：trim 后非空则视为无缩写的纯释义，归到默认 `n.`（保守选择，
 *    issue 没明说，避免整词丢释义）。
 * 4. 只有缩写没有释义（如裸 `"n."`）→ 跳过。
 *
 * 注意：缩写在释义内部也会被识别（如 "n.看 etc. 等" 不会被切，因为 `etc.` 不在缩写表）。
 * 缩写表的固定性即保证了不会误切自然文本。
 */
object PosMeaningParser {

    /** 固定缩写表（issue #21 字面，17 个）。 */
    private val ABBREVIATIONS: List<String> = listOf(
        "n.", "v.", "vt.", "vi.", "aux.v.", "adj.", "a.", "adv.", "ad.",
        "prep.", "conj.", "pron.", "art.", "num.", "int.", "interj.", "abbr.",
    )

    /**
     * 按长度降序排列的缩写表，用于 [Regex] 构造。
     *
     * 必须降序：`aux.v.`（5 字符）必须先于 `aux.`（4 字符）匹配，`adj.`（4）先于 `a.`（2）。
     * 否则正则 alternation `aux.v.|aux.|adj.|a.` 在 "aux.v.做" 上会先匹中 `aux.`，
     * 把 `v.做` 留在释义段，结果错。
     */
    private val SORTED_ABBREVIATIONS: List<String> = ABBREVIATIONS.sortedByDescending { it.length }

    /** 转义正则元字符（`.` → `\.`）。缩写里只有 `.`，转义后安全。 */
    private val ABBREV_PATTERN: Regex = Regex(
        SORTED_ABBREVIATIONS.joinToString("|") { Regex.escape(it) }
    )

    /**
     * 解析列4 文本为 (pos, meaning) 列表。
     *
     * @return 空表表示文本无有效义项（调用方应计入"数据不完整跳过"）。
     */
    fun parse(text: String): List<Pair<String, String>> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val matches = ABBREV_PATTERN.findAll(trimmed).toList()
        if (matches.isEmpty()) {
            // 整段无缩写 → 默认 n.
            return listOf("n." to trimmed)
        }

        val result = mutableListOf<Pair<String, String>>()

        // 第一个锚点之前的文本（trim 后非空）→ 默认 n.
        val leading = trimmed.substring(0, matches.first().range.first).trim()
        if (leading.isNotEmpty()) {
            result.add("n." to leading)
        }

        // 逐段：锚点缩写 + 到下一锚点（或串尾）的释义
        for (i in matches.indices) {
            val pos = matches[i].value
            val meaningStart = matches[i].range.last + 1
            val meaningEnd = if (i + 1 < matches.size) matches[i + 1].range.first else trimmed.length
            val meaning = trimmed.substring(meaningStart, meaningEnd).trim()
            if (meaning.isNotEmpty()) {
                result.add(pos to meaning)
            }
        }

        return result
    }
}
