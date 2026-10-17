package com.wledcontroller.app.show

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Sequences are stored one JSON file each, so editing one doesn't rewrite the others. */
class SequenceRepository(context: Context, scope: CoroutineScope) {
    private val dir = File(context.filesDir, "sequences").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _all = MutableStateFlow<List<Sequence>>(emptyList())
    val all: StateFlow<List<Sequence>> = _all.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _all.value = dir.listFiles { f -> f.extension == "json" }
                ?.mapNotNull { f -> runCatching { json.decodeFromString<Sequence>(f.readText()) }.getOrNull() }
                ?.sortedByDescending { it.modifiedAt }
                ?: emptyList()
        }
    }

    fun get(id: String): Sequence? = _all.value.firstOrNull { it.id == id }

    suspend fun save(seq: Sequence) {
        val stamped = seq.copy(modifiedAt = System.currentTimeMillis())
        _all.value = (listOf(stamped) + _all.value.filterNot { it.id == seq.id })
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "${seq.id}.tmp")
            tmp.writeText(json.encodeToString(Sequence.serializer(), stamped))
            tmp.renameTo(File(dir, "${seq.id}.json"))
        }
    }

    suspend fun delete(id: String) {
        _all.value = _all.value.filterNot { it.id == id }
        withContext(Dispatchers.IO) { File(dir, "$id.json").delete() }
    }
}
