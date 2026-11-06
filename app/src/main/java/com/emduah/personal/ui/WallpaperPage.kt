package com.emduah.personal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emduah.personal.data.Screen
import com.emduah.personal.wallpaper.LockScreenViews

@Composable internal fun WallpaperPage(vm: PersonalViewModel, screen: Screen, selectScreen: (Screen) -> Unit, onActivateMotion: () -> Unit, edit: () -> Unit) {
    Text("A fresh view, at your pace.", style = MaterialTheme.typography.bodyLarge, color = Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Screen.entries.forEach { s -> FilterChip(selected = screen == s, onClick = { selectScreen(s) }, label = { Text(if (s == Screen.HOME) "Home screen" else "Lock screen") }) }
    }
    val inherited = screen == Screen.LOCK && !vm.separateLock
    val source = if (inherited) Screen.HOME else screen
    val p = vm.profiles.getValue(source)
    val media = vm.current.getValue(source)
    val remainingViews = if (LockScreenViews.applies(source, vm.separateLock) && media.file.isNotBlank()) (vm.minimumLockViews - vm.lockViewCount).coerceAtLeast(0) else 0
    val remainingHomeViews = if (source == Screen.HOME && media.file.isNotBlank()) (vm.minimumHomeViews - vm.homeViewCount).coerceAtLeast(0) else 0
    val timing = rememberRotationTiming(p, media.changedAt, remainingViews, remainingHomeViews)
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
    if (screen == Screen.LOCK) MinimumViewSettings(screen, vm.minimumLockViews, vm.lockViewCount, !vm.separateLock, vm::updateMinimumLockViews)
    else MinimumViewSettings(screen, vm.minimumHomeViews, vm.homeViewCount, !vm.separateLock, vm::updateMinimumHomeViews)
    val requiresViews = (vm.minimumLockViews > 0 && LockScreenViews.applies(source, vm.separateLock)) || (source == Screen.HOME && vm.minimumHomeViews > 0)
    if (requiresViews && (!p.motion || inherited)) {
        LiveWallpaperActivationPrompt(rememberLiveWallpaperActive(source, vm.separateLock), source, vm.separateLock, onActivateMotion)
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

@Composable internal fun MinimumViewSettings(screen: Screen, minimum: Int, views: Int, shared: Boolean, save: (Int) -> Unit) {
    val destination = if (screen == Screen.HOME) "home screen" else "lock screen"
    val fieldLabel = if (screen == Screen.HOME) "Minimum home-screen views" else "Minimum lock-screen views"
    var draft by rememberSaveable(screen, minimum) { mutableStateOf((if (minimum > 0) minimum else LockScreenViews.DEFAULT_MINIMUM).toString()) }
    val value = draft.toIntOrNull()
    CardBlock {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Wait until viewed", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(minimum > 0, { save(if (it) LockScreenViews.DEFAULT_MINIMUM else 0) })
        }
        Text(if (shared) "Hold shared media until both enabled view minimums are met." else "Hold $destination media until it has been shown enough times.", color = Muted)
        if (minimum > 0) {
            OutlinedTextField(draft, { draft = it.filter(Char::isDigit).take(4) }, label = { Text(fieldLabel) }, supportingText = { Text("1–1000 · each awake $destination visit counts once") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { value?.let(save) }, enabled = value != null && value in 1..1000 && value != minimum, modifier = Modifier.fillMaxWidth()) { Text("Save minimum views") }
            Text("Viewed $views of $minimum times · Manual Next skips this requirement and starts a new count.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("Requires live wallpaper for images and videos. Previews and always-on display do not count.", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (screen == Screen.HOME) Text("Returning from an app counts as a new visit. Remaining on the launcher does not add views.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
