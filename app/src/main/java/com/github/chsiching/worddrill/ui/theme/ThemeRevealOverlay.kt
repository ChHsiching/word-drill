package com.github.chsiching.worddrill.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Circular Reveal 主题切换（审核反馈 5）。
 *
 * 效果：点击主题切换 icon 后，以 icon 为圆心，新主题的背景色呈圆形从 icon 扩散到全屏。
 *
 * 流程：
 * 1. MeScreen 的 icon 点击 → [LocalThemeRevealTrigger].invoke(iconCenter, targetTheme)
 * 2. ThemeRevealBox 启动 Animatable(0→1)，圆从 icon 扩展
 * 3. 圆到 50% → [onApplyTheme](targetTheme) 真切 ColorScheme（被圆覆盖看不见接缝）
 * 4. 圆到 100% → 动画结束，移除 overlay
 *
 * targetBgColor 用目标主题背景色（深色=黑/浅色=白）。
 *
 * @param targetBgColor reveal 圆的颜色（目标主题背景色）
 * @param onApplyTheme 圆扩展到 50% 时调用，参数是目标主题（在这里真切 ColorScheme）
 */
@Composable
fun ThemeRevealBox(
    targetBgColor: Color,
    onApplyTheme: (targetIsDark: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var triggerCenter by remember { mutableStateOf<Offset?>(null) }
    val revealProgress = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }
    var pendingTargetIsDark by remember { mutableStateOf(false) }

    val trigger: ThemeRevealTrigger = { center, targetIsDark ->
        if (!animating) {
            triggerCenter = center
            pendingTargetIsDark = targetIsDark
            animating = true
            scope.launch {
                revealProgress.snapTo(0f)
                revealProgress.animateTo(0.5f, animationSpec = tween(400))
                onApplyTheme(targetIsDark)
                revealProgress.animateTo(1f, animationSpec = tween(400))
                animating = false
                triggerCenter = null
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalThemeRevealTrigger provides trigger,
    ) {
        Box(
            modifier = Modifier.drawWithContent {
                drawContent()
                val center = triggerCenter
                if (center != null && animating) {
                    val maxRadius = listOf(
                        hypot(center.x, center.y),
                        hypot(size.width - center.x, center.y),
                        hypot(center.x, size.height - center.y),
                        hypot(size.width - center.x, size.height - center.y),
                    ).max()
                    val radius = maxRadius * revealProgress.value
                    clipPath(
                        Path().apply {
                            addOval(Rect(center = center, radius = radius))
                        },
                    ) {
                        drawRect(targetBgColor)
                    }
                }
            },
        ) {
            content()
        }
    }
}

/**
 * 触发主题 reveal。
 * - [Offset]：触发点屏幕坐标（icon 中心）
 * - [Boolean]：目标是否深色（true=切到深色，false=切到浅色）
 */
typealias ThemeRevealTrigger = (center: Offset, targetIsDark: Boolean) -> Unit

val LocalThemeRevealTrigger = compositionLocalOf<ThemeRevealTrigger> {
    // 默认无 reveal：直接应用（向后兼容）
    { _, _ -> }
}
