package com.wledcontroller.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Keeps one [DeviceController] per saved device and connects them while the app is visible. */
class DeviceManager(
    private val api: WledApi,
    private val wsClient: OkHttpClient,
    store: AppStore,
    private val scope: CoroutineScope,
) {
    private val controllers = LinkedHashMap<String, DeviceController>()
    private val _list = MutableStateFlow<List<DeviceController>>(emptyList())
    val list: StateFlow<List<DeviceController>> = _list.asStateFlow()

    private var foreground = false
    private var stopJob: Job? = null

    init {
        scope.launch { store.devices.collect { sync(it) } }
    }

    fun get(id: String): DeviceController? = synchronized(this) { controllers[id] }

    @Synchronized
    private fun sync(devices: List<SavedDevice>) {
        val ids = devices.map { it.id }.toSet()
        controllers.keys.filter { it !in ids }.forEach { controllers.remove(it)?.dispose() }
        for (d in devices) {
            val existing = controllers[d.id]
            if (existing != null && WledApi.normalizeHost(existing.device.host) == WledApi.normalizeHost(d.host)) {
                existing.device = d
            } else {
                existing?.dispose()
                val c = DeviceController(d, api, wsClient, scope)
                controllers[d.id] = c
                if (foreground) c.start()
            }
        }
        // Preserve the user's ordering from the store.
        _list.value = devices.mapNotNull { controllers[it.id] }
    }

    @Synchronized
    fun setForeground(visible: Boolean) {
        foreground = visible
        stopJob?.cancel()
        if (visible) {
            controllers.values.forEach { it.start() }
        } else {
            // Grace period so rotation / quick app switches don't drop the sockets.
            stopJob = scope.launch {
                delay(5_000)
                synchronized(this@DeviceManager) {
                    if (!foreground) controllers.values.forEach { it.stop() }
                }
            }
        }
    }
}
