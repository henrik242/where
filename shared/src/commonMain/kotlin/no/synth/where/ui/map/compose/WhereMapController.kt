package no.synth.where.ui.map.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.MapState
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

/**
 * Imperative camera handle over a maplibre-compose [MapState], replacing the camera parts of the
 * old per-platform MapViewProvider. Bound to a live map by [WhereComposeMap]; calls before binding
 * are dropped.
 */
class WhereMapController {
    internal var state: MapState? by mutableStateOf(null)
    internal var scope: CoroutineScope? = null

    private val position: CameraPosition?
        get() = state?.cameraPosition

    val cameraCenter: LatLng?
        get() = position?.target?.let { LatLng(it.latitude, it.longitude) }

    val cameraZoom: Double
        get() = position?.zoom ?: 0.0

    val cameraBearing: Double
        get() = position?.bearing ?: 0.0

    private fun launch(block: suspend () -> Unit) {
        scope?.launch { block() }
    }

    /** WGS84 position under a screen point, for gesture→map hit resolution. Null if not attached. */
    fun screenToLatLng(offset: DpOffset): LatLng? =
        state?.positionFromScreenLocation(offset)?.let { LatLng(it.latitude, it.longitude) }

    /** Jump (no animation) so opening a point or search result lands immediately. */
    fun setCamera(latitude: Double, longitude: Double, zoom: Double) {
        state?.setCameraPosition(
            CameraPosition(target = Position(longitude = longitude, latitude = latitude), zoom = zoom)
        )
    }

    fun panTo(latitude: Double, longitude: Double) {
        launch {
            state?.animateCamera(
                CameraUpdate(target = Position(longitude = longitude, latitude = latitude))
            )
        }
    }

    fun zoomIn() = launch { state?.animateCamera(CameraUpdate(zoom = cameraZoom + 1.0)) }

    fun zoomOut() = launch { state?.animateCamera(CameraUpdate(zoom = cameraZoom - 1.0)) }

    fun resetNorth() = launch { state?.animateCamera(CameraUpdate(bearing = 0.0)) }

    /** Center on [latitude]/[longitude] and set [bearing] in one update (camera follow). Instant. */
    fun follow(latitude: Double, longitude: Double, bearing: Double) {
        val cur = position ?: return
        state?.setCameraPosition(
            cur.copy(target = Position(longitude = longitude, latitude = latitude), bearing = bearing)
        )
    }

    /** Fit [bounds], optionally clamping in from the computed zoom (e.g. a lone friend dot). */
    fun animateToBounds(bounds: LatLngBounds, maxZoom: Double? = null) {
        launch {
            val s = state ?: return@launch
            val box =
                BoundingBox(
                    west = bounds.west,
                    south = bounds.south,
                    east = bounds.east,
                    north = bounds.north,
                )
            val fitted = s.cameraForBounds(boundingBox = box, fitPadding = DEFAULT_FIT_PADDING)
            val clamped =
                if (maxZoom != null && fitted.zoom > maxZoom) fitted.copy(zoom = maxZoom) else fitted
            s.animateCamera(CameraUpdate(target = clamped.target, zoom = clamped.zoom), CameraAnimation.Ease())
        }
    }

    private companion object {
        val DEFAULT_FIT_PADDING = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)
    }
}
