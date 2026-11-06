package com.emduah.personal.wallpaper

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

/** One EGL producer for images and video; Canvas and decoder producers cannot share a Surface. */
class WallpaperRenderer(surface: Surface, private val onError: (String) -> Unit) {
    private val thread = HandlerThread("VaultWallpaperGL").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var closed = false
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var imageProgram = 0
    private var videoProgram = 0
    private var textureId = 0
    private var videoTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null
    private var player: MediaPlayer? = null
    private var prepared = false
    private var visible = false
    private var hasFrame = false
    private var video = false
    private var width = 1
    private var height = 1
    private var mediaWidth = 1
    private var mediaHeight = 1
    private var maxTextureSize = 4096
    private var capSeconds = 60
    private var loaded: String? = null
    private var reported = false
    private val transform = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val vertices = buffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val imageUv = buffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
    private val videoUv = buffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
    private val loopCheck = object: Runnable {
        override fun run() {
            if (closed || !visible || !prepared) return
            safely {
                if (capSeconds > 0 && (player?.currentPosition ?: 0) >= capSeconds * 1000) player?.seekTo(0)
                handler.postDelayed(this, 200)
            }
        }
    }

    init { handler.post { safely {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, null, 0, null, 0)) { "Cannot initialize the wallpaper display." }
        val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 0, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No wallpaper graphics configuration." }
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        window = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, window, window, context)) { "Cannot attach the wallpaper surface." }
        imageProgram = program(false)
        videoProgram = program(true)
        val size = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, size, 0)
        maxTextureSize = size[0].coerceAtLeast(1024)
        draw()
    } } }

    fun resize(w: Int, h: Int) { handler.post { safely { width = w.coerceAtLeast(1); height = h.coerceAtLeast(1); draw() } } }

    fun load(file: File, type: String, cap: Int, isVisible: Boolean) { handler.post { safely {
        capSeconds = cap
        setVisibleOnThread(isVisible)
        val key = file.path + ":" + type
        if (key == loaded) return@safely
        loaded = key
        reported = false
        releaseMedia()
        video = type == "video"
        Matrix.setIdentityM(transform, 0)
        if (!file.isFile) { draw(); return@safely }
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        textureId = texture[0]
        val target = if (video) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        if (video) {
            val stream = SurfaceTexture(textureId)
            videoTexture = stream
            stream.setOnFrameAvailableListener({ incoming ->
                if (!closed && incoming === videoTexture) safely {
                    incoming.updateTexImage()
                    incoming.getTransformMatrix(transform)
                    hasFrame = true
                    if (visible) draw()
                }
            }, handler)
            videoSurface = Surface(stream)
            val media = MediaPlayer()
            player = media
            media.setDataSource(file.path)
            media.setSurface(videoSurface)
            media.isLooping = true
            media.setVolume(0f, 0f)
            media.setOnVideoSizeChangedListener { _, w, h -> mediaWidth = w.coerceAtLeast(1); mediaHeight = h.coerceAtLeast(1) }
            media.setOnPreparedListener { incoming ->
                if (!closed && incoming === player) safely {
                    prepared = true
                    mediaWidth = incoming.videoWidth.coerceAtLeast(1)
                    mediaHeight = incoming.videoHeight.coerceAtLeast(1)
                    if (visible) { incoming.start(); handler.removeCallbacks(loopCheck); handler.post(loopCheck) }
                }
            }
            media.setOnErrorListener { incoming, what, extra ->
                if (incoming === player) { report("Video playback failed ($what/$extra). Try another file."); releaseMedia() }
                true
            }
            media.prepareAsync()
            draw()
        } else {
            val bitmap = WallpaperImages.decode(file, min(width, maxTextureSize / 2), min(height, maxTextureSize / 2))
            try {
                mediaWidth = bitmap.width
                mediaHeight = bitmap.height
                GLUtils.texImage2D(target, 0, bitmap, 0)
                check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "Cannot upload the wallpaper image." }
                hasFrame = true
                draw()
            } finally { bitmap.recycle() }
        }
    } } }

    fun setVisible(value: Boolean) { handler.post { safely { setVisibleOnThread(value) } } }
    private fun setVisibleOnThread(value: Boolean) {
        if (visible == value) return
        visible = value
        handler.removeCallbacks(loopCheck)
        if (prepared) {
            if (value) { player?.start(); handler.post(loopCheck) } else player?.pause()
        }
        if (value) draw()
    }

    private fun draw() {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(.078f, .133f, .098f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (hasFrame && textureId != 0) {
            val program = if (video) videoProgram else imageProgram
            GLES20.glUseProgram(program)
            val position = GLES20.glGetAttribLocation(program, "aPosition")
            val uv = GLES20.glGetAttribLocation(program, "aUv")
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glEnableVertexAttribArray(uv)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, if (video) videoUv else imageUv)
            val mediaAspect = mediaWidth.toFloat() / mediaHeight
            val surfaceAspect = width.toFloat() / height
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uCrop"), min(1f, surfaceAspect / mediaAspect), min(1f, mediaAspect / surfaceAspect))
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTransform"), 1, false, transform, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(if (video) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(uv)
        }
        check(EGL14.eglSwapBuffers(display, window)) { "The wallpaper surface could not display a frame." }
    }

    private fun releaseMedia() {
        handler.removeCallbacks(loopCheck)
        prepared = false
        player?.release(); player = null
        videoSurface?.release(); videoSurface = null
        videoTexture?.setOnFrameAvailableListener(null)
        videoTexture?.release(); videoTexture = null
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        textureId = 0
        hasFrame = false
    }
    private fun safely(block: () -> Unit) {
        if (closed) return
        try { block() } catch (e: Exception) { releaseMedia(); report(e.message ?: "Wallpaper rendering failed.") }
    }
    private fun report(message: String) { if (!closed && !reported) { reported = true; onError(message) } }
    fun close() {
        if (closed) return
        closed = true
        handler.post {
            runCatching {
                releaseMedia()
                if (imageProgram != 0) GLES20.glDeleteProgram(imageProgram)
                if (videoProgram != 0) GLES20.glDeleteProgram(videoProgram)
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                    EGL14.eglReleaseThread()
                }
            }
            thread.quitSafely()
        }
    }
    private fun program(external: Boolean): Int {
        val vertex = "attribute vec2 aPosition; attribute vec2 aUv; uniform vec2 uCrop; uniform mat4 uTransform; varying vec2 vUv; void main(){ gl_Position=vec4(aPosition,0.0,1.0); vUv=(uTransform*vec4((aUv-0.5)*uCrop+0.5,0.0,1.0)).xy; }"
        val fragment = (if (external) "#extension GL_OES_EGL_image_external : require\n" else "") + "precision mediump float; varying vec2 vUv; uniform ${if (external) "samplerExternalOES" else "sampler2D"} uTexture; void main(){ gl_FragColor=texture2D(uTexture,vUv); }"
        fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
            val result = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, result, 0)
            check(result[0] != 0) { GLES20.glGetShaderInfoLog(id) }
            return id
        }
        val v = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val f = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, v); GLES20.glAttachShader(result, f); GLES20.glLinkProgram(result)
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        val linked = IntArray(1); GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { GLES20.glGetProgramInfoLog(result) }
        return result
    }
    private fun buffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
}
