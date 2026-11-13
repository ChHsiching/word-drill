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
// Ticket #15 typography spec (source: inline styles in designs/worddrill-ui/screens.jsx)
//
// The design spec splits typography into three groups:
// 1. English: system sans-serif (SF Pro / Roboto), 600-weight headlines, italic part of speech
// 2. Phonetics: Charis SIL (serif, bundled in res/font/)
// 3. Chinese: the device's native Chinese font (PingFang SC / Noto Sans SC), via system fallback
//
// Material3 [Typography] only covers the standard slots (displaySmall/titleMedium/bodyLarge...),
// which cannot hold the design spec's dedicated TextStyles (phonetics / part of speech /
// Chinese meaning). Those are added through [WordDrillTypography] and exposed via
// [LocalWordDrillTypography]. Usage:
//   MaterialTheme.wordDrillTypography.phonetic
// ═══════════════════════════════════════════════════════════

/** Charis SIL font family (for phonetics, bundled). Regular + Italic are enough (phonetics never use bold). */
val CharisSilFontFamily = FontFamily(
    Font(R.font.charis_sil_regular),
    Font(R.font.charis_sil_italic, style = FontStyle.Italic),
)

/**
 * Extended typography tokens: dedicated [TextStyle]s beyond the standard Material3 [Typography] slots.
 *
 * - [word]: large English word headline (44px / 600 / negative letter spacing)
 * - [phonetic]: phonetic transcription (Charis SIL / 17px)
 * - [partOfSpeech]: part of speech (italic / 15px / tertiary gray)
 * - [meaning]: Chinese meaning (22px / system native Chinese font; fontFamily=null falls back to PingFang SC / Noto Sans SC)
 * - [statNumber]: large stat number (tabular nums, monospaced alignment)
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
    // Note: colors are injected at call sites via MaterialTheme.colorScheme.*.
    // Only font size / weight / letter spacing / family are defined here (colors are
    // resolved by Composables, so they are not pinned down here).
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
            // fontFamily = null (default): system sans-serif + CJK fallback (PingFang SC / Noto Sans SC).
            // The design spec uses '"PingFang SC", -apple-system, ..., sans-serif'; on Android the
            // system default already covers this.
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

/** Extended typography tokens for the current theme (word / phonetic / partOfSpeech / meaning / statNumber). */
val MaterialTheme.wordDrillTypography: WordDrillTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalWordDrillTypography.current

// ── Material3 standard slots ────────────────────────────────
// Existing Composables reference the M3 standard slots via MaterialTheme.typography.*,
// so the defaults (system sans-serif) are kept; this ticket only adds the extended
// tokens and does not rewrite the standard slots.

/** M3 standard slots (defaults kept, system sans-serif). 13c may override as needed. */
val AppTypography = Typography()
