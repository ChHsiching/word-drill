package com.github.chsiching.worddrill.ui.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 新建/重命名词书时的名称校验（纯函数，无 Android 依赖）。
 *
 * 规格：
 * - 新建词书输入名称（is_preset = false）
 * - 重命名词书
 * - 不允许重名（交接链坑 C：@Insert(REPLACE)+autoGenerate 不会按 name 去重，需显式查重）
 *
 * 拆成纯函数便于 JVM 单测（无 Android 依赖、无 Room/DataStore 耦合）。
 */
class BookNameValidationTest {

    private val existingNames = listOf("CET-4", "CET-6", "考研")

    // ---- 空名 → 报错 ----

    @Test
    fun emptyName_isInvalid() {
        assertThat(validateBookName("", existingNames)).isNotNull()
    }

    @Test
    fun blankName_isInvalid() {
        assertThat(validateBookName("   ", existingNames)).isNotNull()
    }

    // ---- 重名 → 报错（大小写敏感，与 SQLite 默认一致）----

    @Test
    fun duplicateName_isInvalid() {
        assertThat(validateBookName("CET-4", existingNames)).isNotNull()
    }

    @Test
    fun newName_isValid() {
        assertThat(validateBookName("我的生词本", existingNames)).isNull()
    }

    @Test
    fun nameDifferingCase_isValid() {
        // SQLite 默认大小写敏感（text LIKE/比较区分大小写）。
        // "cet-4" 与 "CET-4" 是两个不同的名字，不视为重复。
        assertThat(validateBookName("cet-4", existingNames)).isNull()
    }

    // ---- 回归：trim 后查重（防 "CET-4 " 绕过校验写库成 "CET-4"）----

    @Test
    fun paddedDuplicate_isInvalid_afterTrim() {
        // 写库时会 trim，校验必须在 trim 后的基准上，否则带空格的重名会漏过。
        assertThat(validateBookName("  CET-4  ", existingNames)).isNotNull()
    }

    @Test
    fun paddedNewName_isValid_afterTrim() {
        // 带空格但 trim 后不重名 → 通过（写库会 trim 成 "我的生词本"）
        assertThat(validateBookName("  我的生词本  ", existingNames)).isNull()
    }
}
