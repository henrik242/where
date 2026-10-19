package no.synth.where.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Shared plumbing for the integration tests: a real HTTP client and stdout logging. */
object IntegrationTestSupport {

    /** Every log message since the last [clearLog], so a test can tell why a lookup fell short. */
    val logLines: MutableList<String> = CopyOnWriteArrayList()

    fun clearLog() = logLines.clear()

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
                logLines += message
                println("[$tag] $message")
                t?.printStackTrace()
            }
        })
    }
}
