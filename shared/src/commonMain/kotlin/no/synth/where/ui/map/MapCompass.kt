package no.synth.where.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import no.synth.where.resources.Res
import no.synth.where.resources.compass_locked_north
import no.synth.where.resources.compass_reset_north
import no.synth.where.resources.ic_lock
import no.synth.where.resources.lock_map_to_north
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Diameter of the compass rose; the map's top-right corner reserves this much width. */
internal val COMPASS_SIZE = 44.dp

/**
 * Compass rose pinned to the map's top-right corner, drawn in Compose rather than using the
 * platform map's native ornament so the tap behaviour and locked-state styling are shared. The
 * needle points at true north, so it rotates against [bearing] (the camera's heading, degrees
 * clockwise from north).
 */
@Composable
fun MapCompass(
    bearing: Double,
    northLocked: Boolean,
    following: Boolean,
    onResetNorth: () -> Unit,
    onToggleNorthLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = compassTapAction(bearing, northLocked, following)
    // Names what the next tap does, so the lock is reachable and legible without sight.
    val description = stringResource(
        when {
            northLocked -> Res.string.compass_locked_north
            action == CompassTapAction.TOGGLE_LOCK -> Res.string.lock_map_to_north
            else -> Res.string.compass_reset_north
        }
    )
    val needleNorth = MaterialTheme.colorScheme.error
    val needleSouth = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = if (northLocked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            },
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(COMPASS_SIZE)
                .clickable {
                    when (action) {
                        CompassTapAction.RESET_NORTH -> onResetNorth()
                        CompassTapAction.TOGGLE_LOCK -> onToggleNorthLock()
                    }
                }
                .semantics { contentDescription = description },
        ) {
            Canvas(
                modifier = Modifier
                    .size(COMPASS_SIZE)
                    .rotate(-bearing.toFloat())
            ) {
                drawCompassNeedle(needleNorth, needleSouth)
            }
        }

        if (northLocked) {
            Icon(
                painterResource(Res.drawable.ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp),
            )
        }
    }
}

/** Two-tone kite needle inside a hairline ring, north tip at the top of the (unrotated) canvas. */
private fun DrawScope.drawCompassNeedle(north: Color, south: Color) {
    val c = size.minDimension / 2f
    val tip = c * 0.62f
    val waist = c * 0.22f
    drawCircle(
        color = south.copy(alpha = 0.35f),
        radius = c * 0.86f,
        style = Stroke(width = c * 0.05f),
    )
    val northHalf = Path().apply {
        moveTo(c, c - tip)
        lineTo(c + waist, c)
        lineTo(c - waist, c)
        close()
    }
    val southHalf = Path().apply {
        moveTo(c, c + tip)
        lineTo(c + waist, c)
        lineTo(c - waist, c)
        close()
    }
    drawPath(northHalf, north)
    drawPath(southHalf, south)
}
