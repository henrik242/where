package no.synth.where.ui.map.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import no.synth.where.data.geo.LatLng
import no.synth.where.ui.map.NavColors
import no.synth.where.ui.map.NavStyle
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.has
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.expressions.value.FormattedValue
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.expressions.value.SymbolPlacement
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionStyle
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.jetbrains.compose.resources.painterResource
import no.synth.where.resources.Res
import no.synth.where.resources.nav_arrow

private const val EMPTY_FC = """{"type":"FeatureCollection","features":[]}"""

// Matches the single glyph stack bundled with the app on both platforms (asset://fonts/NotoSansRegular
// on Android, the app bundle's Fonts/ on iOS). A text layer that asks for any other stack draws nothing.
private val FONTS = listOf("NotoSansRegular")

/** Parse a "#RRGGBB" / "#RRGGBBAA" hex string into a Compose [Color]. */
internal fun hexColor(s: String): Color {
    val h = s.removePrefix("#")
    return when (h.length) {
        6 -> Color(0xFF000000 or h.toLong(16))
        8 -> {
            val v = h.toLong(16)
            Color(((v ushr 8) or (v shl 24)) and 0xFFFFFFFF) // RRGGBBAA -> AARRGGBB
        }
        else -> Color.Magenta
    }
}

/**
 * Everything the shared map renders, as the already-built GeoJSON strings the app's repositories
 * produce. A null string draws nothing. Decoupled from the repositories so the same renderer serves
 * the real app and the spike harness.
 */
data class WhereMapRenderData(
    val styleJson: String,
    val initialTarget: LatLng? = null,
    val initialZoom: Double = 13.0,
    val tracksGeoJson: String? = null,
    val elevationMarkerGeoJson: String? = null,
    val savedPointsGeoJson: String? = null,
    val friendTrackGeoJson: String? = null,
    val mySharedPointsGeoJson: String? = null,
    val friendSharedPointsGeoJson: String? = null,
    val searchResultsGeoJson: String? = null,
    val searchHighlightGeoJson: String? = null,
    val rulerLineGeoJson: String? = null,
    val rulerPointsGeoJson: String? = null,
    val measurementLineGeoJson: String? = null,
    val measurementPointsGeoJson: String? = null,
    val coordGridGeoJson: String? = null,
    val navCompletedGeoJson: String? = null,
    val navRemainingGeoJson: String? = null,
    val navOffCourseGeoJson: String? = null,
    val userLocation: LatLng? = null,
)

/** Map interaction callbacks the host screen reacts to. All take WGS84 lat/lng. */
data class WhereMapCallbacks(
    val onMapClick: (LatLng) -> Unit = {},
    val onLongPress: (LatLng) -> Unit = {},
    val onCameraMove: (center: LatLng, zoom: Double, bearing: Double) -> Unit = { _, _, _ -> },
    /** A stationary two-finger tap, with the WGS84 positions of the two fingers. */
    val onTwoFingerTap: (LatLng, LatLng) -> Unit = { _, _ -> },
    /** The user moved the camera by hand (pan/zoom/rotate) - e.g. to drop a follow mode. */
    val onUserGesture: () -> Unit = {},
)

/**
 * The shared maplibre-compose map: a base style plus every app overlay, declared once in commonMain.
 * [controller] drives the camera; [callbacks] report taps, camera movement and a two-finger tap;
 * [rotateEnabled] gates the rotate gesture (north lock). The navigation direction-arrow icon and
 * heading-follow rotation are not wired yet.
 */
