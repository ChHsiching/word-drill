package com.github.chsiching.worddrill.data.settings

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromStorageName(name: String?): ThemeMode =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: SYSTEM
    }
}
