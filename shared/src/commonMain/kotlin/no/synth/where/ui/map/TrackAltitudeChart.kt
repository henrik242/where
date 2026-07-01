package no.synth.where.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import no.synth.where.data.Track
import no.synth.where.data.elevationProfileOrNull
import no.synth.where.util.formatDistance
import kotlin.math.roundToInt

@Composable
fun TrackAltitudeChart(track: Track, modifier: Modifier = Modifier) {
    val profile = remember(track.id, track.points.size) { track.elevationProfileOrNull() } ?: return

    val line = MaterialTheme.colorScheme.primary
    val fill = line.copy(alpha = 0.18f)
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("↑ ${profile.gain.roundToInt()} m", style = MaterialTheme.typography.labelSmall, color = label)
            Text(profile.totalDistance.formatDistance(), style = MaterialTheme.typography.labelSmall, color = label)
            Text(
                "${profile.minEle.roundToInt()}–${profile.maxEle.roundToInt()} m",
                style = MaterialTheme.typography.labelSmall, color = label,
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(88.dp)) {
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
        }
    }
}
