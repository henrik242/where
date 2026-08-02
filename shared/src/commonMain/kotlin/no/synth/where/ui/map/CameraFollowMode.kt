package no.synth.where.ui.map

/**
 * How the camera tracks the user location, cycled by tapping the my-location FAB.
 *
 * - [OFF]: free pan, camera does not follow.
 * - [FOLLOW]: camera stays centered on the puck, north up.
 * - [FOLLOW_HEADING]: camera stays centered and rotates to the device compass heading,
 *   so the direction you are facing points up.
 *
 * Panning or rotating the map by hand drops back to [OFF] (handled per platform).
 */
enum class CameraFollowMode {
    OFF,
    FOLLOW,
    FOLLOW_HEADING;

    /**
     * Next mode in the my-location FAB cycle. [FOLLOW_HEADING] is skipped while the map is locked
     * to north, since it would rotate the camera the lock is there to prevent.
     */
    fun next(northLocked: Boolean = false): CameraFollowMode = when (this) {
        OFF -> FOLLOW
        FOLLOW -> if (northLocked) OFF else FOLLOW_HEADING
        FOLLOW_HEADING -> OFF
    }

    /** The mode to fall back to when north-lock is switched on mid-follow. */
    fun withoutHeading(): CameraFollowMode = if (this == FOLLOW_HEADING) FOLLOW else this
}
