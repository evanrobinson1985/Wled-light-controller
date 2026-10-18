package com.wledcontroller.app.show

/** App-wide playback settings, kept in sync with [com.wledcontroller.app.data.AppStore]. */
object ShowSettings {
    /**
     * How long to hold the lights back behind the music, in ms. Bluetooth speakers play a few
     * hundred ms late; a negative value lets the lights lead instead (to cover network delay).
     */
    @Volatile var lightsDelayMs: Long = 0
}
