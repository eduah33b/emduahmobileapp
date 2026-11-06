package com.emduah.personal

import com.emduah.personal.data.Screen
import com.emduah.personal.wallpaper.*
import org.junit.Assert.*
import org.junit.Test

class HomeScreenViewsTest {
    @Test fun onlyAnAwakeUnlockedVisibleHomeWallpaperCounts() {
        assertTrue(HomeScreenViews.eligible(false, true, true, false, true))
        assertFalse(HomeScreenViews.eligible(true, true, true, false, true))
        assertFalse(HomeScreenViews.eligible(false, false, true, false, true))
        assertFalse(HomeScreenViews.eligible(false, true, false, false, true))
        assertFalse(HomeScreenViews.eligible(false, true, true, true, true))
        assertFalse(HomeScreenViews.eligible(false, true, true, false, false))
    }
    @Test fun independentScreensOnlyRequireTheirOwnMinimum() {
        assertTrue(WallpaperViewPolicy.waiting(Screen.HOME, true, "home", 3, 2, 3, 3, false))
        assertFalse(WallpaperViewPolicy.waiting(Screen.HOME, true, "home", 3, 3, 3, 0, false))
        assertFalse(WallpaperViewPolicy.waiting(Screen.LOCK, true, "lock", 3, 0, 3, 3, false))
        assertTrue(WallpaperViewPolicy.waiting(Screen.LOCK, true, "lock", 3, 3, 3, 2, false))
    }
    @Test fun sharedMediaRequiresBothEnabledMinimumsButManualCanSkip() {
        assertTrue(WallpaperViewPolicy.waiting(Screen.HOME, false, "shared", 3, 3, 3, 2, false))
        assertTrue(WallpaperViewPolicy.waiting(Screen.HOME, false, "shared", 3, 2, 3, 3, false))
        assertFalse(WallpaperViewPolicy.waiting(Screen.HOME, false, "shared", 3, 3, 3, 3, false))
        assertFalse(WallpaperViewPolicy.waiting(Screen.HOME, false, "shared", 0, 0, 0, 0, false))
        assertFalse(WallpaperViewPolicy.waiting(Screen.HOME, false, "", 3, 0, 3, 0, false))
        assertFalse(WallpaperViewPolicy.waiting(Screen.HOME, false, "shared", 3, 0, 3, 0, true))
    }
    @Test fun homeDeficitIsShownAndCountedDownIndependently() {
        val online = RotationEnvironment(internet = true, wifi = true)
        assertEquals("Paused · 1 home view left", RotationStatus.nextUpdate(true, true, online, RotationWork(known = true), 100, 0, 1))
        assertEquals("Paused · 3 lock views left", RotationStatus.nextUpdate(true, true, online, RotationWork(known = true), 100, 3, 0))
    }
}
