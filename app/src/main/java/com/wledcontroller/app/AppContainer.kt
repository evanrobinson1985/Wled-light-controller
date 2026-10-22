package com.wledcontroller.app

import android.content.Context
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.DeviceManager
import com.wledcontroller.app.data.Discovery
import com.wledcontroller.app.data.WledApi
import com.wledcontroller.app.house.HouseEngine
import com.wledcontroller.app.house.StandaloneSetup
import com.wledcontroller.app.show.SequencePlayer
import com.wledcontroller.app.show.SequenceRepository
import com.wledcontroller.app.show.ShowRunner
import com.wledcontroller.app.show.ShowSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container, created once by [WledApp]. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = OkHttpClient.Builder()
        // Simulated controllers answer in-process.
        .addInterceptor(com.wledcontroller.app.sim.SimHub.interceptor)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val wsClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val api = WledApi(http)
    val store = AppStore(context)
    /** The broker connection for controlling the lights from anywhere. */
    val mqtt = com.wledcontroller.app.data.MqttLink()
    val devices = DeviceManager(api, wsClient, store, scope, mqtt, com.wledcontroller.app.data.DeviceCache(java.io.File(context.filesDir, "devcache")))
    val discovery = Discovery(context, api)
    val house = HouseEngine(context, store, scope)
    val standalone = StandaloneSetup(context, api, store)
    val sequences = SequenceRepository(context, scope)
    val player = SequencePlayer(store, house, scope)
    val showRunner = ShowRunner(store, api, sequences, house, scope)
    /** The viewer jukebox and the web page visitors use to pick shows. */
    val jukebox = com.wledcontroller.app.show.Jukebox()
    val jukeboxServer = com.wledcontroller.app.show.JukeboxServer(jukebox) { jukeboxTitle }
    @Volatile var jukeboxTitle: String = "Light show"
    /** The shows in Home Assistant, over the MQTT broker. */
    val homeAssistant = com.wledcontroller.app.data.HomeAssistantBridge(
        context, mqtt, com.wledcontroller.app.data.HomeAssistantBridge.playlistNames(store), showRunner.state, scope,
    )
    /** Alerts when a controller drops out during shows. */
    val health = com.wledcontroller.app.data.HealthMonitor(context, store, api, scope, showsOn = { showRunner.state.value.running || house.running.value })
    /** The 3D house model's files. */
    val models = com.wledcontroller.app.house.model3d.ModelStore(context)
    /** Saved house layouts and light models. */
    val library = com.wledcontroller.app.data.Library(context.filesDir)
    val backup = com.wledcontroller.app.data.Backup(context, store, sequences, library)
    /** A copy of everything in Downloads, which outlasts uninstalling the app. */
    val autoBackup = com.wledcontroller.app.data.AutoBackup(context, store, backup)
    val simulator = com.wledcontroller.app.sim.Simulator(context, store, backup, sequences, house, player, devices)
    /** Keeps the watch app's controller list up to date. */
    val wear = com.wledcontroller.app.wear.WearBridge(context, store, scope)

    init {
        com.wledcontroller.app.show.AndroidRasters.install()
        scope.launch {
            kotlinx.coroutines.flow.combine(store.fixtures, store.outputs) { f, o -> f to o }
                .collect { (f, o) -> com.wledcontroller.app.data.DmxRegistry.update(f, o) }
        }
        scope.launch {
            store.mqtt.collect { saved ->
                val s = saved ?: com.wledcontroller.app.data.MqttSettings()
                mqtt.will = homeAssistant.will(s)
                mqtt.update(s)
                homeAssistant.apply(s)
            }
        }
        scope.launch { store.lightsDelayMs.collect { ShowSettings.lightsDelayMs = it.toLong() } }
        health.start()
        // Every real controller ever added comes back, whatever replaced the list (the simulator
        // keeps its own, so not while it's on).
        scope.launch { if (!simulator.active.value) runCatching { store.restoreRememberedDevices() } }
    }
}
