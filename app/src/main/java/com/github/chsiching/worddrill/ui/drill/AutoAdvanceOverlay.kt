package com.github.chsiching.worddrill.ui.drill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Test tag of the auto-advance dwell hairline at the screen's bottom edge. */
internal const val AutoAdvanceHairlineTag = "auto_advance_hairline"

/** App-wide dwell-hairline fraction read by the navigation root: the drill
 *  ViewModel keeps it in sync with playback (the current card's elapsed fraction
 *  while playing or paused, 0 once idle), and the root draws the hairline from it
 *  above both nav modes — the drill screen itself no longer renders the line. */
@Singleton
class AutoAdvanceOverlayState @Inject constructor() {

    private val _fraction = MutableStateFlow(0f)

    /** 0 when idle; otherwise how far the current card's dwell has progressed (0..1). */
    val fraction: StateFlow<Float> = _fraction.asStateFlow()

    fun update(state: AutoAdvanceState) {
        _fraction.value =
            if (state.phase == AutoAdvancePhase.Idle) 0f else state.elapsedFraction
    }
}

/** Bridge the navigation root collects the app-wide [AutoAdvanceOverlayState]
 *  through; the root composes outside any nav destination, so it has no drill
 *  ViewModel of its own to read playback from. */
@HiltViewModel
class AutoAdvanceOverlayViewModel @Inject constructor(
    overlayState: AutoAdvanceOverlayState,
) : ViewModel() {

    val fraction: StateFlow<Float> = overlayState.fraction
}

/** The dwell hairline: a 2 dp full-width line at the screen's very bottom edge
 *  (approved prototype `.scratch/proto-auto-advance.html`, `edgeprog`): onSurface at
 *  55 % opacity filling left-to-right with the elapsed fraction, frozen while paused,
 *  absent while idle. The navigation root composes it after both nav modes, so it
 *  always draws on top of them. */
@Composable
internal fun AutoAdvanceHairline(elapsedFraction: Float, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .testTag(AutoAdvanceHairlineTag)
            .drawBehind {
                if (elapsedFraction > 0f) {
                    drawRect(
                        color = fill,
                        size = Size(size.width * elapsedFraction, size.height),
                    )
                }
            },
    )
}
