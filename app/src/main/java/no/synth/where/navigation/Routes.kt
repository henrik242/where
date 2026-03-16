package no.synth.where.navigation

import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable data class SettingsRoute(val highlightOfflineMode: Boolean = false)
@Serializable data class TracksRoute(val importUrl: String? = null)
@Serializable object SavedPointsRoute
@Serializable object OnlineTrackingRoute
@Serializable object DownloadRoute
@Serializable object AttributionsRoute
@Serializable data class LayerRegionsRoute(val layerId: String)
