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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore

internal fun filterSummary(p: Profile): String = runCatching {
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
            OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(items.find { it.first == value }?.second ?: value); Icon(Icons.Outlined.ExpandMore, contentDescription = null) } }
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

@Composable internal fun ProfileEditor(p: Profile, options: JSONObject, onActivateMotion: () -> Unit, onSave: (Profile) -> Unit, onCancel: () -> Unit, onDirtyChange: (Boolean) -> Unit) {
    var minutes by rememberSaveable(p.screen) { mutableStateOf(p.minutes.toString()) }
    var maxMb by rememberSaveable(p.screen) { mutableStateOf(p.maxDownloadMb.toString()) }
    var videoSeconds by rememberSaveable(p.screen) { mutableStateOf(p.videoSeconds.toString()) }
    var wifi by rememberSaveable(p.screen) { mutableStateOf(p.wifiOnly) }
    var raw by rememberSaveable(p.screen) { mutableStateOf(JSONObject(p.filters).apply { if (!p.motion) put("type", "image") }.toString()) }
    var extras by rememberSaveable(p.screen) { mutableStateOf(WallpaperFilters.extraFilters(JSONObject(p.filters)).toString(2)) }
    var advanced by rememberSaveable(p.screen) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val initialRaw = remember(p.screen) { JSONObject(p.filters).apply { if (!p.motion) put("type", "image") }.toString() }
    SideEffect { onDirtyChange(minutes != p.minutes.toString() || maxMb != p.maxDownloadMb.toString() || videoSeconds != p.videoSeconds.toString() || wifi != p.wifiOnly || raw != initialRaw || extras != WallpaperFilters.extraFilters(JSONObject(p.filters)).toString(2)) }
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
        Text("Expert filters", fontSize = 16.sp)
        Text("Use MediaVault filter JSON for model, prompt, caption, metadata search, LoRA, subreddit, processing state, audio presence and other detailed filters. Metadata relevance ordering requires metadata search text here.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        OutlinedTextField(extras, { extras = it; error = "" }, label = { Text("Additional filter JSON") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    }
    CardBlock {
        Text("Schedule & data", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Change every (minutes)") }, supportingText = { Text("Minimum 15 min · Android controls exact timing") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maxMb, { maxMb = it.filter(Char::isDigit) }, label = { Text("Maximum download size (MB)") }, supportingText = { Text("1–1024 MB · larger files are excluded") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (motion) OutlinedTextField(videoSeconds, { videoSeconds = it.filter(Char::isDigit) }, label = { Text("Video loop length (seconds)") }, supportingText = { Text("0 = full video · maximum 3600 seconds") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Unmetered network only", modifier = Modifier.weight(1f)); Switch(wifi, { wifi = it }) }
    }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    Button(onClick = { save(p.enabled) }, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
}
