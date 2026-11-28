package com.github.chsiching.worddrill.ui.me

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

@Composable
fun Toggle(
    on: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.wordDrillColors
    Switch(
        checked = on,
        // The switch only shows the state. The row around it is the tap target, see ToggleRow.
        onCheckedChange = null,
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
