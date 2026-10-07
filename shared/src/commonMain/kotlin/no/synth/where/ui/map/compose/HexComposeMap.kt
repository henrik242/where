package no.synth.where.ui.map.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.synth.where.data.DownloadLayers
import no.synth.where.data.geo.LatLng
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/**
 * The offline-download region picker map: the layer's base tiles plus the hex grid, coloured by
 * download status. [hexGeoJson] carries a `status` property per hex (none/downloaded/downloading);
 * [downloadingOpacity] pulses the in-progress fill. Taps report the tapped lat/lng via [onMapClick]
 * (the screen maps it to a hex with HexGrid.hexAtPoint).
 */
@Composable
fun HexComposeMap(
    layerId: String,
    hexGeoJson: String,
    downloadingOpacity: Float,
    modifier: Modifier = Modifier,
    controller: WhereMapController? = null,
    userLocation: LatLng? = null,
    onMapClick: (LatLng) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val onClick by rememberUpdatedState(onMapClick)
    val baseStyle = remember(layerId) { DownloadLayers.getDownloadStyleJson(layerId) }

    val state =
        rememberMapState(
            baseStyle = BaseStyle.Json(baseStyle),
            initialCameraPosition =
                CameraPosition(target = Position(longitude = 13.0, latitude = 64.5), zoom = 3.5),
        ) {
            val source = rememberGeoJsonSource(GeoJsonData.JsonString(hexGeoJson))
            FillLayer(
                id = "hex-fill-downloaded",
                source = source,
                filter = Feature["status"] eqS "downloaded",
                color = hexColorConst("#4CAF50"),
                opacity = const(0.4f),
            )
            FillLayer(
                id = "hex-downloading-fill",
                source = source,
                filter = Feature["status"] eqS "downloading",
                color = hexColorConst("#C4622D"),
                opacity = const(downloadingOpacity),
            )
            LineLayer(
                id = "hex-outline",
                source = source,
                color = hexColorConst("#888888"),
                width = const(1.5.dp),
                opacity = const(0.7f),
            )
            LineLayer(
                id = "hex-outline-downloaded",
                source = source,
                filter = Feature["status"] eqS "downloaded",
                color = hexColorConst("#2E7D32"),
                width = const(1.5.dp),
            )
            LineLayer(
                id = "hex-outline-downloading",
                source = source,
                filter = Feature["status"] eqS "downloading",
                color = hexColorConst("#6E3A16"),
                width = const(1.5.dp),
            )
            if (userLocation != null) {
                val puck = rememberGeoJsonSource(
                    GeoJsonData.JsonString(
                        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${userLocation.longitude},${userLocation.latitude}]}}]}"""
                    )
                )
                CircleLayer(id = "hex-user-dot", source = puck, radius = const(7.dp), color = hexColorConst("#1E88E5"), strokeWidth = const(2.dp), strokeColor = const(androidx.compose.ui.graphics.Color.White))
            }
        }

    if (controller != null) {
        DisposableEffect(state) {
            controller.state = state
            controller.scope = scope
            onDispose { controller.state = null }
        }
    }

    val interactions =
        remember {
            MapInteractions(MapInteractions.Standard) {
                callbacks {
                    click {
                        onUnhandled { event ->
                            event.position?.let { onClick(LatLng(it.latitude, it.longitude)) }
                            ClickResult.Pass
                        }
                    }
                }
            }
        }

    MaplibreMap(modifier = modifier, state = state, interactions = interactions) {
        // Keep only the built-in attribution (tile-source licensing); the picker has no compass.
        include(MapOverlay.AttributionOnly)
    }
}

private fun hexColorConst(s: String) = const(hexColor(s))

private infix fun Expression<*>.eqS(value: String): Expression<BooleanValue> =
    this.cast<EquatableValue?>() eq const(value).cast<EquatableValue?>()
