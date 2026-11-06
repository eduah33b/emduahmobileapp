package com.emduah.personal.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emduah.personal.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.io.IOException
import java.util.concurrent.CancellationException

class RotationWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    companion object { private val gate = Mutex() }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { gate.withLock { rotate() } }
    private fun rotate(): Result {
        val screen = Screen.entries.find { it.key == inputData.getString("screen") } ?: return Result.failure()
        val store = VaultStore(applicationContext)
        val p = store.profile(screen)
        if (!p.enabled || (screen == Screen.LOCK && !store.separateLock())) return Result.success()
        val connection = store.connection()
        if (connection.token.isBlank()) return Result.failure()
        if (!RotationNetwork.allowed(applicationContext, p.wifiOnly)) return Result.retry()
        val old = store.current(screen)
        val directory = File(applicationContext.filesDir, "wallpapers").apply { mkdirs() }
        // One worker across both screens. Remove leftovers from interrupted downloads.
        directory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        val temp = File(directory, "${UUID.randomUUID()}.part")
        var committedFile: File? = null
        try {
            p.validated()
            val manager = WallpaperManager.getInstance(applicationContext)
            require(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { "Wallpaper changes are disabled on this device." }
            val shared = screen == Screen.HOME && !store.separateLock()
            val homeUsesVault = manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.serviceName == VaultWallpaperService::class.java.name
            // A shared live lock screen can have no separate WallpaperInfo or ID (Samsung).
            val lockUsesVault = WallpaperRouting.lockUsesVault(
                homeUsesVault,
                manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.serviceName == VaultWallpaperService::class.java.name,
                manager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0
            )
            val useLive = if (screen == Screen.LOCK) lockUsesVault else homeUsesVault && (!shared || lockUsesVault)
            if (p.motion) require(useLive) { "Activate MediaVault motion for ${if (shared) "both screens" else screen.key} using the system wallpaper picker first." }
            store.saveCurrent(screen, old.copy(message = "Finding your next wallpaper…"))
            val api = VaultApi(store)
            val selected = api.next(connection, p, old)
            require(p.motion || selected.type == "image") { "Image rotation received a video. Check the filters." }
            store.saveCurrent(screen, old.copy(message = "Downloading ${selected.name}…"))
            if (!RotationNetwork.allowed(applicationContext, p.wifiOnly)) return Result.retry()
            var lastNetworkCheck = 0L
            var networkAllowed = true
            api.download(selected, temp, p.maxDownloadMb.toLong() * 1024 * 1024) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastNetworkCheck >= 1000) {
                    lastNetworkCheck = now
                    networkAllowed = RotationNetwork.allowed(applicationContext, p.wifiOnly)
                }
                !isStopped && networkAllowed
            }
            if (isStopped || store.profile(screen).revision != p.revision || (store.connection().sessionId != connection.sessionId || store.connection().baseUrl != connection.baseUrl)) return Result.success()
            val currentFile = File(directory, "${screen.key}-${UUID.randomUUID()}.media")
            val metrics = applicationContext.resources.displayMetrics
            if (selected.type == "video") {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(temp.path)
                    require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "This video cannot be played on your phone." }
                    // Decode one frame before replacing the working wallpaper.
                    val frame = retriever.getFrameAtTime(0) ?: error("This video codec is not supported on your phone.")
                    frame.recycle()
                }
            } else {
                val bitmap = WallpaperImages.decode(temp, metrics.widthPixels, metrics.heightPixels)
                try {
                    if (!useLive) {
                        val crop = WallpaperImages.crop(bitmap, metrics.widthPixels, metrics.heightPixels)
                        try { manager.setBitmap(crop, null, false, if (screen == Screen.LOCK) WallpaperManager.FLAG_LOCK else if (shared) WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM) } finally { crop.recycle() }
                    }
                } finally { bitmap.recycle() }
            }
            check(temp.renameTo(currentFile)) { "Unable to store the downloaded wallpaper." }
            committedFile = currentFile
            store.saveCurrent(screen, CurrentMedia(selected.id, selected.name, selected.type, currentFile.path, System.currentTimeMillis(), selected.nextPosition, "Wallpaper updated", selected.id))
            // Keep only the active file for each screen; the live engine watches this preference.
            val retained = Screen.entries.map { store.current(it).file }.toSet()
            directory.listFiles()?.filter { it.path !in retained }?.forEach { it.delete() }
            return Result.success()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            val message = if (e is ApiException && e.status == 401) "Session expired. Reconnect to MediaVault." else e.message ?: "Wallpaper update failed."
            store.saveCurrent(screen, store.current(screen).copy(message = message))
            if (!RotationNetwork.allowed(applicationContext, p.wifiOnly)) return Result.retry()
            val transient = e is IOException && (e !is ApiException || e.status == 429 || e.status >= 500)
            return if (transient && runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            temp.delete()
            committedFile?.let { if (store.current(screen).file != it.path) it.delete() }
        }
    }
}
