package com.emduah.personal.wallpaper

import com.emduah.personal.data.Screen

object HomeScreenViews {
    const val DEFAULT_MINIMUM = 3
    fun eligible(preview: Boolean, visible: Boolean, interactive: Boolean, locked: Boolean, homeDestination: Boolean): Boolean =
        !preview && visible && interactive && !locked && homeDestination
}

object WallpaperViewPolicy {
    fun waiting(screen: Screen, separateLock: Boolean, file: String, minimumHome: Int, homeViews: Int, minimumLock: Int, lockViews: Int, manual: Boolean): Boolean =
        !manual && ((screen == Screen.HOME && LockScreenViews.waiting(minimumHome, file, homeViews)) ||
            (LockScreenViews.applies(screen, separateLock) && LockScreenViews.waiting(minimumLock, file, lockViews)))
}
