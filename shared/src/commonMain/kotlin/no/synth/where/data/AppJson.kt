package no.synth.where.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * App-wide Json. Error messages leave out the JSON input: tracks and positions must not end up
 * in crash reports or logs.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val AppJson = Json {
    ignoreUnknownKeys = true
    exceptionsWithDebugInfo = false
}

/** [AppJson] that also accepts lenient input from third-party APIs. */
internal val LenientJson = Json(AppJson) { isLenient = true }
