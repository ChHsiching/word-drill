package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21：文件导入完成统计。
 *
 * - [success]：成功导入的词数（含词典命中和文件列3/4 兜底的）
 * - [skipped]：因数据不完整（word 为空 / 词典没有且 col 3+4 也空 / POS 解析无结果）跳过的词数
 *
 * 去重不计入：重复词靠 word 表唯一索引 + IGNORE 静默合并，issue 明确"去重不提示"。
 */
data class ImportSummary(
    val success: Int,
    val skipped: Int,
)
