package com.wledcontroller.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Checks every controller every [periodMs] while [showsOn] says lights are being run, and sends
 * a notification when [HealthWatch] finds something (if alerts are on).
 */
class HealthMonitor(
    private val context: Context,
    private val store: AppStore,
    private val api: WledApi,
    private val scope: CoroutineScope,
    private val showsOn: () -> Boolean,
    private val periodMs: Long = 20_000,
) {
    private val prefs = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
    private val watch = HealthWatch()
    private val _enabled = MutableStateFlow(prefs.getBoolean("health_alerts", true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _events = MutableStateFlow<List<HealthWatch.Event>>(emptyList())
    /** What it has found, newest first. */
    val events: StateFlow<List<HealthWatch.Event>> = _events.asStateFlow()
    private var job: Job? = null

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("health_alerts", on).apply()
        _enabled.value = on
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var wasOn = false
            while (isActive) {
                val on = showsOn() && _enabled.value
                if (on && !wasOn) watch.reset()
                wasOn = on
                if (on) checkAll()
                delay(periodMs)
            }
        }
    }

    private suspend fun checkAll() {
        val now = System.currentTimeMillis()
        for (d in store.devices.first()) {
            if (isSimulatedHost(d.host)) continue
            val info = runCatching { api.probe(d.host) }.getOrNull()
            val reading = HealthWatch.Reading(info != null, info?.uptimeSec, info?.wifiSignal)
            for (e in watch.read(d.id, d.name, reading, now)) {
                _events.value = (listOf(e) + _events.value).take(30)
                notify(e)
            }
        }
    }

    private fun notify(e: HealthWatch.Event) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Controller alerts", NotificationManager.IMPORTANCE_HIGH))
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.wledcontroller.app.R.drawable.ic_tile_bulb)
            .setContentTitle(if (e.kind == HealthWatch.Event.Kind.BACK) "Controller back" else "Controller problem")
            .setContentText(e.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.message))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(e.deviceId.hashCode(), n) }
    }

    private companion object { const val CHANNEL = "controller_alerts" }
}
