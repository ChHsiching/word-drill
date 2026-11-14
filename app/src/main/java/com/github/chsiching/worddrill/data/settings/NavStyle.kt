package com.github.chsiching.worddrill.data.settings

enum class NavStyle {
    PILL,
    BAR;

    companion object {
        fun fromStorageName(name: String?): NavStyle =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: PILL
    }
}
