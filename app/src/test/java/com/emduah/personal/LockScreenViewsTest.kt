package com.emduah.personal

import com.emduah.personal.data.Screen
import com.emduah.personal.wallpaper.*
import org.junit.Assert.*
import org.junit.Test

class LockScreenViewsTest {
    @Test fun minimumProtectsExistingMediaButAllowsTheFirstDownload() {
        assertFalse(LockScreenViews.waiting(0, "current", 0))
        assertFalse(LockScreenViews.waiting(3, "", 0))
        assertTrue(LockScreenViews.waiting(3, "current", 0))
        assertTrue(LockScreenViews.waiting(3, "current", 2))
        assertFalse(LockScreenViews.waiting(3, "current", 3))
        assertFalse(LockScreenViews.waiting(3, "current", 4))
    }
    @Test fun sharedMediaIsHeldButIndependentHomeIsUnaffected() {
        assertTrue(LockScreenViews.applies(Screen.HOME, false))
        assertTrue(LockScreenViews.applies(Screen.LOCK, false))
        assertTrue(LockScreenViews.applies(Screen.LOCK, true))
        assertFalse(LockScreenViews.applies(Screen.HOME, true))
    }
    @Test fun onlyAnAwakeVisibleRealLockScreenQualifies() {
        assertTrue(LockScreenViews.eligible(false, true, true, true, true))
        assertFalse(LockScreenViews.eligible(true, true, true, true, true))
        assertFalse(LockScreenViews.eligible(false, false, true, true, true))
        assertFalse(LockScreenViews.eligible(false, true, false, true, true))
        assertFalse(LockScreenViews.eligible(false, true, true, false, true))
        assertFalse(LockScreenViews.eligible(false, true, true, true, false))
    }
    @Test fun framesAndOverlappingEnginesCountOnlyOncePerVisit() {
        val sessions = LockViewSessions()
        assertTrue(sessions.frame("engine1", true, "a"))
        repeat(100) { assertFalse(sessions.frame("engine1", true, "a")) }
        assertFalse(sessions.frame("engine2", true, "a"))
        sessions.frame("engine1", false, "")
        assertFalse(sessions.frame("engine2", true, "a"))
        sessions.frame("engine2", false, "")
        assertTrue(sessions.frame("engine1", true, "a"))
        assertTrue(sessions.frame("engine1", true, "b"))
        assertFalse(sessions.frame("engine1", true, "b"))
    }
    @Test fun heldStatusReplacesCountdownButManualWorkCanRun() {
        val online = RotationEnvironment(internet = true, wifi = true)
        assertEquals("Paused · 2 lock views left", RotationStatus.nextUpdate(true, true, online, RotationWork(known = true), 100, 2))
        assertEquals("Updating wallpaper…", RotationStatus.nextUpdate(true, true, online, RotationWork(running = true, known = true), 100, 2))
    }
}
