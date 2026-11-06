package com.emduah.personal.wallpaper

import com.emduah.personal.data.Screen

object LockScreenViews {
    const val DEFAULT_MINIMUM = 3
    fun applies(screen: Screen, separateLock: Boolean): Boolean = screen == Screen.LOCK || !separateLock
    fun waiting(minimum: Int, file: String, views: Int): Boolean = minimum > 0 && file.isNotBlank() && views < minimum
    fun eligible(preview: Boolean, visible: Boolean, interactive: Boolean, locked: Boolean, lockDestination: Boolean): Boolean =
        !preview && visible && interactive && locked && lockDestination
}

/** One exposure per lock-screen visit, shared across overlapping wallpaper engines. */
internal class LockViewSessions {
    private val engines = mutableSetOf<Any>()
    private val presented = mutableSetOf<String>()
    fun frame(engine: Any, eligible: Boolean, file: String): Boolean {
        if (!eligible) {
            engines.remove(engine)
            if (engines.isEmpty()) presented.clear()
            return false
        }
        engines.add(engine)
        return file.isNotBlank() && presented.add(file)
    }
}
