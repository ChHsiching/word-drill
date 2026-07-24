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
 * Circular Reveal 主题切换（审核反馈 5 v6 — View 截图方案）。
 *
 * 效果：新主题（背景+文字+卡片+完整 UI）从 icon 中心呈圆形扩散到全屏。
 *
 * 方案：用 Android View.draw(Canvas) 同步截图旧主题（不走 suspend toImageBitmap，
 * 避免时序问题），切主题后用截图 overlay 被 clip 圆从 icon 扩展揭示新主题。
 *
 * 流程：
 * 1. trigger(center, targetColor, onApplied) 被调
 * 2. 同步截图当前 View（旧主题）→ ImageBitmap
 * 3. 立即切主题（底层变新主题，被截图盖住看不见）
 * 4. 动画：截图被 clip 成扩展圆，圆内揭示新主题底层，圆外是旧截图
 * 5. 动画结束，移除 overlay
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
                // 1. 同步截图当前 View（旧主题）
                val bmp = captureView(view)
                snapshot = bmp?.asImageBitmap()
                // 2. 切主题：写 DataStore + 更新 renderedTheme
                //    底层立即变新主题，但被截图 overlay 盖住看不见
                onApplied()
                onThemeApplied()
                // 3. 动画：圆从 icon 扩展，圆内揭示新主题底层，圆外是旧截图
                revealProgress.snapTo(0f)
                revealProgress.animateTo(1f, animationSpec = tween(800))
                // 4. 动画结束，移除 overlay（此时底层已是新主题，无缝）
                snapshot = null
                animating = false
            }
        }
    }

    val progress by revealProgress.asState()

    CompositionLocalProvider(LocalThemeRevealTrigger provides trigger) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            // overlay：旧主题截图，圆内揭示新主题（截图被 clip 扩展圆「吃掉」）
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
                    // 画旧截图，只在圆**外**显示（Difference = 全屏减去圆）
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
 * 同步截图 View 到 Bitmap。用 View.draw(Canvas)，不依赖 suspend API。
 * 在主线程调（View.draw 必须主线程）。返回 null 表示截图失败（宽高 0）。
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
 * 触发 reveal。
 * - [Offset]：圆心（icon 屏幕坐标）
 * - [() -> Unit]：截图后立即调（写 DataStore + 更新 renderedTheme）
 */
typealias ThemeRevealTrigger = (center: Offset, onApplied: () -> Unit) -> Unit

val LocalThemeRevealTrigger = compositionLocalOf<ThemeRevealTrigger> {
    { _, onApplied -> onApplied() } // 默认无 reveal：直接切
}
