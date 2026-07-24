package com.github.chsiching.worddrill.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ticket #15 — 锁定设计稿（designs/worddrill-ui/index.html :root）的色值和字体规范。
 *
 * 这些 token 是 13c UI 重写的基础；任何一个值漂了都会让 UI 偏离设计稿。
 * 用 JVM 单测（纯 Color/Float 常量断言）就能覆盖，不需要 instrumented。
 *
 * 没有断言 M3 ColorScheme 的 47 个 slot —— 那些是从这些原色派生的映射，
 * 断言原色更稳（DRY），M3 slot 之间的映射在 Theme.kt 里集中维护。
 */
class ThemeTokensTest {

    // ── 浅色：精确匹配 :root ────────────────────────────────
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

    // ── 深色：精确匹配 [data-theme="dark"] ──────────────────
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
        // 设计稿 --chip-bg in dark = #2C2C2E（与 surface-elevated 同色）
        assertThat(ChipBgDark).isEqualTo(Color(0xFF2C2C2E))
    }

    @Test
    fun darkProgressTrack_isDesignSpec38383A() {
        assertThat(ProgressTrackDark).isEqualTo(Color(0xFF38383A))
    }

    // ── 配色系统：近似纯黑白灰，无显著彩色 ──────────────────
    // 设计稿的 "secondary" 灰（#86868B / #98989F）有 ±5/255 的微小色差（≈ Apple 系统灰的
    // 蓝紫偏移），不是严格 R=G=B。容差 0.03 防误杀，同时仍能抓出明显彩色（>0.1）。
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

    // ── 深浅反转：text / bg 关系正确 ───────────────────────
    @Test
    fun lightTheme_textIsDarkOnLightBg() {
        // 浅色主题：文字比背景暗（亮度差大，保证可读性）
        assertThat(TextPrimaryLight.red).isLessThan(BgLight.red)
    }

    @Test
    fun darkTheme_textIsLightOnDarkBg() {
        // 深色主题：文字比背景亮
        assertThat(TextPrimaryDark.red).isGreaterThan(BgDark.red)
    }

    // ── Separator alpha ≈ 0.06 / 0.12（CSS rgba 值）─────────
    @Test
    fun lightSeparator_isRgbaBlack006() {
        // rgba(0,0,0,0.06) → alpha = 0.06 * 255 ≈ 15 → hex 0x0F
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

    // ── 字体规范：Charis SIL 家族是 bundled 字体，不是系统默认 ────
    @Test
    fun charisSilFontFamily_isNotSystemDefault() {
        // FontFamily.Default / SansSerif 是单例；Charis SIL 是 FontFamily(Font...) 构造的，
        // 引用必不同。这验证 R.font.charis_sil_* 资源成功被 FontFamily 引用（如果 res/font 缺失，
        // CharisSilFontFamily 会变成 unresolved FontFamily(Font(0)) 或构造失败）。
        assertThat(CharisSilFontFamily).isNotSameInstanceAs(FontFamily.Default)
        assertThat(CharisSilFontFamily).isNotSameInstanceAs(FontFamily.SansSerif)
    }
}
