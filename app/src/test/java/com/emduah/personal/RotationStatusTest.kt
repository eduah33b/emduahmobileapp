package com.emduah.personal

import com.emduah.personal.wallpaper.*
import org.junit.Assert.*
import org.junit.Test

class RotationStatusTest {
    private val now = 1_000_000L
    private val onlineWifi = RotationEnvironment(internet = true, wifi = true)
    private val upcoming = RotationWork(nextAt = now + 120_000, known = true)

    @Test fun showsRelativeUpdateTimesInsteadOfDates() {
        assertEquals("", RotationStatus.updatedAgo(0, now))
        assertEquals("Updated just now", RotationStatus.updatedAgo(now, now))
        assertEquals("Updated just now", RotationStatus.updatedAgo(now + 5000, now))
        assertEquals("Updated 15m ago", RotationStatus.updatedAgo(now - 15 * 60_000, now))
        assertEquals("Updated 20s ago", RotationStatus.updatedAgo(now - 20_000, now))
    }
    @Test fun countdownChangesAsTimePassesAndNeverGoesNegative() {
        assertEquals("Next update in 2m 00s", RotationStatus.nextUpdate(true, true, onlineWifi, upcoming, now))
        assertEquals("Next update in 1m 59s", RotationStatus.nextUpdate(true, true, onlineWifi, upcoming, now + 1000))
        assertEquals("Update due", RotationStatus.nextUpdate(true, true, onlineWifi, upcoming, now + 121_000))
    }
    @Test fun manualPauseAndBlockedNetworkReplaceTheCountdown() {
        assertEquals("Paused", RotationStatus.nextUpdate(false, true, onlineWifi, upcoming, now))
        assertEquals("Paused · waiting for Wi-Fi", RotationStatus.nextUpdate(true, true, RotationEnvironment(internet = true, wifi = false), upcoming, now))
        assertEquals("Paused · no internet", RotationStatus.nextUpdate(true, false, RotationEnvironment(), upcoming, now))
        assertEquals("Paused · no internet", RotationStatus.nextUpdate(true, true, RotationEnvironment(wifi = true), upcoming, now))
        assertEquals("Next update in 2m 00s", RotationStatus.nextUpdate(true, false, RotationEnvironment(internet = true), upcoming, now))
    }
    @Test fun reportsActualWorkAndBatteryStateInsteadOfGuessingFromLastUpdate() {
        assertEquals("Updating wallpaper…", RotationStatus.nextUpdate(true, true, onlineWifi, RotationWork(running = true, known = true), now))
        assertEquals("Paused · low battery", RotationStatus.nextUpdate(true, true, onlineWifi.copy(batteryLow = true), upcoming, now))
        assertEquals("Checking schedule…", RotationStatus.nextUpdate(true, true, onlineWifi, RotationWork(), now))
        assertEquals("No update scheduled", RotationStatus.nextUpdate(true, true, onlineWifi, RotationWork(known = true), now))
    }
    @Test fun compactPreviewKeepsTimingAndPauseReasonOnOneLine() {
        assertEquals("15m ago · Next in 2m 04s", RotationStatus.previewLine("Updated 15m ago", "Next update in 2m 04s"))
        assertEquals("15m ago · Paused · waiting for Wi-Fi", RotationStatus.previewLine("Updated 15m ago", "Paused · waiting for Wi-Fi"))
        assertEquals("Paused", RotationStatus.previewLine("", "Paused"))
    }

}