@Composable
fun WhereComposeMap(
    data: WhereMapRenderData,
    modifier: Modifier = Modifier,
    controller: WhereMapController? = null,
    callbacks: WhereMapCallbacks = WhereMapCallbacks(),
    rotateEnabled: Boolean = true,
    attributionVisible: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val cb by rememberUpdatedState(callbacks)
    val state =
        rememberMapState(
            baseStyle = BaseStyle.Json(data.styleJson),
            initialCameraPosition =
                CameraPosition(
                    target =
                        data.initialTarget?.let { Position(longitude = it.longitude, latitude = it.latitude) }
                            ?: Position(longitude = 10.0, latitude = 60.0),
                    zoom = data.initialZoom,
                ),
        ) {
            // Declaration order is bottom-to-top. Grid sits under the data overlays.
            CoordGridLayers(data.coordGridGeoJson)
            TrackLayers(data.tracksGeoJson)
            NavigationLayers(data.navCompletedGeoJson, data.navRemainingGeoJson, data.navOffCourseGeoJson)
            FriendTrackLayers(data.friendTrackGeoJson)
            RulerLayers(data.rulerLineGeoJson, data.rulerPointsGeoJson)
            MeasurementLayers(data.measurementLineGeoJson, data.measurementPointsGeoJson)
            SavedPointsLayer(data.savedPointsGeoJson)
            SharedPointsLayers(data.mySharedPointsGeoJson, "my-shared-points")
            SharedPointsLayers(data.friendSharedPointsGeoJson, "friend-shared-points")
            SearchResultLayers(data.searchResultsGeoJson, data.searchHighlightGeoJson)
            ElevationMarkerLayer(data.elevationMarkerGeoJson)
            UserLocationLayer(data.userLocation)
        }

    if (controller != null) {
        DisposableEffect(state) {
            controller.state = state
            controller.scope = scope
            onDispose { controller.state = null }
        }
    }

    // Mirror the attribution's expanded state (same rules as the library: starts expanded, collapses
    // on a gesture, toggles on the info button) so we can tint the info icon to match what's behind
    // it - light on the dark pill when open, dark over the map when collapsed.
    var attrExpanded by remember { mutableStateOf(true) }

    // Report camera movement (crosshair readout, grid, compass bearing).
    LaunchedEffect(state) {
        snapshotFlow { state.cameraPosition }
            .collect { pos ->
                cb.onCameraMove(
                    LatLng(pos.target.latitude, pos.target.longitude),
                    pos.zoom,
                    pos.bearing,
                )
                if (state.cameraMoveReason == CameraMoveReason.GESTURE) {
                    cb.onUserGesture()
                    attrExpanded = false
                }
            }
    }

    val interactions =
        remember(rotateEnabled) {
            MapInteractions(MapInteractions.Standard) {
                camera { rotate { enabled = rotateEnabled } }
                callbacks {
                    click {
                        onUnhandled { event ->
                            event.position?.let { p -> cb.onMapClick(LatLng(p.latitude, p.longitude)) }
                            ClickResult.Pass
                        }
                    }
                    longClick {
                        onEvent { event ->
                            event.position?.let { p -> cb.onLongPress(LatLng(p.latitude, p.longitude)) }
                            ClickResult.Consume
                        }
                    }
                }
            }
        }

    // The two-finger-tap detector lives on the Box that is the MAP's PARENT (not a sibling on top),
    // so pointer events flow ancestor -> descendant: the detector observes without consuming and the
    // map underneath still handles taps, pan and pinch.
    val tapModifier =
        if (controller != null) {
            Modifier.pointerInput(controller) {
                detectTwoFingerTap { a, b ->
                    val d = density
                    val ll1 = controller.screenToLatLng(with(d) { DpOffset(a.x.toDp(), a.y.toDp()) })
                    val ll2 = controller.screenToLatLng(with(d) { DpOffset(b.x.toDp(), b.y.toDp()) })
                    if (ll1 != null && ll2 != null) cb.onTwoFingerTap(ll1, ll2)
                }
            }
        } else {
            Modifier
        }

    Box(modifier = modifier.then(tapModifier)) {
        MaplibreMap(modifier = Modifier.matchParentSize(), state = state, interactions = interactions) {
            // We draw our own MapCompass (with north-lock), so skip maplibre's built-in compass
            // (it doubled ours) and render only the tile-source attribution, bottom-left. Themed to
            // follow light/dark mode (the library default is a fixed white pill). Hidden via alpha -
            // not removed - while the crosshair readout occupies that corner, so its expanded/
            // collapsed state survives toggling the crosshair. end padding clears the right FAB column.
            val attrContainer = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            val attrContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            ExpandingAttributionButton(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp, end = 64.dp)
                    .alpha(if (attributionVisible) 1f else 0f),
                expandedStyle = AttributionStyle(containerColor = attrContainer, contentColor = attrContent),
                collapsedStyle = AttributionStyle(
                    containerColor = attrContainer.copy(alpha = 0f),
                    contentColor = attrContent.copy(alpha = 0f),
                ),
                // The default info button tints its icon a fixed black: invisible on the dark pill
                // when open, but wanted when closed over the (usually light) map. Tint it with the
                // themed content color while expanded, dark while collapsed.
                toggleButton = { onClick ->
                    Box(
                        Modifier.size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(
                                onClick = { attrExpanded = !attrExpanded; onClick() },
                                role = Role.Button,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = AttributionDefaults.icon(),
                            contentDescription = AttributionDefaults.contentDescription(),
                            modifier = Modifier.size(24.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(
                                if (attrExpanded) attrContent else Color.Black.copy(alpha = 0.75f),
                            ),
                        )
                    }
                },
            )
        }
    }
}

