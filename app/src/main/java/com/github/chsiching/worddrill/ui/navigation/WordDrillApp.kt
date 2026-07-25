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

/**
 * 二级页路由前缀/字面量（与 [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot]
 * 里的 composable route 常量值一致）。[selectedTabIndexForRoute] 用它们把二级页
 * 归属到正确的顶层 Tab（高亮导航栏用）。
 */
private const val WORD_LIST_ROUTE_PREFIX = "library/"
private const val RECYCLE_BIN_ROUTE = "recycle_bin"

/**
 * 把任意 route 映射到所属顶层 Tab 的索引（用于导航栏高亮）。
 *
 * - 精确匹配某 [TopDestination.route] → 该 Tab
 * - `library/{bookId}`（词书内词条列表，[com.github.chsiching.worddrill.ui.library.WordListScreen]）
 *   前缀匹配 → Library Tab
 * - `recycle_bin`（回收站，[com.github.chsiching.worddrill.ui.recyclebin.RecycleBinScreen]）
 *   → Me Tab（从「我的」进入，语义属「我的」分支）
 * - 都不匹配 → null（不画指示器/不高亮任一项；好过错误地高亮第一个 Tab）
 *
 * 纯函数，便于 JVM 单测覆盖各 route → Tab 归属。
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
