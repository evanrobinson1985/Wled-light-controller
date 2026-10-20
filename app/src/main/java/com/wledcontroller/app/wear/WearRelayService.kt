package com.wledcontroller.app.wear

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.WearableListenerService
import com.wledcontroller.app.WledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Passes the watch's requests on to the controllers when the watch can't reach them itself
 * (it's on Bluetooth to the phone, or the controller is a simulated one): the request names the
 * controller, the API path and an optional JSON body; the controller's answer goes back.
 */
class WearRelayService : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path != WearBridge.HTTP_PATH) return null
        val container = (application as WledApp).container
        val result = TaskCompletionSource<ByteArray>()
        container.scope.launch(Dispatchers.IO) {
            try {
                val req = Json.parseToJsonElement(String(request)) as JsonObject
                val id = req["id"]?.jsonPrimitive?.content
                // The address the phone reaches it at right now (home, VPN …).
                val host = id?.let { container.devices.get(it)?.apiHost } ?: req["host"]!!.jsonPrimitive.content
                val apiPath = req["path"]!!.jsonPrimitive.content
                val body = req["body"]?.jsonPrimitive?.content
                val http = Request.Builder().url(container.api.baseUrl(host) + apiPath).apply {
                    if (body != null) post(body.toRequestBody("application/json".toMediaType()))
                }.build()
                container.api.client.newCall(http).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    result.setResult(resp.body?.bytes() ?: ByteArray(0))
                }
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result.task
    }
}