/**
 * Fires [onTap] with the two touch points of a stationary two-finger tap. Does not consume events,
 * so a moving two-finger gesture (pinch/rotate) falls through to the map. Bails if a finger moves
 * past touch slop from where it first went down, or if more than two fingers are involved.
 */
private suspend fun PointerInputScope.detectTwoFingerTap(onTap: (Offset, Offset) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var sawExactlyTwo = false
        var tooMany = false
        var moved = false
        var p1 = Offset.Unspecified
        var p2 = Offset.Unspecified
        val downPositions = mutableMapOf<PointerId, Offset>()
        val slop = viewConfiguration.touchSlop * 1.5f
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            when {
                pressed.size > 2 -> tooMany = true
                pressed.size == 2 -> {
                    sawExactlyTwo = true
                    p1 = pressed[0].position
                    p2 = pressed[1].position
                }
            }
            event.changes.forEach { c ->
                if (c.pressed) {
                    val start = downPositions.getOrPut(c.id) { c.position }
                    if ((c.position - start).getDistance() > slop) moved = true
                }
            }
            if (event.changes.none { it.pressed }) break
        }
        if (sawExactlyTwo && !tooMany && !moved && p1.isSpecified && p2.isSpecified) {
            onTap(p1, p2)
        }
    }
}

@Composable
private fun src(id: String, geoJson: String?): GeoJsonSource =
    rememberGeoJsonSource(GeoJsonData.JsonString(geoJson ?: EMPTY_FC))

/** Data-driven line color from the feature's `color` string property. */
private fun featureColor(): Expression<ColorValue> = Feature["color"].cast<ColorValue>()

private fun featureText(key: String): Expression<FormattedValue?> = Feature[key].cast<FormattedValue?>()

