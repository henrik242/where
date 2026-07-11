package no.synth.where

import android.util.Log
import no.synth.where.data.MapCacheConfig
import no.synth.where.util.CrashReporter
import org.maplibre.android.log.LoggerDefinition
import timber.log.Timber

/**
 * Forwards MapLibre's native logs to Logcat (its default behavior) and additionally flags the
 * "Unable to make space for entry" message MapLibre emits when it cannot cache a tile — the direct
 * signal that offline/ambient caching is failing (typically because storage is full). Reported to
 * Timber + crash reporting once per session so a recurrence is detectable without guessing.
 *
 * MapLibre logs that message at Info level, so [watch] must run from i()/d() as well as w()/e().
 */
object MapLibreLogWatcher : LoggerDefinition {
    @Volatile private var cacheFailureReported = false

    private fun watch(msg: String?) {
        if (cacheFailureReported || msg == null || !MapCacheConfig.isCacheFailureLog(msg)) return
        cacheFailureReported = true
        Timber.w("MapLibre cannot cache tiles (offline storage may be full): %s", msg)
        CrashReporter.recordException("MapLibre cache write failed: $msg")
    }

    override fun v(tag: String, msg: String) { Log.v(tag, msg) }
    override fun v(tag: String, msg: String, tr: Throwable) { Log.v(tag, msg, tr) }
    override fun d(tag: String, msg: String) { watch(msg); Log.d(tag, msg) }
    override fun d(tag: String, msg: String, tr: Throwable) { watch(msg); Log.d(tag, msg, tr) }
    override fun i(tag: String, msg: String) { watch(msg); Log.i(tag, msg) }
    override fun i(tag: String, msg: String, tr: Throwable) { watch(msg); Log.i(tag, msg, tr) }
    override fun w(tag: String, msg: String) { watch(msg); Log.w(tag, msg) }
    override fun w(tag: String, msg: String, tr: Throwable) { watch(msg); Log.w(tag, msg, tr) }
    override fun e(tag: String, msg: String) { watch(msg); Log.e(tag, msg) }
    override fun e(tag: String, msg: String, tr: Throwable) { watch(msg); Log.e(tag, msg, tr) }
}
