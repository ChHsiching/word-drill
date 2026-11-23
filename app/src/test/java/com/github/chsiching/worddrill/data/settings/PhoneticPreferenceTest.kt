package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneticPreferenceTest {

    // --- Storage round-trip (default US, issue #58) ---

    @Test
    fun fromStorageName_roundTripsAllVariants() {
        assertThat(PhoneticPreference.fromStorageName("UK")).isEqualTo(PhoneticPreference.UK)
        assertThat(PhoneticPreference.fromStorageName("US")).isEqualTo(PhoneticPreference.US)
        assertThat(PhoneticPreference.fromStorageName("BOTH")).isEqualTo(PhoneticPreference.BOTH)
    }

    @Test
    fun fromStorageName_defaultsToUs_whenNull() {
        assertThat(PhoneticPreference.fromStorageName(null)).isEqualTo(PhoneticPreference.US)
    }

    @Test
    fun fromStorageName_defaultsToUs_whenUnknown() {
        assertThat(PhoneticPreference.fromStorageName("garbage")).isEqualTo(PhoneticPreference.US)
        assertThat(PhoneticPreference.fromStorageName("")).isEqualTo(PhoneticPreference.US)
        assertThat(PhoneticPreference.fromStorageName("us")).isEqualTo(PhoneticPreference.US)
    }

    // --- Card display resolution (ADR 0003 fallback: missing variant falls back
    //     to the available one; neither means no phonetic) ---

    @Test
    fun resolve_uk_returnsUk_whenBothExist() {
        assertThat(PhoneticPreference.UK.resolve("/ʌk/", "/ʌs/")).isEqualTo("/ʌk/")
    }

    @Test
    fun resolve_us_returnsUs_whenBothExist() {
        assertThat(PhoneticPreference.US.resolve("/ʌk/", "/ʌs/")).isEqualTo("/ʌs/")
    }

    @Test
    fun resolve_both_joinsWithASingleSpace() {
        assertThat(PhoneticPreference.BOTH.resolve("/ʌk/", "/ʌs/")).isEqualTo("/ʌk/ /ʌs/")
    }

    @Test
    fun resolve_us_fallsBackToUk_whenUsMissing() {
        assertThat(PhoneticPreference.US.resolve("/ʌk/", null)).isEqualTo("/ʌk/")
    }

    @Test
    fun resolve_uk_fallsBackToUs_whenUkMissing() {
        assertThat(PhoneticPreference.UK.resolve(null, "/ʌs/")).isEqualTo("/ʌs/")
    }

    @Test
    fun resolve_both_showsTheAvailableVariant_whenOnlyOneExists() {
        assertThat(PhoneticPreference.BOTH.resolve("/ʌk/", null)).isEqualTo("/ʌk/")
        assertThat(PhoneticPreference.BOTH.resolve(null, "/ʌs/")).isEqualTo("/ʌs/")
    }

    @Test
    fun resolve_both_showsSingleCopy_whenVariantsAreIdentical() {
        assertThat(PhoneticPreference.BOTH.resolve("/əˈbændən/", "/əˈbændən/"))
            .isEqualTo("/əˈbændən/")
    }

    @Test
    fun resolve_returnsNull_whenNeitherVariantExists() {
        assertThat(PhoneticPreference.UK.resolve(null, null)).isNull()
        assertThat(PhoneticPreference.US.resolve(null, null)).isNull()
        assertThat(PhoneticPreference.BOTH.resolve(null, null)).isNull()
    }

    @Test
    fun resolve_treatsBlankVariantsAsMissing() {
        assertThat(PhoneticPreference.US.resolve("  ", "/ʌs/")).isEqualTo("/ʌs/")
        assertThat(PhoneticPreference.US.resolve("", "")).isNull()
    }
    @Test
    fun segments_bothDiffering_labelsEachVariant() {
        val segs = PhoneticPreference.BOTH.segments("/ʌk/", "/ʌs/")
        assertThat(segs).hasSize(2)
        assertThat(segs[0].kind).isEqualTo(PhoneticPreference.Kind.UK)
        assertThat(segs[0].text).isEqualTo("/ʌk/")
        assertThat(segs[1].kind).isEqualTo(PhoneticPreference.Kind.US)
        assertThat(segs[1].text).isEqualTo("/ʌs/")
    }

    @Test
    fun segments_bothIdentical_singleUnlabeledSegment() {
        val segs = PhoneticPreference.BOTH.segments("/ə/", "/ə/")
        assertThat(segs).hasSize(1)
        assertThat(segs[0].kind).isEqualTo(PhoneticPreference.Kind.SHARED)
        assertThat(segs[0].text).isEqualTo("/ə/")
    }

    @Test
    fun segments_fallbackKeepsOriginLabel() {
        val segs = PhoneticPreference.US.segments("/ʌk/", null)
        assertThat(segs).hasSize(1)
        assertThat(segs[0].kind).isEqualTo(PhoneticPreference.Kind.UK)
    }

}
