package com.emduah.personal.ui

import org.junit.Assert.*
import org.junit.Test

class AppLocationTest {
    @Test fun featureBackReturnsToItsEntryTab() {
        for (tab in listOf(AppPage.HOME, AppPage.APPS)) {
            val root = AppLocation(tab, tab)
            assertEquals(root, root.open(AppPage.WALLPAPERS).back())
            assertEquals(root, root.open(AppPage.SETTINGS).back())
        }
    }
    @Test fun editorBackReturnsToWallpaperBeforeEntryTab() {
        val apps = AppLocation(AppPage.APPS, AppPage.APPS)
        val feature = apps.open(AppPage.WALLPAPERS)
        assertEquals(feature, feature.open(AppPage.EDITOR).back())
        assertEquals(apps, feature.open(AppPage.EDITOR).back().back())
    }
    @Test fun rootsDoNotTrapAndroidBack() {
        assertEquals(AppLocation(), AppLocation().back())
        assertEquals(AppLocation(), AppLocation(AppPage.APPS, AppPage.APPS).back())
        assertTrue(AppLocation().isRoot)
        assertFalse(AppLocation().open(AppPage.WALLPAPERS).isRoot)
    }
}
