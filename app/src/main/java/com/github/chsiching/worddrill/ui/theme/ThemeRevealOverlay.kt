package com.github.chsiching.worddrill.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * Circular Reveal theme switch (review feedback 5 v6, View snapshot approach).
 *
 * Effect: the new theme (background + text + cards + the full UI) expands as a circle
 * from the icon center to fill the screen.
 *
 * Approach: capture the old theme synchronously with Android View.draw(Canvas) (skipping the
 * suspend toImageBitmap path to avoid timing problems); after the theme switch, an overlay
 * holding the snapshot is clipped by an expanding circle to reveal the new theme.
 *
 * Flow:
 * 1. trigger(center, targetColor, onApplied) is called
 * 2. The current View is synchronously snapshotted (old theme) into an ImageBitmap
 * 3. The theme switches immediately (the layer below becomes the new theme, hidden by the snapshot)
 * 4. Animate: the snapshot is clipped to an expanding circle; inside the circle the new theme
 *    shows through, outside is the old snapshot
 * 5. When the animation ends, the overlay is removed
 */
@Composable
fun ThemeRevealContent(
    onThemeApplied: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealCenter by remember { mutableStateOf(Offset.Zero) }
    val revealProgress = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }

    val trigger: ThemeRevealTrigger = { center, onApplied ->
        if (!animating) {
            revealCenter = center
            animating = true
            scope.launch(Dispatchers.Main) {
                // 1. Synchronously snapshot the current View (old theme)
                val bmp = captureView(view)
                snapshot = bmp?.asImageBitmap()
                // 2. Switch the theme: write DataStore + update renderedTheme.
                //    The layer below becomes the new theme immediately, but is hidden by the snapshot overlay
                onApplied()
                onThemeApplied()
                // 3. Animate: the circle expands from the icon; inside the circle the new theme
                //    shows through, outside is the old snapshot
                revealProgress.snapTo(0f)
                revealProgress.animateTo(1f, animationSpec = tween(800))
                // 4. When the animation ends, remove the overlay (the layer below is already
                //    the new theme)
                snapshot = null
                animating = false
            }
        }
    }

    val progress by revealProgress.asState()

    CompositionLocalProvider(LocalThemeRevealTrigger provides trigger) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            // Overlay: old-theme snapshot; the new theme is revealed inside the circle
            // (the snapshot is clipped away by the expanding circle)
            snapshot?.let { snap ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = revealCenter
                    val maxRadius = maxOf(
                        hypot(center.x, center.y),
                        hypot(size.width - center.x, center.y),
                        hypot(center.x, size.height - center.y),
                        hypot(size.width - center.x, size.height - center.y),
                    )
                    val radius = maxRadius * progress
                    // Draw the old snapshot, visible only **outside** the circle (Difference = full screen minus the circle)
                    clipPath(
                        Path().apply {
                            addOval(Rect(center = center, radius = radius))
                        },
                        clipOp = androidx.compose.ui.graphics.ClipOp.Difference,
                    ) {
                        drawImage(snap)
                    }
                }
            }
        }
    }
}

/**
 * Synchronously snapshots a View into a Bitmap. Uses View.draw(Canvas), no suspend API.
 * Call on the main thread (View.draw must run on the main thread). Returns null when the
 * snapshot fails (width or height 0).
 */
private fun captureView(view: View): Bitmap? {
    val w = view.width
    val h = view.height
    if (w <= 0 || h <= 0) return null
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    return bitmap
}

/**
 * Triggers the reveal.
 * - [Offset]: circle center (icon screen coordinates)
 * - [() -> Unit]: called right after the snapshot (write DataStore + update renderedTheme)
 */
typealias ThemeRevealTrigger = (center: Offset, onApplied: () -> Unit) -> Unit

val LocalThemeRevealTrigger = compositionLocalOf<ThemeRevealTrigger> {
    { _, onApplied -> onApplied() } // Default without reveal: switch directly
}
