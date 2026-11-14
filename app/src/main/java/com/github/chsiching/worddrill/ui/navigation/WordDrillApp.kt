package com.github.chsiching.worddrill.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector

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

private const val WORD_LIST_ROUTE_PREFIX = "library/"
private const val RECYCLE_BIN_ROUTE = "recycle_bin"

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
