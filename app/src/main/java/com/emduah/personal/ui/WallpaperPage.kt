package com.emduah.personal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emduah.personal.data.Screen

@Composable internal fun WallpaperPage(vm: PersonalViewModel, screen: Screen, selectScreen: (Screen) -> Unit, onActivateMotion: () -> Unit, edit: () -> Unit) {
    Text("A fresh view, at your pace.", style = MaterialTheme.typography.bodyLarge, color = Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Screen.entries.forEach { s -> FilterChip(selected = screen == s, onClick = { selectScreen(s) }, label = { Text(if (s == Screen.HOME) "Home screen" else "Lock screen") }) }
    }
    val inherited = screen == Screen.LOCK && !vm.separateLock
    val source = if (inherited) Screen.HOME else screen
    val p = vm.profiles.getValue(source)
    val media = vm.current.getValue(source)
    val timing = rememberRotationTiming(p, media.changedAt)
    WallpaperPreview(media, screen, p.videoSeconds, timing)
    if (screen == Screen.LOCK) CardBlock {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Separate lock settings", style = MaterialTheme.typography.titleMedium)
                Text(if (vm.separateLock) "Own media, filters and schedule." else "Following your home screen setup.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Switch(vm.separateLock, vm::setLockOverride)
        }
    }
    if (inherited) {
        CardBlock {
            Text("Following home screen", style = MaterialTheme.typography.titleMedium)
            Text("Both screens share the same media and schedule. Enable separate lock settings to customize this screen.", color = Muted)
            OutlinedButton(onClick = { selectScreen(Screen.HOME) }, modifier = Modifier.fillMaxWidth()) { Text("Manage home screen") }
        }
    } else CardBlock {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (timing.status.startsWith("Paused")) "Rotation is paused" else "Rotation is on", style = MaterialTheme.typography.titleMedium)
                Text("Every ${p.minutes} min · ${if (p.wifiOnly) "Wi-Fi only" else "Any network"}", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Switch(p.enabled, { vm.save(p.copy(enabled = it)) })
        }
        Text(filterSummary(p), style = MaterialTheme.typography.bodyMedium, color = Muted)
        // Stack the controls so larger system text remains readable.
        Button(onClick = { vm.next(screen) }, enabled = p.enabled, modifier = Modifier.fillMaxWidth()) { Text("Next wallpaper") }
        OutlinedButton(onClick = edit, modifier = Modifier.fillMaxWidth()) { Text("Configure") }
        if (p.motion) LiveWallpaperActivationPrompt(rememberLiveWallpaperActive(source, vm.separateLock), source, vm.separateLock, onActivateMotion)
    }
    Text("Android may delay background updates while your phone is idle, low on battery, or waiting for your preferred network. Your current wallpaper stays in place if an update fails.", color = Muted, style = MaterialTheme.typography.bodySmall)
}
