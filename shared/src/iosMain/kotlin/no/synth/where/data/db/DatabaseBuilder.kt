package no.synth.where.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

fun getDatabaseBuilder(): RoomDatabase.Builder<WhereDatabase> {
    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val documentsDir = (paths.first() as NSURL).path!!
    val dbFilePath = "$documentsDir/where_database"
    return Room.databaseBuilder<WhereDatabase>(name = dbFilePath)
        .setDriver(BundledSQLiteDriver())
}
