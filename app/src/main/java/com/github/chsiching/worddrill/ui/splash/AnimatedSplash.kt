package com.github.chsiching.worddrill.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ticket #24 app launch animation overlay.
 *
 * Design spec: `designs/worddrill-icon/icon-03b-animation.html` (playable 5-phase prototype).
 * The coordinate system follows the design SVG viewBox `0 0 80 80`; the Canvas internally
 * scales by (iconPx / 80). Icon display size is 160dp (the design's 110px is about 39% of
 * screen width inside the 280px phone frame; a 1080px @ 420dpi screen works out to about
 * 160dp).
 *
 * The 5 elements each hold a pair of [Animatable]s: [ElementState.alpha] and
 * [ElementState.translateX] (design units). Animation drivers run in parallel inside
 * [LaunchedEffect] with [kotlinx.coroutines.launch], following the design timing:
 *
 * - Phase 1 (0ms): back card alpha 0 to 1, translateX +60 to 0 (spring)
 * - Phase 2 (200ms): front card alpha 0 to 1, translateX -60 to 0 (spring)
 * - Phase 3 (600/700/800ms): line1/line3 fly in from +40, line2 from -40 (spring)
 * - Phase 4 (1100-1500ms): pause (no action)
 * - Phase 5 (from 1500ms): staggered fade-out of line1, line2, line3, back card, front
 *   card; each a 500ms tween with a 100ms start interval (no waiting for the previous one
 *   to finish; they overlap in parallel); total duration 4x100 + 500 = 900ms
 *
 * With reduced motion (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) the animation is
 * skipped and [onFinished] fires directly.
 *
 * Caller (MainActivity): the main content is always in the composition underneath; this
 * overlay covers the top. When the animation completes it calls [onFinished], and the
 * caller removes the overlay to reveal the UI underneath.
 */
