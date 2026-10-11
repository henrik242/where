package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.synth.where.BuildInfo
import java.util.concurrent.TimeUnit

actual fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout)
    engine {
        config {
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
        }
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, "Where/${BuildInfo.VERSION_INFO} (Desktop)")
    }
}
