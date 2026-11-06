package com.emduah.personal.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import com.emduah.personal.wallpaper.LockScreenViews
import com.emduah.personal.wallpaper.HomeScreenViews
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultStore(context: Context, name: String = "personal") {
    val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("mediavault-session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mediavault-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun connection(): Connection {
        val credentials = runCatching {
            val bytes = Base64.decode(prefs.getString("token", ""), Base64.NO_WRAP)
            if (bytes.isEmpty()) JSONObject() else Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                JSONObject(String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            }
        }.getOrDefault(JSONObject())
        return Connection(prefs.getString("baseUrl", "")!!, credentials.optString("access"), prefs.getString("name", "")!!, credentials.optString("refresh"), credentials.optString("session"))
    }
    fun saveConnection(connection: Connection) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = JSONObject().put("access", connection.token).put("refresh", connection.refreshToken).put("session", connection.sessionId)
        val encrypted = Base64.encodeToString(cipher.iv + cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("baseUrl", connection.baseUrl).putString("token", encrypted).putString("name", connection.name).commit()
    }
    fun invalidateSession() {
        Screen.entries.forEach { saveProfile(profile(it).copy(enabled = false, revision = java.util.UUID.randomUUID().toString())) }
        clearConnection()
    }
    fun clearConnection() { prefs.edit().remove("token").remove("name").commit() }
    fun separateLock(): Boolean = prefs.getBoolean("separateLock", false)
    fun setSeparateLock(value: Boolean) { prefs.edit().putBoolean("separateLock", value).commit() }
    fun minimumLockViews(): Int = prefs.getInt("minimumLockViews", LockScreenViews.DEFAULT_MINIMUM)
    fun minimumHomeViews(): Int = prefs.getInt("minimumHomeViews", HomeScreenViews.DEFAULT_MINIMUM)
    fun setMinimumHomeViews(value: Int) {
        require(value in 0..1000) { "Choose between 1 and 1000 views." }
        prefs.edit().putInt("minimumHomeViews", value).commit()
    }
    fun homeViews(file: String): Int = if (file.isNotBlank() && prefs.getString("homeViewedFile", "") == file) prefs.getInt("homeViewCount", 0) else 0
    fun endHomeVisit() { if (prefs.contains("homeVisitFile")) prefs.edit().remove("homeVisitFile").commit() }
    fun recordHomeView(file: String) {
        if (file.isBlank() || current(Screen.HOME).file != file || prefs.getString("homeVisitFile", "") == file) return
        prefs.edit().putString("homeViewedFile", file).putString("homeVisitFile", file).putInt("homeViewCount", (homeViews(file) + 1).coerceAtMost(1000)).commit()
    }
    fun setMinimumLockViews(value: Int) {
        require(value in 0..1000) { "Choose between 1 and 1000 views." }
        prefs.edit().putInt("minimumLockViews", value).commit()
    }
    fun lockViews(file: String): Int = if (file.isNotBlank() && prefs.getString("lockViewedFile", "") == file) prefs.getInt("lockViewCount", 0) else 0
    fun endLockVisit() { if (prefs.contains("lockVisitFile")) prefs.edit().remove("lockVisitFile").commit() }
    fun recordLockView(file: String) {
        val source = if (separateLock()) Screen.LOCK else Screen.HOME
        if (file.isBlank() || current(source).file != file || prefs.getString("lockVisitFile", "") == file) return
        prefs.edit().putString("lockViewedFile", file).putString("lockVisitFile", file).putInt("lockViewCount", (lockViews(file) + 1).coerceAtMost(1000)).commit()
    }
    fun profile(screen: Screen): Profile {
        val j = JSONObject(prefs.getString("profile.${screen.key}", "{}")!!)
        return Profile(screen, j.optBoolean("enabled"), j.optLong("minutes", 20), j.optBoolean("wifiOnly", true), j.optBoolean("motion"), j.optString("filters", "{\"sort\":\"random\"}"), j.optString("revision", "initial"), j.optInt("maxDownloadMb", 120), j.optInt("videoSeconds", 60))
    }
    fun saveProfile(p: Profile) {
        p.validated()
        val j = JSONObject().put("enabled", p.enabled).put("minutes", p.minutes).put("wifiOnly", p.wifiOnly).put("motion", p.motion).put("filters", p.filters).put("revision", p.revision).put("maxDownloadMb", p.maxDownloadMb).put("videoSeconds", p.videoSeconds)
        prefs.edit().putString("profile.${p.screen.key}", j.toString()).commit()
    }
    fun current(screen: Screen): CurrentMedia {
        val j = JSONObject(prefs.getString("current.${screen.key}", "{}")!!)
        return CurrentMedia(j.optLong("id"), j.optString("name"), j.optString("type"), j.optString("file"), j.optLong("changedAt"), j.optInt("position"), j.optString("message", "Ready when you are"), j.optLong("sequenceId"))
    }
    fun saveCurrent(screen: Screen, m: CurrentMedia) {
        val j = JSONObject().put("id", m.id).put("name", m.name).put("type", m.type).put("file", m.file).put("changedAt", m.changedAt).put("position", m.position).put("message", m.message).put("sequenceId", m.sequenceId)
        prefs.edit().putString("current.${screen.key}", j.toString()).commit()
    }
}
