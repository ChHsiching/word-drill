package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21: supported file import formats.
 *
 * Column layout (identical for all formats, see issue #21):
 * - col 0: index number (ignored during parsing)
 * - col 1: word (required)
 * - col 2: phonetic (optional)
 * - col 3: pos + meaning (optional, e.g. "a.flat; plain n.apartment; plane" in real data, parsed by [PosMeaningParser])
 */
enum class ImportFormat {
    XLSX,
    TXT,
    CSV,
    PDF;

    companion object {
        /**
         * Detect the format from the file extension. An unknown extension throws
         * [UnsupportedFileTypeException], which the caller catches and shows in the UI layer with
         * [com.github.chsiching.worddrill.R.string.library_import_failed]
         * + a file-type message. No user-visible Chinese is hardcoded in the data layer
         * (project convention: user-visible text goes through R.string).
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

/** Unknown file type exception. Carries the (lowercase) extension; the VM layer assembles the user-visible message. */
class UnsupportedFileTypeException(val extension: String) : Exception("unsupported file type: $extension")
