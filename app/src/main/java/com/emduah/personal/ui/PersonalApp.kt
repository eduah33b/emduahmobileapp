package com.emduah.personal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import android.app.DatePickerDialog
import java.time.LocalDate
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emduah.personal.data.*
import com.emduah.personal.wallpaper.WallpaperImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Ink = Color(0xFF101512)
private val Panel = Color(0xFF1B241E)
private val Lime = Color(0xFFC8EDAB)
private val Muted = Color(0xFFACB9AF)
private val palette = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Color(0xFFEDF3EB), surfaceVariant = Color(0xFF26332A), onSurfaceVariant = Muted, secondaryContainer = Color(0xFF35472F), onSecondaryContainer = Lime)

@Composable fun PersonalApp(vm: PersonalViewModel, onActivateMotion: () -> Unit) {
    MaterialTheme(colorScheme = palette) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        var editing by rememberSaveable { mutableStateOf(false) }
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Ink) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0; editing = false }, icon = { Text("◈", fontSize = 25.sp) }, label = { Text("Wallpapers") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1; editing = false }, icon = { Text("◎", fontSize = 25.sp) }, label = { Text("Connection") })
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("emduah", fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Text("PERSONAL SPACE", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                if (vm.notice.isNotBlank()) Surface(color = Color(0xFF29382C), shape = RoundedCornerShape(14.dp)) { Text(vm.notice, Modifier.padding(14.dp), fontSize = 13.sp) }
                if (tab == 1) ConnectionPage(vm) { tab = 0 }
                else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (editing) "Make it yours." else "A fresh perspective.", fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
                        Text(if (editing) "Choose what appears, and when it changes." else "Your MediaVault. A little different, every time.", color = Muted, fontSize = 14.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Screen.entries.forEach { s -> FilterChip(selected = screen == s, onClick = { screen = s; editing = false }, label = { Text(if (s == Screen.HOME) "Home screen" else "Lock screen") }) }
                    }
                    if (screen == Screen.LOCK) CardBlock {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Separate lock settings", fontSize = 18.sp)
                                Text(if (vm.separateLock) "Own filters, media and timing" else "Using the home screen setup", color = Muted, fontSize = 12.sp)
                            }
                            Switch(vm.separateLock, vm::setLockOverride)
                        }
                    }
                    val p = vm.profiles.getValue(screen)
                    if (vm.connection.token.isBlank() && !editing) {
                        CardBlock {
                            Text("Start with your vault", fontSize = 20.sp)
                            Text("Connect your web app to turn your own images and videos into an evolving wallpaper.", color = Muted)
                            Button(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Connect MediaVault  →") }
                            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Configure before connecting") }
                        }
                    } else if (editing) {
                        ProfileEditor(p, vm.options, onActivateMotion, onSave = { if (vm.save(it)) editing = false }, onCancel = { editing = false })
                    } else {
                        val previewScreen = if (screen == Screen.LOCK && !vm.separateLock) Screen.HOME else screen
                        WallpaperPreview(vm.current.getValue(previewScreen), screen, vm.profiles.getValue(previewScreen).videoSeconds)
                        if (screen == Screen.LOCK && !vm.separateLock) {
                            CardBlock {
                                Text("Following your home screen", fontSize = 19.sp)
                                Text("Both screens use one configuration and the same downloaded file. Enable separate lock settings above to choose different images, videos, filters and timing.", color = Muted)
                            }
                        } else {
                            CardBlock {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(if (p.enabled) "Rotation is on" else "Rotation is paused", fontSize = 19.sp)
                                        Text("Every ${p.minutes} min · ${if (p.wifiOnly) "Unmetered network" else "Any network"}", color = Muted, fontSize = 12.sp)
                                    }
                                    Switch(checked = p.enabled, onCheckedChange = { vm.save(p.copy(enabled = it)) })
                                }
                                Text(filterSummary(p), color = Muted, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("Configure") }
                                    Button(onClick = { vm.next(screen) }, enabled = p.enabled, modifier = Modifier.weight(1f)) { Text("Next now  ↗") }
                                }
                                if (p.motion) OutlinedButton(onClick = onActivateMotion, modifier = Modifier.fillMaxWidth()) { Text("Activate live wallpaper") }
                            }
                        }
                        Text("Made to change at your pace", color = Lime, fontSize = 13.sp)
                        Text("Updates run in the background, including after a restart. Android may delay them while your phone is idle or low on battery. Only the current file for each screen is kept.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Panel) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
}

