package com.emduah.personal.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.emduah.personal.data.Screen
import com.emduah.personal.data.VaultStore
import java.io.File

class VaultWallpaperService: WallpaperService() {
    private val views = LockViewSessions()
    override fun onCreateEngine(): Engine = VaultEngine()
    inner class VaultEngine: Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val store = VaultStore(this@VaultWallpaperService)
        private val handler = Handler(Looper.getMainLooper())
        private var renderer: WallpaperRenderer? = null
        private val receiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!isPreview && (intent?.action == Intent.ACTION_SCREEN_OFF || intent?.action == Intent.ACTION_USER_PRESENT)) {
                    store.endLockVisit()
                    views.frame(this@VaultEngine, false, "")
                }
                render(); handler.postDelayed({ if (isVisible) render() }, 200)
            }
        }
        private val tick = object: Runnable {
            override fun run() {
                if (renderer == null || !isVisible) return
                // Shared engines can survive lock/unlock without a visibility callback.
                render()
                handler.postDelayed(this, 500)
            }
        }
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)
            store.prefs.registerOnSharedPreferenceChangeListener(this)
            registerReceiver(receiver, IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }, Context.RECEIVER_NOT_EXPORTED)
        }
        private fun selectedScreen(): Screen = WallpaperRouting.screenFor(store.separateLock(), wallpaperFlags, getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        override fun onWallpaperFlagsChanged(which: Int) { render() }
        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer?.close()
            renderer = WallpaperRenderer(holder.surface, onFramePresented = { file -> handler.post {
                if (views.frame(this, viewEligible(), file)) store.recordLockView(file)
            } }) { message ->
                handler.post {
                    val screen = selectedScreen()
                    val current = store.current(screen)
                    if (current.message != message) store.saveCurrent(screen, current.copy(message = message))
                }
            }
            renderer?.resize(holder.surfaceFrame.width(), holder.surfaceFrame.height())
            render()
            if (isVisible) { handler.removeCallbacks(tick); handler.post(tick) }
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { super.onSurfaceChanged(holder, format, width, height); renderer?.resize(width, height); render() }
        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) views.frame(this, false, "")
            handler.removeCallbacks(tick)
            renderer?.setVisible(visible)
            if (visible) { render(); handler.post(tick) }
        }
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (key == "current.home" || key == "current.lock" || key == "separateLock" || key == "profile.home" || key == "profile.lock") render()
        }
        private fun render() {
            // Persist visit deduplication across engine/service recreation. Only a definite
            // sleep or unlock ends the visit; hiding the surface alone must not add a view.
            if (!isPreview && !getSystemService(PowerManager::class.java).isInteractive) store.endLockVisit()
            if (!viewEligible()) views.frame(this, false, "")
            val screen = selectedScreen()
            val current = store.current(screen)
            renderer?.load(File(current.file), current.type, store.profile(screen).videoSeconds, isVisible)
        }
        private fun viewEligible(): Boolean = LockScreenViews.eligible(
            isPreview, isVisible, getSystemService(PowerManager::class.java).isInteractive,
            getSystemService(KeyguardManager::class.java).isKeyguardLocked, wallpaperFlags and WallpaperManager.FLAG_LOCK != 0
        )
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { views.frame(this, false, ""); renderer?.close(); renderer = null; handler.removeCallbacks(tick); super.onSurfaceDestroyed(holder) }
        override fun onDestroy() { views.frame(this, false, ""); store.prefs.unregisterOnSharedPreferenceChangeListener(this); unregisterReceiver(receiver); handler.removeCallbacksAndMessages(null); renderer?.close(); renderer = null; super.onDestroy() }
    }
}
