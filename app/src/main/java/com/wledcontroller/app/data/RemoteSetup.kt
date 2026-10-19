package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/** Setting controllers up to be reached through the MQTT broker. */
object RemoteSetup {

    /** A topic of its own for [device] under the broker settings' prefix. */
    fun topicFor(s: MqttSettings, device: SavedDevice) = s.topicFor(device.name + "-" + device.id.filter(Char::isLetterOrDigit).take(4))

    /** WLED's MQTT settings (as in its cfg.json) pointing it at the broker on [topic]. */
    fun wledConfig(s: MqttSettings, topic: String): JsonObject = buildJsonObject {
        putJsonObject("if") {
            putJsonObject("mqtt") {
                put("en", true)
                put("broker", s.host.trim())
                put("port", s.wledPort)
                put("user", s.user)
                put("psk", s.password)
                put("cid", "WLED-" + topic.substringAfterLast('/').take(24) + "-" + UUID.randomUUID().toString().take(4))
                // Retained, so the app sees its state as soon as it connects.
                put("rtn", true)
                putJsonObject("topics") {
                    put("device", topic)
                    put("group", s.groupTopic)
                }
            }
        }
    }

    /** Sends the broker settings to the controller at [host] (at home) and restarts it so they take effect. */
    suspend fun configure(api: WledApi, host: String, s: MqttSettings, topic: String) {
        api.postConfig(host, wledConfig(s, topic))
        runCatching { api.postState(host, buildJsonObject { put("rb", true) }) }
    }
}
