package no.synth.where.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import no.synth.where.data.Track
import no.synth.where.data.cumulativeDistances
import no.synth.where.data.elevationProfileOrNull
import no.synth.where.data.nearestPointIndex
import no.synth.where.resources.Res
import no.synth.where.resources.chart_elevation_profile
import no.synth.where.resources.chart_gain
import no.synth.where.resources.chart_range
import no.synth.where.util.formatDistance
import no.synth.where.util.parseHexColor
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// The marked point resolved for drawing: its track-point [index], the nearest downsampled profile
// [sample] to draw the indicator at, and the [elevation] to show. Bundled so it's computed once.
private data class MarkerSample(val index: Int, val sample: Int, val elevation: Double)

/**
 * Elevation profile pinned bottom-center. Touching or dragging the chart reports the nearest track
 * point index via [onScrub] so the caller can mark it on the map; [markerIndex] (the point currently
 * marked) drives the on-chart indicator so chart and map stay in sync, drawn in [markerColorHex]
 * (the focused track's color) to match the map marker. The marker persists after release, so a
 * plain tap is meaningful.
 */
@Composable
fun TrackAltitudeChart(
    track: Track,
    onScrub: (Int?) -> Unit = {},
    markerIndex: Int? = null,
    markerColorHex: String? = null,
    modifier: Modifier = Modifier,
) {
    val profile = remember(track.id, track.points) { track.elevationProfileOrNull() } ?: return
    val cum = remember(track.id, track.points) { track.cumulativeDistances() }
    val total = profile.totalDistance.takeIf { it > 0.0 } ?: 1.0

    // The profile is downsampled from the track points, so map the marked point index back to the
    // nearest drawn sample for the on-chart indicator. Computed once, used by the readout and draw.
    val marker = markerIndex?.takeIf { it in track.points.indices }?.let { idx ->
        val sample = nearestPointIndex(profile.distances, cum[idx])
        MarkerSample(idx, sample, track.points[idx].altitude ?: profile.elevations[sample])
    }

    val line = MaterialTheme.colorScheme.primary
    val fill = line.copy(alpha = 0.18f)
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = markerColorHex?.let { parseHexColor(it) } ?: MaterialTheme.colorScheme.primary
    val markerRing = MaterialTheme.colorScheme.surface

    val gainText = stringResource(Res.string.chart_gain, profile.gain.roundToInt().toString())
    val distanceText = profile.totalDistance.formatDistance()
    val rangeText = stringResource(
        Res.string.chart_range, profile.minEle.roundToInt().toString(), profile.maxEle.roundToInt().toString(),
    )
    val chartLabel = stringResource(Res.string.chart_elevation_profile)

    // Distance + elevation readout for the marked point, shown in place of the summary while scrubbing.
    val readout = marker?.let { "${cum[it.index].formatDistance()} · ${it.elevation.roundToInt()} m" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = readout ?: "$chartLabel: $gainText, $distanceText, $rangeText" },
    ) {
        if (readout != null) {
            Text(readout, style = MaterialTheme.typography.labelSmall, color = line)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(gainText, style = MaterialTheme.typography.labelSmall, color = label)
                Text(distanceText, style = MaterialTheme.typography.labelSmall, color = label)
                Text(rangeText, style = MaterialTheme.typography.labelSmall, color = label)
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .pointerInput(track.id) {
                    fun emit(px: Float) {
                        val dist = (px / size.width).coerceIn(0f, 1f) * total
                        onScrub(nearestPointIndex(cum, dist))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        emit(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed }
                            if (change != null) {
                                emit(change.position.x)
                                // Consume so the drag scrubs the chart instead of panning the map.
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val w = size.width; val h = size.height
            val eleRange = (profile.maxEle - profile.minEle).takeIf { it > 1.0 } ?: 1.0
            val distRange = profile.totalDistance.takeIf { it > 0.0 } ?: 1.0
            fun x(dist: Double) = (dist / distRange * w).toFloat()
            fun y(el: Double) = (h - (el - profile.minEle) / eleRange * h).toFloat()

            val path = Path().apply {
                moveTo(x(profile.distances.first()), y(profile.elevations.first()))
                for (k in 1 until profile.elevations.size) lineTo(x(profile.distances[k]), y(profile.elevations[k]))
            }
            val area = Path().apply {
                addPath(path)
                lineTo(x(profile.distances.last()), h)
                lineTo(x(profile.distances.first()), h)
                close()
            }
            drawPath(area, fill)
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))

            // Marker indicator: vertical line + a dot on the profile at the marked distance. Clamp the
            // dot's y so it isn't clipped at the edges on a flat profile (which sits at the bottom).
            marker?.let { m ->
                val mx = x(profile.distances[m.sample])
                val knobR = 6.dp.toPx()
                val my = y(profile.elevations[m.sample]).coerceIn(knobR, h - knobR)
                drawLine(markerColor, Offset(mx, 0f), Offset(mx, h), strokeWidth = 2.dp.toPx())
                drawCircle(markerRing, radius = knobR, center = Offset(mx, my))
                drawCircle(markerColor, radius = 4.dp.toPx(), center = Offset(mx, my))
            }
        }
    }
}
