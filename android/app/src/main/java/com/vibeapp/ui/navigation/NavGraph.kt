package com.vibeapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vibeapp.ui.main.MainScreen
import com.vibeapp.ui.pairing.PairingScreen
import com.vibeapp.ui.settings.SettingsScreen
import com.vibeapp.ui.settings.devices.DevicesScreen
import com.vibeapp.ui.settings.patterns.VibrationEditorScreen

object Routes {
    const val MAIN = "main"
    const val PAIRING = "pairing"
    const val SETTINGS = "settings"
    const val DEVICES = "devices"
    const val VIBRATION_EDITOR = "vibration_editor"
}

@Composable
fun VibeLinkNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            MainScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPairing = { navController.navigate(Routes.PAIRING) }
            )
        }

        composable(Routes.PAIRING) {
            PairingScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDevices = { navController.navigate(Routes.DEVICES) },
                onOpenVibrationEditor = { navController.navigate(Routes.VIBRATION_EDITOR) }
            )
        }

        composable(Routes.DEVICES) {
            DevicesScreen(
                onBack = { navController.popBackStack() },
                onAddDevice = { navController.navigate(Routes.PAIRING) }
            )
        }

        composable(Routes.VIBRATION_EDITOR) {
            VibrationEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
