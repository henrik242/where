package no.synth.where.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.synth.where.data.geo.compassPoint8
import no.synth.where.data.geo.wholeDegrees
import no.synth.where.resources.Res
import no.synth.where.resources.compass_e
import no.synth.where.resources.compass_locked_north
import no.synth.where.resources.compass_n
import no.synth.where.resources.compass_reset_north
import no.synth.where.resources.compass_s
import no.synth.where.resources.compass_w
import no.synth.where.resources.ic_lock
import no.synth.where.resources.lock_map_to_north
import no.synth.where.resources.show_large_compass
import no.synth.where.resources.show_small_compass
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Diameter of the compass rose; the map's top-right corner reserves this much width. */
internal val COMPASS_SIZE = 44.dp

/** Diameter of the large compass dial with ticks, cardinal letters and heading readout. */
internal val LARGE_COMPASS_SIZE = 104.dp

internal fun compassSize(large: Boolean): Dp = if (large) LARGE_COMPASS_SIZE else COMPASS_SIZE

/**
 * Compass rose pinned to the map's top-right corner, drawn in Compose rather than using the
 * platform map's native ornament so the tap behaviour and locked-state styling are shared. The
 * needle points at true north, so it rotates against [bearing] (the camera's heading, degrees
 * clockwise from north). [large] swaps the needle for a bigger dial showing the heading; a long
 * press toggles it.
 */
@Composable
fun MapCompass(
    bearing: Double,
    northLocked: Boolean,
    following: Boolean,
    onResetNorth: () -> Unit,
    onToggleNorthLock: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onToggleLarge: () -> Unit = {},
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
    val longClickLabel = stringResource(
        if (large) Res.string.show_small_compass else Res.string.show_large_compass
    )
    val haptics = LocalHapticFeedback.current
    val n = stringResource(Res.string.compass_n)
    val e = stringResource(Res.string.compass_e)
    val s = stringResource(Res.string.compass_s)
    val w = stringResource(Res.string.compass_w)
    val headingText = "${wholeDegrees(bearing)}°"
    val pointText = compassPoint8(bearing, n, e, s, w)
    val needleNorth = MaterialTheme.colorScheme.error
    val needleSouth = MaterialTheme.colorScheme.onSurfaceVariant
    val size = compassSize(large)

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
                .size(size)
                .combinedClickable(
                    onLongClickLabel = longClickLabel,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleLarge()
                    },
                    onClick = {
                        when (action) {
                            CompassTapAction.RESET_NORTH -> onResetNorth()
                            CompassTapAction.TOGGLE_LOCK -> onToggleNorthLock()
                        }
                    },
                )
                .semantics {
                    contentDescription = description
                    // The large dial's readout is drawn on a canvas, so expose it to screen readers.
                    if (large) stateDescription = "$headingText $pointText"
                },
        ) {
            if (large) {
                LargeCompassDial(
                    bearing, listOf(n, e, s, w), headingText, pointText, needleNorth, needleSouth,
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .size(size)
                        .rotate(-bearing.toFloat())
                ) {
                    drawCompassNeedle(needleNorth, needleSouth)
                }
            }
        }

        if (northLocked) {
            // Filled badge so the lock stands out against both the dial and the map.
            // A Box, not a Surface, so taps on the badge still reach the compass.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(if (large) 26.dp else 20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(if (large) 5.dp else 3.dp),
                )
            }
        }
    }
}

/**
 * Dial that turns against [bearing] like a compass card: 5-degree ticks, longer every 30 and 90,
 * and the localized cardinal letters. A fixed lubber mark at the top and the degree readout in
 * the middle give the heading the top of the screen faces. [cardinals] are N, E, S, W in order.
 */
@Composable
private fun LargeCompassDial(
    bearing: Double,
    cardinals: List<String>,
    headingText: String,
    pointText: String,
    north: Color,
    ink: Color,
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface

    // Text is sized from the dial, not the font scale, so it always fits inside the ring.
    Canvas(modifier = Modifier.size(LARGE_COMPASS_SIZE)) {
        val c = size.minDimension / 2f
        val center = Offset(c, c)
        rotate(-bearing.toFloat(), pivot = center) {
            drawTicks(c, ink)
            val letterStyle = TextStyle(fontSize = (c * 0.23f).toSp(), fontWeight = FontWeight.Bold)
            cardinals.forEachIndexed { i, letter ->
                val angle = i * 90f
                rotate(angle, pivot = center) {
                    drawCentered(
                        textMeasurer, letter,
                        letterStyle.copy(color = if (angle == 0f) north else onSurface),
                        Offset(c, c * 0.42f),
                    )
                }
            }
        }
        // Lubber mark: the heading the top of the screen faces, pointing into the dial.
        val lubber = Path().apply {
            moveTo(c, c * 0.2f)
            lineTo(c - c * 0.07f, c * 0.04f)
            lineTo(c + c * 0.07f, c * 0.04f)
            close()
        }
        drawPath(lubber, onSurface)
        drawCentered(
            textMeasurer, headingText,
            TextStyle(fontSize = (c * 0.29f).toSp(), fontWeight = FontWeight.Bold, color = onSurface),
            Offset(c, c * 0.92f),
        )
        drawCentered(
            textMeasurer, pointText,
            TextStyle(fontSize = (c * 0.19f).toSp(), color = ink),
            Offset(c, c * 1.22f),
        )
    }
}

/** Ticks inside the rim, north tick at the top of the (unrotated) dial. */
private fun DrawScope.drawTicks(c: Float, color: Color) {
    val outer = c * 0.94f
    for (deg in 0 until 360 step 5) {
        val (inner, width, alpha) = when {
            deg % 90 == 0 -> Triple(c * 0.76f, c * 0.045f, 1f)
            deg % 30 == 0 -> Triple(c * 0.80f, c * 0.035f, 0.85f)
            else -> Triple(c * 0.87f, c * 0.02f, 0.6f)
        }
        rotate(deg.toFloat(), pivot = Offset(c, c)) {
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(c, c - outer),
                end = Offset(c, c - inner),
                strokeWidth = width,
            )
        }
    }
}

/** Draws [text] centered on [at]. */
private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f),
    )
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
