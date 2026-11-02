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

    @Test
    fun steepOffPathDescentCostsExtraButClimbAndPathDoNot() {
        fun step(drop: Float, path: Boolean): Double {
            val g = TerrainGrid(2, 1, 13, 0, 0, 10.0, floatArrayOf(drop, 0f))
            if (path) {
                g.addFlag(0, CellFlags.PATH)
                g.addFlag(1, CellFlags.PATH)
            }
            return HikingCost.stepSeconds(g, 0, 1, 10.0) ?: error("step blocked")
        }
        fun tobler(gradient: Double) = 10.0 / (6.0 * exp(-3.5 * abs(gradient + 0.05)) / 3.6)
        val offTrail = 1.0 / 0.6
        // 20 degrees down: Tobler only.
        assertTrue(abs(step(3.6f, path = false) - tobler(-0.36) * offTrail) < 1e-3)
        // 35 degrees down: more than Tobler, but not on a path.
        assertTrue(step(7.0f, path = false) > tobler(-0.7) * offTrail * 1.5)
        assertTrue(abs(step(7.0f, path = true) - tobler(-0.7)) < 1e-3)
        // The same slope uphill isn't penalised.
        assertTrue(abs(step(-7.0f, path = false) - tobler(0.7) * offTrail) < 1e-3)
    }
}
