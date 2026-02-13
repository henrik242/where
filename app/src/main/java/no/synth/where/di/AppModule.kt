package no.synth.where.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import no.synth.where.WhereAppViewModel
import no.synth.where.data.ClientIdManager
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.db.WhereDatabase
import no.synth.where.ui.MapScreenViewModel
import no.synth.where.ui.OnlineTrackingScreenViewModel
import no.synth.where.ui.SavedPointsScreenViewModel
import no.synth.where.ui.TracksScreenViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")
internal val Context.clientPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "client_prefs")

val appModule = module {
    single { Room.databaseBuilder(androidContext(), WhereDatabase::class.java, "where_database").build() }
    single { get<WhereDatabase>().trackDao() }
    single { get<WhereDatabase>().savedPointDao() }
    single { TrackRepository(androidContext().filesDir, get()) }
    single { SavedPointsRepository(androidContext().filesDir, get()) }
    single { UserPreferences(androidContext().userPrefsDataStore) }
    single { ClientIdManager(androidContext().clientPrefsDataStore) }
    viewModel { WhereAppViewModel(get(), get()) }
    viewModel { MapScreenViewModel(get(), get(), get()) }
    viewModel { TracksScreenViewModel(get()) }
    viewModel { SavedPointsScreenViewModel(get()) }
    viewModel { OnlineTrackingScreenViewModel(get(), get()) }
}
