package com.wledcontroller.app.sim

import android.content.Context
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.Backup
import com.wledcontroller.app.data.DeviceManager
import com.wledcontroller.app.data.LightProfile
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.house.HouseEffectService
import com.wledcontroller.app.house.HouseEngine
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.XlModel
import com.wledcontroller.app.house.mirroredCopy
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

    private val flags = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
    private val _miniView = MutableStateFlow(flags.getBoolean("sim_mini_view", true))
    /** Whether the small live view of the virtual house floats over the other screens (remembered). */
    val miniView: StateFlow<Boolean> = _miniView.asStateFlow()
    fun setMiniView(on: Boolean) {
        flags.edit().putBoolean("sim_mini_view", on).apply()
        _miniView.value = on
    }

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
            backup.export(realSnapshot, withLibrary = false)
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
        checkDemo()
        reconnectAll()
    }

    /** Keeps the virtual house for next time and puts the real setup back. */
    suspend fun leave() = mutex.withLock {
        if (!_active.value) return@withLock
        stopEverything()
        SimHub.saveNow()
        withContext(Dispatchers.IO) {
            backup.export(sandboxSnapshot, withLibrary = false)
            SimHub.unload()
            if (realSnapshot.exists()) backup.restore(realSnapshot) else backup.clearAll()
            realSnapshot.delete()
            flag.delete()
        }
        _active.value = false
        checkDemo()
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
        checkDemo()
        reconnectAll()
    }

    private suspend fun freshDemo() {
        backup.clearAll()
        DemoSetup.build(store, house, sequences, simFile)
        demoVersionFile.writeText(DemoSetup.VERSION.toString())
    }

    /** Which demo the virtual house was built from (kept with it, in the simulator's folder). */
    private val demoVersionFile get() = File(simFile.parentFile, "demo_version")

    private val _demoOutdated = MutableStateFlow(false)
    /** True when the virtual house was built from an older demo, so it's missing newer things. */
    val demoOutdated: StateFlow<Boolean> = _demoOutdated.asStateFlow()

    // After the flag above exists (properties start up in order).
    init { checkDemo() }

    private fun checkDemo() {
        val built = runCatching { demoVersionFile.readText().trim().toInt() }.getOrDefault(1)
        _demoOutdated.value = _active.value && built < DemoSetup.VERSION
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

    /**
     * Brings a prop model (e.g. a vendor's) into the virtual house: a simulated controller of
     * its own with just enough LEDs, and the prop on the map at its real size when known. New
     * props are spread along the yard so they don't land on top of each other.
     */
    suspend fun addProp(prop: XlModel, widthCm: Float? = prop.widthCm, heightCm: Float? = prop.heightCm): LightRun {
        val name = prop.name.take(40)
        val id = addController(name, prop.ledCount, LightProfile.BULLETS, rgbw = false)
        val map = store.houseMap.first()
        val added = map.runs.count { r -> r.model != null && r.id.startsWith("prop-") }
        val center = MapPoint(0.2f + 0.15f * (added % 5), 0.62f)
        val run = LightRun(
            "prop-" + UUID.randomUUID().toString().take(8), name, id, 0, prop.ledCount,
            points = map.propBox(center, widthCm, heightCm, prop.model?.shapeAspect ?: 1f),
            prop = prop.prop, model = prop.model, submodels = prop.submodels,
            realWidthCm = widthCm, realHeightCm = heightCm,
        )
        store.updateHouseMap { m -> m.copy(runs = m.runs + run) }
        return run
    }

    /**
     * The other one of a pair (a right-hand corner web for a left-hand one): the same prop facing
     * the other way, on a simulated controller of its own, set beside [run].
     */
    suspend fun addMirroredCopy(run: LightRun): LightRun {
        val copy = run.mirroredCopy("prop-" + UUID.randomUUID().toString().take(8))
        val id = addController(copy.name, run.count, LightProfile.BULLETS, rgbw = false)
        val placed = copy.copy(deviceId = id, startLed = 0)
        store.updateHouseMap { m -> m.copy(runs = m.runs + placed) }
        return placed
    }

    /** Removes a simulated controller from the app and the virtual house. */
    suspend fun removeController(id: String) {
        val dev = store.devices.first().firstOrNull { it.id == id } ?: return
        store.removeDevice(id)
        SimHub.remove(dev.host)
    }

    private fun reconnectAll() = devices.list.value.forEach { it.reconnect() }
}
