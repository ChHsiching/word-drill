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
 * Ticket #25 E-style dialog (modeled on DialogE in designs/worddrill-dialogs/dialog-5-full.html).
 *
 * Replaces Material3 [androidx.compose.material3.AlertDialog] with the Compose-native [Dialog]
 * plus a custom [Surface] container, 18dp corners + shadow, Apple-style layout.
 *
 * Key dimensions from the design spec (aligned with existing tokens):
 * - Container corner radius 18dp; padding 20/18/14 (top/sides/bottom); button area padding 0/16/16
 * - Title 17sp / 600 / centered / letterSpacing -0.3px
 * - Message 13sp / secondary / centered / line-height 1.5
 * - Button pill: corner radius 12, padding 12, horizontal row with gap 8; primary button solid
 *   onSurface, cancel chipBg + secondary, destructive light red fill + iOS red ([DestructiveFg])
 *
 * Known deviations from the design spec (Compose platform limits, not implementation slips):
 * - **No backdrop blur on the scrim**: the design spec's CSS `backdrop-filter: blur(3px)` blurs
 *   the content behind, but [Dialog] renders in its own Window and the system paints the scrim
 *   as a flat color, so the content behind cannot be blurred. Real backdrop blur would require
 *   a RenderEffect on the host Activity (a big cost in performance and complexity, beyond a
 *   dialog's job), so this implementation keeps the system default scrim dim (measured: the
 *   dark-theme scrim is pure black, the light-theme one translucent black).
 * - **Single-layer shadow**: the design spec wants two layers, close(0 4px 16px) +
 *   ambient(0 24px 48px), but Compose [Modifier.shadow] supports only one elevation plus the
 *   spot/ambient color channels. 24dp elevation approximates it (visually a wide soft shadow);
 *   no two-layer stacking.
 *
 * Callers fill in one of the three sections [title] / [message] / [content] plus a button
 * list; the visuals of all 11 dialogs converge into this one composable.
 *
 * @param onDismissRequest called on scrim tap / back key
 * @param title top title (17sp / 600 / centered)
 * @param message explanatory text below the title (13sp / secondary / centered); pick either
 *   this or [content], or supply both
 * @param content custom body (input fields, file pickers, etc.); can coexist with [message]
 * @param buttons bottom button area ([DialogButton] list, horizontal, gap 8dp)
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
            // Design spec container width ~230dp (centered on a 320dp phone screen). Platform
            // default width is off; widthIn caps the width and fillMaxWidth stretches to the
            // max available width after padding.
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
            // Corner radius 18 + a single soft shadow (24dp elevation, spot/ambient channels
            // both at 0.10 alpha). The design spec wants close+ambient two layers, but
            // Modifier.shadow supports only one; see the file-header comment.
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
 * E-style dialog button (pill block, for [WordDrillDialog] only).
 *
 * Must sit inside a [RowScope] (the button area of [WordDrillDialog]): `Modifier.weight(1f)`
 * makes the buttons equal width and fill the row.
 *
 * Three styles matching the design spec:
 * - [DialogButtonStyle.Primary]: solid onSurface, inverted text, 600 (confirm/export/import/close/OK)
 * - [DialogButtonStyle.Cancel]: chipBg background + secondary text, 500 (cancel/non-destructive close)
 * - [DialogButtonStyle.Destructive]: [DestructiveBg] light red fill + [DestructiveFg] red text, 500
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

/** E-style dialog button style (decides fill + text color + weight). */
enum class DialogButtonStyle { Primary, Cancel, Destructive }

/**
 * E-style dialog text field (TextField for [WordDrillDialog], matching the design spec).
 *
 * Design spec:
 * - chipBg fill + 10dp corner radius; normal-state border transparent (visually borderless),
 *   focus-state border becomes primary
 * - 15sp font size; error-state border becomes [DestructiveFg]
 *
 * This is a color-customized wrapper around [OutlinedTextField]; other behavior is untouched
 * (IME, clipboard, screen reader, etc. are preserved).
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
            // normal-state border transparent (visually borderless); focus becomes primary, error becomes red
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = DestructiveFg,
            cursorColor = colors.primary,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}
