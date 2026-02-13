package no.synth.where.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class KoinModuleCheckTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun verifyKoinModule() {
        appModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                java.io.File::class,
                DataStore::class,
            )
        )
    }
}
