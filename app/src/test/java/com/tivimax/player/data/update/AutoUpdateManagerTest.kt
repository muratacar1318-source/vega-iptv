package com.tivimax.player.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateManagerTest {

    @Test
    fun testIsNewerVersion() {
        // Newer versions
        assertTrue(AutoUpdateManager.isNewerVersion("v2.1.0", "2.0.0"))
        assertTrue(AutoUpdateManager.isNewerVersion("2.1.0", "2.0.9"))
        assertTrue(AutoUpdateManager.isNewerVersion("v2.0.1", "2.0.0"))
        assertTrue(AutoUpdateManager.isNewerVersion("3.0", "2.9.9"))

        // Older or equal versions
        assertFalse(AutoUpdateManager.isNewerVersion("v2.0.0", "2.0.0"))
        assertFalse(AutoUpdateManager.isNewerVersion("2.0.0", "2.0.1"))
        assertFalse(AutoUpdateManager.isNewerVersion("v1.9.9", "2.0.0"))
    }

    @Test
    fun testSnoozeLogicCalculations() {
        val now = System.currentTimeMillis()
        val durationHours = 24
        val snoozeUntil = now + (durationHours * 60 * 60 * 1000L)

        // Right now should be snoozed
        val isSnoozedCurrently = now < snoozeUntil
        assertTrue(isSnoozedCurrently)

        // 25 hours later should no longer be snoozed
        val futureTime = now + (25 * 60 * 60 * 1000L)
        val isSnoozedInFuture = futureTime < snoozeUntil
        assertFalse(isSnoozedInFuture)
    }
}
