package com.emduah.personal.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emduah.personal.data.*
import com.emduah.personal.wallpaper.RotationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class PersonalViewModel(application: Application): AndroidViewModel(application), SharedPreferences.OnSharedPreferenceChangeListener {
    val store = VaultStore(application)
    var connection by mutableStateOf(store.connection()); private set
    var profiles by mutableStateOf(Screen.entries.associateWith { store.profile(it) }); private set
    var current by mutableStateOf(Screen.entries.associateWith { store.current(it) }); private set
    var separateLock by mutableStateOf(store.separateLock()); private set
    var options by mutableStateOf(JSONObject()); private set
    var devices by mutableStateOf(JSONObject()); private set
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf(""); private set
    var signInError by mutableStateOf(""); private set
    init { store.prefs.registerOnSharedPreferenceChangeListener(this); if (connection.token.isNotBlank()) { loadOptions(); loadDevices() } }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        separateLock = store.separateLock()
        connection = store.connection()
        profiles = Screen.entries.associateWith { store.profile(it) }
        current = Screen.entries.associateWith { store.current(it) }
    }
    fun clearNotice() { notice = "" }
    fun login(base: String, email: String, password: String, done: () -> Unit) {
        viewModelScope.launch {
            busy = true; notice = ""; signInError = ""
            try {
                val result = withContext(Dispatchers.IO) { VaultApi(store).login(base, email, password) }
                store.saveConnection(result)
                loadOptions(); loadDevices()
                notice = "Connected to MediaVault"
                done()
            } catch (e: Exception) { signInError = e.message ?: "Unable to sign in." } finally { busy = false }
        }
    }
    fun loadDevices() {
        viewModelScope.launch {
            try { devices = withContext(Dispatchers.IO) { VaultApi(store).sessions(connection) } }
            catch (e: Exception) { notice = e.message ?: "Unable to load sessions." }
        }
    }
    fun revokeDevice(id: String) {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { VaultApi(store).revokeDevice(connection, id) }; loadDevices(); notice = "Device signed out." }
            catch (e: Exception) { notice = e.message ?: "Unable to revoke device." }
        }
    }
    fun accountLink(base: String, reset: Boolean, open: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val links = withContext(Dispatchers.IO) { VaultApi(store).links(base) }
                val url = links.getString(if (reset) "passwordResetUrl" else "loginUrl")
                require(url.startsWith("https://")) { "Account links must use HTTPS." }
                open(url)
            } catch (e: Exception) { notice = e.message ?: "Unable to open account page." }
        }
    }
    fun loadOptions() {
        viewModelScope.launch {
            try { options = withContext(Dispatchers.IO) { VaultApi(store).metadata(connection) } }
            catch (e: Exception) { notice = e.message ?: "Unable to load MediaVault options." }
        }
    }
    fun disconnect() {
        viewModelScope.launch {
            busy = true; notice = ""
            try {
                val c = connection
                // Revoke on the server before discarding the only copy of the token.
                withContext(Dispatchers.IO) { try { VaultApi(store).disconnect(c) } catch (e: ApiException) { if (e.status != 401) throw e } }
                Screen.entries.forEach { screen ->
                    val p = store.profile(screen)
                    store.saveProfile(p.copy(enabled = false, revision = UUID.randomUUID().toString()))
                }
                RotationScheduler.cancelAll(getApplication())
                store.clearConnection(); options = JSONObject(); notice = "Disconnected. Rotation paused."
            } catch (e: Exception) { notice = e.message ?: "Unable to revoke the connection. Try again online." }
            finally { busy = false }
        }
    }
    fun save(p: Profile): Boolean {
        return runCatching {
            p.validated()
            require(!p.enabled || connection.token.isNotBlank()) { "Connect to MediaVault first." }
            val saved = p.copy(revision = UUID.randomUUID().toString())
            val previous = store.profile(p.screen)
            if (previous.filters != p.filters || previous.motion != p.motion || previous.maxDownloadMb != p.maxDownloadMb) {
                store.saveCurrent(p.screen, store.current(p.screen).copy(position = 0, sequenceId = 0))
            }
            store.saveProfile(saved)
            if (saved.enabled) RotationScheduler.next(getApplication(), saved)
            else RotationScheduler.schedule(getApplication(), saved)
            notice = if (saved.enabled) "Rotation saved. First update queued; waiting for network and battery conditions." else "Settings saved. Rotation paused."
            true
        }.getOrElse { notice = it.message ?: "Unable to save settings."; false }
    }
    fun setLockOverride(value: Boolean) {
        store.setSeparateLock(value)
        if (!value) {
            val lock = store.profile(Screen.LOCK).copy(enabled = false, revision = UUID.randomUUID().toString())
            store.saveProfile(lock); RotationScheduler.schedule(getApplication(), lock)
        }
        val home = store.profile(Screen.HOME)
        store.saveProfile(home.copy(revision = UUID.randomUUID().toString()))
        if (home.enabled) RotationScheduler.next(getApplication(), store.profile(Screen.HOME))
        notice = if (value) "Lock screen override enabled. Configure its media and schedule below." else "Both screens now share the home rotation settings."
    }
    fun next(screen: Screen) {
        val p = store.profile(screen)
        if (!p.enabled) { notice = "Start rotation before requesting the next wallpaper."; return }
        RotationScheduler.next(getApplication(), p)
        notice = "Next wallpaper queued. Network and battery preferences apply."
    }
    override fun onCleared() { store.prefs.unregisterOnSharedPreferenceChangeListener(this) }
}
