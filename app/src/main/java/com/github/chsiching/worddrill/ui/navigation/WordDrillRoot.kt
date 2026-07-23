package com.github.chsiching.worddrill.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.chsiching.worddrill.ui.drill.DrillScreen
import com.github.chsiching.worddrill.ui.library.LibraryScreen
import com.github.chsiching.worddrill.ui.library.WordListScreen
import com.github.chsiching.worddrill.ui.me.MeScreen

/** 「库」Tab 二级页路由（Ticket #7）：navigate("library/$bookId") 与 composable route 共用。 */
private const val WORD_LIST_ROUTE = "library/{bookId}"

/**
 * 应用根 Composable：Scaffold + 底部导航 + NavHost。
 * 底部三个 Tab 切换时保留各自的状态（saveState/restoreState）。
 */
@Composable
fun WordDrillRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                topDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // 切换 Tab 时恢复到该 Tab 上次的状态与回栈
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Drill.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDestination.Drill.route) { DrillScreen() }
            composable(TopDestination.Library.route) {
                LibraryScreen(
                    onOpenBook = { bookId ->
                        navController.navigate("library/$bookId")
                    },
                )
            }
            composable(
                route = WORD_LIST_ROUTE,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
            ) {
                WordListScreen(onBack = { navController.popBackStack() })
            }
            composable(TopDestination.Me.route) { MeScreen() }
        }
    }
}
