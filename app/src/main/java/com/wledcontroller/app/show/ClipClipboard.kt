package com.wledcontroller.app.show

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Effects copied in the show editor, kept while the app runs so they can be pasted into any show. */
object ClipClipboard {
    /** Copied clips with the track they came from, start times relative to the earliest one. */
    var items: List<Pair<String, Clip>> by mutableStateOf(emptyList())
        private set

    fun copy(clips: List<Pair<String, Clip>>) {
        val first = clips.minOfOrNull { it.second.startMs } ?: return
        items = clips.map { (track, c) -> track to c.copy(startMs = c.startMs - first) }
    }
}
