package com.wledcontroller.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps a copy of everything in the phone's Downloads folder (Download/WLED Controller), saved
 * whenever the app goes into the background after something changed. Files there stay when
 * the app is uninstalled, so a reinstall (or a new phone) can be restored from it with Restore.
 * Normal updates keep everything anyway; this covers uninstalling, clearing the app's storage
 * and moving phones.
 */
class AutoBackup(private val context: Context, private val store: AppStore, private val backup: Backup) {

    private val prefs = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ON, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lastSaved = MutableStateFlow(prefs.getLong(KEY_TIME, 0L).takeIf { it > 0 })
    /** When the copy was last written, or null if never. */
    val lastSaved: StateFlow<Long?> = _lastSaved.asStateFlow()

    /** Copies can only go to Downloads without asking for storage access on Android 10 and later. */
    val supported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ON, on).apply()
        _enabled.value = on
    }

    /** What's in the app now, cheaply: the settings and every data file's size and age. */
    private suspend fun fingerprint(): String {
        val files = context.filesDir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.startsWith("sim_") && "devcache" !in it.path }
            .joinToString("|") { "${it.path}:${it.length()}:${it.lastModified()}" }
        return (store.exportAll().toString() + files).hashCode().toString()
    }

    /**
     * Writes the copy if anything changed since the last one ([force] writes it anyway). Not
     * while the simulator is on: the real setup is put away then, and nothing in it changes.
     */
    suspend fun saveIfChanged(simulatorOn: Boolean, force: Boolean = false): Boolean = mutex.withLock {
        if (!supported || (!_enabled.value && !force) || simulatorOn) return@withLock false
        withContext(Dispatchers.IO) {
            val print = fingerprint()
            if (!force && print == prefs.getString(KEY_PRINT, null)) return@withContext false
            // Zipped in app storage first, so a failure never leaves a half-written copy.
            val tmp = File(context.cacheDir, "autosave.zip")
            backup.export(tmp)
            val uri = target() ?: return@withContext false
            context.contentResolver.openOutputStream(uri, "wt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: return@withContext false
            tmp.delete()
            val now = System.currentTimeMillis()
            prefs.edit().putString(KEY_PRINT, print).putLong(KEY_TIME, now).apply()
            _lastSaved.value = now
            true
        }
    }

    /** The copy in Downloads: the one this install made, or a new one if that's gone. */
    private fun target(): Uri? {
        val resolver = context.contentResolver
        prefs.getString(KEY_URI, null)?.let(Uri::parse)?.let { uri ->
            val exists = runCatching { resolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
            if (exists) return uri
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
        return uri
    }

    companion object {
        const val FOLDER = "WLED Controller"
        const val FILE_NAME = "WLED-Controller-autosave.zip"
        private const val KEY_ON = "on"
        private const val KEY_TIME = "saved_at"
        private const val KEY_PRINT = "fingerprint"
        private const val KEY_URI = "uri"
    }
}
