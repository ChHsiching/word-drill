package com.github.chsiching.worddrill.data.settings

/**
 * 主题模式（Ticket #9）：用户对深/浅色的偏好。
 *
 * 持久化到 DataStore 时存 [name]（stringPreferencesKey），读回时用 [fromStorageName] 安全解码：
 * 未知值或损坏数据回退到 [SYSTEM]，避免 [Enum.valueOf] 抛异常导致整个 Flow 崩。
 */
enum class ThemeMode {
    /** 强制浅色。 */
    LIGHT,

    /** 强制深色。 */
    DARK,

    /** 跟随系统深浅色设置（默认值）。 */
    SYSTEM;

    companion object {
        /**
         * 从 DataStore 读回的字符串安全解码为 [ThemeMode]。
         * - null（键不存在）或无法识别的值 → [SYSTEM]（默认）
         *
         * 抽成纯函数便于 JVM 单测（无 Android 依赖）。
         */
        fun fromStorageName(name: String?): ThemeMode =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: SYSTEM
    }
}
