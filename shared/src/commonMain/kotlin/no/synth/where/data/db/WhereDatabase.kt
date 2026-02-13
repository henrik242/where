package no.synth.where.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, TrackPointEntity::class, SavedPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WhereDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun savedPointDao(): SavedPointDao
}
