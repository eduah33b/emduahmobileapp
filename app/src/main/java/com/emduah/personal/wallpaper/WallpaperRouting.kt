package com.emduah.personal.wallpaper

import com.emduah.personal.data.Screen

object WallpaperRouting {
    fun lockUsesVault(homeUsesVault: Boolean, lockUsesVault: Boolean, lockHasOwnWallpaper: Boolean): Boolean =
        lockUsesVault || (homeUsesVault && !lockHasOwnWallpaper)

    fun activeFor(screen: Screen, separateLock: Boolean, homeUsesVault: Boolean, lockUsesVault: Boolean): Boolean =
        if (screen == Screen.LOCK) lockUsesVault else homeUsesVault && (separateLock || lockUsesVault)

    // Android's public flags are 1 (home) and 2 (lock); a shared engine has both.
    fun screenFor(separateLock: Boolean, flags: Int, locked: Boolean): Screen {
        if (!separateLock) return Screen.HOME
        val lockOnly = flags and 2 != 0 && flags and 1 == 0
        val sharedEngine = flags and 2 != 0 && flags and 1 != 0
        return if (lockOnly || (sharedEngine && locked)) Screen.LOCK else Screen.HOME
    }
}
