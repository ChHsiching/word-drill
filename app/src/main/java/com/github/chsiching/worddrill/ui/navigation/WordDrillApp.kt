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
 * 顶层目的地定义。三个 Tab 的路由、文案、图标集中管理。
 * 路由字符串与 [androidx.navigation.NavHostController] 的 route 对应。
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
