package no.synth.where.navigation

import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable data class SettingsRoute(val highlightOfflineMode: Boolean = false)
@Serializable object TracksRoute
@Serializable object SavedPointsRoute
@Serializable object OnlineTrackingRoute
@Serializable object DownloadRoute
@Serializable data class LayerRegionsRoute(val layerId: String)
