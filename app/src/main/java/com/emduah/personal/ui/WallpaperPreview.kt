package com.emduah.personal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emduah.personal.data.*
import com.emduah.personal.wallpaper.WallpaperImages
import com.emduah.personal.wallpaper.RotationStatus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun WallpaperPreview(media: CurrentMedia, screen: Screen, videoSeconds: Int, timing: RotationTiming) {
    var playing by remember(media.file, screen) { mutableStateOf(true) }
    var playbackError by remember(media.file, screen) { mutableStateOf<String?>(null) }
    val hasVideo = media.type == "video" && File(media.file).isFile
    val bitmap by produceState<android.graphics.Bitmap?>(null, media.file) {
        value = withContext(Dispatchers.IO) { runCatching { if (media.type == "image" && File(media.file).isFile) WallpaperImages.decode(File(media.file), 500, 700) else null }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF27272A))) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = media.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (hasVideo) key(media.file, screen) {
            VideoPreview(File(media.file), videoSeconds, playing && playbackError == null, Modifier.fillMaxSize()) { playbackError = it }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .5f to Color.Transparent, 1f to Color.Black.copy(alpha = .85f))))
        if (hasVideo && playbackError == null) IconButton(
            onClick = { playing = !playing },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = .35f), contentColor = Color.White)
        ) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = if (playing) "Pause preview" else "Play preview") }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(media.name.ifBlank { "No wallpaper selected" }, color = Color.White.copy(alpha = .8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                RotationStatus.previewLine(timing.updated, if (playbackError == null) timing.status else "Preview unavailable"),
                modifier = Modifier.semantics { contentDescription = listOf(timing.updated, playbackError ?: timing.status).filter { it.isNotBlank() }.joinToString(". ") },
                color = Color.White.copy(alpha = .9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
