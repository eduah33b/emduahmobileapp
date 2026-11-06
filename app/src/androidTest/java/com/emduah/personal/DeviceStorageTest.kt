package com.emduah.personal

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emduah.personal.data.*
import com.emduah.personal.wallpaper.WallpaperImages
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun lockViewsPersistPerAppliedFileAndResetOnlyWhenMediaChanges() {
        val name = "test-${UUID.randomUUID()}"
        val store = VaultStore(context, name)
        try {
            store.setSeparateLock(true)
            store.setMinimumLockViews(3)
            val first = CurrentMedia(id = 42, file = "first")
            store.saveCurrent(Screen.LOCK, first)
            store.recordLockView("first")
            store.recordLockView("first")
            assertEquals(1, store.lockViews("first"))
            assertEquals(1, VaultStore(context, name).lockViews("first"))
            VaultStore(context, name).recordLockView("first") // Engine recreation is the same visit.
            assertEquals(1, store.lockViews("first"))
            store.endLockVisit()
            store.recordLockView("first")
            assertEquals(2, VaultStore(context, name).lockViews("first"))
            assertEquals(3, VaultStore(context, name).minimumLockViews())
            // A failed download/status update keeps the current media and its views.
            store.saveCurrent(Screen.LOCK, first.copy(message = "Download failed"))
            assertEquals(2, store.lockViews(store.current(Screen.LOCK).file))
            // Even selecting the same catalog ID again creates a new applied file.
            store.saveCurrent(Screen.LOCK, first.copy(file = "replacement"))
            store.recordLockView("first") // Ignore late frames from the old renderer.
            assertEquals(0, store.lockViews("replacement"))
            store.recordLockView("replacement")
            assertEquals(1, store.lockViews("replacement"))
            store.setSeparateLock(false)
            store.saveCurrent(Screen.HOME, first.copy(file = "shared"))
            store.recordLockView("shared")
            assertEquals(1, store.lockViews("shared"))
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun credentialsAreEncryptedAndRoundTripThroughKeystore() {
        val name = "test-${UUID.randomUUID()}"
        val store = VaultStore(context, name)
        try {
            val c = Connection("https://mediavault.emduah.xyz", "secret-access", "Emmanuel", "secret-refresh", "session-id")
            store.saveConnection(c)
            assertEquals(c, VaultStore(context, name).connection())
            val disk = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertFalse(disk.contains("secret-access"))
            assertFalse(disk.contains("secret-refresh"))
            store.invalidateSession()
            assertEquals("", store.connection().token)
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun deviceDecodesJpegPngAndWebpWallpapers() {
        val formats = listOf(Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP_LOSSLESS)
        formats.forEach { format ->
            val file = File(context.cacheDir, "image-test-${UUID.randomUUID()}")
            val source = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            try {
                file.outputStream().use { source.compress(format, 95, it) }
                val decoded = WallpaperImages.decode(file, 1080, 2340)
                val cropped = WallpaperImages.crop(decoded, 100, 220)
                assertEquals(100, cropped.width)
                assertEquals(220, cropped.height)
                assertTrue(Color.green(cropped.getPixel(50, 110)) > 200)
                cropped.recycle(); decoded.recycle()
            } finally { source.recycle(); file.delete() }
        }
    }
    @Test fun profilesKeepIndependentSettingsAndConfigurableCaps() {
        val name = "test-${UUID.randomUUID()}"
        val store = VaultStore(context, name)
        try {
            store.saveProfile(Profile(Screen.HOME, motion = true, maxDownloadMb = 240, videoSeconds = 90))
            store.saveProfile(Profile(Screen.LOCK, minutes = 60, motion = true, maxDownloadMb = 40, videoSeconds = 0))
            store.setSeparateLock(true)
            assertTrue(VaultStore(context, name).separateLock())
            assertEquals(240, store.profile(Screen.HOME).maxDownloadMb)
            assertEquals(40, store.profile(Screen.LOCK).maxDownloadMb)
            assertEquals(0, store.profile(Screen.LOCK).videoSeconds)
            assertEquals(60L, store.profile(Screen.LOCK).minutes)
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun expiredAccessRenewsOnceAndStoresRotatedCredentials() {
        val certificate = okhttp3.tls.HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = okhttp3.tls.HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = okhttp3.tls.HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val prefsName = "test-${UUID.randomUUID()}"
        val store = VaultStore(context, prefsName)
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val base = server.url("/").toString().trimEnd('/')
            val old = Connection(base, "old-access", "Emmanuel", "old-refresh", "same-session")
            store.saveConnection(old)
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(401).setBody("{}"))
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"token":"new-access","refreshToken":"new-refresh","name":"Emmanuel","sessionId":"same-session"}"""))
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"version":1}"""))
            val client = okhttp3.OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).followRedirects(false).build()
            try {
                assertEquals(1, VaultApi(store, client).metadata(old).getInt("version"))
                assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
                val refreshRequest = server.takeRequest()
                assertNull(refreshRequest.getHeader("Authorization"))
                assertTrue(refreshRequest.body.readUtf8().contains("old-refresh"))
                assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
                assertEquals("new-refresh", VaultStore(context, prefsName).connection().refreshToken)
                assertEquals("same-session", store.connection().sessionId)
            } finally { context.deleteSharedPreferences(prefsName) }
        }
    }

}
