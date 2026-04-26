package no.synth.where.ui

import androidx.compose.runtime.Composable
import no.synth.where.resources.Res
import no.synth.where.resources.duration_hours_minutes
import no.synth.where.resources.duration_hours_only
import no.synth.where.resources.duration_minutes
import no.synth.where.resources.duration_seconds
import no.synth.where.util.RemainingTime
import no.synth.where.util.remainingTimeOf
import org.jetbrains.compose.resources.stringResource

/**
 * Localized rendering of a positive remaining-time value. Returns an empty
 * string for [RemainingTime.Zero]; callers should hide their UI when the
 * timer has elapsed instead of relying on the format.
 */
@Composable
fun formatRemaining(millis: Long): String =
    when (val r = remainingTimeOf(millis)) {
        RemainingTime.Zero -> ""
        is RemainingTime.HoursOnly -> stringResource(Res.string.duration_hours_only, r.hours)
        is RemainingTime.HoursAndMinutes ->
            stringResource(Res.string.duration_hours_minutes, r.hours, r.minutes)
        is RemainingTime.MinutesOnly -> stringResource(Res.string.duration_minutes, r.minutes)
        is RemainingTime.SecondsOnly -> stringResource(Res.string.duration_seconds, r.seconds)
    }
