package com.wledcontroller.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocketFactory

/**
 * A broker both the app and the controllers connect to, for control from anywhere. The
 * controllers can't use TLS, so they use [wledPort] (plain); the app can use [port] with [tls].
 */
@Serializable
data class MqttSettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 8883,
    val tls: Boolean = true,
    val wledPort: Int = 1883,
    val user: String = "",
    val password: String = "",
    /** Start of every controller's topic; random, so strangers on a shared broker can't guess it. */
    val prefix: String = "wled-" + UUID.randomUUID().toString().take(10),
) {
    val usable get() = enabled && host.isNotBlank()
    fun topicFor(name: String) = prefix + "/" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "lights" }
    val groupTopic get() = "$prefix/all"
}

/**
 * The app's one connection to the MQTT broker, shared by every controller that's reached that
 * way. WLED takes JSON API commands on "[topic]/api" and reports its state on "[topic]/v" and
 * whether it's online on "[topic]/status".
 */
class MqttLink {
    @Volatile var settings: MqttSettings = MqttSettings()
        private set
    private var client: MqttClient? = null
    private val listeners = ConcurrentHashMap<String, (sub: String, payload: String) -> Unit>()

    val ready get() = settings.usable

    @Synchronized
    fun update(s: MqttSettings) {
        if (s == settings) return
        settings = s
        disconnect()
    }

    @Synchronized
    private fun disconnect() {
        val c = client ?: return
        client = null
        runCatching { if (c.isConnected) c.disconnect(1000) }
        runCatching { c.close(true) }
    }

    private fun serverUri(s: MqttSettings) = (if (s.tls) "ssl://" else "tcp://") + s.host.trim() + ":" + s.port

    private fun options(s: MqttSettings) = MqttConnectOptions().apply {
        isCleanSession = true
        isAutomaticReconnect = true
        keepAliveInterval = 30
        connectionTimeout = 10
        if (s.user.isNotBlank()) userName = s.user
        if (s.password.isNotEmpty()) password = s.password.toCharArray()
        if (s.tls) socketFactory = SSLSocketFactory.getDefault()
    }

    /** Connects if needed; blocking, so call off the main thread. */
    @Synchronized
    private fun connected(): MqttClient {
        client?.takeIf { it.isConnected }?.let { return it }
        val s = settings
        check(s.usable) { "Remote control over MQTT isn't set up." }
        disconnect()
        val c = MqttClient(serverUri(s), "wled-app-" + UUID.randomUUID().toString().take(8), MemoryPersistence())
        c.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                if (reconnect) listeners.keys.forEach { t -> runCatching { c.subscribe(arrayOf("$t/v", "$t/status"), intArrayOf(0, 0)) } }
            }
            override fun connectionLost(cause: Throwable?) = Unit
            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            override fun messageArrived(topic: String, message: MqttMessage) {
                val payload = String(message.payload)
                for ((t, l) in listeners) if (topic.startsWith("$t/")) l(topic.removePrefix("$t/"), payload)
            }
        })
        c.connect(options(s))
        client = c
        return c
    }

    val isConnected get() = client?.isConnected == true

    /** Follows a controller's [topic]: [listener] gets its "v" (state) and "status" messages. */
    suspend fun follow(topic: String, listener: (sub: String, payload: String) -> Unit) = withContext(Dispatchers.IO) {
        listeners[topic] = listener
        connected().subscribe(arrayOf("$topic/v", "$topic/status"), intArrayOf(0, 0))
    }

    suspend fun unfollow(topic: String) = withContext(Dispatchers.IO) {
        listeners.remove(topic)
        runCatching { client?.takeIf { it.isConnected }?.unsubscribe(arrayOf("$topic/v", "$topic/status")) }
        Unit
    }

    /** Sends a JSON API command to the controller on [topic]. */
    suspend fun command(topic: String, json: String) = withContext(Dispatchers.IO) {
        connected().publish("$topic/api", json.toByteArray(), 0, false)
    }

    companion object {
        /** Tries [s] out: connects and disconnects. Throws with the broker's reason if it can't. */
        suspend fun test(s: MqttSettings) = withContext(Dispatchers.IO) {
            val link = MqttLink()
            val c = MqttClient(link.serverUri(s), "wled-app-test-" + UUID.randomUUID().toString().take(6), MemoryPersistence())
            try {
                c.connect(link.options(s).apply { isAutomaticReconnect = false })
                c.disconnect(1000)
            } finally {
                runCatching { c.close(true) }
            }
        }
    }
}
