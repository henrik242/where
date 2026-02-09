package no.synth.where.navigation

import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable object SettingsRoute
@Serializable object TracksRoute
@Serializable object SavedPointsRoute
@Serializable object OnlineTrackingRoute
@Serializable object DownloadRoute
@Serializable data class LayerRegionsRoute(val layerId: String)
