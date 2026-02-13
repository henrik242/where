package no.synth.where.ui.map

import android.content.Context
import android.location.Location
import no.synth.where.data.Track
import no.synth.where.data.RulerState
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
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
            val locationComponent = map.locationComponent

            if (locationComponent.isLocationComponentActivated) {
                locationComponent.isLocationComponentEnabled = true
                locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.COMPASS
                if (locationComponent.lastKnownLocation == null && DeviceUtils.isEmulator()) {
                    forceLocationOnEmulator(locationComponent)
                }
                return
            }

            locationComponent.activateLocationComponent(
                org.maplibre.android.location.LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(true)
                    .build()
            )

            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.COMPASS

            if (locationComponent.lastKnownLocation == null && DeviceUtils.isEmulator()) {
                forceLocationOnEmulator(locationComponent)
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
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

