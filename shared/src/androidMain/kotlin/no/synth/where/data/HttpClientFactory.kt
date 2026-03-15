package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.synth.where.BuildInfo

actual fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, "Where/${BuildInfo.VERSION_INFO} (Android)")
    }
}
