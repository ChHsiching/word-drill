package com.github.chsiching.worddrill.data.settings

/**
 * Theme mode (Ticket #9): the user's light/dark preference.
 *
 * Persisted to DataStore as [name] (stringPreferencesKey), decoded safely on read with
 * [fromStorageName]: unknown or corrupted values fall back to [SYSTEM], so [Enum.valueOf]
 * cannot throw and crash the whole Flow.
 */
enum class ThemeMode {
    /** Force light. */
    LIGHT,

    /** Force dark. */
    DARK,

    /** Follow the system light/dark setting (default). */
    SYSTEM;

    companion object {
        /**
         * Safely decodes a string read back from DataStore into a [ThemeMode].
         * - null (key absent) or an unrecognized value falls back to [SYSTEM] (default)
         *
         * Extracted as a pure function for easy JVM unit testing (no Android dependency).
         */
        fun fromStorageName(name: String?): ThemeMode =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: SYSTEM
    }
}
