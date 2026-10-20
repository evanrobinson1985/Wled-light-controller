package com.wledcontroller.app.wear

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.WearableListenerService
import com.wledcontroller.app.AppContainer
import com.wledcontroller.app.WledApp
import com.wledcontroller.app.house.HouseEffect
import com.wledcontroller.app.house.HouseEffectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The watch's requests. Commands to a controller go through the phone's own connection to it
 * (so they're remembered as its start-up look, like changes made on the phone), other calls are
 * passed on as they are, and the phone also answers for previews, scenes, groups and the
 * whole-house effects, which live on the phone.
 */
class WearRelayService : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (!path.startsWith("/wled/")) return null
        val container = (application as WledApp).container
        val result = TaskCompletionSource<ByteArray>()
        container.scope.launch(Dispatchers.IO) {
            try {
                val req = Json.parseToJsonElement(String(request)) as JsonObject
                result.setResult(
                    when (path) {
                        WearBridge.HTTP_PATH -> http(container, req)
                        WearBridge.PREVIEW_PATH -> preview(container, req)
                        WearBridge.SCENE_PATH -> scene(container, req)
                        WearBridge.GROUP_PATH -> group(container, req)
                        WearBridge.HOUSE_PATH -> house(container, req)
                        WearBridge.SYNC_PATH -> container.wear.current().toString().toByteArray()
                        else -> throw IOException("Unknown request $path")
                    },
                )
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result.task
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content

    private fun http(container: AppContainer, req: JsonObject): ByteArray {
        val controller = req.str("id")?.let { container.devices.get(it) }
        val apiPath = req.str("path")!!
        val body = req.str("body")
        // A state change: through the controller, as if made on the phone.
        if (controller != null && body != null && apiPath == "/json/state") {
            controller.send(Json.parseToJsonElement(body) as JsonObject)
            return "{}".toByteArray()
        }
        val host = controller?.apiHost ?: req.str("host")!!
        val http = Request.Builder().url(container.api.baseUrl(host) + apiPath).apply {
            if (body != null) post(body.toRequestBody("application/json".toMediaType()))
        }.build()
        container.api.client.newCall(http).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    /** [n] colours (3 bytes each) spread along the controller's LEDs, as the app draws its preview. */
    private fun preview(container: AppContainer, req: JsonObject): ByteArray {
        val controller = container.devices.get(req.str("id") ?: "") ?: throw IOException("Unknown controller")
        val snap = controller.snapshot.value
        val state = snap.state ?: return ByteArray(0)
        val count = snap.info?.ledCount?.takeIf { it > 0 } ?: return ByteArray(0)
        val leds = com.wledcontroller.app.sim.StatePreview.render(state, snap.effects, snap.palettes, count, System.currentTimeMillis())
        val n = (req["n"]?.jsonPrimitive?.intOrNull ?: 48).coerceIn(1, 256)
        val out = ByteArray(n * 3)
        for (i in 0 until n) {
            val c = leds[(i.toLong() * leds.size / n).toInt().coerceIn(0, leds.lastIndex)]
            out[i * 3] = (c shr 16).toByte(); out[i * 3 + 1] = (c shr 8).toByte(); out[i * 3 + 2] = c.toByte()
        }
        return out
    }

    private suspend fun scene(container: AppContainer, req: JsonObject): ByteArray {
        val scene = container.store.scenes.first().firstOrNull { it.id == req.str("id") } ?: throw IOException("Unknown scene")
        scene.entries.forEach { e -> container.devices.get(e.deviceId)?.applyScene(e.state) }
        return "{}".toByteArray()
    }

    /** Switches a group's controllers on or off; with no "on" given, just reports whether any are on. */
    private suspend fun group(container: AppContainer, req: JsonObject): ByteArray {
        val group = container.store.groups.first().firstOrNull { it.id == req.str("id") } ?: throw IOException("Unknown group")
        val members = group.deviceIds.mapNotNull { container.devices.get(it) }
        req["on"]?.jsonPrimitive?.booleanOrNull?.let { on -> members.forEach { it.setPower(on) } }
        val anyOn = members.any { it.snapshot.value.state?.on == true }
        return buildJsonObject { put("on", anyOn) }.toString().toByteArray()
    }

    /**
     * The whole-house effect: "action" play / stop / set (anything else just reports), with
     * optional "effect" (by name), "speed", "size" and "count". Answers with what's running.
     */
    private fun house(container: AppContainer, req: JsonObject): ByteArray {
        val house = container.house
        var p = house.params.value
        req.str("effect")?.let { name -> HouseEffect.entries.firstOrNull { it.name == name }?.let { p = p.copy(effect = it) } }
        req["speed"]?.jsonPrimitive?.floatOrNull?.let { p = p.copy(speed = it.coerceIn(0f, 1f)) }
        req["size"]?.jsonPrimitive?.floatOrNull?.let { p = p.copy(size = it.coerceIn(0f, 1f)) }
        req["count"]?.jsonPrimitive?.intOrNull?.let { p = p.copy(count = it.coerceIn(1, 8)) }
        house.params.value = p
        when (req.str("action")) {
            "play" -> if (!house.running.value) {
                // The streaming service; if Android won't start it from here, stream straight from the app.
                runCatching { HouseEffectService.start(this) }.onFailure { house.start() }
            }
            "stop" -> { runCatching { HouseEffectService.stop(this) }; house.stop() }
        }
        return buildJsonObject {
            put("running", house.running.value || req.str("action") == "play")
            put("effect", p.effect.name)
            put("speed", p.speed); put("size", p.size); put("count", p.count)
        }.toString().toByteArray()
    }
}
