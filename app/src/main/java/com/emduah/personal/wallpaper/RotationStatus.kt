package com.emduah.personal.wallpaper

data class RotationEnvironment(val internet: Boolean = false, val wifi: Boolean = false, val batteryLow: Boolean = false)
data class RotationWork(val nextAt: Long? = null, val running: Boolean = false, val known: Boolean = false)

object RotationStatus {
    fun updatedAgo(changedAt: Long, now: Long): String {
        if (changedAt <= 0) return ""
        val seconds = ((now - changedAt).coerceAtLeast(0)) / 1000
        return when {
            seconds < 5 -> "Updated just now"
            seconds < 60 -> "Updated ${seconds}s ago"
            seconds < 3600 -> "Updated ${seconds / 60}m ago"
            seconds < 86400 -> "Updated ${seconds / 3600}h ago"
            else -> "Updated ${seconds / 86400}d ago"
        }
    }

    fun nextUpdate(enabled: Boolean, wifiOnly: Boolean, environment: RotationEnvironment, work: RotationWork, now: Long): String {
        return when {
            !enabled -> "Paused"
            wifiOnly && !environment.wifi -> "Paused · waiting for Wi-Fi"
            !environment.internet -> "Paused · no internet"
            environment.batteryLow -> "Paused · low battery"
            work.running -> "Updating wallpaper…"
            !work.known -> "Checking schedule…"
            work.nextAt == null -> "No update scheduled"
            work.nextAt <= now -> "Update due"
            else -> "Next update in ${remaining(work.nextAt - now)}"
        }
    }

    fun previewLine(updated: String, status: String): String {
        val next = if (status.startsWith("Next update in ")) "Next in " + status.removePrefix("Next update in ") else status
        return listOf(updated.removePrefix("Updated "), next).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun remaining(milliseconds: Long): String {
        val seconds = (milliseconds + 999) / 1000
        return when {
            seconds >= 86400 -> "${seconds / 86400}d ${(seconds % 86400) / 3600}h"
            seconds >= 3600 -> "${seconds / 3600}h ${((seconds % 3600) / 60).toString().padStart(2, '0')}m"
            seconds >= 60 -> "${seconds / 60}m ${(seconds % 60).toString().padStart(2, '0')}s"
            else -> "${seconds}s"
        }
    }
}
