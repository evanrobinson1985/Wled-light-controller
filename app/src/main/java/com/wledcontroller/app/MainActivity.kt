package com.wledcontroller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wledcontroller.app.ui.screens.CameraScanScreen
import com.wledcontroller.app.ui.screens.DeviceScreen
import com.wledcontroller.app.ui.screens.GroupScreen
import com.wledcontroller.app.ui.screens.HomeScreen
import com.wledcontroller.app.ui.screens.HouseMapEditor
import com.wledcontroller.app.ui.screens.HouseScreen
import com.wledcontroller.app.ui.screens.StandaloneScreen
import com.wledcontroller.app.ui.screens.LedSetupScreen
import com.wledcontroller.app.ui.screens.ScheduleScreen
import com.wledcontroller.app.ui.screens.SequenceEditorScreen
import com.wledcontroller.app.ui.screens.SettingsScreen
import com.wledcontroller.app.ui.screens.ShowsScreen
import com.wledcontroller.app.ui.screens.SimMiniView
import com.wledcontroller.app.ui.screens.SimulatorScreen
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
                    val simActive by container.simulator.active.collectAsStateWithLifecycle()
                    val miniView by container.simulator.miniView.collectAsStateWithLifecycle()
                    val route = nav.currentBackStackEntryAsState().value?.destination?.route
                    Box(Modifier.fillMaxSize()) {
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onOpenDevice = { nav.navigate("device/$it") },
                                onOpenGroup = { nav.navigate("group/$it") },
                                onOpenHouse = { nav.navigate("house") },
                                onOpenSchedule = { nav.navigate("schedule") },
                                onOpenSettings = { nav.navigate("settings") },
                                onOpenSimulator = { nav.navigate("sim") },
                            )
                        }
                        composable("sim") {
                            SimulatorScreen(
                                onBack = { nav.popBackStack() },
                                onOpenDevice = { nav.navigate("device/$it") },
                                onOpenGroup = { nav.navigate("group/$it") },
                                onOpenHouse = { nav.navigate("house") },
                                onOpenShows = { nav.navigate("shows") },
                                onOpenShow = { nav.navigate("shows/$it") },
                                onOpenSchedule = { nav.navigate("schedule") },
                                onOpenMap = { nav.navigate("house/map") },
                            )
                        }
                        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                        composable("house") {
                            HouseScreen(
                                onBack = { nav.popBackStack() },
                                onEditMap = { nav.navigate("house/map") },
                                onStandalone = { nav.navigate("house/standalone") },
                                onShows = { nav.navigate("shows") },
                            )
                        }
                        composable("shows") {
                            ShowsScreen(
                                onBack = { nav.popBackStack() },
                                onOpen = { nav.navigate("shows/$it") },
                                onEditMap = { nav.navigate("house/map") },
                                onSchedule = { nav.navigate("schedule") },
                            )
                        }
                        composable("schedule") { ScheduleScreen(onBack = { nav.popBackStack() }) }
                        composable("shows/{id}", arguments = idArg) { entry ->
                            SequenceEditorScreen(sequenceId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                        }
                        composable("house/map") {
                            HouseMapEditor(onBack = { nav.popBackStack() }, onScan = { nav.navigate("house/scan") })
                        }
                        composable("house/scan") {
                            CameraScanScreen(
                                onBack = { nav.popBackStack() },
                                onFinished = {
                                    // Land in the editor to fine-tune the result.
                                    nav.popBackStack()
                                    if (nav.currentDestination?.route != "house/map") nav.navigate("house/map")
                                },
                            )
                        }
                        composable("house/standalone") {
                            StandaloneScreen(onBack = { nav.popBackStack() }, onOpenDevice = { nav.navigate("device/$it") })
                        }
                        composable("device/{id}", arguments = idArg) { entry ->
                            DeviceScreen(
                                deviceId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                                onOpenWebUi = { nav.navigate("web/$it") },
                                onOpenLedSetup = { nav.navigate("setup/$it") },
                            )
                        }
                        composable("group/{id}", arguments = idArg) { entry ->
                            GroupScreen(
                                groupId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                                onOpenDevice = { nav.navigate("device/$it") },
                            )
                        }
                        composable("setup/{id}", arguments = idArg) { entry ->
                            LedSetupScreen(
                                deviceId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("web/{id}", arguments = idArg) { entry ->
                            WebUiScreen(
                                deviceId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                    // The virtual house stays in view while you use the rest of the app.
                    if (simActive && miniView && route != "sim" && route != "house/scan") {
                        SimMiniView(
                            onOpen = { nav.navigate("sim") { launchSingleTop = true } },
                            onClose = { container.simulator.setMiniView(false) },
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
