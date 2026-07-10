package no.synth.where.ui.map

import android.content.Context
import android.location.Location
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.TransitionOptions
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

    private const val LOCATION_ENGINE_INTERVAL_MS = 2000L
    private const val LOCATION_ENGINE_FASTEST_INTERVAL_MS = 1000L
    private const val STALE_FIX_TIMEOUT_MS = 30_000L
    private const val ACCURACY_RING_ALPHA = 0.30f
    // Location puck + accuracy ring stay the conventional blue "you are here" — the strongest map
    // convention, and the opponent color that pops over Kartverket's yellow/green/brown topo tints.
    private const val ACCURACY_RING_COLOR = "#1E88E5"
    // Earthy taupe for other people's live tracks (line, dot, and clientId label).
    private const val FRIEND_TRACK_COLOR = "#8D6E63"

    private const val MEASURE_LINE_SOURCE = "measure-line-source"
    private const val MEASURE_CASING_LAYER = "measure-casing-layer"
    private const val MEASURE_LINE_LAYER = "measure-line-layer"
    private const val MEASURE_POINT_SOURCE = "measure-point-source"
    private const val MEASURE_POINT_LAYER = "measure-point-layer"
    private const val MEASURE_LABEL_LAYER = "measure-label-layer"

    /**
     * Render any number of track lines from a single FeatureCollection, using data-driven paint so
     * each feature's `color`/`width`/`opacity` properties style it. One source + one layer handles
     * the whole viewing set (plus the recording track), so adding/removing tracks never churns the
     * style. An empty collection clears the lines.
     */
    fun updateTracksOnMap(style: Style, geoJson: String) {
        try {
            val sourceId = "track-source"
            val layerId = "track-layer"

            val existing = style.getSourceAs<GeoJsonSource>(sourceId)
            if (existing != null) {
                existing.setGeoJson(geoJson)
                return
            }

            style.addSource(GeoJsonSource(sourceId, geoJson))
            val lineLayer = LineLayer(layerId, sourceId).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(Expression.get("width")),
                PropertyFactory.lineOpacity(Expression.get("opacity")),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            style.addLayer(lineLayer)
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /**
     * Single marker for elevation-chart scrubbing. An empty FeatureCollection draws nothing, so the
     * caller can pass the marker geojson unconditionally. Updates the source in place for smooth drag.
     */
    fun updateElevationMarkerOnMap(style: Style, geoJson: String) {
        try {
            val sourceId = "elevation-marker-source"
            val layerId = "elevation-marker-layer"

            val existing = style.getSourceAs<GeoJsonSource>(sourceId)
            if (existing != null) {
                existing.setGeoJson(geoJson)
                return
            }

            style.addSource(GeoJsonSource(sourceId, geoJson))
            val circleLayer = CircleLayer(layerId, sourceId).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Expression.get("color"))
            )
            style.addLayer(circleLayer)
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
                        PropertyFactory.lineColor(FRIEND_TRACK_COLOR),
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
                        PropertyFactory.circleColor(FRIEND_TRACK_COLOR),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor("#FFFFFF")
                    )
                    style.addLayer(pointLayer)

                    val labelLayer = SymbolLayer(labelLayerId, pointSourceId).withProperties(
                        PropertyFactory.textField(Expression.get("clientId")),
                        PropertyFactory.textSize(12f),
                        PropertyFactory.textColor(FRIEND_TRACK_COLOR),
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
     * Update two-finger measurement visualization on the map.
     */
    fun updateMeasurementOnMap(style: Style, measurement: TwoFingerMeasurement?) {
        try {
            if (measurement == null) {
                fadeOutMeasurementLayers(style)
                return
            }
            removeMeasurementLayers(style)

            style.addSource(GeoJsonSource(MEASURE_LINE_SOURCE, buildMeasurementLineGeoJson(measurement)))

            style.addLayer(LineLayer(MEASURE_CASING_LAYER, MEASURE_LINE_SOURCE).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineCap("round")
            ))
            style.addLayer(LineLayer(MEASURE_LINE_LAYER, MEASURE_LINE_SOURCE).withProperties(
                PropertyFactory.lineColor("#000000"),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineDasharray(arrayOf(3f, 2f))
            ))

            style.addSource(GeoJsonSource(MEASURE_POINT_SOURCE, buildMeasurementPointsGeoJson(measurement)))

            val endpointFilter = Expression.eq(Expression.get("role"), Expression.literal("endpoint"))
            val labelFilter = Expression.eq(Expression.get("role"), Expression.literal("label"))

            style.addLayer(CircleLayer(MEASURE_POINT_LAYER, MEASURE_POINT_SOURCE).withProperties(
                PropertyFactory.circlePitchAlignment("viewport"),
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor("#000000"),
                PropertyFactory.circleOpacity(0.8f),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor("#FFFFFF")
            ).also { it.setFilter(endpointFilter) })

            style.addLayer(SymbolLayer(MEASURE_LABEL_LAYER, MEASURE_POINT_SOURCE).withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf("NotoSansRegular")),
                PropertyFactory.textSize(14f),
                PropertyFactory.textColor("#000000"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textAnchor("bottom"),
                PropertyFactory.textOffset(arrayOf(0f, -0.6f)),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            ).also { it.setFilter(labelFilter) })
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    /** Fade the measurement layers to transparent; call [removeMeasurementLayers] after the fade. */
    private fun fadeOutMeasurementLayers(style: Style) {
        val fade = TransitionOptions(TwoFingerTap.FADE_OUT_MS, 0)
        (style.getLayer(MEASURE_CASING_LAYER) as? LineLayer)?.apply {
            setLineOpacityTransition(fade)
            setProperties(PropertyFactory.lineOpacity(0f))
        }
        (style.getLayer(MEASURE_LINE_LAYER) as? LineLayer)?.apply {
            setLineOpacityTransition(fade)
            setProperties(PropertyFactory.lineOpacity(0f))
        }
        (style.getLayer(MEASURE_POINT_LAYER) as? CircleLayer)?.apply {
            setCircleOpacityTransition(fade)
            setProperties(PropertyFactory.circleOpacity(0f))
        }
        (style.getLayer(MEASURE_LABEL_LAYER) as? SymbolLayer)?.apply {
            setTextOpacityTransition(fade)
            setProperties(PropertyFactory.textOpacity(0f))
        }
    }

    fun removeMeasurementLayers(style: Style) {
        style.getLayer(MEASURE_LABEL_LAYER)?.let { style.removeLayer(it) }
        style.getLayer(MEASURE_POINT_LAYER)?.let { style.removeLayer(it) }
        style.getLayer(MEASURE_LINE_LAYER)?.let { style.removeLayer(it) }
        style.getLayer(MEASURE_CASING_LAYER)?.let { style.removeLayer(it) }
        style.getSource(MEASURE_POINT_SOURCE)?.let { style.removeSource(it) }
        style.getSource(MEASURE_LINE_SOURCE)?.let { style.removeSource(it) }
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
                        addStringProperty("color", point.color ?: PointColors.DEFAULT)
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

    fun updateCoordGridOnMap(style: Style, geoJson: String?) {
        try {
            val sourceId = "coord-grid-source"
            val lineLayerId = "coord-grid-line-layer"
            val casingLayerId = "coord-grid-line-casing-layer"
            val zoneLayerId = "coord-grid-zone-layer"
            val labelLayerId = "coord-grid-label-layer"
            val cellLayerId = "coord-grid-cell-layer"

            if (geoJson == null) {
                style.getLayer(cellLayerId)?.let { style.removeLayer(it) }
                style.getLayer(labelLayerId)?.let { style.removeLayer(it) }
                style.getLayer(lineLayerId)?.let { style.removeLayer(it) }
                style.getLayer(casingLayerId)?.let { style.removeLayer(it) }
                style.getLayer(zoneLayerId)?.let { style.removeLayer(it) }
                style.getSource(sourceId)?.let { style.removeSource(it) }
                return
            }

            val existingSource = style.getSourceAs<GeoJsonSource>(sourceId)
            if (existingSource != null) {
                existingSource.setGeoJson(geoJson)
            } else {
                val source = GeoJsonSource(sourceId, geoJson)
                style.addSource(source)

                val overlayBelow = listOf("track-layer", "ruler-line-layer", "friend-track-line-layer", "saved-points-layer")
                    .firstOrNull { style.getLayer(it) != null }

                val zoneLayer = LineLayer(zoneLayerId, sourceId).withProperties(
                    PropertyFactory.lineColor("#E67E22"),
                    PropertyFactory.lineWidth(1.5f),
                    PropertyFactory.lineOpacity(0.7f)
                )
                zoneLayer.setFilter(Expression.has("zone"))
                if (overlayBelow != null) style.addLayerBelow(zoneLayer, overlayBelow) else style.addLayer(zoneLayer)

                // White casing beneath the thin grid line so it stays visible over dark
                // basemaps (e.g. satellite), mirroring the white halo used on the labels.
                val lineCasingLayer = LineLayer(casingLayerId, sourceId).withProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(1.6f),
                    PropertyFactory.lineOpacity(0.35f)
                )
                lineCasingLayer.setFilter(Expression.not(Expression.has("zone")))
                if (overlayBelow != null) style.addLayerBelow(lineCasingLayer, overlayBelow) else style.addLayer(lineCasingLayer)

                val lineLayer = LineLayer(lineLayerId, sourceId).withProperties(
                    PropertyFactory.lineColor("#000000"),
                    PropertyFactory.lineWidth(0.8f),
                    PropertyFactory.lineOpacity(0.3f)
                )
                lineLayer.setFilter(Expression.not(Expression.has("zone")))
                if (overlayBelow != null) style.addLayerBelow(lineLayer, overlayBelow) else style.addLayer(lineLayer)

                val labelLayer = SymbolLayer(labelLayerId, sourceId).withProperties(
                    PropertyFactory.textField(Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("NotoSansRegular")),
                    PropertyFactory.textSize(10f),
                    PropertyFactory.textColor("#000000"),
                    PropertyFactory.textOpacity(0.6f),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.5f),
                    PropertyFactory.textAnchor(Expression.get("anchor")),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textIgnorePlacement(false),
                    PropertyFactory.textPadding(2f)
                )
                labelLayer.setFilter(Expression.not(Expression.has("cell")))
                if (overlayBelow != null) style.addLayerBelow(labelLayer, overlayBelow) else style.addLayer(labelLayer)

                val cellLayer = SymbolLayer(cellLayerId, sourceId).withProperties(
                    PropertyFactory.textField(Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("NotoSansRegular")),
                    PropertyFactory.textSize(14f),
                    PropertyFactory.textColor("#C62828"),
                    PropertyFactory.textOpacity(0.85f),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.5f),
                    PropertyFactory.textAnchor(Expression.get("anchor")),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textIgnorePlacement(false),
                    PropertyFactory.textPadding(8f)
                )
                cellLayer.setFilter(Expression.has("cell"))
                if (overlayBelow != null) style.addLayerBelow(cellLayer, overlayBelow) else style.addLayer(cellLayer)
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

            // Re-activate on every call: each setStyle replaces the map style,
            // and the LocationComponent's puck layers must be rebound to the
            // new style or the puck silently disappears.
            val request = LocationEngineRequest.Builder(LOCATION_ENGINE_INTERVAL_MS)
                .setFastestInterval(LOCATION_ENGINE_FASTEST_INTERVAL_MS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .build()
            val options = LocationComponentOptions.builder(context)
                .accuracyAlpha(ACCURACY_RING_ALPHA)
                .accuracyColor(Color.parseColor(ACCURACY_RING_COLOR))
                .accuracyAnimationEnabled(true)
                .staleStateTimeout(STALE_FIX_TIMEOUT_MS)
                .foregroundStaleTintColor(Color.GRAY)
                .backgroundStaleTintColor(Color.LTGRAY)
                .build()
            locationComponent.activateLocationComponent(
                org.maplibre.android.location.LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(true)
                    .locationEngineRequest(request)
                    .locationComponentOptions(options)
                    .build()
            )

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

    // "nav-arrows" first so its layer is removed before the shared nav-remaining source it rides on.
    private val navLayerIds = listOf("nav-arrows", "nav-offcourse", "nav-remaining", "nav-completed")
    private const val NAV_ARROW_IMAGE = "nav-arrow"

    // Navigation line colors live in the shared NavColors (kept in sync with iOS). Widths are here.
    private const val NAV_COMPLETED_WIDTH = 4f
    private const val NAV_REMAINING_WIDTH = 6f
    private const val NAV_OFFCOURSE_WIDTH = 3f
    private const val NAV_ARROW_SPACING = 48f

    fun updateNavigationOnMap(
        style: Style,
        completedGeoJson: String?,
        remainingGeoJson: String?,
        offCourseGeoJson: String?,
    ) {
        try {
            navLayerIds.forEach { id ->
                style.getLayer("$id-layer")?.let { style.removeLayer(it) }
                style.getSource("$id-source")?.let { style.removeSource(it) }
            }
            if (completedGeoJson == null || remainingGeoJson == null) return

            style.addSource(GeoJsonSource("nav-completed-source", completedGeoJson))
            style.addLayer(
                LineLayer("nav-completed-layer", "nav-completed-source").withProperties(
                    PropertyFactory.lineColor(NavColors.completed),
                    PropertyFactory.lineWidth(NAV_COMPLETED_WIDTH),
                    PropertyFactory.lineOpacity(0.7f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
            style.addSource(GeoJsonSource("nav-remaining-source", remainingGeoJson))
            style.addLayer(
                LineLayer("nav-remaining-layer", "nav-remaining-source").withProperties(
                    PropertyFactory.lineColor(NavColors.remaining),
                    PropertyFactory.lineWidth(NAV_REMAINING_WIDTH),
                    PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
            // Direction arrows repeated along the remaining line, rotated to follow it.
            if (style.getImage(NAV_ARROW_IMAGE) == null) {
                style.addImage(NAV_ARROW_IMAGE, navArrowImage())
            }
            style.addLayer(
                SymbolLayer("nav-arrows-layer", "nav-remaining-source").withProperties(
                    PropertyFactory.iconImage(NAV_ARROW_IMAGE),
                    PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                    PropertyFactory.symbolSpacing(NAV_ARROW_SPACING),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )
            if (offCourseGeoJson != null) {
                style.addSource(GeoJsonSource("nav-offcourse-source", offCourseGeoJson))
                style.addLayer(
                    LineLayer("nav-offcourse-layer", "nav-offcourse-source").withProperties(
                        PropertyFactory.lineColor(NavColors.offCourse),
                        PropertyFactory.lineWidth(NAV_OFFCOURSE_WIDTH),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                    )
                )
            }
        } catch (e: Exception) {
            Logger.e(e, "Map render error")
        }
    }

    // White chevron with a dark outline for contrast on the blue line, pointing +x (east);
    // the symbol layer rotates it to the line's travel direction.
    private fun navArrowImage(): Bitmap {
        val size = 28
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val path = Path().apply {
            moveTo(8f, 6f)
            lineTo(22f, 14f)
            lineTo(8f, 22f)
            lineTo(13f, 14f)
            close()
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, outline)
        canvas.drawPath(path, fill)
        return bmp
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

