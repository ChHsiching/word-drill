package com.github.chsiching.worddrill.ui.library

/**
 * 新建/重命名词书的名称校验（纯函数，无 Android 依赖）。
 *
 * 规则（与 [androidx.room.OnConflictStrategy] 无关，需显式查重）：
 * - 先 trim：写库时也会 trim，校验必须在同一基准上，否则 "CET-4 " 会绕过查重
 * - 空白名（trim 后为空）→ 报错
 * - 与已有词书重名（大小写敏感，与 SQLite 默认一致）→ 报错
 *
 * @param name 用户输入的名称（未 trim）。
 * @param existingNames 当前已存在的词书名集合（大小写敏感比对）。
 * @return 错误提示文案；null 表示通过校验、可提交。
 */
internal fun validateBookName(name: String, existingNames: List<String>): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "名称不能为空"
        trimmed in existingNames -> "已存在同名词书"
        else -> null
    }
}
