package com.github.chsiching.worddrill.ui.me

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

/**
 * Apple 风格 toggle 开关（Ticket #16）。
 *
 * 与 Material3 [androidx.compose.material3.Switch] 的差异：
 * - 配色用 textPrimary（on 时）+ chipBg（off 时），非 Material 默认绿色
 * - knob 用 offset 滑动 + spring 动画（spec：knob 0.35s cubic-bezier(0.22,1,0.36,1)，
 *   用 spring(dampingRatio=1.0, stiffness≈Spring.StiffnessMediumLow) 近似）
 * - 尺寸 51×31，knob 27×27，与 iOS Settings 视觉一致
 *
 * 定义在顶层（非嵌套于 Composable 内）避免每次重组都生成新类型、丢失动画态。
 */
@Composable
fun Toggle(
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.wordDrillColors.chipBg
    val knobOffset by animateDpAsState(
        targetValue = if (on) 22.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy, // damping 1.0 = critically damped
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "toggleKnob",
    )
    Surface(
        shape = CircleShape,
        color = trackColor,
        modifier = modifier
            .width(51.dp)
            .height(31.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 2.dp)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}
