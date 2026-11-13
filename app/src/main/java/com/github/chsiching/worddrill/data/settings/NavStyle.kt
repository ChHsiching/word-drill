package com.github.chsiching.worddrill.data.settings

/**
 * Bottom navigation bar style (Ticket #16): floating pill / full-width bottom bar.
 *
 * Same as [ThemeMode]: persisted as [name], decoded safely on read with [fromStorageName];
 * unknown values fall back to the default [PILL], avoiding exceptions on corrupted data.
 */
enum class NavStyle {
    /** Centered floating pill: 100px corner radius, frosted-glass translucency, black sliding indicator, selected item text inverted to white. */
    PILL,

    /** Full-width bottom-anchored bar: no corner radius, selected item highlighted by text brightness (no black block). */
    BAR;

    companion object {
        /** Safely decodes a string read back from DataStore. null or unrecognized -> [PILL]. */
        fun fromStorageName(name: String?): NavStyle =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: PILL
    }
}
