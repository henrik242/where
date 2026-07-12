package no.synth.where.location

import com.google.android.gms.location.Priority
import org.maplibre.android.location.engine.LocationEngineRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class FusedMapLocationEngineTest {

    @Test
    fun mapsMapLibrePrioritiesToFusedPriorities() {
        assertEquals(
            Priority.PRIORITY_HIGH_ACCURACY,
            fusedPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY),
        )
        assertEquals(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            fusedPriority(LocationEngineRequest.PRIORITY_BALANCED_POWER_ACCURACY),
        )
        assertEquals(
            Priority.PRIORITY_LOW_POWER,
            fusedPriority(LocationEngineRequest.PRIORITY_LOW_POWER),
        )
        assertEquals(
            Priority.PRIORITY_PASSIVE,
            fusedPriority(LocationEngineRequest.PRIORITY_NO_POWER),
        )
    }

    @Test
    fun unknownPriorityFallsBackToPassive() {
        assertEquals(Priority.PRIORITY_PASSIVE, fusedPriority(42))
    }
}
