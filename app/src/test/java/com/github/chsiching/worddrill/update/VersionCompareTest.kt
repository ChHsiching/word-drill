package com.github.chsiching.worddrill.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionCompareTest {

    @Test
    fun isNewerVersion_comparesDottedNumericVersions() {
        // Release tags carry a leading "v" (release.yml publishes v* tags); the running
        // build's VERSION_NAME never does.
        assertThat(isNewerVersion(remote = "v0.3.0", current = "0.2.1")).isTrue()
        assertThat(isNewerVersion(remote = "0.2.2", current = "0.2.1")).isTrue()
        // Numeric, not lexicographic: "0.2.10" is newer than "0.2.9".
        assertThat(isNewerVersion(remote = "v0.2.10", current = "0.2.9")).isTrue()
        // A higher minor wins even against a longer remote suffix.
        assertThat(isNewerVersion(remote = "0.3", current = "0.2.9")).isTrue()

        // Equal, older, or equal-with-prefix are all "not newer".
        assertThat(isNewerVersion(remote = "v0.2.1", current = "0.2.1")).isFalse()
        assertThat(isNewerVersion(remote = "v0.1.9", current = "0.2.1")).isFalse()
        assertThat(isNewerVersion(remote = "0.2", current = "0.2.0")).isFalse()
    }
}
