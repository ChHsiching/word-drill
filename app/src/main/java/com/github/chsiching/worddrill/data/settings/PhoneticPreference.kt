package com.github.chsiching.worddrill.data.settings

/** Phonetic preference (GLOSSARY.md): which phonetic(s) a drill card shows — UK, US,
 *  or both. A global setting in the Me tab; default US (issue #58). */
enum class PhoneticPreference {
    UK,
    US,
    BOTH;

    /** Which variant a display segment carries; SHARED marks the identical-form
     *  dedupe of BOTH, which carries no label (no ambiguity to resolve). */
    enum class Kind { UK, US, SHARED }

    /** One labeled phonetic for card display. */
    data class Segment(val kind: Kind, val text: String)

    /** Labeled display segments (ADR 0003 fallback): the chosen variant, the
     *  available one when the chosen is missing, both side by side for BOTH
     *  (identical forms shown once as SHARED). Blank variants count as missing. */
    fun segments(phoneticUk: String?, phoneticUs: String?): List<Segment> {
        val uk = phoneticUk?.takeIf { it.isNotBlank() }
        val us = phoneticUs?.takeIf { it.isNotBlank() }
        return when (this) {
            UK -> when {
                uk != null -> listOf(Segment(Kind.UK, uk))
                us != null -> listOf(Segment(Kind.US, us))
                else -> emptyList()
            }
            US -> when {
                us != null -> listOf(Segment(Kind.US, us))
                uk != null -> listOf(Segment(Kind.UK, uk))
                else -> emptyList()
            }
            BOTH -> when {
                uk != null && us != null && uk == us -> listOf(Segment(Kind.SHARED, uk))
                else -> listOfNotNull(
                    uk?.let { Segment(Kind.UK, it) },
                    us?.let { Segment(Kind.US, it) },
                )
            }
        }
    }

    /** Setting-row tap cycle (issue #58): UK -> US -> BOTH -> UK. */
    fun next(): PhoneticPreference = when (this) {
        UK -> US
        US -> BOTH
        BOTH -> UK
    }

    companion object {
        fun fromStorageName(name: String?): PhoneticPreference =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: US
    }
}
