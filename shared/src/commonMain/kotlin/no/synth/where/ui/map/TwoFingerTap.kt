package no.synth.where.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

object TwoFingerTap {
    const val MAX_DURATION_MS = 600L
    const val MAX_MOVEMENT_DP = 20
    const val MIN_SEPARATION_DP = 48
    const val DISMISS_DELAY_MS = 15_000L
    // Duration of the native layer fade-out when a measurement is dismissed.
    const val FADE_OUT_MS = 400L
}

@Composable
fun rememberAutoDismissingTwoFingerMeasurement(): MutableState<TwoFingerMeasurement?> {
    val state = remember { mutableStateOf<TwoFingerMeasurement?>(null) }
    val current = state.value
    // Key on the geographic endpoints so an unchanged measurement doesn't restart the dismiss timer.
    LaunchedEffect(current?.lat1, current?.lng1, current?.lat2, current?.lng2) {
        if (current != null) {
            delay(TwoFingerTap.DISMISS_DELAY_MS)
            state.value = null
        }
    }
    return state
}
