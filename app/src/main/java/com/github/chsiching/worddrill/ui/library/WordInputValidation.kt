package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R

/**
 * 词条输入校验（纯函数，无 Android 运行时依赖）。
 *
 * 沿用 #6 的 trim 基准教训（坑 B）：写库时会 trim，校验必须在 trim 后的值上做，
 * 否则 "  " 能绕过非空约束，写库后变 ""。
 *
 * @return 错误文案的资源 id（R.string.*）；null 表示通过、可提交。由 Composable 层用
 *   `stringResource(...)` 解析（不把中文写死在 VM/校验函数里，Ticket #12）。
 */
internal fun validateAddWordInput(text: String, pos: String, meaning: String): Int? = when {
    text.trim().isEmpty() -> R.string.validation_word_empty
    pos.trim().isEmpty() -> R.string.validation_pos_empty
    meaning.trim().isEmpty() -> R.string.validation_meaning_empty
    else -> null
}

/** 编辑义项时的校验（词性 + 释义，二者皆必填）。 */
internal fun validateSenseEditInput(pos: String, meaning: String): Int? = when {
    pos.trim().isEmpty() -> R.string.validation_pos_empty
    meaning.trim().isEmpty() -> R.string.validation_meaning_empty
    else -> null
}
