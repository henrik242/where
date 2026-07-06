package no.synth.where.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import no.synth.where.data.ElevationProfile

/**
 * Draws [profile] as an area-filled elevation line spanning the full [DrawScope] size: distance maps
 * to x, elevation (clamped to a >1 m range so a flat profile still renders) to y. Shared by the
 * read-only altitude chart and the crop chart so the profile looks identical in both.
 */
fun DrawScope.drawElevationProfile(profile: ElevationProfile, line: Color, fill: Color) {
    val w = size.width
    val h = size.height
    val eleRange = (profile.maxEle - profile.minEle).takeIf { it > 1.0 } ?: 1.0
    val distRange = profile.totalDistance.takeIf { it > 0.0 } ?: 1.0
    fun x(dist: Double) = (dist / distRange * w).toFloat()
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
    drawPath(area, fill)
    drawPath(linePath, line, style = Stroke(width = 2.dp.toPx()))
}
