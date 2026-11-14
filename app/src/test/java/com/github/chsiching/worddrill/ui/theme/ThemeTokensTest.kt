package com.github.chsiching.worddrill.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun lightBg_isDesignSpecFAFAFA() {
        assertThat(BgLight).isEqualTo(Color(0xFFFAFAFA))
    }

    @Test
    fun lightSurface_isPureWhite() {
        assertThat(SurfaceLight).isEqualTo(Color(0xFFFFFFFF))
    }

    @Test
    fun lightTextPrimary_isDesignSpec1A1A1A() {
        assertThat(TextPrimaryLight).isEqualTo(Color(0xFF1A1A1A))
    }

    @Test
    fun lightTextSecondary_isDesignSpec86868B() {
        assertThat(TextSecondaryLight).isEqualTo(Color(0xFF86868B))
    }

    @Test
    fun lightTextTertiary_isDesignSpecAEAEB2() {
        assertThat(TextTertiaryLight).isEqualTo(Color(0xFFAEAEB2))
    }

    @Test
    fun lightChipBg_isDesignSpecF5F5F7() {
        assertThat(ChipBgLight).isEqualTo(Color(0xFFF5F5F7))
    }

    @Test
    fun lightProgressTrack_isDesignSpecE8E8ED() {
        assertThat(ProgressTrackLight).isEqualTo(Color(0xFFE8E8ED))
    }

    @Test
    fun darkBg_isPureBlack() {
        assertThat(BgDark).isEqualTo(Color(0xFF000000))
    }

    @Test
    fun darkSurface_isDesignSpec1C1C1E() {
        assertThat(SurfaceDark).isEqualTo(Color(0xFF1C1C1E))
    }

    @Test
    fun darkSurfaceElevated_isDesignSpec2C2C2E() {
        assertThat(SurfaceElevatedDark).isEqualTo(Color(0xFF2C2C2E))
    }

    @Test
    fun darkTextPrimary_isPureWhite() {
        assertThat(TextPrimaryDark).isEqualTo(Color(0xFFFFFFFF))
    }

    @Test
    fun darkTextSecondary_isDesignSpec98989F() {
        assertThat(TextSecondaryDark).isEqualTo(Color(0xFF98989F))
    }

    @Test
    fun darkTextTertiary_isDesignSpec636366() {
        assertThat(TextTertiaryDark).isEqualTo(Color(0xFF636366))
    }

    @Test
    fun darkChipBg_isDesignSpec2C2C2E() {
        assertThat(ChipBgDark).isEqualTo(Color(0xFF2C2C2E))
    }

    @Test
    fun darkProgressTrack_isDesignSpec38383A() {
        assertThat(ProgressTrackDark).isEqualTo(Color(0xFF38383A))
    }

    @Test
    fun allLightTokens_areGrayscale_noChroma() {
        val tokens = listOf(
            BgLight, SurfaceLight, SurfaceElevatedLight, TextPrimaryLight,
            TextSecondaryLight, TextTertiaryLight, ChipBgLight, ProgressTrackLight,
        )
        tokens.forEach { color ->
            val (r, g, b) = Triple(color.red, color.green, color.blue)
            assertThat(maxOf(r, g, b) - minOf(r, g, b)).isLessThan(0.03f)
        }
    }

    @Test
    fun allDarkTokens_areGrayscale_noChroma() {
        val tokens = listOf(
            BgDark, SurfaceDark, SurfaceElevatedDark, TextPrimaryDark,
            TextSecondaryDark, TextTertiaryDark, ChipBgDark, ProgressTrackDark,
        )
        tokens.forEach { color ->
            val (r, g, b) = Triple(color.red, color.green, color.blue)
            assertThat(maxOf(r, g, b) - minOf(r, g, b)).isLessThan(0.03f)
        }
    }

    @Test
    fun lightTheme_textIsDarkOnLightBg() {
        assertThat(TextPrimaryLight.red).isLessThan(BgLight.red)
    }

    @Test
    fun darkTheme_textIsLightOnDarkBg() {
        assertThat(TextPrimaryDark.red).isGreaterThan(BgDark.red)
    }

    @Test
    fun lightSeparator_isRgbaBlack006() {
        assertThat(SeparatorLight.alpha).isWithin(0.01f).of(0.06f)
        assertThat(SeparatorLight.red).isEqualTo(0f)
        assertThat(SeparatorLight.green).isEqualTo(0f)
        assertThat(SeparatorLight.blue).isEqualTo(0f)
    }

    @Test
    fun lightSeparatorStrong_isRgbaBlack012() {
        assertThat(SeparatorStrongLight.alpha).isWithin(0.01f).of(0.12f)
    }

    @Test
    fun darkSeparator_isRgbaWhite008() {
        assertThat(SeparatorDark.alpha).isWithin(0.01f).of(0.08f)
        assertThat(SeparatorDark.red).isEqualTo(1f)
    }

    @Test
    fun darkSeparatorStrong_isRgbaWhite015() {
        assertThat(SeparatorStrongDark.alpha).isWithin(0.01f).of(0.15f)
    }

    @Test
    fun charisSilFontFamily_isNotSystemDefault() {
        assertThat(CharisSilFontFamily).isNotSameInstanceAs(FontFamily.Default)
        assertThat(CharisSilFontFamily).isNotSameInstanceAs(FontFamily.SansSerif)
    }
}
