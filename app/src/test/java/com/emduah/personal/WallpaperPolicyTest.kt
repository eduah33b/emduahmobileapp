package com.emduah.personal

import com.emduah.personal.data.*
import com.emduah.personal.wallpaper.WallpaperRouting
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class WallpaperPolicyTest {
    @Test fun sharedLiveLockInheritsHomeProviderWithoutSeparateInfo() {
        assertTrue(WallpaperRouting.lockUsesVault(true, false, false))
        assertTrue(WallpaperRouting.lockUsesVault(false, true, true))
        assertFalse(WallpaperRouting.lockUsesVault(true, false, true))
        assertFalse(WallpaperRouting.lockUsesVault(false, false, false))
    }
    @Test fun sharedSettingsUseOneFileOnEitherScreen() {
        for (flags in listOf(1, 2, 3)) for (locked in listOf(false, true)) {
            assertEquals(Screen.HOME, WallpaperRouting.screenFor(false, flags, locked))
        }
    }
    @Test fun independentEnginesChooseTheirOwnMedia() {
        assertEquals(Screen.HOME, WallpaperRouting.screenFor(true, 1, true))
        assertEquals(Screen.LOCK, WallpaperRouting.screenFor(true, 2, false))
        assertEquals(Screen.LOCK, WallpaperRouting.screenFor(true, 3, true))
        assertEquals(Screen.HOME, WallpaperRouting.screenFor(true, 3, false))
    }
    @Test fun filtersPreserveArraysAndAlwaysExcludeTrash() {
        val p = Profile(Screen.LOCK, motion = true, filters = """{"rating":[4,5],"status":["keep"],"type":"video","metadata":"forest","sort":"relevance"}""", maxDownloadMb = 75)
        val j = p.validated().requestFilters()
        assertEquals(2, j.getJSONArray("rating").length())
        assertEquals("keep", j.getJSONArray("status").getString(0))
        assertEquals("video", j.getString("type"))
        assertEquals("forest", j.getString("metadata"))
        assertEquals(75L * 1024 * 1024, j.getLong("max_bytes"))
        assertFalse(j.getBoolean("trash"))
        assertEquals("mediavault", j.getString("seed"))
    }
    @Test fun imageModeForcesImagesEvenWithCustomType() {
        assertEquals("image", Profile(Screen.HOME, filters = """{"type":"video"}""").requestFilters().getString("type"))
    }
    @Test fun rejectsInvalidSchedulesAndLimits() {
        listOf(Profile(Screen.HOME, minutes = 14), Profile(Screen.LOCK, maxDownloadMb = 0), Profile(Screen.LOCK, maxDownloadMb = 1025), Profile(Screen.HOME, videoSeconds = -1)).forEach { p ->
            assertThrows(IllegalArgumentException::class.java) { p.validated() }
        }
        Profile(Screen.LOCK, motion = true, videoSeconds = 0).validated()
    }
    @Test fun apiOriginRequiresHttpsAndNoCredentialsOrPath() {
        listOf("http://mediavault.emduah.xyz", "https://user:password@example.com", "https://example.com/api", "https://example.com?token=secret").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { VaultApi.normalizeBase(url) }
        }
        assertEquals("https://mediavault.emduah.xyz", VaultApi.normalizeBase(" https://mediavault.emduah.xyz/ "))
    }
    @Test fun activationIsRequiredOnlyForTheConfiguredScreens() {
        assertFalse(WallpaperRouting.activeFor(Screen.HOME, false, true, false))
        assertTrue(WallpaperRouting.activeFor(Screen.HOME, false, true, true))
        assertTrue(WallpaperRouting.activeFor(Screen.HOME, true, true, false))
        assertFalse(WallpaperRouting.activeFor(Screen.HOME, true, false, true))
        assertTrue(WallpaperRouting.activeFor(Screen.LOCK, true, false, true))
        assertFalse(WallpaperRouting.activeFor(Screen.LOCK, true, true, false))
    }

}
