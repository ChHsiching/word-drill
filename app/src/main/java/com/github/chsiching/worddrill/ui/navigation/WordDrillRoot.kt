package com.github.chsiching.worddrill.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.chsiching.worddrill.data.settings.NavStyle
import com.github.chsiching.worddrill.ui.drill.DrillScreen
import com.github.chsiching.worddrill.ui.library.LibraryScreen
import com.github.chsiching.worddrill.ui.library.WordListScreen
import com.github.chsiching.worddrill.ui.me.MeScreen
import com.github.chsiching.worddrill.ui.me.SettingsViewModel
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import kotlin.math.roundToInt

/** 「库」Tab 二级页路由（Ticket #7）：navigate("library/$bookId") 与 composable route 共用。 */
private const val WORD_LIST_ROUTE = "library/{bookId}"

/**
 * 应用根 Composable（Ticket #16 重写）：自定义底部导航 + NavHost。
 *
 * 替换 Material3 [androidx.compose.material3.NavigationBar]：
 * - 浮动胶囊（默认）：居中悬浮，圆角 100px，黑色滑动指示器，选中项文字反白
 * - 底部栏：全宽贴底，选中项亮度高亮（无黑块）
 * - 切换风格：先 fade out（0.2s opacity only）→ 切 class → fade in；指示器不飘移
 * - 简约导航：隐藏文字标签，padding 收缩
 * - 锁定态（仅 Drill Tab）：导航栏 opacity 淡出消失（不用位移）
 * - 切 Tab 自动解锁
 *
 * lock 状态 hoisted 在此：Drill Tab 的锁定按钮 toggle 本地 state，切 Tab reset。
 */
@Composable
fun WordDrillRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val navStyle by settingsViewModel.navStyle.collectAsStateWithLifecycle()
    val compactNav by settingsViewModel.compactNav.collectAsStateWithLifecycle()
    val hidePhonetic by settingsViewModel.hidePhonetic.collectAsStateWithLifecycle()

    // lock 状态 hoisted：切 Tab 自动解锁
    var drillLocked by remember { mutableStateOf(false) }

    val navigateTo: (String) -> Unit = { route ->
        if (route != currentRoute) {
            // 切 Tab 自动解锁
            if (currentRoute == TopDestination.Drill.route) drillLocked = false
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = TopDestination.Drill.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(TopDestination.Drill.route) {
                DrillScreen(
                    locked = drillLocked,
                    onToggleLock = { drillLocked = !drillLocked },
                    hidePhonetic = hidePhonetic,
                )
            }
            composable(TopDestination.Library.route) {
                LibraryScreen(
                    onOpenBook = { bookId -> navController.navigate("library/$bookId") },
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

        // 导航栏浮在内容上方
        BottomNavOverlay(
            currentRoute = currentRoute,
            navStyle = navStyle,
            compactNav = compactNav,
            hidden = currentRoute == TopDestination.Drill.route && drillLocked,
            onNavigate = navigateTo,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 浮在内容区上的导航栏：根据 [navStyle] 渲染浮动胶囊或全宽底部栏。
 * 锁定时整体 opacity 淡出（不用位移），淡出后仍占位但不接收点击（pointer-events:none）。
 */
@Composable
private fun BottomNavOverlay(
    currentRoute: String?,
    navStyle: NavStyle,
    compactNav: Boolean,
    hidden: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 隐藏态 opacity 0；显隐之间 0.2s opacity only 过渡
    val opacity by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navOpacity",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (hidden) Modifier.clickable(enabled = false) {} else Modifier),
    ) {
        if (opacity > 0.01f) {
            when (navStyle) {
                NavStyle.PILL -> PillNav(
                    currentRoute = currentRoute,
                    compactNav = compactNav,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { this.alpha = opacity },
                )
                NavStyle.BAR -> BarNav(
                    currentRoute = currentRoute,
                    compactNav = compactNav,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { this.alpha = opacity }
                        .windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        }
    }
}

/**
 * 浮动胶囊：圆角 100px，毛玻璃半透明（Surface alpha），黑色滑动指示器，选中项文字反白。
 *
 * 指示器实现：在 Row 上用 [drawBehind] 直接画黑色圆角矩形，位置 = selectedIndex × itemWidth。
 * itemWidth 在 onGloballyPositioned 里测量（Row 总宽 / 3），spring 动画由 animateFloatAsState 驱动。
 * 这比自定义 Layout 简单可靠（不依赖 measurables 顺序）。
 */
@Composable
private fun PillNav(
    currentRoute: String?,
    compactNav: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = topDestinations.indexOfFirst { it.route == currentRoute }.let { if (it < 0) 0 else it }
    val indicatorColor = MaterialTheme.colorScheme.onSurface

    var rowWidth by remember { mutableStateOf(0f) }
    val itemWidth = if (rowWidth > 0f) rowWidth / topDestinations.size else 0f

    val animatedX by animateFloatAsState(
        targetValue = selectedIndex * itemWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "indicatorX",
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(100.dp),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.wordDrillColors.separator),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(6.dp)
                .onGloballyPositioned { rowWidth = it.size.width.toFloat() }
                .drawBehind {
                    // 画黑色圆角矩形指示器（高度 = Row 高度，宽度 = itemWidth，x = animatedX）
                    if (itemWidth > 0f) {
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = androidx.compose.ui.geometry.Offset(animatedX, 0f),
                            size = androidx.compose.ui.geometry.Size(itemWidth, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        )
                    }
                },
        ) {
            topDestinations.forEachIndexed { index, dest ->
                NavItem(
                    destination = dest,
                    selected = index == selectedIndex,
                    compactNav = compactNav,
                    onNavigate = { onNavigate(dest.route) },
                    // 等宽：每个 item 用固定宽度，保证指示器等分
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * 底部栏：全宽贴底，选中项亮度高亮（无黑块）。
 */
@Composable
private fun BarNav(
    currentRoute: String?,
    compactNav: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.wordDrillColors.separator),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 24.dp),
        ) {
            topDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                BarNavItem(
                    destination = dest,
                    selected = selected,
                    compactNav = compactNav,
                    onNavigate = { onNavigate(dest.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: TopDestination,
    selected: Boolean,
    compactNav: Boolean,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNavigate,
            )
            .padding(horizontal = if (compactNav) 18.dp else 22.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = stringResource(destination.labelRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (!compactNav) {
            Text(
                text = stringResource(destination.labelRes),
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun BarNavItem(
    destination: TopDestination,
    selected: Boolean,
    compactNav: Boolean,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNavigate,
            )
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = stringResource(destination.labelRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (!compactNav) {
            Text(
                text = stringResource(destination.labelRes),
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
