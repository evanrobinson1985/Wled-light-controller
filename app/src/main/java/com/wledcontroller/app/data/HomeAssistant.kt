package com.wledcontroller.app.data

import android.content.Context
import com.wledcontroller.app.show.RunnerState
import com.wledcontroller.app.show.ShowControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The app's shows in Home Assistant, through MQTT discovery: a "Light show" switch, a choice of
 * what plays, "Next song" and "Stop" buttons and a "Now playing" sensor. Home Assistant can
 * then pass them on to Google Home or Alexa ("Hey Google, turn on the light show"). The
 * controllers themselves already show up in Home Assistant through its WLED integration.
 */
object HomeAssistant {
    const val OFF = "Off"
    const val SCHEDULE = "Schedule"
    const val JUKEBOX = "Jukebox"
    private const val PLAY = "Play: "

    fun base(prefix: String) = "$prefix/app"
    fun stateTopic(prefix: String) = "${base(prefix)}/state"
    fun statusTopic(prefix: String) = "${base(prefix)}/status"
    fun commandTopic(prefix: String, what: String) = "${base(prefix)}/set/$what"

    /** The choices for what plays. */
    fun options(playlists: List<String>) = listOf(OFF, SCHEDULE, JUKEBOX) + playlists.map { PLAY + it }

    /** The choice that matches what's happening now. */
    fun modeOf(s: RunnerState): String = when {
        !s.running -> OFF
        s.mode == "schedule" -> SCHEDULE
        s.mode == "jukebox" -> JUKEBOX
        s.playlistName != null -> PLAY + s.playlistName
        else -> OFF
    }

    fun stateJson(s: RunnerState): String = buildJsonObject {
        put("power", if (s.running) "ON" else "OFF")
        put("mode", modeOf(s))
        put("now", s.nowPlaying ?: if (s.running) (s.weatherHold?.let { "On hold: $it" } ?: "Waiting") else "Off")
    }.toString()

    /** What a message on a command topic asks for, as a [ShowControl] command and argument. */
    fun command(prefix: String, topic: String, payload: String): Pair<String, String?>? {
        val what = topic.removePrefix(commandTopic(prefix, "")).takeIf { it != topic } ?: return null
        val p = payload.trim()
        return when (what) {
            "power" -> if (p.equals("ON", true)) "play" to null else "stop" to null
            "skip" -> "skip" to null
            "stop" -> "stop" to null
            "mode" -> when {
                p == OFF -> "stop" to null
                p == SCHEDULE -> "schedule" to null
                p == JUKEBOX -> "jukebox" to null
                p.startsWith(PLAY) -> "play" to p.removePrefix(PLAY)
                else -> null
            }
            else -> null
        }
    }

    /** The discovery messages (topic to retained payload) that make the entities appear. */
    fun discovery(prefix: String, playlists: List<String>): List<Pair<String, String>> {
        val id = prefix.replace(Regex("[^A-Za-z0-9_-]"), "_")
        fun config(component: String, key: String, name: String, icon: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
            "homeassistant/$component/${id}_app/$key/config" to buildJsonObject {
                put("name", name)
                put("unique_id", "${id}_app_$key")
                put("object_id", "light_show_$key")
                put("icon", icon)
                put("availability_topic", statusTopic(prefix))
                putJsonObject("device") {
                    put("identifiers", JsonArray(listOf(JsonPrimitive("${id}_app"))))
                    put("name", "Light show")
                    put("manufacturer", "WLED Controller app")
                    put("model", "Show player")
                }
                extra()
            }.toString()
        return listOf(
            config("switch", "power", "Light show", "mdi:string-lights") {
                put("command_topic", commandTopic(prefix, "power")); put("state_topic", stateTopic(prefix))
                put("value_template", "{{ value_json.power }}")
            },
            config("select", "mode", "Show", "mdi:playlist-music") {
                put("command_topic", commandTopic(prefix, "mode")); put("state_topic", stateTopic(prefix))
                put("value_template", "{{ value_json.mode }}")
                putJsonArray("options") { options(playlists).forEach { add(JsonPrimitive(it)) } }
            },
            config("button", "skip", "Next song", "mdi:skip-next") { put("command_topic", commandTopic(prefix, "skip")) },
            config("button", "stop", "Stop show", "mdi:stop") { put("command_topic", commandTopic(prefix, "stop")) },
            config("sensor", "now", "Now playing", "mdi:music") {
                put("state_topic", stateTopic(prefix)); put("value_template", "{{ value_json.now }}")
            },
        )
    }
}

/** Keeps the Home Assistant entities up to date and carries out what they're asked to do. */
class HomeAssistantBridge(
    private val context: Context,
    private val mqtt: MqttLink,
    private val playlists: Flow<List<String>>,
    private val state: Flow<RunnerState>,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** The last-will message to give the broker, so Home Assistant shows the show as unavailable when the app goes. */
    fun will(s: MqttSettings): Pair<String, String>? = if (s.usable && s.homeAssistant) HomeAssistant.statusTopic(s.prefix) to "offline" else null

    @Synchronized
    fun apply(s: MqttSettings) {
        job?.cancel()
        job = null
        if (!s.usable || !s.homeAssistant) return
        val prefix = s.prefix
        job = scope.launch(Dispatchers.IO) {
            // Retry until the broker answers.
            while (true) {
                val ok = runCatching {
                    mqtt.subscribeAll(HomeAssistant.commandTopic(prefix, "")) { topic, payload ->
                        HomeAssistant.command(prefix, topic, payload)?.let { (cmd, arg) ->
                            scope.launch { ShowControl.perform(context, cmd, arg) }
                        }
                    }
                    mqtt.publish(HomeAssistant.statusTopic(prefix), "online", retained = true)
                }.isSuccess
                if (ok) break
                delay(30_000)
            }
            launch {
                playlists.distinctUntilChanged().collect { names ->
                    HomeAssistant.discovery(prefix, names).forEach { (t, p) -> runCatching { mqtt.publish(t, p, retained = true) } }
                }
            }
            state.map(HomeAssistant::stateJson).distinctUntilChanged().collect { json ->
                runCatching { mqtt.publish(HomeAssistant.stateTopic(prefix), json, retained = true) }
            }
        }
    }

    companion object {
        fun playlistNames(store: AppStore): Flow<List<String>> =
            store.playlists.map { l -> l.filter { it.sequenceIds.isNotEmpty() }.map { it.name } }
    }
}
