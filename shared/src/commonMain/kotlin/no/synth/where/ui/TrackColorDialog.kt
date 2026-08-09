package no.synth.where.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.ui.map.TrackColors
import no.synth.where.util.parseHexColor
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Per-track color picker: tap a palette swatch to apply it, expand "Custom" for an HSV picker, or
 * "Random" for a random palette color. All choices apply immediately and close.
 */
@Composable
fun TrackColorDialog(
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(210f) }
    var saturation by remember { mutableFloatStateOf(0.7f) }
    var brightness by remember { mutableFloatStateOf(0.8f) }
    val customColor = Color.hsv(hue, saturation, brightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackColors.palette.chunked(4).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { hex ->
                            val selected = hex.equals(current, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(parseHexColor(hex), CircleShape)
                                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .clickable { onPick(hex) }
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { showCustom = !showCustom },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(stringResource(Res.string.track_color_custom))
                }

                if (showCustom) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(customColor, RoundedCornerShape(6.dp))
                    )
                    ColorSlider(Res.string.color_hue, hue, 0f..360f) { hue = it }
                    ColorSlider(Res.string.color_saturation, saturation, 0f..1f) { saturation = it }
                    ColorSlider(Res.string.color_brightness, brightness, 0f..1f) { brightness = it }
                    Button(
                        onClick = { onPick(customColor.toHex()) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(Res.string.track_color_apply)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(TrackColors.palette.random()) }) {
                Text(stringResource(Res.string.track_color_random))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun ColorSlider(
    label: org.jetbrains.compose.resources.StringResource,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(84.dp)
        )
        Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

private fun Color.toHex(): String {
    fun channel(v: Float) = (v * 255).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}
