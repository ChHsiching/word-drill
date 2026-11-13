package com.github.chsiching.worddrill.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [selectedTabIndexForRoute]: which tab the bottom bar highlights.
 * Fixes the bug where entering the recycle bin highlighted the "Drill" tab.
 *
 * Covers:
 * - The three top-level routes each match their own tab exactly
 * - Secondary route library/{bookId} maps to the Library Tab (also covers a pre-existing bug of the same kind)
 * - Secondary route recycle_bin maps to the Me Tab (the target of this fix)
 * - Unknown route / null maps to null (no tab highlighted)
 */
class SelectedTabIndexForRouteTest {

    @Test
    fun exactMatch_drill() {
        assertThat(selectedTabIndexForRoute("drill"))
            .isEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun exactMatch_library() {
        assertThat(selectedTabIndexForRoute("library"))
            .isEqualTo(topDestinations.indexOfFirst { it.route == "library" })
    }

    @Test
    fun exactMatch_me() {
        assertThat(selectedTabIndexForRoute("me"))
            .isEqualTo(topDestinations.indexOfFirst { it.route == "me" })
    }

    @Test
    fun wordListRoute_mapsToLibrary() {
        // The library/{bookId} secondary page belongs to the Library Tab. The old fallback to 0/Drill was the bug.
        val idx = selectedTabIndexForRoute("library/123")
        assertThat(idx).isEqualTo(topDestinations.indexOfFirst { it.route == "library" })
        assertThat(idx).isNotEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun recycleBinRoute_mapsToMe() {
        // recycle_bin is entered from "Me", so it belongs to the Me Tab. This is the core of the fix.
        val idx = selectedTabIndexForRoute("recycle_bin")
        assertThat(idx).isEqualTo(topDestinations.indexOfFirst { it.route == "me" })
        assertThat(idx).isNotEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun unknownRoute_returnsNull() {
        // An unknown route highlights no tab, which beats wrongly highlighting the first one.
        assertThat(selectedTabIndexForRoute("unknown")).isNull()
    }

    @Test
    fun nullRoute_returnsNull() {
        assertThat(selectedTabIndexForRoute(null)).isNull()
    }
}
