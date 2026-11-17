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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Compose platform limits: Dialog renders in its own Window and the system scrim is a flat
// color, so the design's backdrop blur cannot be done; Modifier.shadow supports a single
// elevation, so the design's two-layer shadow is approximated with one 24dp elevation.
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
            // The platform default width does not match the design; widthIn below caps it.
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
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
            .height(46.dp)
            .then(if (!enabled) Modifier.alpha(0.4f) else Modifier),
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

enum class DialogButtonStyle { Primary, Cancel, Destructive }

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
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = DestructiveFg,
            cursorColor = colors.primary,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}
