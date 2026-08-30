package com.vibeapp.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vibeapp.core.service.VibeLinkForegroundService
import timber.log.Timber

/**
 * Starts the VibeLink foreground service after device boot.
 * Handles both BOOT_COMPLETED and LOCKED_BOOT_COMPLETED.
 * No re-pairing required — credentials are restored from Room + Keystore.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.d("Boot/update received — starting VibeLink service")
                try {
                    val serviceIntent = Intent(context, VibeLinkForegroundService::class.java).apply {
                        action = VibeLinkForegroundService.ACTION_START
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start service on boot")
                }
            }
        }
    }
}
