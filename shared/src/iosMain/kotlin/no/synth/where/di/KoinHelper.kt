package no.synth.where.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(iosModule)
    }
}
