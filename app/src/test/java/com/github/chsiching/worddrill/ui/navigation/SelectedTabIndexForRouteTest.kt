package com.github.chsiching.worddrill.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
        val idx = selectedTabIndexForRoute("library/123")
        assertThat(idx).isEqualTo(topDestinations.indexOfFirst { it.route == "library" })
        assertThat(idx).isNotEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun recycleBinRoute_mapsToMe() {
        val idx = selectedTabIndexForRoute("recycle_bin")
        assertThat(idx).isEqualTo(topDestinations.indexOfFirst { it.route == "me" })
        assertThat(idx).isNotEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun unknownRoute_returnsNull() {
        assertThat(selectedTabIndexForRoute("unknown")).isNull()
    }

    @Test
    fun nullRoute_returnsNull() {
        assertThat(selectedTabIndexForRoute(null)).isNull()
    }
}