@Composable
private fun TrackLayers(geoJson: String?) {
    val source = src("track-source", geoJson)
    LineLayer(
        id = "track-layer",
        source = source,
        color = featureColor(),
        width = Feature["width"].cast(),
        opacity = Feature["opacity"].cast(),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
}

@Composable
private fun ElevationMarkerLayer(geoJson: String?) {
    val source = src("elevation-marker-source", geoJson)
    CircleLayer(
        id = "elevation-marker-layer",
        source = source,
        radius = const(7.dp),
        color = const(Color.White),
        strokeWidth = const(3.dp),
        strokeColor = featureColor(),
    )
}

@Composable
private fun FriendTrackLayers(geoJson: String?) {
    val source = src("friend-track-source", geoJson)
    // Dashed line per friend (color from feature).
    LineLayer(
        id = "friend-track-line-layer",
        source = source,
        filter = Feature.geometryType() eqStr "LineString",
        color = featureColor(),
        width = const(4.dp),
        opacity = const(0.8f),
        dasharray = const(listOf(3f, 1.5f)),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    // Halo grows as you zoom out (18dp at z8 -> 0 at z15), only for still-sharing friends.
    CircleLayer(
        id = "friend-track-halo-layer",
        source = source,
        filter = all(Feature["active"].cast<BooleanValue>(), Feature.geometryType() eqStr "Point"),
        radius = interpolate(linear(), zoom(), 8 to const(18.dp), 15 to const(0.dp)),
        color = featureColor(),
        opacity = const(0.22f),
        strokeWidth = interpolate(linear(), zoom(), 8 to const(3.dp), 15 to const(0.dp)),
        strokeColor = const(Color.White),
    )
    CircleLayer(
        id = "friend-track-point-layer",
        source = source,
        filter = Feature.geometryType() eqStr "Point",
        radius = const(6.dp),
        color = featureColor(),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
    )
    SymbolLayer(
        id = "friend-track-label-layer",
        source = source,
        filter = Feature.geometryType() eqStr "Point",
        minZoom = 11f,
        textField = featureText("label"),
        textFont = const(FONTS),
        textSize = const(12.sp),
        textColor = featureColor(),
        textHaloColor = const(Color.White),
        textHaloWidth = const(1.5.dp),
        textOffset = textOffset(0.em, 1.5.em),
        textAnchor = const(SymbolAnchor.Top),
    )
}

@Composable
private fun RulerLayers(lineGeoJson: String?, pointsGeoJson: String?) {
    val orange = hexColor("#FFA500")
    LineLayer(
        id = "ruler-line-layer",
        source = src("ruler-line-source", lineGeoJson),
        color = const(orange),
        width = const(3.dp),
        opacity = const(0.9f),
        dasharray = const(listOf(2f, 2f)),
    )
    CircleLayer(
        id = "ruler-point-layer",
        source = src("ruler-point-source", pointsGeoJson),
        radius = const(6.dp),
        color = const(orange),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
    )
}

@Composable
private fun MeasurementLayers(lineGeoJson: String?, pointsGeoJson: String?) {
    val lineSource = src("measure-line-source", lineGeoJson)
    LineLayer(
        id = "measure-casing-layer",
        source = lineSource,
        color = const(Color.White),
        width = const(4.5.dp),
        opacity = const(0.6f),
        cap = const(LineCap.Round),
    )
    LineLayer(
        id = "measure-line-layer",
        source = lineSource,
        color = const(Color.Black),
        width = const(2.5.dp),
        opacity = const(0.8f),
        cap = const(LineCap.Round),
        dasharray = const(listOf(3f, 2f)),
    )
    val pointSource = src("measure-point-source", pointsGeoJson)
    CircleLayer(
        id = "measure-point-layer",
        source = pointSource,
        filter = Feature["role"] eqStr "endpoint",
        radius = const(5.dp),
        color = const(Color.Black),
        opacity = const(0.8f),
        strokeWidth = const(1.5.dp),
        strokeColor = const(Color.White),
    )
    SymbolLayer(
        id = "measure-label-layer",
        source = pointSource,
        filter = Feature["role"] eqStr "label",
        textField = featureText("label"),
        textFont = const(FONTS),
        textSize = const(14.sp),
        textColor = const(Color.Black),
        textHaloColor = const(Color.White),
        textHaloWidth = const(2.dp),
        textAnchor = const(SymbolAnchor.Bottom),
        textOffset = textOffset(0.em, (-0.6f).em),
        textAllowOverlap = const(true),
    )
}

@Composable
private fun SavedPointsLayer(geoJson: String?) {
    CircleLayer(
        id = "saved-points-layer",
        source = src("saved-points-source", geoJson),
        radius = const(6.dp),
        color = featureColor(),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
    )
}

@Composable
private fun SharedPointsLayers(geoJson: String?, idPrefix: String) {
    val source = src("$idPrefix-source", geoJson)
    CircleLayer(
        id = "$idPrefix-ring-layer",
        source = source,
        radius = const(9.dp),
        color = const(Color.White),
        strokeWidth = const(3.dp),
        strokeColor = featureColor(),
    )
    CircleLayer(
        id = "$idPrefix-dot-layer",
        source = source,
        radius = const(4.dp),
        color = featureColor(),
    )
    SymbolLayer(
        id = "$idPrefix-label-layer",
        source = source,
        textField = featureText("name"),
        textFont = const(FONTS),
        textSize = const(13.sp),
        textColor = hexColorConst("#222222"),
        textHaloColor = const(Color.White),
        textHaloWidth = const(1.5.dp),
        textOffset = textOffset(0.em, 1.2.em),
        textAnchor = const(SymbolAnchor.Top),
    )
}

@Composable
private fun SearchResultLayers(resultsGeoJson: String?, highlightGeoJson: String?) {
    val pink = hexColor("#E91E63")
    CircleLayer(
        id = "search-results-layer",
        source = src("search-results-source", resultsGeoJson),
        radius = const(7.dp),
        color = const(pink),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
        opacity = const(0.9f),
    )
    CircleLayer(
        id = "search-highlight-layer",
        source = src("search-highlight-source", highlightGeoJson),
        radius = const(11.dp),
        color = const(pink),
        strokeWidth = const(3.dp),
        strokeColor = const(Color.White),
    )
}

@Composable
private fun CoordGridLayers(geoJson: String?) {
    val source = src("coord-grid-source", geoJson)
    LineLayer(
        id = "coord-grid-zone-layer",
        source = source,
        filter = Feature.has("zone"),
        color = hexColorConst("#E67E22"),
        width = const(1.5.dp),
        opacity = const(0.7f),
    )
    LineLayer(
        id = "coord-grid-casing-layer",
        source = source,
        filter = !Feature.has("zone"),
        color = const(Color.White),
        width = const(1.6.dp),
        opacity = const(0.35f),
    )
    LineLayer(
        id = "coord-grid-line-layer",
        source = source,
        filter = !Feature.has("zone"),
        color = const(Color.Black),
        width = const(0.8.dp),
        opacity = const(0.3f),
    )
    SymbolLayer(
        id = "coord-grid-label-layer",
        source = source,
        filter = !Feature.has("cell"),
        textField = featureText("label"),
        textFont = const(FONTS),
        textSize = const(10.sp),
        textColor = const(Color.Black),
        textOpacity = const(0.6f),
        textHaloColor = const(Color.White),
        textHaloWidth = const(1.5.dp),
    )
    SymbolLayer(
        id = "coord-grid-cell-layer",
        source = source,
        filter = Feature.has("cell"),
        textField = featureText("label"),
        textFont = const(FONTS),
        textSize = const(14.sp),
        textColor = hexColorConst("#C62828"),
        textOpacity = const(0.85f),
        textHaloColor = const(Color.White),
        textHaloWidth = const(1.5.dp),
    )
}

@Composable
private fun NavigationLayers(completedGeoJson: String?, remainingGeoJson: String?, offCourseGeoJson: String?) {
    val remaining = hexColor(NavColors.remaining)
    LineLayer(
        id = "nav-completed-layer",
        source = src("nav-completed-source", completedGeoJson),
        color = const(remaining),
        width = const(NavStyle.completedWidth.toFloat().dp),
        opacity = const(NavStyle.traversedOpacity.toFloat()),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        dasharray = const(listOf(0f, NavStyle.traversedDashGap.toFloat())),
    )
    val remainingSource = src("nav-remaining-source", remainingGeoJson)
    LineLayer(
        id = "nav-remaining-layer",
        source = remainingSource,
        color = const(remaining),
        width = const(NavStyle.remainingWidth.toFloat().dp),
        opacity = const(NavStyle.remainingOpacity.toFloat()),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    // Direction arrows repeated along the remaining line, rotated to follow it.
    SymbolLayer(
        id = "nav-arrows-layer",
        source = remainingSource,
        iconImage = image(painterResource(Res.drawable.nav_arrow)),
        placement = const(SymbolPlacement.Line),
        spacing = const(NavStyle.arrowSpacing.toFloat().dp),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
    LineLayer(
        id = "nav-offcourse-layer",
        source = src("nav-offcourse-source", offCourseGeoJson),
        color = const(hexColor(NavColors.offCourse)),
        width = const(NavStyle.offCourseWidth.toFloat().dp),
        dasharray = const(listOf(NavStyle.offCourseDash.toFloat(), NavStyle.offCourseDash.toFloat())),
    )
}

@Composable
private fun UserLocationLayer(location: LatLng?) {
    val geoJson =
        location?.let {
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.longitude},${it.latitude}]}}]}"""
        }
    val source = src("user-location-source", geoJson)
    CircleLayer(
        id = "user-location-accuracy",
        source = source,
        radius = const(16.dp),
        color = hexColorConst("#1E88E5"),
        opacity = const(0.3f),
    )
    CircleLayer(
        id = "user-location-dot",
        source = source,
        radius = const(7.dp),
        color = hexColorConst("#1E88E5"),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
    )
}

private fun hexColorConst(s: String) = const(hexColor(s))

/** `feature-property == "value"` filter helper. */
private infix fun Expression<*>.eqStr(value: String): Expression<BooleanValue> =
    this.cast<EquatableValue?>() eq const(value).cast<EquatableValue?>()