@Composable
fun AnimatedSplashOverlay(
    darkTheme: Boolean,
    onReady: () -> Unit,
    onFinished: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()

    val elements = remember {
        Element.entries.associateWith { e ->
            ElementState(
                alpha = Animatable(0f),
                translateX = Animatable(e.flyInOffset),
            )
        }
    }

    if (reduceMotion) {
        // Reduce motion: do not render the overlay; reveal the layer underneath directly.
        // Must call both onReady (release the system splash keep condition) and onFinished
        // (remove the overlay), otherwise setKeepOnScreenCondition never releases and the
        // system splash is stuck.
        LaunchedEffect(Unit) {
            onReady()
            onFinished()
        }
        return
    }

    LaunchedEffect(Unit) {
        // Composition done + frame clock ready: notify to release the system splash.
        // The overlay is already in the composition at this point; the animation starts
        // drawing on the next frame.
        onReady()
        elements.values.forEach { it.alpha.snapTo(0f) }
        elements.forEach { (e, st) -> st.translateX.snapTo(e.flyInOffset) }

        val springIn = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

        // Phase 1: back card outline flies in (0ms)
        launch {
            elements[Element.BACK_CARD]!!.let { b ->
                launch { b.alpha.animateTo(1f, springIn) }
                b.translateX.animateTo(0f, springIn)
            }
        }
        // Phase 2: solid front card flies in (200ms)
        launch {
            delay(200)
            elements[Element.FRONT_CARD]!!.let { f ->
                launch { f.alpha.animateTo(1f, springIn) }
                f.translateX.animateTo(0f, springIn)
            }
        }
        // Phase 3: three lines fly in staggered (600/700/800ms)
        launch {
            delay(600)
            elements[Element.LINE1]!!.let { l ->
                launch { l.alpha.animateTo(1f, springIn) }
                l.translateX.animateTo(0f, springIn)
            }
        }
        launch {
            delay(700)
            elements[Element.LINE2]!!.let { l ->
                launch { l.alpha.animateTo(1f, springIn) }
                l.translateX.animateTo(0f, springIn)
            }
        }
        launch {
            delay(800)
            elements[Element.LINE3]!!.let { l ->
                launch { l.alpha.animateTo(1f, springIn) }
                l.translateX.animateTo(0f, springIn)
            }
        }

        // Phase 5: staggered fade-out (stagger, not a pipeline).
        // Order: line1, line2, line3, back card, front card. Each element starts **without
        // waiting for the previous one to finish**, with a 100ms start interval (compact,
        // not sluggish); each fade-out is a spring (critically damped, same as fly-in).
        // Several elements sit mid fade-out at different progresses, giving a layered look.
        val fadeOutOrder = listOf(
            Element.LINE1, Element.LINE2, Element.LINE3,
            Element.BACK_CARD, Element.FRONT_CARD,
        )
        for ((idx, e) in fadeOutOrder.withIndex()) {
            val startTime = 1500L + idx * 100L
            launch {
                delay(startTime)
                elements[e]!!.alpha.animateTo(0f, springIn)
            }
        }

        // Reveal the layer underneath after all fade-outs finish: the last item (front
        // card) starts at 1500 + 4*100 = 1900ms, and the spring settles within about 500ms
        // at StiffnessMediumLow, so waiting 2500ms is enough.
        delay(2500L)
        onFinished()
    }

    val back = if (darkTheme) Color.White else Color.Black
    val onBack = if (darkTheme) Color.Black else Color(0xFFFAFAFA)
    val bg = if (darkTheme) Color.Black else Color(0xFFFAFAFA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        // Design spec: icon 110px inside the 280x500 phone frame is about 39% of screen width.
        // A typical 1080px @ 420dpi (2.625x) screen works out to about 421px = ~160dp.
        Canvas(modifier = Modifier.size(160.dp)) {
            // 110dp rendered pixels divided by 80 design units = scale
            val s = size.minDimension / 80f
            // scale maps the whole drawing domain from design space: every coordinate
            // (path / line / translate) is written in the design's 80-unit coordinates, and
            // scale enlarges them uniformly to real pixels.
            // Key point: path coordinates cannot be pre-multiplied by s (Path is built
            // outside the DrawScope, so s cannot be injected); the scale scope must enlarge
            // everything as a whole so the path follows.
            //
            // Vertical centering correction: the design content spans y [14,72] with center
            // 43, while the 80-unit canvas center is 40, a 3-unit difference. First
            // translate(0, -3f) aligns the content center with the canvas center, then scale.
            translate(left = 0f, top = -3f * s) {
                scale(s, pivot = Offset.Zero) {
                // back card
                elements[Element.BACK_CARD]?.let { st ->
                    val a = st.alpha.value
                    if (a > 0f) {
                        translate(left = st.translateX.value, top = 0f) {
                            drawPath(
                                path = roundRectPath(14f, 22f, 37f, 50f, 6f),
                                color = back,
                                alpha = a,
                                style = Stroke(width = 5f),
                            )
                        }
                    }
                }
                // front card
                elements[Element.FRONT_CARD]?.let { st ->
                    val a = st.alpha.value
                    if (a > 0f) {
                        translate(left = st.translateX.value, top = 0f) {
                            drawPath(
                                path = roundRectPath(29f, 14f, 37f, 50f, 6f),
                                color = back,
                                alpha = a,
                            )
                        }
                    }
                }
                // three lines (design-space coordinates)
                fun drawTextLine(st: ElementState?, x1: Float, y1: Float, x2: Float, y2: Float) {
                    if (st == null) return
                    val a = st.alpha.value
                    if (a <= 0f) return
                    translate(left = st.translateX.value, top = 0f) {
                        drawLine(
                            color = onBack,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 4f,
                            alpha = a,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                drawTextLine(elements[Element.LINE1], 38f, 28f, 58f, 28f)
                drawTextLine(elements[Element.LINE2], 38f, 38f, 52f, 38f)
                drawTextLine(elements[Element.LINE3], 38f, 48f, 55f, 48f)
                }
            }
        }
    }
}

/** Builds a rounded-rectangle [Path] in the design's 80-unit space (all parameters are design units). */
private fun roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            left = x, top = y, right = x + w, bottom = y + h,
            cornerRadius = CornerRadius(r, r),
        ),
    )
}

/** 5 elements: [flyInOffset] is in design-space units (±40/±60). */
private enum class Element(val flyInOffset: Float) {
    BACK_CARD(60f),    // back card outline: flies in from the right
    FRONT_CARD(-60f),  // solid front card: flies in from the left
    LINE1(40f),        // line 1: flies in from the right
    LINE2(-40f),       // line 2: flies in from the left
    LINE3(40f),        // line 3: flies in from the right
}

/** Holds an element's alpha / translateX pair of [Animatable]s, addressable by enum key. */
private class ElementState(
    val alpha: Animatable<Float, *>,
    val translateX: Animatable<Float, *>,
)

/**
 * Reads the system "remove animations" signal (Settings.Global.ANIMATOR_DURATION_SCALE == 0).
 * The value is 0 after the user enables Developer options, Remove animations, or
 * Accessibility, Remove animations. This is Android's de facto reduce-motion entry point
 * (equivalent to prefers-reduced-motion).
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}
