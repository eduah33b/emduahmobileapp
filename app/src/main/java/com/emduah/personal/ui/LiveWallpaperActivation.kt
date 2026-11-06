package com.emduah.personal.ui

import android.app.WallpaperManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emduah.personal.data.Screen
import com.emduah.personal.wallpaper.VaultWallpaperService
import com.emduah.personal.wallpaper.WallpaperRouting

@Composable internal fun rememberLiveWallpaperActive(screen: Screen, separateLock: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun readActive(): Boolean = runCatching {
        val manager = WallpaperManager.getInstance(context)
        val home = manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.serviceName == VaultWallpaperService::class.java.name
        val lock = WallpaperRouting.lockUsesVault(
            home,
            manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.serviceName == VaultWallpaperService::class.java.name,
            manager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0
        )
        WallpaperRouting.activeFor(screen, separateLock, home, lock)
    }.getOrDefault(false)
    var active by remember(context, screen, separateLock) { mutableStateOf(readActive()) }
    DisposableEffect(lifecycle, context, screen, separateLock) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) active = readActive()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return active
}

@Composable internal fun LiveWallpaperActivationPrompt(active: Boolean, screen: Screen, separateLock: Boolean, activate: () -> Unit) {
    if (active) return
    val destination = when {
        screen == Screen.LOCK -> "your lock screen"
        separateLock -> "your home screen"
        else -> "both screens"
    }
    Text("Activate live wallpaper for $destination to use dynamic wallpapers and track lock-screen views. Android will ask you which screens to apply it to.", color = Muted, fontSize = 12.sp)
    OutlinedButton(onClick = activate, modifier = Modifier.fillMaxWidth()) { Text("Activate live wallpaper") }
}
