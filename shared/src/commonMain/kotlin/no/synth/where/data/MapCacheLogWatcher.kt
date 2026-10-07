package no.synth.where.data

import no.synth.where.util.CrashReporter
import no.synth.where.util.Logger
import org.maplibre.compose.logging.MapLogger
import org.maplibre.compose.logging.MapLogging
import org.maplibre.compose.util.DelicateMaplibreComposeApi

/**
 * Flags the one maplibre-native log line that signals offline/ambient caching is failing (storage
 * full) - the direct signal the old native MapLibreLogWatcher watched for - while still forwarding
 * every record to the platform log. Install once at startup.
 */
object MapCacheLogWatcher {
    private var reported = false

    @OptIn(DelicateMaplibreComposeApi::class)
    fun install() {
        val platform = MapLogging.platformLogger
        MapLogging.logger = MapLogger { record ->
            if (!reported && MapCacheConfig.isCacheFailureLog(record.message)) {
                reported = true
                Logger.w("MapLibre cannot cache tiles (offline storage may be full): %s", record.message)
                // Record once per session so a recurrence is detectable in crash reporting (Logger.w
                // alone goes nowhere in release builds).
                CrashReporter.recordException("MapLibre cache write failed: ${record.message}")
            }
            platform.log(record)
        }
    }
}
