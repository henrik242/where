package no.synth.where.ui.map

import android.content.Context
import android.location.Location
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.Track
import no.synth.where.data.RulerState
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import no.synth.where.util.DeviceUtils
import no.synth.where.util.Logger

/**
 * Utilities for managing map layers and rendering.
 */
object MapRenderUtils {

    /**
     * Update track visualization on the map.
     */
    fun updateTrackOnMap(style: Style, track: Track?, isCurrentTrack: Boolean = true) {
        try {
            val sourceId = "track-source"
            val layerId = "track-layer"

            style.getLayer(layerId)?.let { style.removeLayer(it) }
            style.getSource(sourceId)?.let { style.removeSource(it) }

            if (track != null && track.points.size >= 2) {
                val points =
                    track.points.map { Point.fromLngLat(it.latLng.longitude, it.latLng.latitude) }
                val lineString = LineString.fromLngLats(points)
                val feature = Feature.fromGeometry(lineString)

                val source = GeoJsonSource(sourceId, feature)
                style.addSource(source)

                val lineColor = if (isCurrentTrack) "#FF0000" else "#0000FF"

                val lineLayer = LineLayer(layerId, sourceId).withProperties(
                    PropertyFactory.lineColor(lineColor),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineOpacity(0.8f)
                )
                style.addLayer(lineLayer)
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /**
     * Update friend's track visualization on the map (dashed blue line).
     */
    fun updateFriendTrackOnMap(style: Style, geoJson: String?) {
        try {
            val lineSourceId = "friend-track-line-source"
            val pointSourceId = "friend-track-point-source"
            val lineLayerId = "friend-track-line-layer"
            val pointLayerId = "friend-track-point-layer"
            val labelLayerId = "friend-track-label-layer"

            style.getLayer(labelLayerId)?.let { style.removeLayer(it) }
            style.getLayer(lineLayerId)?.let { style.removeLayer(it) }
            style.getLayer(pointLayerId)?.let { style.removeLayer(it) }
            style.getSource(lineSourceId)?.let { style.removeSource(it) }
            style.getSource(pointSourceId)?.let { style.removeSource(it) }

            if (geoJson != null) {
                val fc = try {
                    FeatureCollection.fromJson(geoJson)
                } catch (e: Exception) {
                    Logger.e(e, "Failed to parse friend GeoJSON")
                    return
                }
                val features = fc.features() ?: return

                // Build separate collections for lines and points
                val lineFeatures = mutableListOf<Feature>()
                val pointFeatures = mutableListOf<Feature>()
                for (feature in features) {
                    when (feature.geometry()?.type()) {
                        "LineString" -> lineFeatures.add(feature)
                        "Point" -> pointFeatures.add(feature)
                        else -> {}
                    }
                }

                if (lineFeatures.isNotEmpty()) {
                    val lineSource = GeoJsonSource(lineSourceId, FeatureCollection.fromFeatures(lineFeatures))
                    style.addSource(lineSource)
                    val lineLayer = LineLayer(lineLayerId, lineSourceId).withProperties(
                        PropertyFactory.lineColor("#2196F3"),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineOpacity(0.8f),
                        PropertyFactory.lineDasharray(arrayOf(4f, 2f))
                    )
                    style.addLayer(lineLayer)
                }

                if (pointFeatures.isNotEmpty()) {
                    val pointSource = GeoJsonSource(pointSourceId, FeatureCollection.fromFeatures(pointFeatures))
                    style.addSource(pointSource)
                    val pointLayer = CircleLayer(pointLayerId, pointSourceId).withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor("#2196F3"),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor("#FFFFFF")
                    )
                    style.addLayer(pointLayer)

                    val labelLayer = SymbolLayer(labelLayerId, pointSourceId).withProperties(
                        PropertyFactory.textField(Expression.get("clientId")),
                        PropertyFactory.textSize(12f),
                        PropertyFactory.textColor("#2196F3"),
                        PropertyFactory.textHaloColor("#FFFFFF"),
                        PropertyFactory.textHaloWidth(1.5f),
                        PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                        PropertyFactory.textAnchor("top")
                    )
                    style.addLayer(labelLayer)
                }
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /**
     * Update ruler visualization on the map.
     */
    fun updateRulerOnMap(style: Style, rulerState: RulerState) {
        try {
            val lineSourceId = "ruler-line-source"
            val lineLayerId = "ruler-line-layer"
            val pointSourceId = "ruler-point-source"
            val pointLayerId = "ruler-point-layer"

            style.getLayer(lineLayerId)?.let { style.removeLayer(it) }
            style.getSource(lineSourceId)?.let { style.removeSource(it) }
            style.getLayer(pointLayerId)?.let { style.removeLayer(it) }
            style.getSource(pointSourceId)?.let { style.removeSource(it) }

            if (rulerState.points.isNotEmpty()) {
                if (rulerState.points.size >= 2) {
                    val points = rulerState.points.map {
                        Point.fromLngLat(it.latLng.longitude, it.latLng.latitude)
                    }
                    val lineString = LineString.fromLngLats(points)
                    val lineFeature = Feature.fromGeometry(lineString)

                    val lineSource = GeoJsonSource(lineSourceId, lineFeature)
                    style.addSource(lineSource)

                    val lineLayer = LineLayer(lineLayerId, lineSourceId).withProperties(
                        PropertyFactory.lineColor("#FFA500"),
                        PropertyFactory.lineWidth(3f),
                        PropertyFactory.lineOpacity(0.9f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                    )
                    style.addLayer(lineLayer)
                }

                val pointFeatures = rulerState.points.map { rulerPoint ->
                    Feature.fromGeometry(
                        Point.fromLngLat(
                            rulerPoint.latLng.longitude,
                            rulerPoint.latLng.latitude
                        )
                    )
                }
                val pointSource = GeoJsonSource(
                    pointSourceId,
                    FeatureCollection.fromFeatures(pointFeatures)
                )
                style.addSource(pointSource)

                val pointLayer = CircleLayer(pointLayerId, pointSourceId).withProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor("#FFA500"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#FFFFFF")
                )
                style.addLayer(pointLayer)
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /**
     * Update saved points visualization on the map.
     */
    fun updateSavedPointsOnMap(
        style: Style,
        savedPoints: List<no.synth.where.data.SavedPoint>
    ) {
        try {
            val sourceId = "saved-points-source"
            val layerId = "saved-points-layer"

            style.getLayer(layerId)?.let { style.removeLayer(it) }
            style.getSource(sourceId)?.let { style.removeSource(it) }

            if (savedPoints.isNotEmpty()) {
                val features = savedPoints.map { point ->
                    Feature.fromGeometry(
                        Point.fromLngLat(point.latLng.longitude, point.latLng.latitude)
                    ).apply {
                        addStringProperty("name", point.name)
                        addStringProperty("color", point.color ?: "#FF5722")
                    }
                }

                val source = GeoJsonSource(
                    sourceId,
                    FeatureCollection.fromFeatures(features)
                )
                style.addSource(source)

                val circleLayer = CircleLayer(layerId, sourceId).withProperties(
                    PropertyFactory.circlePitchAlignment("viewport"),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor(
                        org.maplibre.android.style.expressions.Expression.get("color")
                    ),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#FFFFFF")
                )
                style.addLayer(circleLayer)
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /**
     * Update search results visualization on the map.
     */
    fun updateSearchResultsOnMap(
        style: Style,
        results: List<PlaceSearchClient.SearchResult>
    ) {
        try {
            val sourceId = "search-results-source"
            val layerId = "search-results-layer"

            style.getLayer(layerId)?.let { style.removeLayer(it) }
            style.getSource(sourceId)?.let { style.removeSource(it) }

            if (results.isNotEmpty()) {
                val features = results.map { result ->
                    Feature.fromGeometry(
                        Point.fromLngLat(result.latLng.longitude, result.latLng.latitude)
                    ).apply {
                        addStringProperty("name", result.name)
                    }
                }

                val source = GeoJsonSource(
                    sourceId,
                    FeatureCollection.fromFeatures(features)
                )
                style.addSource(source)

                val circleLayer = CircleLayer(layerId, sourceId).withProperties(
                    PropertyFactory.circlePitchAlignment("viewport"),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor("#E91E63"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleOpacity(0.9f)
                )
                style.addLayer(circleLayer)
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /**
     * Update highlighted search result on the map.
     */
    fun updateHighlightedSearchResult(
        style: Style,
        result: PlaceSearchClient.SearchResult?
    ) {
        try {
            val sourceId = "search-highlight-source"
            val layerId = "search-highlight-layer"

            style.getLayer(layerId)?.let { style.removeLayer(it) }
            style.getSource(sourceId)?.let { style.removeSource(it) }

            if (result != null) {
                val feature = Feature.fromGeometry(
                    Point.fromLngLat(result.latLng.longitude, result.latLng.latitude)
                )

                val source = GeoJsonSource(sourceId, feature)
                style.addSource(source)

                val circleLayer = CircleLayer(layerId, sourceId).withProperties(
                    PropertyFactory.circlePitchAlignment("viewport"),
                    PropertyFactory.circleRadius(11f),
                    PropertyFactory.circleColor("#E91E63"),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleOpacity(1f)
                )
                style.addLayer(circleLayer)
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /**
     * Enable location component on the map.
     */
    @SuppressWarnings("MissingPermission")
    fun enableLocationComponent(
        map: org.maplibre.android.maps.MapLibreMap,
        style: Style,
        context: Context,
        hasPermission: Boolean
    ) {
        if (!hasPermission) return

        try {
            // Check if any location provider has a fix before enabling,
            // to avoid MapLibre's noisy internal "Last location unavailable" error log
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val hasLocationFix = locationManager?.let { lm ->
                lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) != null ||
                    lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) != null ||
                    lm.getLastKnownLocation(android.location.LocationManager.FUSED_PROVIDER) != null
            } ?: false

            val locationComponent = map.locationComponent

            if (!locationComponent.isLocationComponentActivated) {
                locationComponent.activateLocationComponent(
                    org.maplibre.android.location.LocationComponentActivationOptions.builder(context, style)
                        .useDefaultLocationEngine(true)
                        .build()
                )
            }

            if (!hasLocationFix && DeviceUtils.isEmulator()) {
                forceLocationOnEmulator(locationComponent)
            }

            if (hasLocationFix || locationComponent.lastKnownLocation != null) {
                locationComponent.isLocationComponentEnabled = true
                locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.COMPASS
            }
        } catch (e: Exception) {
            Logger.d("Location component not ready: %s", e.message ?: "unknown")
        }
    }

    /**
     * Force a mock location on emulator for testing.
     */
    @SuppressWarnings("MissingPermission")
    private fun forceLocationOnEmulator(locationComponent: LocationComponent) {
        try {
            val mockLocation = Location("emulator_mock").apply {
                latitude = 59.9139
                longitude = 10.7522
                accuracy = 10f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }
            locationComponent.forceLocationUpdate(mockLocation)
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }
}

