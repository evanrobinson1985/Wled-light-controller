package com.wledcontroller.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedDevice(val id: String, val name: String, val host: String)

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

    suspend fun setTileDevice(id: String) {
        context.dataStore.edit { it[Keys.TILE_DEVICE] = id }
    }
}
