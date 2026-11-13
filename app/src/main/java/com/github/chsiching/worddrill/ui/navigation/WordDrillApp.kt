package com.github.chsiching.worddrill.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations. Routes, labels, and icons of the three tabs are managed centrally.
 * Route strings correspond to the routes of [androidx.navigation.NavHostController].
 */
sealed class TopDestination(val route: String, val labelRes: Int, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    data object Drill : TopDestination(
        route = "drill",
        labelRes = com.github.chsiching.worddrill.R.string.tab_drill,
        selectedIcon = Icons.Filled.Style,
        unselectedIcon = Icons.Outlined.Style,
    )
    data object Library : TopDestination(
        route = "library",
        labelRes = com.github.chsiching.worddrill.R.string.tab_library,
        selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
        unselectedIcon = Icons.AutoMirrored.Outlined.LibraryBooks,
    )
    data object Me : TopDestination(
        route = "me",
        labelRes = com.github.chsiching.worddrill.R.string.tab_me,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    )
}

val topDestinations = listOf(TopDestination.Drill, TopDestination.Library, TopDestination.Me)

/**
 * Secondary-page route prefix/literals (matching the composable route constant values in
 * [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot]). [selectedTabIndexForRoute]
 * uses them to attribute secondary pages to the correct top-level tab (for nav bar
 * highlighting).
 */
private const val WORD_LIST_ROUTE_PREFIX = "library/"
private const val RECYCLE_BIN_ROUTE = "recycle_bin"

/**
 * Maps any route to the index of its owning top-level tab (for nav bar highlighting).
 *
 * - Exact match with a [TopDestination.route] gives that tab
 * - `library/{bookId}` (word list inside a book, [com.github.chsiching.worddrill.ui.library.WordListScreen])
 *   prefix-matches to the Library tab
 * - `recycle_bin` (recycle bin, [com.github.chsiching.worddrill.ui.recyclebin.RecycleBinScreen])
 *   maps to the Me tab (entered from "Me"; semantically belongs to the "Me" branch)
 * - No match gives null (draw no indicator and highlight nothing; better than wrongly
 *   highlighting the first tab)
 *
 * Pure function, easy to cover each route-to-tab mapping in JVM unit tests.
 */
fun selectedTabIndexForRoute(route: String?): Int? {
    if (route == null) return null
    topDestinations.forEachIndexed { index, dest ->
        if (route == dest.route) return index
    }
    return when {
        route.startsWith(WORD_LIST_ROUTE_PREFIX) ->
            topDestinations.indexOfFirst { it.route == TopDestination.Library.route }
        route == RECYCLE_BIN_ROUTE ->
            topDestinations.indexOfFirst { it.route == TopDestination.Me.route }
        else -> null
    }
}