@Composable private fun WallpaperPreview(media: CurrentMedia, screen: Screen, videoSeconds: Int) {
    var playing by remember(media.file, screen) { mutableStateOf(true) }
    var playbackError by remember(media.file, screen) { mutableStateOf<String?>(null) }
    val hasVideo = media.type == "video" && File(media.file).isFile
    val bitmap by produceState<android.graphics.Bitmap?>(null, media.file) {
        value = withContext(Dispatchers.IO) { runCatching { if (media.type == "image" && File(media.file).isFile) WallpaperImages.decode(File(media.file), 500, 700) else null }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF3E5843), Color(0xFF1D3329), Color(0xFF607459))))) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = media.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (hasVideo) key(media.file, screen) {
            VideoPreview(File(media.file), videoSeconds, playing && playbackError == null, Modifier.fillMaxSize()) { playbackError = it }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
        Column(Modifier.align(Alignment.TopStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (hasVideo) "VIDEO · MUTED" else if (screen == Screen.HOME) "YOUR HOME, REIMAGINED" else "JUST FOR YOUR LOCK SCREEN", color = Color.White.copy(alpha = .8f), fontSize = 10.sp, letterSpacing = 1.8.sp)
            if (media.id == 0L) Text("Space for something\nbeautiful.", fontSize = 30.sp, lineHeight = 35.sp, color = Color.White, fontWeight = FontWeight.Light)
        }
        if (hasVideo && playbackError == null) TextButton(
            onClick = { playing = !playing },
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) { Text(if (playing) "Pause preview" else "Play preview") }
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(media.name.ifBlank { "No wallpaper downloaded yet" }, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(playbackError?.let { "Preview unavailable: $it" } ?: media.message, color = Color(0xFFD8E6D8), fontSize = 12.sp, maxLines = 3)
            if (media.changedAt > 0) Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(media.changedAt)), color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable private fun ConnectionPage(vm: PersonalViewModel, done: () -> Unit) {
    val context = LocalContext.current
    val openLink: (String) -> Unit = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    Text("Your vault, connected.", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
    Text("MediaVault is one source for your personal app. More tools can live here over time.", color = Muted, fontSize = 14.sp)
    if (vm.connection.token.isNotBlank()) {
        CardBlock {
            Text("●  Connected as ${vm.connection.name}", color = Lime)
            Text(vm.connection.baseUrl, color = Muted)
            Text("Your session is encrypted with Android Keystore. Your web app password is never saved.", color = Muted, fontSize = 13.sp)
            OutlinedButton(onClick = vm::loadOptions, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh filter options") }
            OutlinedButton(onClick = vm::disconnect, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Disconnect & pause rotation") }
        }
        val devices = vm.devices.optJSONArray("sessions")
        if (devices != null) CardBlock {
            Text("Signed-in devices", fontSize = 20.sp)
            for (index in 0 until devices.length()) {
                val device = devices.getJSONObject(index)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.getString("deviceName"))
                        Text(if (device.optBoolean("current")) "This phone" else device.optString("lastUsedAt"), color = Muted, fontSize = 12.sp)
                    }
                    if (!device.optBoolean("current")) TextButton(onClick = { vm.revokeDevice(device.getString("id")) }) { Text("Sign out") }
                }
            }
            TextButton(onClick = vm::loadDevices) { Text("Refresh devices") }
        }
        TextButton(onClick = { vm.accountLink(vm.connection.baseUrl, false, openLink) }) { Text("Manage web account") }
    } else {
        var base by rememberSaveable { mutableStateOf(vm.connection.baseUrl.ifBlank { "https://mediavault.emduah.xyz" }) }
        var email by rememberSaveable { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        CardBlock {
            Text("Connect MediaVault", fontSize = 20.sp)
            OutlinedTextField(base, { base = it }, label = { Text("Web app URL") }, placeholder = { Text("https://app.example.com") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.login(base, email, password) { password = ""; done() } }, enabled = !vm.busy && base.isNotBlank() && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Connect securely  →")
            }
        }
        TextButton(onClick = { vm.accountLink(base, true, openLink) }, enabled = base.isNotBlank()) { Text("Forgot password?") }
        TextButton(onClick = { vm.accountLink(base, false, openLink) }, enabled = base.isNotBlank()) { Text("Verify email / open web sign-in") }
        Text("Sign in with your existing web app account. Your password is used only to sign in. Your email must be verified.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

private fun filterSummary(p: Profile): String = runCatching {
    val j = JSONObject(p.filters)
    val sort = j.optString("sort", "random").replace("random", "stable shuffle").replace('_', ' ')
    val kind = if (!p.motion) "Images" else if (j.optString("type") == "video") "Videos" else "Images & videos"
    "$kind · $sort" + (if (j.optBoolean("favorite")) " · favorites" else "")
}.getOrDefault("Custom filters")

@Composable private fun Picker(label: String, value: String, items: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(items.find { it.first == value }?.second ?: value); Text("⌄") } }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { items.forEach { item -> DropdownMenuItem(text = { Text(item.second) }, onClick = { onChange(item.first); expanded = false }) } }
        }
    }
}

@Composable private fun DateFilter(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
                DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text(value.ifBlank { "Any date" }) }
            if (value.isNotBlank()) TextButton(onClick = { onChange("") }) { Text("Clear") }
        }
    }
}

