package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21：支持的文件导入格式。
 *
 * 列结构（所有格式统一，见 issue #21）：
 * - col 0：序号（解析时忽略）
 * - col 1：单词（必须）
 * - col 2：音标（可选）
 * - col 3：词性 + 释义（可选，如 "a.平坦的；平淡的 n.公寓；平面"，由 [PosMeaningParser] 解析）
 */
enum class ImportFormat {
    XLSX,
    TXT,
    CSV,
    PDF;

    companion object {
        /**
         * 按文件扩展名识别格式。未知扩展名抛 [UnsupportedFileTypeException]，
         * 由调用方捕获 → 在 UI 层用 [com.github.chsiching.worddrill.R.string.library_import_failed]
         * + 文件类型文案展示。不在数据层硬编码用户可见中文（项目约定：用户可见文案走 R.string）。
         */
        fun byExtension(filename: String): ImportFormat {
            val lower = filename.substringAfterLast('.', "").lowercase()
            return when (lower) {
                "xlsx" -> XLSX
                "txt" -> TXT
                "csv" -> CSV
                "pdf" -> PDF
                else -> throw UnsupportedFileTypeException(lower)
            }
        }
    }
}

/** 未知文件类型异常。携带扩展名（小写），由 VM 层组装用户可见文案。 */
class UnsupportedFileTypeException(val extension: String) : Exception("unsupported file type: $extension")
