package dev.jeonsi.blects.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.jeonsi.blects.ui.detail.DeviceDetailScreen
import dev.jeonsi.blects.ui.home.HomeScreen
import dev.jeonsi.blects.ui.settings.SettingsScreen

/** 단일 홈 + 상세 + 설정. 탭·드로어 없음. */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenDevice = { nav.navigate("device/$it") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable(
            route = "device/{address}",
            arguments = listOf(navArgument("address") { type = NavType.StringType }),
        ) { entry ->
            val address = entry.arguments?.getString("address") ?: return@composable
            DeviceDetailScreen(address = address, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
