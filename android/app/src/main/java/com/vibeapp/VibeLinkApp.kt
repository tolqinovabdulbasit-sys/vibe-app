package com.vibeapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.vibeapp.core.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class VibeLinkApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Logging — only in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Persistent connection status channel (low importance — no sound)
        val connectionChannel = NotificationChannel(
            NotificationChannels.CONNECTION_STATUS,
            "Connection Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows VibeLink connection state in background"
            setShowBadge(false)
        }

        // Signal received channel (high importance — user-visible)
        val signalChannel = NotificationChannel(
            NotificationChannels.SIGNAL_RECEIVED,
            "Signal Received",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies when a vibration signal is received"
        }

        // Connection lost alert channel
        val alertChannel = NotificationChannel(
            NotificationChannels.CONNECTION_ALERT,
            "Connection Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when connection to paired devices is lost"
        }

        manager.createNotificationChannels(
            listOf(connectionChannel, signalChannel, alertChannel)
        )
    }
}
