package com.github.chsiching.worddrill.data.settings

/** Phonetic preference (GLOSSARY.md): which phonetic(s) a drill card shows — UK, US,
 *  or both. A global setting in the Me tab; default US (issue #58). */
enum class PhoneticPreference {
    UK,
    US,
    BOTH;

    /** Card display string for this preference given a word's UK/US variants
     *  (ADR 0003 fallback): the chosen variant, the available one when the chosen is
     *  missing for that word, both joined by a space for BOTH, null when neither
     *  exists. Blank variants count as missing. */
    fun resolve(phoneticUk: String?, phoneticUs: String?): String? {
        val uk = phoneticUk?.takeIf { it.isNotBlank() }
        val us = phoneticUs?.takeIf { it.isNotBlank() }
        return when (this) {
            UK -> uk ?: us
            US -> us ?: uk
            BOTH -> listOfNotNull(uk, us).joinToString(" ").ifEmpty { null }
        }
    }

    companion object {
        fun fromStorageName(name: String?): PhoneticPreference =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: US
    }
}
