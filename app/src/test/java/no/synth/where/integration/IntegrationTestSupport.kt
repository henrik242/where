package no.synth.where.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Shared plumbing for the integration tests: a real HTTP client and stdout logging. */
object IntegrationTestSupport {

    fun makeClient() = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    fun plantLogger() {
        if (Timber.treeCount > 0) return
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("[$tag] $message")
                t?.printStackTrace()
            }
        })
    }
}
