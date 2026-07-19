package no.synth.where.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import no.synth.where.data.NavigationSession
import no.synth.where.data.Track
import no.synth.where.data.geo.LatLng
import no.synth.where.data.navigation.NavigationProgress
import no.synth.where.data.navigation.TrackNavigator

/** How often the poller re-reads the user location while navigating. */
private const val NAV_POLL_INTERVAL_MS = 1000L

/**
 * Grouped navigation state + callbacks threaded from the screen down through the overlays. [track]
 * (the route in travel order) and [chartVisible] drive the tap-to-open altitude chart; a non-null
 * [track] means a session is active, so [isNavigating] derives from it.
 */
data class NavigationUiState(
    val progress: NavigationProgress? = null,
    val track: Track? = null,
    val chartVisible: Boolean = false,
    val onToggleReverse: () -> Unit = {},
    val onStop: () -> Unit = {},
) {
    val isNavigating: Boolean get() = track != null
}

/**
 * Observes navigation [progress] (the repository's `navigationProgress` flow) for an active
 * [session] and renders the split route layers via [onRenderLayers] / [onClearLayers]. Returns the
 * current progress for the UI banner.
 *
 * Pure observer: production happens outside the composition — on Android in the foreground
 * service (so navigation keeps updating while backgrounded), on iOS in [NavigationProgressPoller]
 * until it grows a background producer of its own.
 */
@Composable
fun rememberNavigationProgress(
    session: NavigationSession?,
    progress: StateFlow<NavigationProgress?>,
    onRenderLayers: (NavigationLayers) -> Unit,
    onClearLayers: () -> Unit,
): NavigationProgress? {
    val current by progress.collectAsState()

    LaunchedEffect(current, session) {
        val p = current
        if (p != null && session != null) {
            onRenderLayers(buildNavigationLayers(session.track, session.reversed, p))
        } else {
            onClearLayers()
        }
    }

    return current
}

/**
 * Foreground producer for platforms without a service-owned navigator (iOS today): rebuilds the
 * [TrackNavigator] when the track or direction changes and polls [location] every second, feeding
 * results into [updateProgress] (the repository's `updateNavigationProgress`). The repository
 * clears the flow on session changes, so observers reset to the "locating" state without help here.
 */
@Composable
fun NavigationProgressPoller(
    session: NavigationSession?,
    location: () -> LatLng?,
    updateProgress: (NavigationProgress) -> Unit,
) {
    val navigator = remember(session?.track?.id, session?.reversed) {
        session?.let { TrackNavigator(it.track, it.reversed) }
    }

    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        while (true) {
            location()?.let { updateProgress(nav.progressAt(it)) }
            delay(NAV_POLL_INTERVAL_MS)
        }
    }
}
