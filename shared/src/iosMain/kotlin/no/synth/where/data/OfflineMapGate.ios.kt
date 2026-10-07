package no.synth.where.data

import org.maplibre.compose.resource.ConnectivityMode
import org.maplibre.compose.resource.MapConnectivity

internal actual fun setMapOffline(offline: Boolean) {
    MapConnectivity.mode = if (offline) ConnectivityMode.ForceOffline else ConnectivityMode.Automatic
}
