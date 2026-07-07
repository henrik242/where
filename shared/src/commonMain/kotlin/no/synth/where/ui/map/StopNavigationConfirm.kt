package no.synth.where.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Visibility of the "stop navigation?" confirmation, hoisted so the host screen can raise it from
 * both the navigation card's stop button and (on Android) the Back handler.
 */
class StopNavigationConfirmState {
    var isVisible by mutableStateOf(false)
        private set

    fun request() {
        isVisible = true
    }

    fun dismiss() {
        isVisible = false
    }
}

@Composable
fun rememberStopNavigationConfirmState() = remember { StopNavigationConfirmState() }

/**
 * Renders the confirmation while requested and runs [onConfirm] if accepted. Shared by both
 * platforms so the dialog wiring and auto-dismiss live in one place. Auto-dismisses if navigation
 * ends on its own (e.g. arrival) while the dialog is open.
 */
@Composable
fun StopNavigationConfirmDialog(
    state: StopNavigationConfirmState,
    isNavigating: Boolean,
    onConfirm: () -> Unit,
) {
    LaunchedEffect(isNavigating) { if (!isNavigating) state.dismiss() }
    if (state.isVisible) {
        MapDialogs.ConfirmStopNavigationDialog(
            onConfirm = {
                state.dismiss()
                onConfirm()
            },
            onDismiss = { state.dismiss() },
        )
    }
}
