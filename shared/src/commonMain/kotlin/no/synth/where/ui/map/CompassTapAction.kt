package no.synth.where.ui.map

/** What a tap on the compass rose does next. */
enum class CompassTapAction {
    /** Turn a rotated map back to north-up. */
    RESET_NORTH,

    /** Lock the map to north, or release an existing lock. */
    TOGGLE_LOCK,
}

/** Below this many degrees off north the map counts as already pointing north. */
private const val NORTH_TOLERANCE_DEGREES = 0.5

/**
 * Two consecutive taps lock the map: the first straightens a rotated map, the second (with nothing
 * left to straighten) engages the lock. While locked, a single tap releases it. Keyed off the
 * camera [bearing] rather than a tap counter, so there is no timing window to hit and the
 * [MapCompass] label can always say what the next tap will do.
 *
 * A [following] camera owns the bearing, so there a tap locks straight away: north-up tracking
 * straightens and locks in one step, while an external bearing animation would cancel the follow
 * mode. Only heading mode leaves the map both rotated and following, since rotating by hand drops
 * follow anyway.
 */
fun compassTapAction(
    bearing: Double,
    northLocked: Boolean,
    following: Boolean,
): CompassTapAction = when {
    northLocked -> CompassTapAction.TOGGLE_LOCK
    following -> CompassTapAction.TOGGLE_LOCK
    pointsNorth(bearing) -> CompassTapAction.TOGGLE_LOCK
    else -> CompassTapAction.RESET_NORTH
}

private fun pointsNorth(bearing: Double): Boolean {
    val normalized = ((bearing % 360.0) + 360.0) % 360.0
    return normalized < NORTH_TOLERANCE_DEGREES || normalized > 360.0 - NORTH_TOLERANCE_DEGREES
}
