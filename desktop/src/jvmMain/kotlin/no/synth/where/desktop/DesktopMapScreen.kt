package no.synth.where.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import no.synth.where.ui.MapScreenNavigation
import no.synth.where.ui.map.MapPlatform
import no.synth.where.ui.map.MapScreen
import no.synth.where.ui.map.MapScreenState

/**
 * The desktop map screen: the shared [MapScreen] as a viewer. The desktop has no location source,
 * so there is no puck, follow or recording; navigation progress is not advanced either.
 */
@Composable
fun DesktopMapScreen(nav: MapScreenNavigation) {
    val scope = rememberCoroutineScope()
    val state = remember {
        MapScreenState(
            AppDependencies.trackRepository,
            AppDependencies.savedPointsRepository,
            AppDependencies.userPreferences,
            AppDependencies.onlineTrackingCoordinator,
            AppDependencies.liveTrackingFollower,
            AppDependencies.hikingRouteSession,
            scope,
        )
    }
    MapScreen(state = state, nav = nav, platform = remember { MapPlatform() })
}
