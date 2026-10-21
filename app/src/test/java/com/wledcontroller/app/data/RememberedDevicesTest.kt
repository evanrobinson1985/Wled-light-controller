package com.wledcontroller.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberedDevicesTest {
    @Test
    fun onlyRealControllersAreRemembered() {
        // Simulated controllers and DMX nodes live inside the app; real ones are kept for good.
        assertTrue(isSimulatedHost("127.77.0.3"))
        assertTrue(isSimulatedHost("http://127.77.1.1/"))
        assertFalse(isSimulatedHost("192.168.1.40"))
        assertFalse(isSimulatedHost("wled-roof.local"))
        assertFalse(isSimulatedHost("127.0.0.1:8080"))
    }
}
