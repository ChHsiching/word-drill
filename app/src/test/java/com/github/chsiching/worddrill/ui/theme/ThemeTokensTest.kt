package com.github.chsiching.worddrill.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ticket #15 locks the color values and font spec of the design mock (designs/worddrill-ui/index.html :root).
 *
 * These tokens are the basis of the 13c UI rewrite. If any value drifts, the UI departs from the mock.
 * JVM unit tests with plain Color/Float constant assertions cover this, so instrumented tests are not needed.
 *
 * The tests do not assert the 47 slots of the M3 ColorScheme. Those slots are mappings derived from
 * these base colors, and asserting the base colors is more stable (DRY). The mapping among the M3
 * slots is maintained centrally in Theme.kt.
 */
class ThemeTokensTest {

    // ── Light tokens exactly match :root ────────────────────────────────
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

    // ── Dark tokens exactly match [data-theme="dark"] ──────────────────
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
        // The design mock sets --chip-bg in dark to #2C2C2E, the same color as surface-elevated.
        assertThat(ChipBgDark).isEqualTo(Color(0xFF2C2C2E))
    }

    @Test
    fun darkProgressTrack_isDesignSpec38383A() {
        assertThat(ProgressTrackDark).isEqualTo(Color(0xFF38383A))
    }

    // ── Palette is near pure black, white, and gray, with no strong chroma ──────────────────
    // The "secondary" grays in the design mock (#86868B / #98989F) have a tiny ±5/255 channel
    // spread, roughly the blue-purple shift of Apple system grays, so they are not strictly
    // R=G=B. The 0.03 tolerance avoids false failures while still catching obvious color (>0.1).
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

    // ── Light and dark themes invert the text and bg relation correctly ───────────────────────
    @Test
    fun lightTheme_textIsDarkOnLightBg() {
        // In the light theme the text is darker than the background. The large brightness gap keeps it readable.
        assertThat(TextPrimaryLight.red).isLessThan(BgLight.red)
    }

    @Test
    fun darkTheme_textIsLightOnDarkBg() {
        // In the dark theme the text is lighter than the background.
        assertThat(TextPrimaryDark.red).isGreaterThan(BgDark.red)
    }

    // ── Separator alpha ≈ 0.06 / 0.12, taken from the CSS rgba values ─────────
    @Test
    fun lightSeparator_isRgbaBlack006() {
        // rgba(0,0,0,0.06) gives alpha = 0.06 * 255, roughly 15, which is 0x0F in hex.
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
        // rgba(255,255,255,0.08)
        assertThat(SeparatorDark.alpha).isWithin(0.01f).of(0.08f)
        assertThat(SeparatorDark.red).isEqualTo(1f)
    }

    @Test
    fun darkSeparatorStrong_isRgbaWhite015() {
        assertThat(SeparatorStrongDark.alpha).isWithin(0.01f).of(0.15f)
    }

    // ── Font spec. The Charis SIL family is a bundled font, not the system default ────
    @Test
    fun charisSilFontFamily_isNotSystemDefault() {
        // FontFamily.Default and FontFamily.SansSerif are singletons, while Charis SIL is built with
        // the FontFamily(Font...) constructor, so the references are never the same. This verifies
        // that the FontFamily references the R.font.charis_sil_* resources. If res/font were missing,
        // CharisSilFontFamily would become an unresolved FontFamily(Font(0)) or the constructor would fail.
        assertThat(CharisSilFontFamily).isNotSameInstanceAs(FontFamily.Default)
        assertThat(CharisSilFontFamily).isNotSameInstanceAs(FontFamily.SansSerif)
    }
}
