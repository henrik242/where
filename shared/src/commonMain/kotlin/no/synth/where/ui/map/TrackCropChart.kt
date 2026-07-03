package no.synth.where.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import no.synth.where.data.Track
import no.synth.where.data.cumulativeDistances
import no.synth.where.data.elevationProfileOrNull
import no.synth.where.resources.Res
import no.synth.where.resources.crop_kept_distance
import no.synth.where.util.formatDistance
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private enum class Handle { Start, End }

/**
 * Interactive crop panel pinned bottom-center: the track's elevation profile (or a flat baseline
 * when it has no altitude data) with two draggable handles. The trimmed head/tail are scrimmed so
 * the kept span stands out, mirroring the grey/colored split drawn on the map. Dragging a handle
 * reports the nearest point index via [onCropChange]; the caller (updateCrop) clamps to keep >= 2
 * points. Reuses the same downsampled [elevationProfileOrNull] the read-only chart draws so the
 * profile looks identical in both modes.
 */
@Composable
fun TrackCropChart(
    track: Track,
    startIndex: Int,
    endIndex: Int,
    onCropChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cum = remember(track.id, track.points) { track.cumulativeDistances() }
    if (cum.size < 2) return
    val total = cum.last().takeIf { it > 0.0 } ?: 1.0
    val profile = remember(track.id, track.points) { track.elevationProfileOrNull() }

    val s = startIndex.coerceIn(0, cum.lastIndex)
    val e = endIndex.coerceIn(0, cum.lastIndex)
    val keptText = stringResource(
        Res.string.crop_kept_distance, (cum[e] - cum[s]).coerceAtLeast(0.0).formatDistance(),
    )

    // pointerInput(track.id) captures cum/total once per track; wrap the handle indices and callback
    // so the long-lived drag lambda always reads the latest values rather than stale captures.
    val startState = rememberUpdatedState(s)
    val endState = rememberUpdatedState(e)
    val onChange = rememberUpdatedState(onCropChange)

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.18f)
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
    val knobRing = MaterialTheme.colorScheme.surface
    val label = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = keptText },
    ) {
        Text(keptText, style = MaterialTheme.typography.labelSmall, color = label)
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .pointerInput(track.id) {
                    var active = Handle.Start
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat()
                            val xs = (cum[startState.value] / total * w).toFloat()
                            val xe = (cum[endState.value] / total * w).toFloat()
                            active = if (abs(offset.x - xs) <= abs(offset.x - xe)) Handle.Start else Handle.End
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat()
                            val dist = (change.position.x / w).coerceIn(0f, 1f) * total
                            val idx = nearestIndex(cum, dist)
                            // Keep the active handle from crossing the other; updateCrop clamps to bounds.
                            if (active == Handle.Start) {
                                onChange.value(idx.coerceAtMost(endState.value - 1), endState.value)
                            } else {
                                onChange.value(startState.value, idx.coerceAtLeast(startState.value + 1))
                            }
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            fun xAt(index: Int) = (cum[index] / total * w).toFloat()

            // Profile line + fill, scaled exactly like TrackAltitudeChart; flat baseline when no elevation.
            if (profile != null) {
                val eleRange = (profile.maxEle - profile.minEle).takeIf { it > 1.0 } ?: 1.0
                fun x(dist: Double) = (dist / total * w).toFloat()
                fun y(el: Double) = (h - (el - profile.minEle) / eleRange * h).toFloat()
                val linePath = Path().apply {
                    moveTo(x(profile.distances.first()), y(profile.elevations.first()))
                    for (k in 1 until profile.elevations.size) lineTo(x(profile.distances[k]), y(profile.elevations[k]))
                }
                val area = Path().apply {
                    addPath(linePath)
                    lineTo(x(profile.distances.last()), h)
                    lineTo(x(profile.distances.first()), h)
                    close()
                }
                drawPath(area, fillColor)
                drawPath(linePath, lineColor, style = Stroke(width = 2.dp.toPx()))
            } else {
                val y = h * 0.7f
                drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 2.dp.toPx())
            }

            // Scrim the trimmed head/tail.
            val xs = xAt(startState.value)
            val xe = xAt(endState.value)
            if (xs > 0f) drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(xs, h))
            if (xe < w) drawRect(scrim, topLeft = Offset(xe, 0f), size = Size(w - xe, h))

            // Handle bars with a ringed knob so they stay distinct from the same-colored profile line.
            val handleW = 4.dp.toPx()
            val knobR = 10.dp.toPx()
            for (hx in listOf(xs, xe)) {
                drawRect(lineColor, topLeft = Offset(hx - handleW / 2, 0f), size = Size(handleW, h))
                drawCircle(knobRing, radius = knobR + 2.dp.toPx(), center = Offset(hx, h / 2))
                drawCircle(lineColor, radius = knobR, center = Offset(hx, h / 2))
            }
        }
    }
}

/** Index of the point whose cumulative distance is closest to [dist]; [cum] is ascending. */
private fun nearestIndex(cum: List<Double>, dist: Double): Int {
    var lo = 0
    var hi = cum.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (cum[mid] < dist) lo = mid + 1 else hi = mid
    }
    // lo is the first index with cum[lo] >= dist; compare with the previous one.
    if (lo > 0 && abs(cum[lo - 1] - dist) <= abs(cum[lo] - dist)) return lo - 1
    return lo
}
