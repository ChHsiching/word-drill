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
                val bmp = captureView(view)
                snapshot = bmp?.asImageBitmap()
                onApplied()
                onThemeApplied()
                revealProgress.snapTo(0f)
                revealProgress.animateTo(1f, animationSpec = tween(800))
                snapshot = null
                animating = false
            }
        }
    }

    val progress by revealProgress.asState()

    CompositionLocalProvider(LocalThemeRevealTrigger provides trigger) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
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

// View.draw must run on the main thread; synchronous by design (no suspend image-capture API).
private fun captureView(view: View): Bitmap? {
    val w = view.width
    val h = view.height
    if (w <= 0 || h <= 0) return null
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    return bitmap
}

typealias ThemeRevealTrigger = (center: Offset, onApplied: () -> Unit) -> Unit

val LocalThemeRevealTrigger = compositionLocalOf<ThemeRevealTrigger> {
    { _, onApplied -> onApplied() } // Default without reveal: switch directly
}
