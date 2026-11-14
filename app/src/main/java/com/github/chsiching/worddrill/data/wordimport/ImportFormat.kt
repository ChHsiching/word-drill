package com.github.chsiching.worddrill.data.wordimport

enum class ImportFormat {
    XLSX,
    TXT,
    CSV,
    PDF;

    companion object {
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

class UnsupportedFileTypeException(val extension: String) : Exception("unsupported file type: $extension")
