package com.wledcontroller.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * When the app closes unexpectedly, what went wrong is kept, so it can be shared the next time
 * the app opens (and the problem fixed instead of guessed at).
 */
object CrashReport {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                file(app).writeText(
                    "WLED Controller $version on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
                        "Thread: ${thread.name}\n" +
                        "Memory: ${Runtime.getRuntime().totalMemory() / 1_000_000} MB used of ${Runtime.getRuntime().maxMemory() / 1_000_000} MB\n\n" +
                        trace.take(20_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report from the last time the app closed unexpectedly, if there's one not yet seen. */
    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) { file(context).delete() }
}