@Composable private fun ProfileEditor(p: Profile, options: JSONObject, onActivateMotion: () -> Unit, onSave: (Profile) -> Unit, onCancel: () -> Unit) {
    var minutes by rememberSaveable(p.screen) { mutableStateOf(p.minutes.toString()) }
    var maxMb by rememberSaveable(p.screen) { mutableStateOf(p.maxDownloadMb.toString()) }
    var videoSeconds by rememberSaveable(p.screen) { mutableStateOf(p.videoSeconds.toString()) }
    var wifi by rememberSaveable(p.screen) { mutableStateOf(p.wifiOnly) }
    var raw by rememberSaveable(p.screen) { mutableStateOf(JSONObject(p.filters).apply { if (!p.motion) put("type", "image") }.toString()) }
    var extras by rememberSaveable(p.screen) { mutableStateOf(WallpaperFilters.extraFilters(JSONObject(p.filters)).toString(2)) }
    var advanced by rememberSaveable(p.screen) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val j = JSONObject(raw)
    val motion = j.optString("type") != "image"
    fun update(key: String, value: String) {
        raw = JSONObject(raw).apply {
            when {
                key == "rating" -> WallpaperFilters.setMinimumRating(this, value.toIntOrNull())
                value.isBlank() -> remove(key)
                key == "status" -> put(key, JSONArray().put(value))
                key == "favorite" -> put(key, value == "1")
                else -> put(key, value)
            }
        }.toString()
        error = ""
    }
    fun choices(collection: String): List<Pair<String, String>> {
        val list = options.optJSONArray(collection)
        return listOf("" to "Any") + if (list == null) emptyList() else (0 until list.length()).map { index -> list.getJSONObject(index).let { it.getString("id") to it.getString("name") } }
    }
    fun save(enabled: Boolean) {
        runCatching {
            p.copy(
                minutes = minutes.toLongOrNull() ?: 0,
                wifiOnly = wifi,
                maxDownloadMb = maxMb.toIntOrNull() ?: 0,
                videoSeconds = videoSeconds.toIntOrNull() ?: -1,
                motion = motion,
                filters = WallpaperFilters.mergeExtraFilters(JSONObject(raw), extras).toString(),
                enabled = enabled
            ).validated()
        }.onSuccess(onSave).onFailure { error = it.message ?: "Check your settings." }
    }
    CardBlock {
        Text("Choose your media", fontSize = 21.sp)
        Picker("Bucket", j.optString("bucket"), choices("buckets")) { update("bucket", it) }
        Picker("Ordering", j.optString("sort", "random"), listOf("random" to "Stable shuffle · MediaVault order", "r2_modified_desc" to "Newest modified first", "r2_modified_asc" to "Oldest modified first", "rating_desc" to "Highest rated first", "path_asc" to "Path A–Z", "filename_asc" to "Filename A–Z", "filename_desc" to "Filename Z–A", "duration_asc" to "Shortest videos first", "duration_desc" to "Longest videos first", "relevance" to "Metadata relevance", "size_desc" to "Largest files first", "manual" to "Category manual order")) { update("sort", it) }
        Picker("Media type", j.optString("type"), listOf("image" to "Images", "video" to "Videos", "" to "Images & videos")) { update("type", it) }
        if (motion) {
            Text("Videos use MediaVault motion. Apply it to both screens in Android’s wallpaper picker; each screen follows its own configured media.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            OutlinedButton(onClick = onActivateMotion, modifier = Modifier.fillMaxWidth()) { Text("Open system wallpaper picker") }
        }
        Text("Date range", fontSize = 16.sp)
        Text("Last modified · either date can be left open.", color = Muted, fontSize = 12.sp)
        DateFilter("Minimum date", j.optString("date_from")) { update("date_from", it) }
        DateFilter("Maximum date", j.optString("date_to")) { update("date_to", it) }
        Picker("Minimum rating", WallpaperFilters.minimumRating(j)?.toString().orEmpty(), listOf("" to "Any rating") + (1..5).map { it.toString() to "$it ${if (it == 1) "star" else "stars"} & above" }) { update("rating", it) }
        OutlinedTextField(j.optString("path"), { update("path", it) }, label = { Text("Path") }, supportingText = { Text("Search anywhere in path; prefix - to exclude…") }, modifier = Modifier.fillMaxWidth())
    }
    OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) { Text(if (advanced) "Hide advanced filters" else "Advanced filters") }
    if (advanced) CardBlock {
        Text("Advanced filters", fontSize = 21.sp)
        OutlinedTextField(j.optString("directory"), { update("directory", it) }, label = { Text("Directory (includes subfolders)") }, modifier = Modifier.fillMaxWidth())
        Picker("Path matching", j.optString("path_mode", "and"), listOf("and" to "Match all included terms", "or" to "Match any included term")) { update("path_mode", it) }
        Picker("Favorites", if (j.has("favorite")) (if (j.optBoolean("favorite")) "1" else "0") else "", listOf("" to "Any", "1" to "Favorites only", "0" to "Exclude favorites")) { update("favorite", it) }
        Picker("Review status", j.optJSONArray("status")?.optString(0).orEmpty(), listOf("" to "Any status", "keep" to "Keep", "maybe" to "Maybe", "unreviewed" to "Unreviewed", "reject" to "Reject", "archived" to "Archived")) { update("status", it) }
        Picker("Tag", j.optString("tag"), choices("tags")) { update("tag", it) }
        Picker("Category", j.optString("category"), choices("categories")) { update("category", it) }
        if (j.optString("sort", "random") == "random") {
            OutlinedTextField(j.optString("seed", "mediavault"), { update("seed", it) }, label = { Text("Shuffle seed") }, supportingText = { Text("Same seed = same MediaVault order.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { update("seed", java.util.UUID.randomUUID().toString()) }) { Text("Reshuffle with a new seed") }
        }
        Text("Additional filters", fontSize = 16.sp)
        Text("Use MediaVault filter JSON for model, prompt, caption, metadata search, LoRA, subreddit, processing state, audio presence and other detailed filters. Metadata relevance ordering requires metadata search text here.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        OutlinedTextField(extras, { extras = it; error = "" }, label = { Text("Additional filter JSON") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("Timing, download & playback", fontSize = 19.sp)
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Change every (minutes)") }, supportingText = { Text("Minimum 15 min · Android controls exact timing") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maxMb, { maxMb = it.filter(Char::isDigit) }, label = { Text("Maximum download size (MB)") }, supportingText = { Text("1–1024 MB · larger files are excluded") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (motion) OutlinedTextField(videoSeconds, { videoSeconds = it.filter(Char::isDigit) }, label = { Text("Video loop length (seconds)") }, supportingText = { Text("0 = full video · maximum 3600 seconds") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Unmetered network only", modifier = Modifier.weight(1f)); Switch(wifi, { wifi = it }) }
    }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    Button(onClick = { save(true) }, modifier = Modifier.fillMaxWidth()) { Text("Save & start rotation") }
    OutlinedButton(onClick = { save(false) }, modifier = Modifier.fillMaxWidth()) { Text("Save paused") }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
}
