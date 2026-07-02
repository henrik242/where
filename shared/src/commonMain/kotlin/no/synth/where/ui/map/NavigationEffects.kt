package no.synth.where.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import no.synth.where.data.NavigationSession
import no.synth.where.data.geo.LatLng
import no.synth.where.data.navigation.NavigationProgress
import no.synth.where.data.navigation.TrackNavigator

/** How often the navigator re-reads the user location while navigating. */
private const val NAV_POLL_INTERVAL_MS = 1000L

/** Grouped navigation state + callbacks threaded from the screen down through the overlays. */
data class NavigationUiState(
    val isNavigating: Boolean = false,
    val progress: NavigationProgress? = null,
    val onToggleReverse: () -> Unit = {},
    val onStop: () -> Unit = {},
)

/**
 * Drives an active navigation [session]: rebuilds the [TrackNavigator] when the track or direction
 * changes, polls [location] every second to compute progress, and renders the split route layers
 * via [onRenderLayers] / [onClearLayers]. Returns the current progress for the UI banner.
 *
 * The poll-and-render loop lives here so Android and iOS share it; each platform supplies only the
 * one-line location read and the layer renderer, which are the only genuinely platform-specific bits.
 */
@Composable
fun rememberNavigationProgress(
    session: NavigationSession?,
    location: () -> LatLng?,
    onRenderLayers: (NavigationLayers) -> Unit,
    onClearLayers: () -> Unit,
): NavigationProgress? {
    val navigator = remember(session?.track?.id, session?.reversed) {
        session?.let { TrackNavigator(it.track, it.reversed) }
    }
    var progress by remember { mutableStateOf<NavigationProgress?>(null) }

    LaunchedEffect(navigator) {
        val nav = navigator
        if (nav == null) {
            progress = null
            return@LaunchedEffect
        }
        while (true) {
            location()?.let { progress = nav.progressAt(it) }
            delay(NAV_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(progress, session) {
        val p = progress
        if (p != null && session != null) {
            onRenderLayers(buildNavigationLayers(session.track, session.reversed, p))
        } else {
            onClearLayers()
        }
    }

    return progress
}
