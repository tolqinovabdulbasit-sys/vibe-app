package com.vibeapp.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.vibeapp.core.service.VibeLinkForegroundService
import timber.log.Timber

/** Triggers reconnect when network becomes available after loss */
class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(network) ?: return

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        if (hasInternet) {
            Timber.d("Network available — triggering reconnect")
            try {
                val serviceIntent = Intent(context, VibeLinkForegroundService::class.java).apply {
                    action = VibeLinkForegroundService.ACTION_START
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reconnect on network change")
            }
        }
    }
}
