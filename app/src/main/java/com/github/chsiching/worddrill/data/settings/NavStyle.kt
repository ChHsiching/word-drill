package com.github.chsiching.worddrill.data.settings

/**
 * 底部导航栏风格（Ticket #16）：浮动胶囊 / 全宽底部栏。
 *
 * 与 [ThemeMode] 一样：持久化存 [name]，读回用 [fromStorageName] 安全解码，
 * 未知值回退到默认 [PILL]，避免损坏数据抛异常。
 */
enum class NavStyle {
    /** 居中悬浮胶囊：圆角 100px，毛玻璃半透明，黑色滑动指示器，选中项文字反白。 */
    PILL,

    /** 全宽贴底栏：无圆角，选中项用文字亮度高亮（无黑块）。 */
    BAR;

    companion object {
        /** 从 DataStore 读回的字符串安全解码。null 或无法识别 → [PILL]。 */
        fun fromStorageName(name: String?): NavStyle =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: PILL
    }
}
