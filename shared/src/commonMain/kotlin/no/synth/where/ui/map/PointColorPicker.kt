package no.synth.where.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import no.synth.where.resources.Res
import no.synth.where.resources.ic_check
import no.synth.where.util.parseHexColor
import org.jetbrains.compose.resources.painterResource

/**
 * Colour swatches for a saved point. Wraps so the whole palette stays reachable on narrow dialogs,
 * and marks the selected one with a check rather than colour alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PointColorPicker(
    colors: List<Pair<String, String>>,
    selectedColor: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { (colorHex, colorName) ->
            val selected = selectedColor.equals(colorHex, ignoreCase = true)
            val swatch = parseHexColor(colorHex)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onColorChange(colorHex) }
                    )
                    .semantics { contentDescription = colorName },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(40.dp).background(swatch, CircleShape))
                if (selected) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
