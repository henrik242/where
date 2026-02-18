package no.synth.where.di

import no.synth.where.data.UserPreferences
import no.synth.where.util.CrashReporter
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform.getKoin

fun initKoin() {
    startKoin {
        modules(iosModule)
    }
    val prefs = getKoin().get<UserPreferences>()
    CrashReporter.setEnabled(prefs.crashReportingEnabled.value)
}
