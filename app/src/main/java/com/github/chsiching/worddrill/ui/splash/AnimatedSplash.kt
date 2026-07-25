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
 * Ticket #24 — App 启动动画 overlay。
 *
 * 设计稿：`designs/worddrill-icon/icon-03b-animation.html`（可播放 5 阶段原型）。
 * 坐标系沿用设计稿 SVG viewBox `0 0 80 80`，Canvas 内部按 (iconPx / 80) 缩放；
 * 图标显示尺寸 160dp（设计稿 110px 在 280px 手机壳内 ≈ 屏宽 39%，1080px@420dpi 屏 ≈ 160dp）。
 *
 * 5 个元素各持一对 [Animatable]：[ElementState.alpha] 与 [ElementState.translateX]（设计单位）。
 * 动画驱动在 [LaunchedEffect] 里用 [kotlinx.coroutines.launch] 并行，按设计稿时序：
 *
 * - Phase 1 (0ms)：后卡 alpha 0→1、translateX +60→0（spring）
 * - Phase 2 (200ms)：前卡 alpha 0→1、translateX −60→0（spring）
 * - Phase 3 (600/700/800ms)：线1/3 从 +40、线2 从 −40 飞入（spring）
 * - Phase 4 (1100–1500ms)：停顿（无动作）
 * - Phase 5 (1500ms 起)：交错淡出（stagger）线1→线2→线3→后卡→前卡，每个 500ms tween、
 *   启动间隔 100ms（不等上一个结束，并行重叠），总时长 4×100 + 500 = 900ms
 *
 * Reduced motion（`Settings.Global.ANIMATOR_DURATION_SCALE == 0`）时跳过动画，直接 [onFinished]。
 *
 * 调用方（MainActivity）：主内容始终在底层 composition，本 overlay 盖住顶层；
 * 动画完成调用 [onFinished]，调用方移除 overlay 揭示底层 UI。
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
        // Reduce motion：不渲染 overlay，直接揭示底层。
        // 必须同时调 onReady（释放系统 splash keep 条件）和 onFinished（移除 overlay），
        // 否则 setKeepOnScreenCondition 永远不释放，系统 splash 卡住。
        LaunchedEffect(Unit) {
            onReady()
            onFinished()
        }
        return
    }

    LaunchedEffect(Unit) {
        // composition 完成 + 帧时钟就绪 → 通知释放系统 splash。
        // 此时 overlay 已在 composition 中，下一帧即开始绘制动画。
        onReady()
        elements.values.forEach { it.alpha.snapTo(0f) }
        elements.forEach { (e, st) -> st.translateX.snapTo(e.flyInOffset) }

        val springIn = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

        // Phase 1: 后卡轮廓飞入（0ms）
        launch {
            elements[Element.BACK_CARD]!!.let { b ->
                launch { b.alpha.animateTo(1f, springIn) }
                b.translateX.animateTo(0f, springIn)
            }
        }
        // Phase 2: 前卡实心飞入（200ms）
        launch {
            delay(200)
            elements[Element.FRONT_CARD]!!.let { f ->
                launch { f.alpha.animateTo(1f, springIn) }
                f.translateX.animateTo(0f, springIn)
            }
        }
        // Phase 3: 三条线交错飞入（600/700/800ms）
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

        // Phase 5: 交错淡出（stagger，非流水线）。
        // 顺序：线1→线2→线3→后卡→前卡。每个元素**不等上一个结束**就启动，
        // 启动间隔 100ms（紧凑、不拖沓），单个淡出 spring（critically damped，与飞入一致）。
        // 多个元素同时处于不同淡出进度 → 视觉上有层次感。
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

        // 全部淡出完成后揭示底层：最后一项（前卡）起始 1500 + 4*100 = 1900ms，
        // spring 在 StiffnessMediumLow 下约 500ms 内稳定 → 等 2500ms 足够。
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
        // 设计稿：手机壳 280×500 内 icon 110px ≈ 屏宽 39%。
        // 典型 1080px @ 420dpi（2.625x）屏 ≈ 421px = ~160dp。
        Canvas(modifier = Modifier.size(160.dp)) {
            // 110dp 渲染像素 ÷ 80 设计单位 = 比例
            val s = size.minDimension / 80f
            // 用 scale 把整个绘图域缩放到设计空间：所有坐标（path / line / translate）
            // 都用设计稿 80 单位坐标书写，scale 统一放大到实际像素。
            // 关键：path 的坐标不能用 ×s 手算（Path 在 DrawScope 外构造，无法注入 s），
            // 必须靠 scale 作用域整体放大，path 才会跟着变。
            //
            // 内容垂直居中修正：设计内容 y [14,72] 中心 = 43，而 80 单位画布中心 = 40，
            // 差 3 单位。先 translate(0, -3f) 让内容中心对齐画布中心，再 scale。
            translate(left = 0f, top = -3f * s) {
                scale(s, pivot = Offset.Zero) {
                // 后卡
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
                // 前卡
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
                // 三条线（设计空间坐标）
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

/** 构造设计 80 空间坐标的圆角矩形 [Path]（参数都是设计单位）。 */
private fun roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            left = x, top = y, right = x + w, bottom = y + h,
            cornerRadius = CornerRadius(r, r),
        ),
    )
}

/** 5 个元素：[flyInOffset] 是设计空间单位（±40/±60）。 */
private enum class Element(val flyInOffset: Float) {
    BACK_CARD(60f),    // 后卡轮廓：右侧飞入
    FRONT_CARD(-60f),  // 前卡实心：左侧飞入
    LINE1(40f),        // 线1：右侧飞入
    LINE2(-40f),       // 线2：左侧飞入
    LINE3(40f),        // 线3：右侧飞入
}

/** 持有单个元素的 alpha / translateX 两个 [Animatable]，便于按枚举索引定位。 */
private class ElementState(
    val alpha: Animatable<Float, *>,
    val translateX: Animatable<Float, *>,
)

/**
 * 读取系统「移除动画」信号（Settings.Global.ANIMATOR_DURATION_SCALE == 0）。
 * 用户在「开发者选项 → 移除动画」或「无障碍 → 移除动画」开启后此值为 0。
 * 这是 Android 平台事实标准的 reduce motion 入口（与 prefers-reduced-motion 等价）。
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
