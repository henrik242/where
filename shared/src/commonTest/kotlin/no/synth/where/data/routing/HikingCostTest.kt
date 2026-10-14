package no.synth.where.data.routing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertTrue

class HikingCostTest {
    /** Two path cells 10 m apart, so only Tobler's function sets the time. */
    private fun stepSeconds(rise: Float): Double {
        val g = TerrainGrid(2, 1, 13, 0, 0, 10.0, floatArrayOf(0f, rise))
        g.addFlag(0, CellFlags.PATH)
        g.addFlag(1, CellFlags.PATH)
        return HikingCost.stepSeconds(g, 0, 1, 10.0) ?: error("path step blocked")
    }

    @Test
    fun lookupTableMatchesToblersFunction() {
        val maxGradient = tan(40.0 * PI / 180.0)
        // Both table edges, the kink at -0.05, and steeper path steps computed exactly.
        val gradients = (-120..120).map { it / 100.0 } + listOf(-0.05, maxGradient, -maxGradient - 0.1, maxGradient - 1e-9)
        for (gradient in gradients) {
            val rise = (gradient * 10).toFloat()
            val g = rise / 10.0
            val exact = 10.0 / (6.0 * exp(-3.5 * abs(g + 0.05)) / 3.6)
            val actual = stepSeconds(rise)
            assertTrue(abs(actual - exact) <= exact * 1e-6, "gradient=$g exact=$exact actual=$actual")
        }
    }
}
