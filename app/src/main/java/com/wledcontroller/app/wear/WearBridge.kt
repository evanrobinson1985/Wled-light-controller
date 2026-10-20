package com.wledcontroller.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wledcontroller.app.data.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Keeps the watch app's list of controllers (and favourite effects) in step with the phone's,
 * through the Wear OS data layer. Without a watch (or Google Play services) it does nothing.
 */
class WearBridge(private val context: Context, store: AppStore, scope: CoroutineScope) {
    init {
        scope.launch {
            combine(store.devices, store.favoriteEffects, store.effectSettings) { devices, favorites, settings -> Triple(devices, favorites, settings) }
                .collect { (devices, favorites, settings) ->
                    val list = buildJsonArray {
                        devices.forEach { d ->
                            add(kotlinx.serialization.json.buildJsonObject { put("id", d.id); put("name", d.name); put("host", d.host) })
                        }
                    }
                    val request = PutDataMapRequest.create(DEVICES_PATH).apply {
                        dataMap.putString("devices", list.toString())
                        dataMap.putStringArrayList("favorites", ArrayList(favorites))
                        // Each effect's remembered settings, so the watch plays effects as they were set.
                        dataMap.putString("effectSettings", Json.encodeToString(settings))
                    }.asPutDataRequest().setUrgent()
                    runCatching { Wearable.getDataClient(context).putDataItem(request).await() }
                }
        }
    }

    companion object {
        const val DEVICES_PATH = "/wled/devices"
        /** Requests from the watch to pass on to a controller. */
        const val HTTP_PATH = "/wled/http"
    }
}
