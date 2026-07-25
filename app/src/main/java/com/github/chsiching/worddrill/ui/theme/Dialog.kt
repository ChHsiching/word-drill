package com.github.chsiching.worddrill.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Ticket #25 — E 风格弹窗（参考 designs/worddrill-dialogs/dialog-5-full.html 的 DialogE）。
 *
 * 替代 Material3 [androidx.compose.material3.AlertDialog]，改用 Compose 原生 [Dialog] +
 * 自定义 [Surface] 容器，圆角 18 + 阴影，按 Apple 风格排版。
 *
 * 设计稿关键尺寸（与现有 token 对齐）：
 * - 容器圆角 18dp；padding 20/18/14（顶/横/底）；按钮区 padding 0/16/16
 * - 标题 17sp / 600 / 居中 / letterSpacing -0.3px
 * - 消息 13sp / secondary / 居中 / line-height 1.5
 * - 按钮 pill：圆角 12，padding 12，水平排列 gap 8；主按钮实心 onSurface、
 *   取消 chipBg + secondary、destructive 浅红底 + iOS 红（[DestructiveFg]）
 *
 * 与设计稿的已知偏差（Compose 平台限制，非实现疏漏）：
 * - **scrim 不做 backdrop blur**：设计稿 CSS `backdrop-filter: blur(3px)` 模糊背后内容，
 *   但 [Dialog] 在独立 Window 渲染，scrim 由系统画 flat 色，无法对背后内容做模糊。
 *   要真 backdrop blur 需对宿主 Activity 套 RenderEffect（性能/复杂度大，超出弹窗职责），
 *   本实现保留系统默认 scrim dim（实测深色态背景 → 纯黑，浅色态 → 半透黑）。
 * - **单层阴影**：设计稿要 close(0 4px 16px) + ambient(0 24px 48px) 两层，但 Compose
 *   [Modifier.shadow] 只支持一层 elevation + spot/ambient 双色通道。用 24dp elevation
 *   做近似（视觉上是大范围柔和阴影），不做两层叠加。
 *
 * 调用方只填 [title] / [message] / [content] / [buttons] 三段之一 + 按钮列表，
 * 所有 11 个弹窗的视觉都收敛到这一个 composable。
 *
 * @param onDismissRequest scrim 点击 / 返回键时调用
 * @param title 顶部标题（17sp / 600 / 居中）
 * @param message 标题下方说明文字（13sp / secondary / 居中）；与 [content] 二选一或都给
 * @param content 自定义主体（输入框、文件选择等）；与 [message] 可同时存在
 * @param buttons 底部按钮区（[DialogButton] 列表，水平排列，gap 8dp）
 */
@Composable
fun WordDrillDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
    buttons: @Composable RowScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            // 设计稿容器宽度 ~230dp（手机屏 320dp 居中）。关掉平台默认宽度，
            // 用 widthIn 限宽 + fillMaxWidth 撑满 padding 后的最大可用宽度。
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
            // 圆角 18 + 单层柔和阴影（24dp elevation，spot/ambient 双通道 0.10 alpha）。
            // 设计稿要 close+ambient 两层，但 Modifier.shadow 只支持一层；详见文件头注释。
            modifier = modifier
                .widthIn(max = 320.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = Color.Black.copy(alpha = 0.10f),
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                ),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Column(modifier = Modifier.padding(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 14.dp)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (message != null) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = (13 * 1.5).sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (content != null) {
                        Spacer(Modifier.size(12.dp))
                        content()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    buttons()
                }
            }
        }
    }
}

/**
 * E 风格弹窗按钮（pill 色块，[WordDrillDialog] 专用）。
 *
 * 必须放在 [RowScope]（[WordDrillDialog] 的按钮区）里：`Modifier.weight(1f)` 让按钮等宽铺满。
 *
 * 三种样式对应设计稿：
 * - [DialogButtonStyle.Primary]：实心 onSurface，反色文字，600（确认/导出/导入/关闭/确定）
 * - [DialogButtonStyle.Cancel]：chipBg 背景 + secondary 文字，500（取消/关闭非破坏）
 * - [DialogButtonStyle.Destructive]：[DestructiveBg] 浅红底 + [DestructiveFg] 红字，500
 */
@Composable
fun RowScope.DialogButton(
    text: String,
    onClick: () -> Unit,
    style: DialogButtonStyle = DialogButtonStyle.Cancel,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg, weight) = when (style) {
        DialogButtonStyle.Primary -> Triple(colors.onSurface, colors.surface, FontWeight.SemiBold)
        DialogButtonStyle.Cancel -> Triple(MaterialTheme.wordDrillColors.chipBg, colors.onSurfaceVariant, FontWeight.Medium)
        DialogButtonStyle.Destructive -> Triple(DestructiveBg, DestructiveFg, FontWeight.Medium)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .weight(1f)
            .height(46.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = weight,
                color = fg,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** E 风格弹窗按钮样式（决定底色 + 文字色 + 字重）。 */
enum class DialogButtonStyle { Primary, Cancel, Destructive }

/**
 * E 风格弹窗输入框（[WordDrillDialog] 专用 TextField，匹配设计稿）。
 *
 * 设计稿规范：
 * - chipBg 填充 + 10dp 圆角；normal 态 border 透明（视觉上无边），focus 态 border 变 primary
 * - 15sp 字号；error 态 border 变 [DestructiveFg]
 *
 * 这是 [OutlinedTextField] 的颜色定制包装，不动其他行为（保留 IME、剪贴板、screen reader 等）。
 */
@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.wordDrillColors.chipBg,
            unfocusedContainerColor = MaterialTheme.wordDrillColors.chipBg,
            disabledContainerColor = MaterialTheme.wordDrillColors.chipBg,
            errorContainerColor = MaterialTheme.wordDrillColors.chipBg,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            disabledTextColor = colors.onSurfaceVariant,
            errorTextColor = colors.onSurface,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            disabledLabelColor = colors.onSurfaceVariant,
            errorLabelColor = DestructiveFg,
            focusedSupportingTextColor = colors.primary,
            unfocusedSupportingTextColor = colors.onSurfaceVariant,
            errorSupportingTextColor = DestructiveFg,
            // normal 态 border 透明（视觉上无边），focus → primary，error → 红
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = DestructiveFg,
            cursorColor = colors.primary,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}
