package no.synth.where.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import no.synth.where.resources.Res
import no.synth.where.resources.ic_visibility
import no.synth.where.resources.live_chip_label
import no.synth.where.resources.viewers_watching
import no.synth.where.resources.viewers_watching_plural
import no.synth.where.ui.formatRemaining
import no.synth.where.util.currentTimeMillis
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Top-right indicator for live broadcasting state. Tappable; tap navigates
 * to the Direktesporing settings screen so the user can toggle / extend /
 * stop sharing from a context where the consequences are obvious.
 *
 * - [enabled] = false  → muted, static dot ("you could be sharing").
 * - [enabled] = true   → pulsing dot + "Direkte".
 * - [untilMillis] != null and in the future → countdown is appended.
 *   Re-ticks every 30 s above a minute, then by the second.
 */
@Composable
internal fun LiveSharingChip(
    enabled: Boolean,
    untilMillis: Long?,
    viewerCount: Int,
    onClick: () -> Unit,
) {
    val countdownActive = enabled && untilMillis != null && untilMillis > 0L
    var nowMillis by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(countdownActive, untilMillis) {
        if (!countdownActive || untilMillis == null) return@LaunchedEffect
        while (untilMillis > nowMillis) {
            val remaining = untilMillis - nowMillis
            delay(if (remaining < 60_000L) 1_000L else 30_000L)
            nowMillis = currentTimeMillis()
        }
    }
    val remainingMillis =
        if (countdownActive && untilMillis != null) (untilMillis - nowMillis).coerceAtLeast(0L)
        else 0L

    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (enabled) Modifier else Modifier.drawWithContent {
                    drawContent()
                    drawLine(
                        color = contentColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChipDot(enabled = enabled, color = contentColor)
        Text(
            text = stringResource(Res.string.live_chip_label),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
        if (remainingMillis > 0L) {
            Text(
                text = formatRemaining(remainingMillis),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
        if (enabled && viewerCount > 0) {
            ChipViewerIndicator(viewerCount = viewerCount, tint = contentColor)
        }
    }
}

@Composable
private fun ChipDot(enabled: Boolean, color: Color) {
    val alpha = if (enabled) {
        val transition = rememberInfiniteTransition(label = "liveDot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "liveDotAlpha",
        ).value
    } else 1f
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun ChipViewerIndicator(viewerCount: Int, tint: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "eyeBlink")
    val scaleY by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f at 0 using LinearEasing
                1f at 3600 using LinearEasing
                0.1f at 3700 using LinearEasing
                1f at 3800 using LinearEasing
                1f at 4000 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "eyeScaleY",
    )
    val description = if (viewerCount == 1) {
        stringResource(Res.string.viewers_watching)
    } else {
        stringResource(Res.string.viewers_watching_plural, viewerCount)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painterResource(Res.drawable.ic_visibility),
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer { this.scaleY = scaleY },
        )
        if (viewerCount > 1) {
            Text(
                text = viewerCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}
