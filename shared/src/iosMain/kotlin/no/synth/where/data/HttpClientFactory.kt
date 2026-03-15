package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.synth.where.BuildInfo

actual fun createDefaultHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureRequest {
            setAllowsCellularAccess(true)
            setTimeoutInterval(30.0)
        }
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, "Where/${BuildInfo.VERSION_INFO} (iOS)")
    }
}
