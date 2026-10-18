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
    val devices = DeviceManager(api, wsClient, store, scope)
    val discovery = Discovery(context, api)
    val house = HouseEngine(context, store, scope)
    val standalone = StandaloneSetup(context, api, store)
    val sequences = SequenceRepository(context, scope)
    val player = SequencePlayer(store, house, scope)
    val showRunner = ShowRunner(store, api, sequences, house, scope)
    val backup = com.wledcontroller.app.data.Backup(context, store, sequences)
    val simulator = com.wledcontroller.app.sim.Simulator(context, store, backup, sequences, house, player, devices)

    init {
        com.wledcontroller.app.show.AndroidRasters.install()
        scope.launch { store.lightsDelayMs.collect { ShowSettings.lightsDelayMs = it.toLong() } }
    }
}
