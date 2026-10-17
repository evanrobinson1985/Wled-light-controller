package com.wledcontroller.app.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wledcontroller.app.WledApp
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.data.WledState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Quick Settings tile that toggles the chosen WLED device (first device by default). */
class PowerTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val container get() = (application as WledApp).container

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val device = target() ?: return@launch render(null, null)
            val state = runCatching { container.api.get(device.host, "/json/state") as? JsonObject }.getOrNull()
            render(device, state?.let { WledState.parse(it) })
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val device = target() ?: return@launch
            val body = buildJsonObject { put("on", "t"); put("v", true) }
            val result = runCatching { container.api.postState(device.host, body) as? JsonObject }.getOrNull()
            // With "v": true WLED answers with the full state object.
            render(device, result?.let { WledState.parse(it) })
        }
    }

    private suspend fun target(): SavedDevice? {
        val devices = container.store.devices.first()
        val id = container.store.tileDeviceId.first()
        return devices.firstOrNull { it.id == id } ?: devices.firstOrNull()
    }

    private fun render(device: SavedDevice?, state: WledState?) {
        val tile = qsTile ?: return
        tile.label = device?.name ?: "WLED"
        tile.state = when {
            device == null || state == null -> Tile.STATE_UNAVAILABLE
            state.on -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                device == null -> "No device"
                state == null -> "Offline"
                state.on -> "${(state.bri * 100 / 255).coerceAtLeast(1)}%"
                else -> "Off"
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
