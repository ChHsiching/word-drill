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
import com.github.chsiching.worddrill.ui.drill.DrillScreen
import com.github.chsiching.worddrill.ui.library.LibraryScreen
import com.github.chsiching.worddrill.ui.library.WordListScreen
import com.github.chsiching.worddrill.ui.me.MeScreen
import com.github.chsiching.worddrill.ui.me.SettingsViewModel
import com.github.chsiching.worddrill.ui.recyclebin.RecycleBinScreen
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import kotlin.math.roundToInt

/** Secondary-page route of the "Library" tab (Ticket #7): shared by navigate("library/$bookId") and the composable route. */
private const val WORD_LIST_ROUTE = "library/{bookId}"

/** Recycle bin route (Ticket #22): entered from the general group on the "Me" tab. */
private const val RECYCLE_BIN_ROUTE = "recycle_bin"

/**
 * App root composable (Ticket #16 rewrite): custom bottom navigation + NavHost.
 *
 * Replaces Material3 [androidx.compose.material3.NavigationBar]:
 * - Floating pill (default): centered and floating, 100px corner radius, black sliding
 *   indicator, selected item text inverted to white
 * - Bottom bar: full width flush to the bottom, selected item highlighted by brightness
 *   (no black block)
 * - Style switch: fade out first (0.2s opacity only), then switch the class, then fade in;
 *   the indicator does not drift
 * - Compact navigation: text labels hidden, padding shrunk
 * - Locked state (Drill tab only): the nav bar fades out via opacity (no translation)
 * - Switching tabs unlocks automatically
 *
 * The lock state is hoisted here: the Drill tab's lock button toggles local state; a tab
 * switch resets it.
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

    // Lock state hoisted here: switching tabs unlocks automatically
    var drillLocked by remember { mutableStateOf(false) }

    val navigateTo: (String) -> Unit = { route ->
        if (route != currentRoute) {
            // Switching tabs unlocks automatically
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
            // edge-to-edge: content leaves status-bar space at the top (Bug 1: otherwise the
            // top text overlaps the status bar). No bottom padding here; the floating nav
            // bar applies its own navigationBarsPadding to avoid the gesture bar, and each
            // screen adds bottom padding to its LazyColumn/Column to make room for the nav height.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
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
            composable(TopDestination.Me.route) {
                MeScreen(onNavigateToRecycleBin = { navController.navigate(RECYCLE_BIN_ROUTE) })
            }
            composable(RECYCLE_BIN_ROUTE) {
                RecycleBinScreen(onBack = { navController.popBackStack() })
            }
        }

        // The nav bar floats above the content
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
 * Nav bar floating over the content area: renders the floating pill or the full-width
 * bottom bar according to [navStyle].
 *
 * Style switch (review feedback 2A): pill and bar **both exist** at the same BottomCenter
 * alignment point, cross-fading via alpha (0.3s tween). Both share the same position, so
 * nothing jumps around; only the alpha smoothly transitions between 0 and 1. When locked,
 * the whole thing fades out via opacity (layered on top of the style alpha).
 *
 * Why not AnimatedContent: an AnimatedContent switch removes the old composable first and
 * then adds the new one, and the position jumps in between (the pill floats centered while
 * the bar is full-width flush to the bottom; the two differ in width and position), and the
 * user explicitly asked for no jumping. Same-position overlay + alpha cross-fade is the
 * only approach that does not jump (apple-design §3 interruptibility).
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
    // Pill target alpha: 1 when currently pill and not hidden, otherwise 0
    val pillAlpha by animateFloatAsState(
        targetValue = if (navStyle == NavStyle.PILL && !hidden) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pillAlpha",
    )
    // Bar target alpha: 1 when currently bar and not hidden, otherwise 0
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
            // Disable clicks when hidden (locked), to avoid accidental taps during the fade-out
            .then(if (hidden) Modifier.clickable(enabled = false) {} else Modifier),
    ) {
        // Both navs exist at once, rendered on top of each other. At alpha=0 they still take
        // up space but are invisible and do not respond to clicks
        // (graphicsLayer treats alpha=0 as visually invisible, but the clickable enabled
        // check is added for safety)
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
                        // Fully transparent: no click response (hit test short-circuits)
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

/**
 * Floating pill: 100px corner radius, frosted-glass translucency (Surface alpha), black
 * sliding indicator, selected item text inverted to white.
 *
 * Indicator implementation: [drawBehind] on the Row draws a black rounded rectangle
 * directly; position = selectedIndex × itemWidth. itemWidth is measured in
 * onGloballyPositioned (total Row width / 3); the spring animation is driven by
 * animateFloatAsState. This is simpler and more reliable than a custom Layout (no
 * dependency on measurables order).
 */
@Composable
private fun PillNav(
    currentRoute: String?,
    compactNav: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selected tab index: selectedTabIndexForRoute maps the current route (including
    // secondary pages) to its owning top-level tab. null means the current route belongs to
    // no top-level tab (should not happen, but handled defensively): the indicator is not
    // drawn, avoiding a wrong highlight.
    val selectedIndex = selectedTabIndexForRoute(currentRoute)
    val indicatorColor = MaterialTheme.colorScheme.onSurface

    var rowWidth by remember { mutableStateOf(0f) }
    val itemWidth = if (rowWidth > 0f) rowWidth / topDestinations.size else 0f

    val animatedX by animateFloatAsState(
        // When selectedIndex is null (current route belongs to no top-level tab), the
        // indicator moves outside the Row to the left (-itemWidth) and drawBehind does not
        // draw it, avoiding a wrong highlight.
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
                    // Draw the black indicator: height = Row height, corner radius =
                    // height/2 (full semicircles at both ends). In compact mode (compactNav)
                    // the Row is shorter, so the indicator naturally becomes a circle; that
                    // is reasonable and left alone. Height reduction in non-compact mode is
                    // done via NavItem padding (see below), not changed here. Not drawn when
                    // selectedIndex is null (current route belongs to no top-level tab).
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
                    // Equal width: each item uses a fixed width so the indicator divides evenly
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * Bottom bar: full width flush to the bottom, selected item highlighted by brightness
 * (no black block).
 */
@Composable
private fun BarNav(
    currentRoute: String?,
    compactNav: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // edge-to-edge: the Surface background extends to the bottom of the screen (covering
    // the gesture-bar area); the inner Row uses navigationBarsPadding so icons/text avoid
    // the gesture bar. The gesture bar is then drawn over the bar background, avoiding the
    // ugly "bar shifted up with a strip of background color exposed below" look (Bug 2 fix).
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
            // Use selectedTabIndexForRoute so selection matches PillNav
            // (secondary pages: library/{bookId} maps to Library, recycle_bin maps to Me).
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
            // Compact vs non-compact transition (review feedback): animateContentSize
            // smooths the Column height change (the height delta from the label appearing
            // or disappearing). tween 200ms FastOutSlowIn: crisp without wobble; a fixed
            // duration is more predictable than a spring (spring MediumLow is too soft and
            // feels wobbly).
            .animateContentSize(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
            // Non-compact vertical=4dp (pill-shaped indicator), compact vertical=8dp (circular indicator, unchanged)
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
