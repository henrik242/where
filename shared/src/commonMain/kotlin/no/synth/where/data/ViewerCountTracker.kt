package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.util.HmacUtils
import no.synth.where.util.Logger

class ViewerCountTracker(
    private val serverUrl: String,
    private val clientId: String,
    private val trackingHint: String,
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val onViewerCountChanged: (Int) -> Unit
) {
    private var pollingJob: Job? = null

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                fetchViewerCount()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        onViewerCountChanged(0)
    }

    private suspend fun fetchViewerCount() {
        try {
            val signature = HmacUtils.generateSignature(clientId, trackingHint)
            val response = client.get("$serverUrl/api/tracks/viewers/$clientId") {
                header("X-Client-Id", clientId)
                header("X-Signature", signature)
            }
            if (response.status.value in 200..299) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                onViewerCountChanged(json["viewers"]?.jsonPrimitive?.int ?: 0)
            }
        } catch (e: Exception) {
            Logger.d("Failed to fetch viewer count: %s", e.message ?: "unknown")
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
    }
}
