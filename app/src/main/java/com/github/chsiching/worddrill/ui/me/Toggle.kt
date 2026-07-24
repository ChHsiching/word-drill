package com.github.chsiching.worddrill.ui.me

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

/**
 * Toggle 开关（Ticket #16 + 审核反馈 3 重写）。
 *
 * 审核反馈 3：原手画 Toggle（Box + offset knob）有位置偏移 + 方形按下效果，别扭。
 * 改用 Material3 [Switch]（系统原生组件，正确的动画 + 按下手势），只覆盖配色到
 * 纯黑白灰（spec 要求非 Material 默认绿色）：
 * - checked：thumb 白 / track onSurface（黑）
 * - unchecked：thumb surface / track chipBg / border transparent
 *
 * 保持顶层定义（非嵌套于 Composable 内），避免重组丢动画态。
 */
@Composable
fun Toggle(
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.wordDrillColors
    Switch(
        checked = on,
        onCheckedChange = { onClick() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.surface,
            checkedTrackColor = colors.onSurface,
            checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            uncheckedThumbColor = colors.surface,
            uncheckedTrackColor = extended.chipBg,
            uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = modifier,
    )
}
