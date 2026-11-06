package com.emduah.personal.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emduah.personal.wallpaper.WallpaperRenderer
import java.io.File

/** Uses the downloaded file and the same muted, cropped renderer as the wallpaper. */
@Composable internal fun VideoPreview(file: File, seconds: Int, playing: Boolean, modifier: Modifier = Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reportError by rememberUpdatedState(onError)
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var visible by remember { mutableStateOf(false) }
    val controller = remember { VideoPreviewController(context) { reportError(it) } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    AndroidView(
        factory = { controller.view },
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            visible = coordinates.isAttached && bounds.width > 0 && bounds.height > 0
        },
        update = { controller.update(file, seconds, playing && resumed && visible) }
    )
}

private class VideoPreviewController(context: Context, private val onError: (String) -> Unit) : TextureView.SurfaceTextureListener {
    private val main = Handler(Looper.getMainLooper())
    val view = TextureView(context).apply { surfaceTextureListener = this@VideoPreviewController }
    private var renderer: WallpaperRenderer? = null
    private var output: Surface? = null
    private var file = File("")
    private var seconds = 60
    private var playing = false
    private var closed = false

    fun update(file: File, seconds: Int, playing: Boolean) {
        this.file = file
        this.seconds = seconds
        this.playing = playing
        renderer?.load(file, "video", seconds, playing)
    }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        output = Surface(texture)
        renderer = WallpaperRenderer(output!!) { message -> main.post { if (!closed) onError(message) } }
        renderer?.resize(width, height)
        renderer?.load(file, "video", seconds, playing)
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { renderer?.resize(width, height) }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { releaseOutput(); return true }
    private fun releaseOutput() { renderer?.close(); renderer = null; output?.release(); output = null }
    fun close() { closed = true; releaseOutput(); main.removeCallbacksAndMessages(null) }
}
