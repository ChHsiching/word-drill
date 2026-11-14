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
        // Both callbacks are required: onReady releases the system splash keep-condition
        // (setKeepOnScreenCondition in MainActivity); onFinished removes this overlay.
        LaunchedEffect(Unit) {
            onReady()
            onFinished()
        }
        return
    }

    LaunchedEffect(Unit) {
        onReady()
        elements.values.forEach { it.alpha.snapTo(0f) }
        elements.forEach { (e, st) -> st.translateX.snapTo(e.flyInOffset) }

        val springIn = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

        launch {
            elements[Element.BACK_CARD]!!.let { b ->
                launch { b.alpha.animateTo(1f, springIn) }
                b.translateX.animateTo(0f, springIn)
            }
        }
        launch {
            delay(200)
            elements[Element.FRONT_CARD]!!.let { f ->
                launch { f.alpha.animateTo(1f, springIn) }
                f.translateX.animateTo(0f, springIn)
            }
        }
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

        val fadeOutOrder = listOf(
            Element.LINE1, Element.LINE2, Element.LINE3,
            Element.BACK_CARD, Element.FRONT_CARD,
        )
        val fadeOutStartMs = 1500L
        val fadeOutStaggerMs = 100L
        for ((idx, e) in fadeOutOrder.withIndex()) {
            launch {
                delay(fadeOutStartMs + idx * fadeOutStaggerMs)
                elements[e]!!.alpha.animateTo(0f, springIn)
            }
        }

        val settleMs = 600L
        delay(fadeOutStartMs + (fadeOutOrder.size - 1) * fadeOutStaggerMs + settleMs)
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
        Canvas(modifier = Modifier.size(160.dp)) {
            // All drawing coordinates are in the design's 80-unit space; scale enlarges the
            // whole scope (paths are built outside DrawScope, so coordinates cannot be
            // pre-multiplied). The -3f centers the design content (spanning y [14,72]).
            val s = size.minDimension / 80f
            translate(left = 0f, top = -3f * s) {
                scale(s, pivot = Offset.Zero) {
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

private fun roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            left = x, top = y, right = x + w, bottom = y + h,
            cornerRadius = CornerRadius(r, r),
        ),
    )
}

private enum class Element(val flyInOffset: Float) {
    BACK_CARD(60f),    // back card outline: flies in from the right
    FRONT_CARD(-60f),  // solid front card: flies in from the left
    LINE1(40f),        // line 1: flies in from the right
    LINE2(-40f),       // line 2: flies in from the left
    LINE3(40f),        // line 3: flies in from the right
}

private class ElementState(
    val alpha: Animatable<Float, *>,
    val translateX: Animatable<Float, *>,
)

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
