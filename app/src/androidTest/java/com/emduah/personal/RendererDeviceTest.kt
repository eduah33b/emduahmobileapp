package com.emduah.personal

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emduah.personal.wallpaper.WallpaperRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@RunWith(AndroidJUnit4::class)
class RendererDeviceTest {
    @Test fun switchesImageToVideoAndBackOnTheSameSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "renderer-${UUID.randomUUID()}").apply { mkdirs() }
        val image = File(directory, "still.png")
        val video = File(directory, "motion.mp4")
        val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        instrumentation.context.assets.open("motion-test.mp4").use { source -> video.outputStream().use { source.copyTo(it) } }
        val errors = ConcurrentLinkedQueue<String>()
        val reader = ImageReader.newInstance(160, 240, PixelFormat.RGBA_8888, 3)
        val renderer = WallpaperRenderer(reader.surface) { errors.add(it) }
        fun awaitColor(matches: (Int, Int, Int) -> Boolean): Boolean {
            val until = SystemClock.uptimeMillis() + 6000
            while (SystemClock.uptimeMillis() < until) {
                reader.acquireLatestImage()?.use { frame ->
                    val plane = frame.planes[0]
                    val offset = 120 * plane.rowStride + 80 * plane.pixelStride
                    val bytes = plane.buffer
                    if (matches(bytes.get(offset).toInt() and 255, bytes.get(offset + 1).toInt() and 255, bytes.get(offset + 2).toInt() and 255)) return true
                }
                if (errors.isNotEmpty()) fail(errors.joinToString())
                SystemClock.sleep(20)
            }
            return false
        }
        try {
            renderer.resize(160, 240)
            renderer.load(image, "image", 60, true)
            assertTrue("Initial image was not rendered", awaitColor { r, g, b -> g > 200 && r < 40 && b < 40 })
            renderer.load(video, "video", 60, true)
            assertTrue("Video did not decode red frames", awaitColor { r, g, b -> r > 180 && g < 70 && b < 70 })
            assertTrue("Video did not advance to blue frames", awaitColor { r, g, b -> b > 180 && r < 70 && g < 70 })
            renderer.load(image, "image", 60, true)
            assertTrue("Image was not restored after video", awaitColor { r, g, b -> g > 200 && r < 40 && b < 40 })
            assertTrue(errors.joinToString(), errors.isEmpty())
        } finally {
            renderer.close()
            SystemClock.sleep(150)
            reader.close()
            image.delete(); video.delete(); directory.delete()
        }
    }
}
