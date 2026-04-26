package no.synth.where.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import no.synth.where.resources.Res
import no.synth.where.resources.live_chip_label
import no.synth.where.ui.formatRemaining
import no.synth.where.util.currentTimeMillis
import org.jetbrains.compose.resources.stringResource

/**
 * Top-of-map indicator visible while the user is broadcasting their live
 * location without recording. Re-ticks every 30 s while the remaining time
 * is over a minute, then by the second so the final countdown is accurate.
 */
@Composable
internal fun LiveSharingChip(
    untilMillis: Long,
    onClick: () -> Unit,
) {
    var nowMillis by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(untilMillis) {
        while (untilMillis > nowMillis) {
            val remaining = untilMillis - nowMillis
            delay(if (remaining < 60_000L) 1_000L else 30_000L)
            nowMillis = currentTimeMillis()
        }
    }
    val remainingMillis = (untilMillis - nowMillis).coerceAtLeast(0L)
    if (remainingMillis <= 0L) return
    val remainingText = formatRemaining(remainingMillis)

    val infiniteTransition = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveDotAlpha",
    )

    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = dotAlpha),
                    CircleShape,
                ),
        )
        Text(
            text = stringResource(Res.string.live_chip_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = remainingText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
