package com.vibeapp.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vibeapp.core.db.dao.*
import com.vibeapp.core.db.entity.*

@Database(
    entities = [
        DeviceEntity::class,
        PairingEntity::class,
        VibrationPatternEntity::class,
        HistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun pairingDao(): PairingDao
    abstract fun vibrationPatternDao(): VibrationPatternDao
    abstract fun historyDao(): HistoryDao
}
