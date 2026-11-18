package com.github.chsiching.worddrill.ui.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.github.chsiching.worddrill.ui.drill.AutoAdvanceHairline
import com.github.chsiching.worddrill.ui.drill.AutoAdvanceOverlayViewModel
import com.github.chsiching.worddrill.ui.drill.DrillScreen
import com.github.chsiching.worddrill.ui.library.LibraryScreen
import com.github.chsiching.worddrill.ui.library.WordListScreen
import com.github.chsiching.worddrill.ui.me.MeScreen
import com.github.chsiching.worddrill.ui.me.SettingsViewModel
import com.github.chsiching.worddrill.ui.recyclebin.RecycleBinScreen
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import kotlin.math.roundToInt

private const val WORD_LIST_ROUTE = "library/{bookId}"

private const val RECYCLE_BIN_ROUTE = "recycle_bin"

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

    val overlayViewModel: AutoAdvanceOverlayViewModel = hiltViewModel()
    val dwellFraction by overlayViewModel.fraction.collectAsStateWithLifecycle()

    var drillLocked by remember { mutableStateOf(false) }

    val navigateTo: (String) -> Unit = { route ->
        if (route != currentRoute) {
            if (currentRoute == TopDestination.Drill.route) drillLocked = false
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = TopDestination.Drill.route,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            composable(TopDestination.Drill.route) {
                DrillScreen(
                    locked = drillLocked,
                    onToggleLock = { drillLocked = !drillLocked },
                    hidePhonetic = hidePhonetic,
                    onSwitchBook = { navigateTo(TopDestination.Library.route) },
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
            composable(TopDestination.Me.route) {
                MeScreen(onNavigateToRecycleBin = { navController.navigate(RECYCLE_BIN_ROUTE) })
            }
            composable(RECYCLE_BIN_ROUTE) {
                RecycleBinScreen(onBack = { navController.popBackStack() })
            }
        }

        BottomNavOverlay(
            currentRoute = currentRoute,
            navStyle = navStyle,
            compactNav = compactNav,
            hidden = currentRoute == TopDestination.Drill.route && drillLocked,
            onNavigate = navigateTo,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // The dwell hairline always sits at the screen's very bottom edge, drawn on
        // top of everything including the bottom nav (owner decision 2026-11-18):
        // composed after the nav overlay so it floats above both nav modes.
        if (dwellFraction > 0f) {
            AutoAdvanceHairline(
                elapsedFraction = dwellFraction,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun BottomNavOverlay(
    currentRoute: String?,
    navStyle: NavStyle,
    compactNav: Boolean,
    hidden: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillAlpha by animateFloatAsState(
        targetValue = if (navStyle == NavStyle.PILL && !hidden) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pillAlpha",
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (navStyle == NavStyle.BAR && !hidden) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "barAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (hidden) Modifier.clickable(enabled = false) {} else Modifier),
    ) {
        if (pillAlpha > 0.01f) {
            PillNav(
                currentRoute = currentRoute,
                compactNav = compactNav,
                onNavigate = onNavigate,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        this.alpha = pillAlpha
                        if (pillAlpha < 0.5f) this.alpha = pillAlpha
                    }
                    .then(if (pillAlpha < 0.5f) Modifier.clickable(enabled = false) {} else Modifier),
            )
        }
        if (barAlpha > 0.01f) {
            BarNav(
                currentRoute = currentRoute,
                compactNav = compactNav,
                onNavigate = onNavigate,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { this.alpha = barAlpha }
                    .then(if (barAlpha < 0.5f) Modifier.clickable(enabled = false) {} else Modifier),
            )
        }
    }
}

@Composable
private fun PillNav(
    currentRoute: String?,
    compactNav: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = selectedTabIndexForRoute(currentRoute)
    val indicatorColor = MaterialTheme.colorScheme.onSurface

    var rowWidth by remember { mutableStateOf(0f) }
    val itemWidth = if (rowWidth > 0f) rowWidth / topDestinations.size else 0f

    val animatedX by animateFloatAsState(
        targetValue = (selectedIndex ?: -1) * itemWidth,
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
                    if (selectedIndex != null && itemWidth > 0f) {
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
                    modifier = Modifier,
                )
            }
        }
    }
}

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
                .navigationBarsPadding()
                .padding(top = 6.dp),
        ) {
            val selectedIndex = selectedTabIndexForRoute(currentRoute)
            topDestinations.forEachIndexed { index, dest ->
                val selected = index == selectedIndex
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
            .animateContentSize(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
            .padding(horizontal = if (compactNav) 18.dp else 22.dp, vertical = if (compactNav) 8.dp else 4.dp),
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
