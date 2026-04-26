package no.synth.where.ui.map

import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.hypot

class TwoFingerTapDetector(
    private val density: Float,
    private val onTap: (PointF, PointF) -> Unit
) {
    private sealed interface State {
        data object Idle : State
        data class Tracking(
            val downTime: Long,
            val initial1: PointF, val initial2: PointF,
            val latest1: PointF, val latest2: PointF
        ) : State
        data object JustRecognized : State
        data object Failed : State
    }

    private var state: State = State.Idle

    /**
     * Returns true when this event was the recognized two-finger-tap completion
     * (caller should consume to suppress MLN's two-finger-tap-to-zoom).
     */
    fun onTouch(event: MotionEvent): Boolean {
        val maxMovementPx = TwoFingerTap.MAX_MOVEMENT_DP * density
        val minSeparationPx = TwoFingerTap.MIN_SEPARATION_DP * density

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> state = State.Idle
            MotionEvent.ACTION_POINTER_DOWN -> {
                state = when {
                    event.pointerCount == 2 && state !is State.Failed -> {
                        val p1 = PointF(event.getX(0), event.getY(0))
                        val p2 = PointF(event.getX(1), event.getY(1))
                        State.Tracking(event.eventTime, p1, p2, p1, p2)
                    }
                    event.pointerCount > 2 -> State.Failed
                    else -> state
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val tracking = state as? State.Tracking ?: return false
                if (event.pointerCount < 2) return false
                val p1 = PointF(event.getX(0), event.getY(0))
                val p2 = PointF(event.getX(1), event.getY(1))
                state = if (p1.distanceTo(tracking.initial1) > maxMovementPx ||
                    p2.distanceTo(tracking.initial2) > maxMovementPx
                ) {
                    State.Failed
                } else {
                    tracking.copy(latest1 = p1, latest2 = p2)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val tracking = state as? State.Tracking
                if (tracking != null && event.pointerCount == 2 &&
                    event.eventTime - tracking.downTime <= TwoFingerTap.MAX_DURATION_MS &&
                    tracking.latest1.distanceTo(tracking.latest2) >= minSeparationPx
                ) {
                    state = State.JustRecognized
                    onTap(tracking.latest1, tracking.latest2)
                    return true
                }
                state = State.Failed
            }
            MotionEvent.ACTION_UP -> {
                // Eat the second finger's lift too — MLN's two-finger-tap-zoom
                // detector can fire on this event rather than POINTER_UP.
                val consume = state == State.JustRecognized
                state = State.Idle
                return consume
            }
            MotionEvent.ACTION_CANCEL -> state = State.Idle
            else -> Unit
        }
        return false
    }

    private fun PointF.distanceTo(other: PointF): Float =
        hypot(x - other.x, y - other.y)
}
