package com.wledcontroller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wledcontroller.app.ui.screens.DeviceScreen
import com.wledcontroller.app.ui.screens.GroupScreen
import com.wledcontroller.app.ui.screens.HomeScreen
import com.wledcontroller.app.ui.screens.WebUiScreen
import com.wledcontroller.app.ui.theme.WledTheme

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

class MainActivity : ComponentActivity() {
    private val container get() = (application as WledApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                WledTheme {
                    val nav = rememberNavController()
                    val idArg = listOf(navArgument("id") { type = NavType.StringType })
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onOpenDevice = { nav.navigate("device/$it") },
                                onOpenGroup = { nav.navigate("group/$it") },
                            )
                        }
                        composable("device/{id}", arguments = idArg) { entry ->
                            DeviceScreen(
                                deviceId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                                onOpenWebUi = { nav.navigate("web/$it") },
                            )
                        }
                        composable("group/{id}", arguments = idArg) { entry ->
                            GroupScreen(
                                groupId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                                onOpenDevice = { nav.navigate("device/$it") },
                            )
                        }
                        composable("web/{id}", arguments = idArg) { entry ->
                            WebUiScreen(
                                deviceId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.devices.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        container.devices.setForeground(false)
    }
}
