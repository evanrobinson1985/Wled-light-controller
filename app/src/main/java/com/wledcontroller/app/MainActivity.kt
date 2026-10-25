package com.wledcontroller.app

import android.os.Bundle
import kotlinx.coroutines.launch
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
import com.wledcontroller.app.ui.screens.TutorialScreen
import com.wledcontroller.app.ui.screens.WebUiScreen
import com.wledcontroller.app.ui.theme.WledTheme

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

class MainActivity : ComponentActivity() {
    private val container get() = (application as WledApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val pixelStyle by container.store.pixelStyle.collectAsStateWithLifecycle(com.wledcontroller.app.ui.components.PixelStyle())
            CompositionLocalProvider(
                LocalAppContainer provides container,
                com.wledcontroller.app.ui.components.LocalPixelStyle provides pixelStyle,
            ) {
                WledTheme {
                    val nav = rememberNavController()
                    val idArg = listOf(navArgument("id") { type = NavType.StringType })
                    val simActive by container.simulator.active.collectAsStateWithLifecycle()
                    val miniView by container.simulator.miniView.collectAsStateWithLifecycle()
                    val route = nav.currentBackStackEntryAsState().value?.destination?.route
                    // First time the app is opened: the walk-through.
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (!container.store.tutorialSeen.value) {
                            nav.navigate("tutorial")
                            container.store.setTutorialSeen()
                        }
                    }
                    // The app closed unexpectedly last time: offer to share what went wrong.
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val crashState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CrashReport.pending(ctx)) }
                    crashState.value?.let { report ->
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { CrashReport.clear(ctx); crashState.value = null },
                            title = { androidx.compose.material3.Text("The app closed unexpectedly") },
                            text = { androidx.compose.material3.Text("Sorry about that. A report of what went wrong was kept: share it (e.g. paste it into your chat with Claude) so it can be fixed.") },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "WLED Controller crash report")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, report)
                                    ctx.startActivity(android.content.Intent.createChooser(send, "Share the crash report"))
                                    CrashReport.clear(ctx); crashState.value = null
                                }) { androidx.compose.material3.Text("Share report") }
                            },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { CrashReport.clear(ctx); crashState.value = null }) { androidx.compose.material3.Text("Dismiss") } },
                        )
                    }
                    Box(Modifier.fillMaxSize()) {
                    NavHost(navController = nav, startDestination = "home") {
                        composable("tutorial") {
                            TutorialScreen(
                                onClose = { nav.popBackStack() },
                                onTry = { r -> if (r == "home") nav.popBackStack("home", inclusive = false) else nav.navigate(r) },
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onOpenDevice = { nav.navigate("device/$it") },
                                onOpenGroup = { nav.navigate("group/$it") },
                                onOpenHouse = { nav.navigate("house") },
                                onOpenSchedule = { nav.navigate("schedule") },
                                onOpenSettings = { nav.navigate("settings") },
                                onOpenSimulator = { nav.navigate("sim") },
                                onOpenTutorial = { nav.navigate("tutorial") },
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
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() }, onOpenTutorial = { nav.navigate("tutorial") }, onOpenTest = { nav.navigate("test") }, onOpenFirmware = { nav.navigate("firmware") }, onOpenProjects = { nav.navigate("projects") })
                        }
                        composable("projects") { com.wledcontroller.app.ui.screens.ProjectsScreen(onBack = { nav.popBackStack() }, onOpenMap = { nav.navigate("house/map") }) }
                        composable("house/controllers") { com.wledcontroller.app.ui.screens.ControllerMapScreen(onBack = { nav.popBackStack() }) }
                        composable("house/model3d") { com.wledcontroller.app.ui.screens.Model3DScreen(onBack = { nav.popBackStack() }, onScan = { nav.navigate("house/scan3d") }) }
                        composable("house/scan3d") { com.wledcontroller.app.ui.screens.ScanHouseScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() }) }
                        composable("house/power") { com.wledcontroller.app.ui.screens.PowerScreen(onBack = { nav.popBackStack() }) }
                        composable("firmware") { com.wledcontroller.app.ui.screens.FirmwareScreen(onBack = { nav.popBackStack() }, onOpenWebUi = { nav.navigate("web/$it") }) }
                        composable("jukebox") { com.wledcontroller.app.ui.screens.JukeboxScreen(onBack = { nav.popBackStack() }) }
                        composable("test") { com.wledcontroller.app.ui.screens.TestLightsScreen(onBack = { nav.popBackStack() }) }
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
                                onJukebox = { nav.navigate("jukebox") },
                            )
                        }
                        composable("schedule") { ScheduleScreen(onBack = { nav.popBackStack() }) }
                        composable("shows/{id}", arguments = idArg) { entry ->
                            SequenceEditorScreen(sequenceId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                        }
                        composable("house/map") {
                            HouseMapEditor(onBack = { nav.popBackStack() }, onScan = { nav.navigate("house/scan") }, onControllers = { nav.navigate("house/controllers") }, onPower = { nav.navigate("house/power") }, onModel3d = { nav.navigate("house/model3d") })
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
        // A newer build on GitHub goes in by itself (at most every few hours, on Wi-Fi).
        val c = container
        c.scope.launch { c.updater.checkInBackground() }
    }

    override fun onStop() {
        super.onStop()
        container.devices.setForeground(false)
        // Leaving the app: refresh the copy in Downloads if anything changed.
        val c = container
        c.scope.launch { runCatching { c.autoBackup.saveIfChanged(simulatorOn = c.simulator.active.value) } }
    }
}
