package com.wledcontroller.app.data

import android.content.Context
import android.net.Uri
import com.wledcontroller.app.show.FseqReader
import com.wledcontroller.app.show.SequenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(val devices: Int, val shows: Int, val files: Int, val bytes: Long)

/**
 * Saves everything — settings, controllers, house map and photo, shows, songs, xLights
 * sequences and standalone-mode backups — into one zip, and restores it (on this phone or
 * another). File paths inside the data are rewritten to the restoring phone's storage.
 */
class Backup(private val context: Context, private val store: AppStore, private val sequences: SequenceRepository) {

    private val filesDir get() = context.filesDir
    private val json = Json { ignoreUnknownKeys = true }

    private fun looseFiles() = filesDir.listFiles { f -> f.isFile && (f.name.startsWith("house_") || f.name.startsWith("standalone_backup_")) }
        ?.toList() ?: emptyList()

    /** Everything in app storage that belongs in a backup. */
    private fun backupFiles(): List<File> {
        val dirs = DIRS.map { File(filesDir, it) }
        return dirs.flatMap { d -> d.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.toList() } + looseFiles()
    }

    suspend fun export(uri: Uri): BackupSummary =
        export(context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Couldn't write the backup file"))

    suspend fun export(file: File): BackupSummary = export(file.outputStream())

    suspend fun restore(uri: Uri): BackupSummary =
        restore(context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Couldn't open that file"))

    suspend fun restore(file: File): BackupSummary = restore(file.inputStream())

    /** Removes all app data: controllers, settings, house map, shows and their files. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        DIRS.forEach { File(filesDir, it).deleteRecursively() }
        looseFiles().forEach { it.delete() }
        store.importAll(JsonObject(emptyMap())) { it }
        FseqReader.forgetAll()
        sequences.reload()
    }

    private suspend fun export(stream: java.io.OutputStream): BackupSummary = withContext(Dispatchers.IO) {
        val prefs = store.exportAll()
        val files = backupFiles()
        val manifest = buildJsonObject {
            put("format", 1)
            put("app", "wled-controller")
            put("createdAt", System.currentTimeMillis())
            put("filesDir", filesDir.absolutePath)
        }
        var bytes = 0L
        stream.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("settings.json")); zip.write(prefs.toString().toByteArray()); zip.closeEntry()
                for (f in files) {
                    zip.putNextEntry(ZipEntry("files/" + f.relativeTo(filesDir).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    bytes += f.length()
                }
            }
        }
        val deviceCount = store.devices.first().size
        BackupSummary(deviceCount, sequences.all.value.size, files.size, bytes)
    }

    private suspend fun restore(stream: java.io.InputStream): BackupSummary = withContext(Dispatchers.IO) {
        // Unpack to a staging folder first, so a bad file can't leave the app half-restored.
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var manifest: JsonObject? = null
        var settings: JsonObject? = null
        var fileCount = 0
        var bytes = 0L
        stream.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.isDirectory -> Unit
                        e.name == "manifest.json" -> manifest = json.parseToJsonElement(zip.readBytes().decodeToString()) as? JsonObject
                        e.name == "settings.json" -> settings = json.parseToJsonElement(zip.readBytes().decodeToString()) as? JsonObject
                        e.name.startsWith("files/") -> {
                            val target = File(staging, e.name.removePrefix("files/"))
                            // Refuse entries that would escape the staging folder.
                            if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) throw SecurityException("Bad backup entry")
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            fileCount++
                            bytes += target.length()
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        val m = manifest ?: throw IllegalArgumentException("That isn't a WLED Controller backup")
        if (m.str("app") != "wled-controller") throw IllegalArgumentException("That isn't a WLED Controller backup")
        if ((m["format"]?.jsonPrimitive?.int ?: 0) > 1) throw IllegalArgumentException("This backup is from a newer version of the app")
        val oldDir = m.str("filesDir") ?: filesDir.absolutePath
        val newDir = filesDir.absolutePath
        fun fixPath(s: String) = if (oldDir == newDir) s else s.replace(oldDir, newDir)

        // Swap in the restored files.
        DIRS.forEach { File(filesDir, it).deleteRecursively() }
        looseFiles().forEach { it.delete() }
        staging.walkTopDown().filter { it.isFile }.forEach { f ->
            val dest = File(filesDir, f.relativeTo(staging).path)
            dest.parentFile?.mkdirs()
            if (f.parentFile?.name == "sequences" && f.extension == "json") dest.writeText(fixPath(f.readText()))
            else f.copyTo(dest, overwrite = true)
        }
        staging.deleteRecursively()
        store.importAll(settings ?: JsonObject(emptyMap()), ::fixPath)
        FseqReader.forgetAll()
        sequences.reload()
        BackupSummary(store.devices.first().size, sequences.all.value.size, fileCount, bytes)
    }

    private companion object {
        /** Folders of app data; "simulator" holds the simulated controllers. */
        val DIRS = listOf("sequences", "audio", "fseq", "simulator")
    }
}
