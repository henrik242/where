package no.synth.where.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import no.synth.where.data.ClientIdManager
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.db.SavedPointDao
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.WhereDatabase
import javax.inject.Singleton

internal val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")
internal val Context.clientPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "client_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WhereDatabase {
        return Room.databaseBuilder(
            context,
            WhereDatabase::class.java,
            "where_database"
        ).build()
    }

    @Provides
    fun provideTrackDao(database: WhereDatabase): TrackDao {
        return database.trackDao()
    }

    @Provides
    fun provideSavedPointDao(database: WhereDatabase): SavedPointDao {
        return database.savedPointDao()
    }

    @Provides
    @Singleton
    fun provideTrackRepository(@ApplicationContext context: Context, trackDao: TrackDao): TrackRepository {
        return TrackRepository(context.filesDir, trackDao)
    }

    @Provides
    @Singleton
    fun provideSavedPointsRepository(@ApplicationContext context: Context, savedPointDao: SavedPointDao): SavedPointsRepository {
        return SavedPointsRepository(context.filesDir, savedPointDao)
    }

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context.userPrefsDataStore)
    }

    @Provides
    @Singleton
    fun provideClientIdManager(@ApplicationContext context: Context): ClientIdManager {
        return ClientIdManager(context.clientPrefsDataStore)
    }
}
