package no.synth.where.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import no.synth.where.data.ClientIdManager
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun trackRepository(): TrackRepository
    fun savedPointsRepository(): SavedPointsRepository
    fun userPreferences(): UserPreferences
    fun clientIdManager(): ClientIdManager
}
