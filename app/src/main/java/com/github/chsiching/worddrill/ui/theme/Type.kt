package com.github.chsiching.worddrill.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.github.chsiching.worddrill.R

// ═══════════════════════════════════════════════════════════
// Ticket #15 — 字体规范（来源：designs/worddrill-ui/screens.jsx 的 inline style）
//
// 设计稿把字体规范拆成三类：
// 1. 英文：系统无衬线（SF Pro / Roboto），600 字重大标题、斜体词性
// 2. 音标：Charis SIL（衬线，bundled in res/font/）
// 3. 中文：设备原生中文字体（PingFang SC / Noto Sans SC），由系统 fallback
//
// Material3 [Typography] 只覆盖标准 slot（displaySmall/titleMedium/bodyLarge…），
// 不够装设计稿的专属 TextStyles（音标 / 词性 / 中文释义）。后者通过
// [WordDrillTypography] 扩展，由 [LocalWordDrillTypography] 暴露，使用方式：
//   MaterialTheme.wordDrillTypography.phonetic
// ═══════════════════════════════════════════════════════════

/** Charis SIL 字体族（音标专用，bundled）。Regular + Italic 已够用（音标不会用粗体）。 */
val CharisSilFontFamily = FontFamily(
    Font(R.font.charis_sil_regular),
    Font(R.font.charis_sil_italic, style = FontStyle.Italic),
)

/**
 * 扩展字体 token：超出 Material3 [Typography] 标准槽位的专属 [TextStyle]。
 *
 * - [word]：英文单词大标题（44px / 600 / 负字距）
 * - [phonetic]：音标（Charis SIL / 17px）
 * - [partOfSpeech]：词性（斜体 / 15px / tertiary 灰）
 * - [meaning]：中文释义（22px / 系统原生中文字体 —— fontFamily=null 走 PingFang SC / Noto Sans SC fallback）
 * - [statNumber]：统计大数字（tabular nums，等宽对齐）
 */
data class WordDrillTypography(
    val word: TextStyle,
    val phonetic: TextStyle,
    val partOfSpeech: TextStyle,
    val meaning: TextStyle,
    val statNumber: TextStyle,
    val statNumberLarge: TextStyle,
)

private val DefaultLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Composable
fun provideWordDrillTypography(): WordDrillTypography {
    // 注意：颜色统一在调用点用 MaterialTheme.colorScheme.* 注入，
    // 这里只定义字号/字重/字距/字体族（颜色是 Composable 取的，不便在这里锁死）。
    return WordDrillTypography(
        word = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 44.sp,
            letterSpacing = (-0.5).sp,
            lineHeight = 48.sp,
            lineHeightStyle = DefaultLineHeightStyle,
        ),
        phonetic = TextStyle(
            fontFamily = CharisSilFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            letterSpacing = 0.5.sp,
            lineHeight = 22.sp,
            lineHeightStyle = DefaultLineHeightStyle,
        ),
        partOfSpeech = TextStyle(
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            lineHeightStyle = DefaultLineHeightStyle,
        ),
        meaning = TextStyle(
            // fontFamily = null（默认）：系统 sans-serif + CJK fallback（PingFang SC / Noto Sans SC）。
            // 设计稿用 '"PingFang SC", -apple-system, ..., sans-serif' —— Android 上系统默认已覆盖。
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            lineHeightStyle = DefaultLineHeightStyle,
        ),
        statNumber = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontFeatureSettings = "tnum", // tabular nums
        ),
        statNumberLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            letterSpacing = (-1).sp,
            lineHeight = 60.sp,
            fontFeatureSettings = "tnum",
        ),
    )
}

val LocalWordDrillTypography = staticCompositionLocalOf<WordDrillTypography> {
    error("WordDrillTypography not provided. Wrap content in WordDrillTheme.")
}

/** 取当前主题的扩展字体 token（word / phonetic / partOfSpeech / meaning / statNumber）。 */
val MaterialTheme.wordDrillTypography: WordDrillTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalWordDrillTypography.current

// ── Material3 标准槽位 ───────────────────────────────────────
// 现有 Composable 通过 MaterialTheme.typography.* 引用 M3 标准槽位，
// 沿用默认值即可（系统无衬线字体）；本 ticket 只新增扩展 token，不重写标准槽位。

/** M3 标准槽位（沿用默认，系统无衬线）。13c 可能按需覆盖。 */
val AppTypography = Typography()
