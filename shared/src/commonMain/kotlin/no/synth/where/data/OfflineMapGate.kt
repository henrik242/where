package no.synth.where.data

import kotlin.concurrent.Volatile
import org.maplibre.compose.map.DefaultMapRuntime

/**
 * "Offline mode": while [enabled], MapLibre Native serves only already-cached data (ambient browsing
 * cache + downloaded offline packs) and never touches the network, so a user on cellular can browse
 * cached areas without spending data.
 *
 * Backed by maplibre-compose's process-wide connectivity override (ForceOffline), via [setMapOffline].
 * This replaces the old resource-provider hack, which could not serve ambient tiles because the engine
 * requested them as NetworkOnly (bypassing its disk cache) while it believed it was online.
 *
 * [configure] must run once at startup, before the first map or offline manager is created.
 */
object OfflineMapGate {
    @Volatile
    var enabled: Boolean = false
        set(value) {
            field = value
            setMapOffline(value)
        }

    private var configured = false

    fun configure() {
        if (configured) return
        configured = true
        runCatching {
            // Build the runtime now, on the startup (main) thread, so its MainThreadGuard posts its
            // pin to the main dispatcher before the first map composes. Otherwise the guard is built
            // lazily during rememberMapState inside Scaffold's measure-pass subcomposition, where its
            // setBaseStyle side-effect can run in the same frame before the pin drains, crashing with
            // "Map state was used before its main dispatcher ran."
            DefaultMapRuntime.instance
            setMapOffline(enabled)
        }
    }
}

/**
 * Force MapLibre Native offline (cache-only) or back to following OS connectivity. The connectivity
 * API lives in the library's native source set, so it is reached per-platform.
 */
internal expect fun setMapOffline(offline: Boolean)
