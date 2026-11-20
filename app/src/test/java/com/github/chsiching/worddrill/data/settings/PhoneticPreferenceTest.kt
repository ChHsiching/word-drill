package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneticPreferenceTest {

    // --- Storage round-trip (default 美式, issue #58) ---

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
}
