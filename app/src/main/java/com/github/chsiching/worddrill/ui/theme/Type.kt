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

val CharisSilFontFamily = FontFamily(
    Font(R.font.charis_sil_regular),
    Font(R.font.charis_sil_italic, style = FontStyle.Italic),
)

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

val MaterialTheme.wordDrillTypography: WordDrillTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalWordDrillTypography.current

val AppTypography = Typography()
