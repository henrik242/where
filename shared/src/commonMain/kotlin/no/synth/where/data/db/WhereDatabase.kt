package no.synth.where.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [TrackEntity::class, TrackPointEntity::class, SavedPointEntity::class],
    version = 1,
    exportSchema = false
)
@ConstructedBy(WhereDatabaseConstructor::class)
abstract class WhereDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun savedPointDao(): SavedPointDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object WhereDatabaseConstructor : RoomDatabaseConstructor<WhereDatabase> {
    override fun initialize(): WhereDatabase
}
