package com.github.chsiching.worddrill.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Circular Reveal 主题切换（审核反馈 5 重写）。
 *
 * 之前错误：只画纯色圆覆盖内容。正确效果：圆内是完整的新主题 UI（背景+文字+所有元素），
 * 圆外是旧主题 UI。所以需要 app 内容渲染两遍（旧 + 新 overlay），新主题被 clip 成圆形。
 *
 * 用法：在 [ThemeRevealContent] 里渲染 app 内容，它会自动处理双主题叠加 + clip 动画。
 * 触发点通过 [LocalThemeRevealTrigger] 传入 icon 中心坐标 + 目标主题。
 *
 * @param currentTheme 当前渲染的主题（传给 content lambda 决定用哪套 ColorScheme）
 * @param content 渲染 app 内容的 lambda，接收「应该用哪个主题渲染」参数
 */
@Composable
fun <T> ThemeRevealContent(
    currentTheme: T,
    content: @Composable (theme: T) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // overlay 主题（reveal 期间用，null = 无 overlay）
    var overlayTheme by remember { mutableStateOf<T?>(null) }
    // 触发点（屏幕坐标 px）
    var revealCenter by remember { mutableStateOf(Offset.Zero) }
    // reveal 进度 0→1
    val revealProgress = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }

    // 动态圆形 Shape（半径随 revealProgress 变）
    val circleShape = remember(revealCenter, revealProgress.value) {
        CircleRevealShape(revealCenter, revealProgress.value)
    }

    val trigger: ThemeRevealTrigger = { center, startReveal ->
        if (!animating) {
            revealCenter = center
            animating = true
            startReveal { newTheme ->
                // startReveal 回调返回目标主题；设为 overlay，开始动画
                @Suppress("UNCHECKED_CAST")
                overlayTheme = newTheme as T
                scope.launch {
                    revealProgress.snapTo(0f)
                    revealProgress.animateTo(1f, animationSpec = tween(700))
                    animating = false
                    overlayTheme = null
                }
            }
        }
    }

    // 底层：当前主题
    content(currentTheme)

    // 顶层 overlay：新主题（如果有），被 clip 成圆形
    overlayTheme?.let { ot ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(circleShape),
        ) {
            content(ot)
        }
    }
}

/** 触发 reveal。参数：icon 中心坐标 + 启动回调（回调接收一个 setTheme 函数）。 */
typealias ThemeRevealTrigger = (center: Offset, startReveal: (setOverlayTheme: (Any?) -> Unit) -> Unit) -> Unit

val LocalThemeRevealTrigger = compositionLocalOf<ThemeRevealTrigger> {
    // 默认无 reveal：直接调 startReveal 但不设 overlay（退化为瞬间切，无动画）
    { _, startReveal -> startReveal {} }
}

/**
 * 以 [center] 为圆心、半径 = maxRadius × [progress] 的圆形 Shape。
 * 用于 clip overlay（新主题只在圆内可见）。
 */
private class CircleRevealShape(
    private val center: Offset,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline {
        val maxRadius = listOf(
            hypot(center.x, center.y),
            hypot(size.width - center.x, center.y),
            hypot(center.x, size.height - center.y),
            hypot(size.width - center.x, size.height - center.y),
        ).max()
        val radius = maxRadius * progress
        val path = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = center,
                    radius = radius,
                ),
            )
        }
        return Outline.Generic(path)
    }
}
