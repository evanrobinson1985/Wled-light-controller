package com.wledcontroller.app.show

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wledcontroller.app.MainActivity
import com.wledcontroller.app.R
import com.wledcontroller.app.WledApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.format.DateTimeFormatter

/** Runs the show schedule (or a playlist now) with the screen off, as a foreground service. */
class ShowRunnerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopping = false
    private var watcher: kotlinx.coroutines.Job? = null
    private val container get() = (application as WledApp).container
    private val runner get() = container.showRunner

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(runner.state.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        when (intent?.action) {
            ACTION_STOP -> {
                stopping = true
                runBlocking { container.store.setScheduleOn(false) }
                runner.stop()
                container.jukeboxServer.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> intent.getStringExtra(EXTRA_PLAYLIST)?.let { runner.playNow(it) }
            ACTION_JUKEBOX -> runner.runJukebox(container.jukebox)
            else -> {
                // Schedule (also used when the system restarts the service).
                if (!runBlocking { container.store.scheduleOn.first() }) { stopSelf(); return START_NOT_STICKY }
                runner.runSchedule()
            }
        }
        acquireLocks()
        if (watcher == null) watcher = scope.launch {
            // Keep the notification current. When a "play now" finishes, fall back to the
            // schedule if it's on, otherwise stop.
            runner.state.drop(1).collect { s ->
                if (!s.running) {
                    if (!stopping && container.store.scheduleOn.first()) runner.runSchedule() else stopSelf()
                    return@collect
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(s))
            }
        }
        return START_STICKY
    }

    private fun acquireLocks() {
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wifi?.createWifiLock(mode, "wled:shows")?.apply { setReferenceCounted(false); acquire() }
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wled:shows")
                ?.apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun notification(s: RunnerState): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Light shows", NotificationManager.IMPORTANCE_LOW))
        }
        val text = when {
            s.nowPlaying != null -> "Now playing: ${s.nowPlaying}" + (s.playlistName?.let { " · $it" } ?: "")
            s.nextStart != null -> "Next show: " + s.nextStart.format(DateTimeFormatter.ofPattern("EEE h:mm a"))
            s.mode == "schedule" -> "Waiting for a scheduled show"
            else -> "Starting…"
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2, Intent(this, ShowRunnerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_bulb)
            .setContentTitle(when (s.mode) { "playlist" -> "Playing light show"; "jukebox" -> "Viewer jukebox on"; else -> "Show schedule on" })
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        runCatching { wifiLock?.release() }
        runCatching { wakeLock?.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "shows"
        private const val NOTIFICATION_ID = 43
        private const val ACTION_STOP = "stop"
        private const val ACTION_PLAY = "play"
        private const val ACTION_JUKEBOX = "jukebox"
        private const val EXTRA_PLAYLIST = "playlist"

        fun startSchedule(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ShowRunnerService::class.java))
        }

        fun playNow(context: Context, playlistId: String) {
            ContextCompat.startForegroundService(
                context, Intent(context, ShowRunnerService::class.java).setAction(ACTION_PLAY).putExtra(EXTRA_PLAYLIST, playlistId),
            )
        }

        /** Runs the viewer jukebox on the lights until stopped. */
        fun startJukebox(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ShowRunnerService::class.java).setAction(ACTION_JUKEBOX))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ShowRunnerService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** Restarts the show schedule after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as WledApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (container.store.scheduleOn.first()) ShowRunnerService.startSchedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
