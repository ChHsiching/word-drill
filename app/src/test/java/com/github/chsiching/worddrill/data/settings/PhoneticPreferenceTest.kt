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

    // --- Display resolution (ADR 0003 fallback: missing variant falls back
    //     to the available one; neither means no phonetic) ---

    @Test
    fun segments_singlePreference_showsOnlyTheChosenVariant() {
        assertThat(PhoneticPreference.UK.segments("/ʌk/", "/ʌs/").map { it.text })
            .containsExactly("/ʌk/")
        assertThat(PhoneticPreference.US.segments("/ʌk/", "/ʌs/").map { it.text })
            .containsExactly("/ʌs/")
    }

    @Test
    fun segments_singlePreference_fallsBackToTheOtherVariant() {
        assertThat(PhoneticPreference.US.segments("/ʌk/", null).map { it.text })
            .containsExactly("/ʌk/")
        assertThat(PhoneticPreference.UK.segments(null, "/ʌs/").map { it.text })
            .containsExactly("/ʌs/")
    }

    @Test
    fun segments_both_showsUkThenUs_whenBothExist() {
        assertThat(PhoneticPreference.BOTH.segments("/ʌk/", "/ʌs/").map { it.text })
            .containsExactly("/ʌk/", "/ʌs/")
    }

    @Test
    fun segments_both_showsTheAvailableVariant_whenOnlyOneExists() {
        assertThat(PhoneticPreference.BOTH.segments("/ʌk/", null).map { it.text })
            .containsExactly("/ʌk/")
        assertThat(PhoneticPreference.BOTH.segments(null, "/ʌs/").map { it.text })
            .containsExactly("/ʌs/")
    }

    @Test
    fun segments_areEmpty_whenNeitherVariantExists() {
        assertThat(PhoneticPreference.UK.segments(null, null)).isEmpty()
        assertThat(PhoneticPreference.US.segments(null, null)).isEmpty()
        assertThat(PhoneticPreference.BOTH.segments(null, null)).isEmpty()
    }

    @Test
    fun segments_treatsBlankVariantsAsMissing() {
        assertThat(PhoneticPreference.US.segments("  ", "/ʌs/").map { it.text })
            .containsExactly("/ʌs/")
        assertThat(PhoneticPreference.US.segments("", "")).isEmpty()
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
