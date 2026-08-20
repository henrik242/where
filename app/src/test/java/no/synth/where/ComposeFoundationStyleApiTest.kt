package no.synth.where

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * androidx material3 1.5.0-alpha17, which compose-multiplatform pulls in, styles text fields with
 * the experimental androidx.compose.foundation.style API, and compose 1.12 reshaped that API.
 * Bumping composeBom past the 1.11 train therefore crashes every OutlinedTextField in
 * ResolvedStyle.build. These checks catch that at build time instead. All lookups go through
 * reflection so the test compiles regardless of which shape foundation ships.
 */
class ComposeFoundationStyleApiTest {

    @Test
    fun styleAcceptsTheScopeThatMaterial3Passes() {
        val style = styleClass("Style")
        val scope = styleClass("StyleScope")
        val params = style.methods.filter { it.name == "applyStyle" }.map { it.parameterTypes.toList() }
        assertTrue(
            "$OUT_OF_SYNC Style.applyStyle takes $params, expected [$scope].",
            params.contains(listOf(scope)),
        )
    }

    @Test
    fun foundationKeepsTheStyleFunctionsThatMaterial3Calls() {
        val style = styleClass("Style")
        val scope = styleClass("StyleScope")
        assertTrue(
            "$OUT_OF_SYNC StyleStateKt.focused(StyleScope, Style) is gone.",
            styleClass("StyleStateKt").hasMethod("focused", scope, style),
        )
        assertTrue(
            "$OUT_OF_SYNC StyleScope.animate(AnimationSpec, Style) is gone.",
            scope.hasMethod("animate", Class.forName("androidx.compose.animation.core.AnimationSpec"), style),
        )
    }

    private fun styleClass(name: String): Class<*> =
        Class.forName("androidx.compose.foundation.style.$name")

    private fun Class<*>.hasMethod(name: String, vararg params: Class<*>) =
        runCatching { getDeclaredMethod(name, *params) }.isSuccess

    private companion object {
        const val OUT_OF_SYNC =
            "compose and material3 are out of sync: either roll composeBom back to the 1.11 train, " +
                "or move composeMaterial3 to a version built against the newer foundation."
    }
}
