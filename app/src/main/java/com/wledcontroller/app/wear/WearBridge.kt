package com.wledcontroller.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.house.HouseEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Keeps the watch app in step with the phone through the Wear OS data layer: the controllers,
 * favourite effects and each effect's settings, scenes, groups, and the whole-house effects
 * (with the holiday shadows). Without a watch (or Google Play services) it does nothing.
 */
class WearBridge(private val context: Context, store: AppStore, scope: CoroutineScope) {
    init {
        scope.launch {
            combine(store.devices, store.favoriteEffects, store.effectSettings, store.scenes, store.groups) { devices, favorites, settings, scenes, groups ->
                val request = PutDataMapRequest.create(DEVICES_PATH).apply {
                    dataMap.putString("devices", buildJsonArray {
                        devices.forEach { d -> add(buildJsonObject { put("id", d.id); put("name", d.name); put("host", d.host) }) }
                    }.toString())
                    dataMap.putStringArrayList("favorites", ArrayList(favorites))
                    // Each effect's remembered settings, so the watch plays effects as they were set.
                    dataMap.putString("effectSettings", Json.encodeToString(settings))
                    dataMap.putString("scenes", buildJsonArray {
                        scenes.forEach { s -> add(buildJsonObject { put("id", s.id); put("name", s.name) }) }
                    }.toString())
                    dataMap.putString("groups", buildJsonArray {
                        groups.forEach { g -> add(buildJsonObject { put("id", g.id); put("name", g.name); putJsonArray("devices") { g.deviceIds.forEach { add(it) } } }) }
                    }.toString())
                    dataMap.putString("houseEffects", houseEffects())
                }.asPutDataRequest().setUrgent()
                request
            }.collect { request -> runCatching { Wearable.getDataClient(context).putDataItem(request).await() } }
        }
    }

    /** The whole-house effects, so the watch lists the phone's (holiday shadows grouped by holiday). */
    private fun houseEffects() = buildJsonArray {
        HouseEffect.entries.forEach { e ->
            add(buildJsonObject {
                put("name", e.name); put("label", e.label)
                e.holiday?.let { put("holiday", it) }
                if (e.figures.isNotEmpty()) { put("noun", e.noun); put("plural", e.plural) }
            })
        }
    }.toString()

    companion object {
        const val DEVICES_PATH = "/wled/devices"
        /** Requests from the watch to pass on to a controller. */
        const val HTTP_PATH = "/wled/http"
        /** What one controller is showing, as a few dozen colours for the watch face. */
        const val PREVIEW_PATH = "/wled/preview"
        const val SCENE_PATH = "/wled/scene"
        const val GROUP_PATH = "/wled/group"
        /** Whole-house effects: read the status, play, change or stop. */
        const val HOUSE_PATH = "/wled/house"
    }
}
