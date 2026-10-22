package com.wledcontroller.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.wledcontroller.app.show.ShowControl
import kotlinx.coroutines.launch

/**
 * The app's shortcuts (long-press the icon, a home-screen shortcut, or "Hey Google, open
 * Start the show" / a Bixby or Google routine): does the show command and goes away.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = intent?.getStringExtra(EXTRA_COMMAND) ?: intent?.data?.lastPathSegment ?: "play"
        val app = application as WledApp
        app.container.scope.launch {
            val problem = ShowControl.perform(app, command)
            val done = when (command) {
                "play" -> "Starting the show"; "stop" -> "Show stopped"; "skip" -> "Next song"
                "jukebox" -> "Jukebox on"; "schedule" -> "Schedule on"; else -> null
            }
            (problem ?: done)?.let { msg -> android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() } }
        }
        finish()
    }

    companion object {
        const val EXTRA_COMMAND = "command"
    }
}
