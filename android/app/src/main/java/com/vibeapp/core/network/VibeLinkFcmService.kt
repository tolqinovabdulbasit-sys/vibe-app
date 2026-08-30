package com.vibeapp.core.network

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vibeapp.core.service.VibeLinkForegroundService
import timber.log.Timber
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Firebase Cloud Messaging service.
 * FCM is used as a FALLBACK background wake-up mechanism.
 * When the WebSocket connection is dormant (Doze/App Standby),
 * a high-priority FCM message wakes the device to reconnect.
 */
class VibeLinkFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed")
        // Token update is handled by FirebaseManager on next connection
        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("FCM message received: ${remoteMessage.data}")

        val messageType = remoteMessage.data["type"] ?: return

        // Ensure the foreground service is running
        val serviceIntent = Intent(this, VibeLinkForegroundService::class.java).apply {
            action = VibeLinkForegroundService.ACTION_FCM_WAKEUP
            putExtra("fcm_type", messageType)
            putExtra("fcm_payload", remoteMessage.data["payload"])
        }

        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service from FCM")
        }
    }
}
