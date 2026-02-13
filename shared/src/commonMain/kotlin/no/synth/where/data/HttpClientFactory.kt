package no.synth.where.data

import io.ktor.client.HttpClient

expect fun createDefaultHttpClient(): HttpClient
