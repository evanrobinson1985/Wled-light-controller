package com.wledcontroller.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.DeviceGroup
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.data.Scene
import com.wledcontroller.app.house.HouseEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class WearBridge(private val context: Context, private val store: AppStore, scope: CoroutineScope) {
    init {
        scope.launch {
            combine(store.devices, store.favoriteEffects, store.effectSettings, store.scenes, store.groups) { devices, favorites, settings, scenes, groups ->
                payload(devices, favorites, settings, scenes, groups)
            }.collect { data ->
                val request = PutDataMapRequest.create(DEVICES_PATH).apply {
                    data.forEach { (k, v) ->
                        if (k == "favorites") dataMap.putStringArrayList(k, ArrayList((v as JsonArray).map { it.jsonPrimitive.content }))
                        else dataMap.putString(k, v.jsonPrimitive.content)
                    }
                }.asPutDataRequest().setUrgent()
                runCatching { Wearable.getDataClient(context).putDataItem(request).await() }
            }
        }
    }

    /** Everything the watch needs, for a watch that asks directly (its list is empty, or out of date). */
    suspend fun current(): JsonObject = payload(
        store.devices.first(), store.favoriteEffects.first(), store.effectSettings.first(), store.scenes.first(), store.groups.first(),
    )

    /** The watch's data: lists as JSON text, favourites as a list of names. */
    private fun payload(
        devices: List<SavedDevice>,
        favorites: Set<String>,
        settings: Map<String, Map<String, Int>>,
        scenes: List<Scene>,
        groups: List<DeviceGroup>,
    ) = buildJsonObject {
        put("devices", buildJsonArray {
            devices.forEach { d -> add(buildJsonObject { put("id", d.id); put("name", d.name); put("host", d.host) }) }
        }.toString())
        putJsonArray("favorites") { favorites.forEach { add(it) } }
        // Each effect's remembered settings, so the watch plays effects as they were set.
        put("effectSettings", Json.encodeToString(settings))
        put("scenes", buildJsonArray {
            scenes.forEach { sc -> add(buildJsonObject { put("id", sc.id); put("name", sc.name) }) }
        }.toString())
        put("groups", buildJsonArray {
            groups.forEach { g -> add(buildJsonObject { put("id", g.id); put("name", g.name); putJsonArray("devices") { g.deviceIds.forEach { add(it) } } }) }
        }.toString())
        put("houseEffects", houseEffects())
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
        /** The watch asking for everything at once. */
        const val SYNC_PATH = "/wled/sync"
    }
}
