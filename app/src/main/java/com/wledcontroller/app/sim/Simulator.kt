package com.wledcontroller.app.sim

import android.content.Context
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.Backup
import com.wledcontroller.app.data.DeviceManager
import com.wledcontroller.app.data.LightProfile
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.house.HouseEffectService
import com.wledcontroller.app.house.HouseEngine
import com.wledcontroller.app.show.SequencePlayer
import com.wledcontroller.app.show.SequenceRepository
import com.wledcontroller.app.show.ShowRunnerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Simulator mode: the app swaps your real setup for a virtual house of simulated controllers,
 * where every feature works without touching real lights. Your setup is saved first and comes
 * back when you leave; the virtual house is kept for next time.
 */
class Simulator(
    private val context: Context,
    private val store: AppStore,
    private val backup: Backup,
    private val sequences: SequenceRepository,
    private val house: HouseEngine,
    private val player: SequencePlayer,
    private val devices: DeviceManager,
) {
    private val dir get() = context.filesDir
    private val flag get() = File(dir, "sim_active")
    private val realSnapshot get() = File(dir, "sim_saved_setup.zip")
    private val sandboxSnapshot get() = File(dir, "sim_virtual_house.zip")
    val simFile get() = File(dir, "simulator/devices.json")

    private val _active = MutableStateFlow(flag.exists())
    /** True while the app is running the virtual house. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val mutex = Mutex()

    private val _miniView = MutableStateFlow(true)
    /** Whether the small live view of the virtual house floats over the other screens. */
    val miniView: StateFlow<Boolean> = _miniView.asStateFlow()
    fun setMiniView(on: Boolean) { _miniView.value = on }

    init {
        if (_active.value) SimHub.load(simFile)
    }

    private fun stopEverything() {
        ShowRunnerService.stop(context)
        HouseEffectService.stop(context)
        player.stopAll()
        house.stop()
    }

    /** Saves the real setup and opens the virtual house (the last one used, or a fresh demo). */
    suspend fun enter() = mutex.withLock {
        if (_active.value) return@withLock
        stopEverything()
        withContext(Dispatchers.IO) {
            backup.export(realSnapshot)
            if (sandboxSnapshot.exists()) {
                val restored = runCatching { backup.restore(sandboxSnapshot) }.isSuccess
                if (!restored) freshDemo()
            } else {
                freshDemo()
            }
            flag.writeText("1")
        }
        SimHub.load(simFile)
        _active.value = true
        reconnectAll()
    }

    /** Keeps the virtual house for next time and puts the real setup back. */
    suspend fun leave() = mutex.withLock {
        if (!_active.value) return@withLock
        stopEverything()
        SimHub.saveNow()
        withContext(Dispatchers.IO) {
            backup.export(sandboxSnapshot)
            SimHub.unload()
            if (realSnapshot.exists()) backup.restore(realSnapshot) else backup.clearAll()
            realSnapshot.delete()
            flag.delete()
        }
        _active.value = false
        reconnectAll()
        if (store.scheduleOn.first()) ShowRunnerService.startSchedule(context)
    }

    /** Throws the virtual house away and builds the demo again. */
    suspend fun reset() = mutex.withLock {
        if (!_active.value) return@withLock
        stopEverything()
        SimHub.unload()
        withContext(Dispatchers.IO) { freshDemo() }
        SimHub.load(simFile)
        reconnectAll()
    }

    private suspend fun freshDemo() {
        backup.clearAll()
        DemoSetup.build(store, house, sequences, simFile)
    }

    /** Adds a simulated controller with [leds] LEDs. Returns its id in the app. */
    suspend fun addController(name: String, leds: Int, profile: LightProfile, rgbw: Boolean): String {
        val host = SimHub.nextHost()
        val id = "sim-" + UUID.randomUUID().toString().take(8)
        SimHub.add(SimDeviceData(
            host = host, name = name, seed = (Math.random() * 100000).toInt(), createdAt = System.currentTimeMillis(),
            state = SimDevice.defaultState(leds).toString(),
            cfg = SimDevice.defaultCfg(name, leds, type = if (rgbw) 30 else 22).toString(),
            presets = """{"0":{}}""",
        ))
        store.upsertDevice(SavedDevice(id, name, host))
        store.setLightProfile(LightProfile.deviceKey(id), profile)
        return id
    }

    /** Removes a simulated controller from the app and the virtual house. */
    suspend fun removeController(id: String) {
        val dev = store.devices.first().firstOrNull { it.id == id } ?: return
        store.removeDevice(id)
        SimHub.remove(dev.host)
    }

    private fun reconnectAll() = devices.list.value.forEach { it.reconnect() }
}
