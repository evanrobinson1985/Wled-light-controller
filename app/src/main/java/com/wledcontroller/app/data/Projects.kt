package com.wledcontroller.app.data

import android.content.Context
import com.wledcontroller.app.house.HouseEffectService
import com.wledcontroller.app.house.HouseEngine
import com.wledcontroller.app.show.SequencePlayer
import com.wledcontroller.app.show.ShowRunnerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class ProjectInfo(val id: String, val name: String, val createdAt: Long, val note: String = "")

/**
 * Separate projects: each its own house photo and map, props, groups, shows, songs, pictures
 * and settings. One is open at a time; switching keeps the open one aside (as a backup of it)
 * and brings the other back. Your controllers are there in every project, and the saved
 * layouts and light models are shared.
 */
class Projects(
    private val context: Context,
    private val store: AppStore,
    private val backup: Backup,
    private val house: HouseEngine,
    private val player: SequencePlayer,
    private val devices: DeviceManager,
) {
    private val root get() = File(context.filesDir, "projects").apply { mkdirs() }
    private val indexFile get() = File(root, "projects.json")
    private val activeFile get() = File(root, "active")
    private fun snapshot(id: String) = File(File(root, id).apply { mkdirs() }, "setup.zip")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<ProjectInfo>> = _list.asStateFlow()
    private val _active = MutableStateFlow(activeFile.takeIf { it.exists() }?.readText()?.trim() ?: MAIN)
    /** The project that's open. */
    val active: StateFlow<String> = _active.asStateFlow()

    private fun load(): List<ProjectInfo> {
        val saved = runCatching { json.decodeFromString(ListSerializer(ProjectInfo.serializer()), indexFile.readText()) }.getOrNull()
        // The setup the app had before projects is the first one.
        return saved?.takeIf { it.isNotEmpty() } ?: listOf(ProjectInfo(MAIN, "My house", 0L))
    }

    private fun saveIndex(list: List<ProjectInfo>) {
        _list.value = list
        indexFile.writeText(json.encodeToString(ListSerializer(ProjectInfo.serializer()), list))
    }

    val activeName get() = _list.value.firstOrNull { it.id == _active.value }?.name ?: "My house"

    private fun stopEverything() {
        ShowRunnerService.stop(context)
        HouseEffectService.stop(context)
        player.stopAll()
        house.stop()
    }

    /** Opens project [id], keeping the open one as it is for later. */
    suspend fun switchTo(id: String) = mutex.withLock {
        if (id == _active.value || _list.value.none { it.id == id }) return@withLock
        stopEverything()
        withContext(Dispatchers.IO) {
            backup.export(snapshot(_active.value), withLibrary = false)
            val target = snapshot(id)
            if (target.exists() && target.length() > 0) backup.restore(target) else backup.clearAll()
            setActive(id)
        }
        afterSwitch()
    }

    /**
     * Starts a new, empty project called [name] (the open one kept as it is), opens it, and
     * fills it with [fill]. If filling fails, the new project is thrown away and the previous
     * one comes back.
     */
    suspend fun create(name: String, note: String = "", fill: suspend () -> Unit = {}): ProjectInfo = mutex.withLock {
        stopEverything()
        val previous = _active.value
        val info = ProjectInfo(UUID.randomUUID().toString(), name.ifBlank { "New project" }, System.currentTimeMillis(), note)
        withContext(Dispatchers.IO) {
            backup.export(snapshot(previous), withLibrary = false)
            backup.clearAll()
            saveIndex(_list.value + info)
            setActive(info.id)
        }
        afterSwitch()
        try {
            fill()
        } catch (e: Throwable) {
            withContext(Dispatchers.IO) {
                backup.restore(snapshot(previous))
                setActive(previous)
                saveIndex(_list.value.filterNot { it.id == info.id })
                File(root, info.id).deleteRecursively()
            }
            afterSwitch()
            throw e
        }
        info
    }

    fun rename(id: String, name: String) {
        saveIndex(_list.value.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it })
    }

    /** Deletes a project that isn't open. */
    fun delete(id: String) {
        if (id == _active.value) return
        saveIndex(_list.value.filterNot { it.id == id })
        File(root, id).deleteRecursively()
    }

    private fun setActive(id: String) {
        activeFile.writeText(id)
        _active.value = id
    }

    private suspend fun afterSwitch() {
        // Your controllers belong in every project.
        store.restoreRememberedDevices()
        devices.list.value.forEach { it.reconnect() }
    }

    companion object {
        const val MAIN = "main"
    }
}
