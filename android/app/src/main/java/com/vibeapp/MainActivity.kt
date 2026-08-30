package com.vibeapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.vibeapp.core.service.VibeLinkForegroundService
import com.vibeapp.ui.navigation.VibeLinkNavHost
import com.vibeapp.ui.theme.Background
import com.vibeapp.ui.theme.VibeLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start the foreground service
        startVibeLinkService()

        setContent {
            VibeLinkTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background)
                ) {
                    VibeLinkNavHost()
                }
            }
        }
    }

    private fun startVibeLinkService() {
        val intent = Intent(this, VibeLinkForegroundService::class.java).apply {
            action = VibeLinkForegroundService.ACTION_START
        }
        startForegroundService(intent)
    }
}
