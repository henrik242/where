package no.synth.where.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import no.synth.where.data.PlatformFile
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
                PlatformFile::class,
                DataStore::class,
            )
        )
    }
}
