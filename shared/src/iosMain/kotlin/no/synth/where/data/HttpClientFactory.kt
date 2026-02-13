package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createDefaultHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureRequest {
            setAllowsCellularAccess(true)
        }
    }
}
