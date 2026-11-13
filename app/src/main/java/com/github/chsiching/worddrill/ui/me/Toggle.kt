package com.github.chsiching.worddrill.ui.me

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

/**
 * Toggle switch (Ticket #16 + review feedback 3 rewrite).
 *
 * Review feedback 3: the original hand-drawn toggle (Box + offset knob) had position drift
 * and a square press effect. Replaced with Material3 [Switch] (a system
 * component with correct animation + press gesture); only the colors are overridden to
 * pure black/white/gray (the spec requires no Material default green):
 * - checked: white thumb / onSurface (black) track
 * - unchecked: surface thumb / chipBg track / transparent border
 *
 * Kept as a top-level definition (not nested inside a Composable) to avoid losing
 * animation state on recomposition.
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
