package com.emduah.personal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emduah.personal.data.*
import com.emduah.personal.wallpaper.WallpaperImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable internal fun WallpaperPreview(media: CurrentMedia, screen: Screen, videoSeconds: Int) {
    var playing by remember(media.file, screen) { mutableStateOf(true) }
    var playbackError by remember(media.file, screen) { mutableStateOf<String?>(null) }
    val hasVideo = media.type == "video" && File(media.file).isFile
    val bitmap by produceState<android.graphics.Bitmap?>(null, media.file) {
        value = withContext(Dispatchers.IO) { runCatching { if (media.type == "image" && File(media.file).isFile) WallpaperImages.decode(File(media.file), 500, 700) else null }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF38383D), Color(0xFF18181B), Color(0xFF52525B))))) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = media.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (hasVideo) key(media.file, screen) {
            VideoPreview(File(media.file), videoSeconds, playing && playbackError == null, Modifier.fillMaxSize()) { playbackError = it }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
        Column(Modifier.align(Alignment.TopStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (hasVideo) "VIDEO · MUTED" else if (screen == Screen.HOME) "HOME SCREEN" else "LOCK SCREEN", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, letterSpacing = 1.8.sp)
            if (media.id == 0L) Text("Your next wallpaper\nstarts here.", fontSize = 30.sp, lineHeight = 35.sp, color = Color.White, fontWeight = FontWeight.Light)
        }
        if (hasVideo && playbackError == null) TextButton(
            onClick = { playing = !playing },
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) { Text(if (playing) "Pause preview" else "Play preview") }
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(media.name.ifBlank { "No wallpaper downloaded yet" }, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(playbackError?.let { "Preview unavailable: $it" } ?: media.message, color = Color.White.copy(alpha = .85f), fontSize = 12.sp, maxLines = 3)
            if (media.changedAt > 0) Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(media.changedAt)), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
    }
}

