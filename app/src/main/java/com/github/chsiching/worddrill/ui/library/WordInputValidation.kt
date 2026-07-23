package com.github.chsiching.worddrill.ui.library

/**
 * 词条输入校验（纯函数，无 Android 依赖）。
 *
 * 沿用 #6 的 trim 基准教训（坑 B）：写库时会 trim，校验必须在 trim 后的值上做，
 * 否则 "  " 能绕过非空约束，写库后变 ""。
 *
 * @return 错误提示文案；null 表示通过、可提交。
 */
internal fun validateAddWordInput(text: String, pos: String, meaning: String): String? = when {
    text.trim().isEmpty() -> "单词不能为空"
    pos.trim().isEmpty() -> "词性不能为空"
    meaning.trim().isEmpty() -> "释义不能为空"
    else -> null
}

/** 编辑义项时的校验（词性 + 释义，二者皆必填）。 */
internal fun validateSenseEditInput(pos: String, meaning: String): String? = when {
    pos.trim().isEmpty() -> "词性不能为空"
    meaning.trim().isEmpty() -> "释义不能为空"
    else -> null
}
