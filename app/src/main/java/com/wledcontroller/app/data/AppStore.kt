package com.wledcontroller.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.show.Playlist
import com.wledcontroller.app.show.ScheduleEntry

@Serializable
data class SavedDevice(
    val id: String,
    val name: String,
    val host: String,
    /** Another address for it when the home Wi-Fi can't reach it: a VPN / Tailscale address or a forwarded hostname. */
    val remoteHost: String? = null,
    /** Its MQTT topic, for control through the broker from anywhere. */
    val mqttTopic: String? = null,
)

@Serializable
data class DeviceGroup(val id: String, val name: String, val deviceIds: List<String>)

@Serializable
data class SceneEntry(val deviceId: String, val state: String)

@Serializable
data class Scene(val id: String, val name: String, val entries: List<SceneEntry>, val color: Int = 0)

private val Context.dataStore by preferencesDataStore(name = "wled_store")

/** Everything the app persists: devices, groups, scenes and personal favourites. */
class AppStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val DEVICES = stringPreferencesKey("devices")
        val GROUPS = stringPreferencesKey("groups")
        val SCENES = stringPreferencesKey("scenes")
        val FAVORITE_COLORS = stringPreferencesKey("favorite_colors")
        val RECENT_COLORS = stringPreferencesKey("recent_colors")
        val FAVORITE_EFFECTS = stringSetPreferencesKey("favorite_effects")
        val TILE_DEVICE = stringPreferencesKey("tile_device")
        val LIGHT_PROFILES = stringPreferencesKey("light_profiles")
        val HOUSE_MAP = stringPreferencesKey("house_map")
        val PLAYLISTS = stringPreferencesKey("playlists")
        val SCHEDULES = stringPreferencesKey("schedules")
        val SCHEDULE_ON = androidx.datastore.preferences.core.booleanPreferencesKey("schedule_on")
        val LIGHTS_DELAY = androidx.datastore.preferences.core.intPreferencesKey("lights_delay_ms")
        val PIXEL_SIZE = androidx.datastore.preferences.core.floatPreferencesKey("pixel_size_dp")
        val LOCATION = stringPreferencesKey("location")
        val OUTPUTS = stringPreferencesKey("outputs")
        val FIXTURES = stringPreferencesKey("dmx_fixtures")
        val MQTT = stringPreferencesKey("mqtt")
        val EFFECT_SETTINGS = stringPreferencesKey("effect_settings")
        val KEEP_STARTUP = androidx.datastore.preferences.core.booleanPreferencesKey("keep_startup_look")
        val HOUSE_EFFECT = stringPreferencesKey("house_effect")
    }

    private inline fun <reified T> listFlow(key: Preferences.Key<String>): Flow<List<T>> =
        context.dataStore.data.map { prefs ->
            prefs[key]?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrNull() } ?: emptyList()
        }

    private suspend inline fun <reified T> editList(key: Preferences.Key<String>, crossinline transform: (List<T>) -> List<T>) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrNull() } ?: emptyList()
            prefs[key] = json.encodeToString(transform(current))
        }
    }

    val devices: Flow<List<SavedDevice>> = listFlow(Keys.DEVICES)
    val groups: Flow<List<DeviceGroup>> = listFlow(Keys.GROUPS)
    val scenes: Flow<List<Scene>> = listFlow(Keys.SCENES)
    val favoriteColors: Flow<List<Int>> = listFlow(Keys.FAVORITE_COLORS)
    val recentColors: Flow<List<Int>> = listFlow(Keys.RECENT_COLORS)
    val favoriteEffects: Flow<Set<String>> = context.dataStore.data.map { it[Keys.FAVORITE_EFFECTS] ?: emptySet() }
    val tileDeviceId: Flow<String?> = context.dataStore.data.map { it[Keys.TILE_DEVICE] }

    suspend fun upsertDevice(device: SavedDevice) = editList<SavedDevice>(Keys.DEVICES) { list ->
        if (list.any { it.id == device.id }) list.map { if (it.id == device.id) device else it } else list + device
    }

    suspend fun removeDevice(id: String) {
        editList<SavedDevice>(Keys.DEVICES) { list -> list.filterNot { it.id == id } }
        editList<DeviceGroup>(Keys.GROUPS) { list -> list.map { g -> g.copy(deviceIds = g.deviceIds - id) } }
    }

    suspend fun upsertGroup(group: DeviceGroup) = editList<DeviceGroup>(Keys.GROUPS) { list ->
        if (list.any { it.id == group.id }) list.map { if (it.id == group.id) group else it } else list + group
    }

    suspend fun removeGroup(id: String) = editList<DeviceGroup>(Keys.GROUPS) { list -> list.filterNot { it.id == id } }

    suspend fun addScene(scene: Scene) = editList<Scene>(Keys.SCENES) { it + scene }

    suspend fun removeScene(id: String) = editList<Scene>(Keys.SCENES) { list -> list.filterNot { it.id == id } }

    suspend fun toggleFavoriteColor(rgb: Int) = editList<Int>(Keys.FAVORITE_COLORS) { list ->
        if (rgb in list) list - rgb else (list + rgb).takeLast(24)
    }

    suspend fun addRecentColor(rgb: Int) = editList<Int>(Keys.RECENT_COLORS) { list ->
        (listOf(rgb) + (list - rgb)).take(12)
    }

    suspend fun toggleFavoriteEffect(name: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.FAVORITE_EFFECTS] ?: emptySet()
            prefs[Keys.FAVORITE_EFFECTS] = if (name in cur) cur - name else cur + name
        }
    }

    /** Light type per device ("deviceId") or per segment ("deviceId#segId"). */
    val lightProfiles: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.LIGHT_PROFILES]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()
    }

    suspend fun setLightProfile(key: String, profile: LightProfile?) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.LIGHT_PROFILES]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()
            val next = if (profile == null) cur - key else cur + (key to profile.name)
            prefs[Keys.LIGHT_PROFILES] = json.encodeToString(next)
        }
    }

    val houseMap: Flow<HouseMap> = context.dataStore.data.map { prefs ->
        prefs[Keys.HOUSE_MAP]?.let { runCatching { json.decodeFromString<HouseMap>(it) }.getOrNull() } ?: HouseMap()
    }

    suspend fun updateHouseMap(transform: (HouseMap) -> HouseMap) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.HOUSE_MAP]?.let { runCatching { json.decodeFromString<HouseMap>(it) }.getOrNull() } ?: HouseMap()
            prefs[Keys.HOUSE_MAP] = json.encodeToString(transform(cur))
        }
    }

    val playlists: Flow<List<Playlist>> = listFlow(Keys.PLAYLISTS)
    val schedules: Flow<List<ScheduleEntry>> = listFlow(Keys.SCHEDULES)
    val scheduleOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCHEDULE_ON] ?: false }

    suspend fun upsertPlaylist(p: Playlist) = editList<Playlist>(Keys.PLAYLISTS) { list ->
        if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p
    }
    suspend fun removePlaylist(id: String) {
        editList<Playlist>(Keys.PLAYLISTS) { list -> list.filterNot { it.id == id } }
        editList<ScheduleEntry>(Keys.SCHEDULES) { list -> list.filterNot { it.playlistId == id } }
    }
    suspend fun upsertSchedule(e: ScheduleEntry) = editList<ScheduleEntry>(Keys.SCHEDULES) { list ->
        if (list.any { it.id == e.id }) list.map { if (it.id == e.id) e else it } else list + e
    }
    suspend fun removeSchedule(id: String) = editList<ScheduleEntry>(Keys.SCHEDULES) { list -> list.filterNot { it.id == id } }
    suspend fun setScheduleOn(on: Boolean) { context.dataStore.edit { it[Keys.SCHEDULE_ON] = on } }

    val lightsDelayMs: Flow<Int> = context.dataStore.data.map { it[Keys.LIGHTS_DELAY] ?: 0 }
    suspend fun setLightsDelay(ms: Int) { context.dataStore.edit { it[Keys.LIGHTS_DELAY] = ms } }

    /** Non-WLED pixel controllers that shows and whole-house effects stream to. */
    val outputs: Flow<List<PixelOutput>> = listFlow(Keys.OUTPUTS)
    suspend fun upsertOutput(o: PixelOutput) = editList<PixelOutput>(Keys.OUTPUTS) { list ->
        if (list.any { it.id == o.id }) list.map { if (it.id == o.id) o else it } else list + o
    }
    suspend fun removeOutput(id: String) = editList<PixelOutput>(Keys.OUTPUTS) { list -> list.filterNot { it.id == id } }

    /** DMX lights (pars, washes, moving heads) on E1.31 / Art-Net outputs. */
    val fixtures: Flow<List<DmxFixture>> = listFlow(Keys.FIXTURES)
    suspend fun upsertFixture(f: DmxFixture) = editList<DmxFixture>(Keys.FIXTURES) { list ->
        if (list.any { it.id == f.id }) list.map { if (it.id == f.id) f else it } else list + f
    }
    suspend fun removeFixture(id: String) = editList<DmxFixture>(Keys.FIXTURES) { list -> list.filterNot { it.id == id } }

    /** Where to stream each light run's controller: WLED hosts, other controllers and DMX fixtures. */
    suspend fun streamTargets(): Map<String, String> =
        devices.first().associate { it.id to it.host } + outputs.first().associate { it.id to it.address } +
            fixtures.first().associate { it.id to DmxRegistry.address(it.id) }

    // Kept apart from the settings above, which backups and the simulator swap in and out.
    private val flags = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
    private val _tutorialSeen = kotlinx.coroutines.flow.MutableStateFlow(flags.getBoolean("tutorial_seen", false))
    /** Whether the walk-through has been shown (it opens by itself on first launch). */
    val tutorialSeen: kotlinx.coroutines.flow.StateFlow<Boolean> = _tutorialSeen
    fun setTutorialSeen() {
        flags.edit().putBoolean("tutorial_seen", true).apply()
        _tutorialSeen.value = true
    }

    /** Each effect's own settings per controller (key from [EffectMemory.key]). */
    val effectSettings: Flow<Map<String, Map<String, Int>>> = context.dataStore.data.map { prefs ->
        prefs[Keys.EFFECT_SETTINGS]?.let { runCatching { json.decodeFromString<Map<String, Map<String, Int>>>(it) }.getOrNull() } ?: emptyMap()
    }

    suspend fun saveEffectSettings(deviceId: String, fx: Int, values: Map<String, Int>) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.EFFECT_SETTINGS]?.let { runCatching { json.decodeFromString<Map<String, Map<String, Int>>>(it) }.getOrNull() } ?: emptyMap()
            prefs[Keys.EFFECT_SETTINGS] = json.encodeToString(cur + (EffectMemory.key(deviceId, fx) to values))
        }
    }

    /** The whole-house effect and its colours and sliders, as last set. */
    val houseEffect: Flow<com.wledcontroller.app.house.HouseEffectParams?> = context.dataStore.data.map { prefs ->
        prefs[Keys.HOUSE_EFFECT]?.let { runCatching { json.decodeFromString<com.wledcontroller.app.house.HouseEffectParams>(it) }.getOrNull() }
    }
    suspend fun setHouseEffect(p: com.wledcontroller.app.house.HouseEffectParams) {
        context.dataStore.edit { it[Keys.HOUSE_EFFECT] = mqttJson.encodeToString(p) }
    }

    /** Whether controllers start up with whatever was last chosen in the app. */
    val keepStartupLook: Flow<Boolean> = context.dataStore.data.map { it[Keys.KEEP_STARTUP] ?: true }
    suspend fun setKeepStartupLook(on: Boolean) { context.dataStore.edit { it[Keys.KEEP_STARTUP] = on } }

    /** The MQTT broker for remote control, if set up. */
    val mqtt: Flow<MqttSettings?> = context.dataStore.data.map { prefs ->
        prefs[Keys.MQTT]?.let { runCatching { json.decodeFromString<MqttSettings>(it) }.getOrNull() }
    }
    // Defaults written out too: the topic prefix is random, so it must be stored as it is.
    private val mqttJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    suspend fun setMqtt(s: MqttSettings) { context.dataStore.edit { it[Keys.MQTT] = mqttJson.encodeToString(s) } }

    /** Where the show is, for sunset and dusk schedule times. */
    val location: Flow<com.wledcontroller.app.show.GeoPoint?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCATION]?.let { runCatching { json.decodeFromString<com.wledcontroller.app.show.GeoPoint>(it) }.getOrNull() }
    }
    suspend fun setLocation(place: com.wledcontroller.app.show.GeoPoint?) {
        context.dataStore.edit { if (place == null) it.remove(Keys.LOCATION) else it[Keys.LOCATION] = json.encodeToString(place) }
    }

    /** How lights are drawn in previews, the house map and the simulator. */
    val pixelStyle: Flow<com.wledcontroller.app.ui.components.PixelStyle> = context.dataStore.data.map {
        com.wledcontroller.app.ui.components.PixelStyle(
            sizeDp = it[Keys.PIXEL_SIZE] ?: com.wledcontroller.app.ui.components.PixelStyle.DEFAULT_SIZE,
        )
    }
    suspend fun setPixelStyle(style: com.wledcontroller.app.ui.components.PixelStyle) {
        context.dataStore.edit { it[Keys.PIXEL_SIZE] = style.sizeDp }
    }

    /** Every stored setting as JSON (typed), for backups. */
    suspend fun exportAll(): kotlinx.serialization.json.JsonObject {
        val prefs = context.dataStore.data.first()
        return kotlinx.serialization.json.buildJsonObject {
            prefs.asMap().forEach { (k, v) ->
                val typed = when (v) {
                    is String -> kotlinx.serialization.json.buildJsonObject { put("t", "s"); put("v", v) }
                    is Boolean -> kotlinx.serialization.json.buildJsonObject { put("t", "b"); put("v", v) }
                    is Int -> kotlinx.serialization.json.buildJsonObject { put("t", "i"); put("v", v) }
                    is Long -> kotlinx.serialization.json.buildJsonObject { put("t", "l"); put("v", v) }
                    is Float -> kotlinx.serialization.json.buildJsonObject { put("t", "f"); put("v", v) }
                    is Set<*> -> kotlinx.serialization.json.buildJsonObject {
                        put("t", "ss")
                        put("v", kotlinx.serialization.json.JsonArray(v.map { kotlinx.serialization.json.JsonPrimitive(it.toString()) }))
                    }
                    else -> null
                }
                if (typed != null) put(k.name, typed)
            }
        }
    }

    /** Replaces every stored setting with a backup made by [exportAll]. [fixPath] rewrites file paths. */
    suspend fun importAll(data: kotlinx.serialization.json.JsonObject, fixPath: (String) -> String) {
        context.dataStore.edit { prefs ->
            prefs.clear()
            data.forEach { (name, el) ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val v = o["v"] ?: return@forEach
                when (o.str("t")) {
                    "s" -> prefs[stringPreferencesKey(name)] = fixPath(v.asString() ?: "")
                    "b" -> prefs[androidx.datastore.preferences.core.booleanPreferencesKey(name)] = v.asBool()
                    "i" -> prefs[androidx.datastore.preferences.core.intPreferencesKey(name)] = v.asInt()
                    "l" -> prefs[androidx.datastore.preferences.core.longPreferencesKey(name)] = v.asInt().toLong()
                    "f" -> prefs[androidx.datastore.preferences.core.floatPreferencesKey(name)] = (v.asString()?.toFloatOrNull() ?: 0f)
                    "ss" -> prefs[stringSetPreferencesKey(name)] =
                        (v as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.asString() }?.toSet() ?: emptySet()
                }
            }
        }
    }

    suspend fun setTileDevice(id: String) {
        context.dataStore.edit { it[Keys.TILE_DEVICE] = id }
    }
}
