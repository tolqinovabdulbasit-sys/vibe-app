package com.vibeapp.core.di

import android.content.Context
import androidx.room.Room
import com.vibeapp.core.db.AppDatabase
import com.vibeapp.core.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "vibelink.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()
    @Provides fun providePairingDao(db: AppDatabase): PairingDao = db.pairingDao()
    @Provides fun provideVibrationPatternDao(db: AppDatabase): VibrationPatternDao = db.vibrationPatternDao()
    @Provides fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
}
