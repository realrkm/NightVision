package com.nightvision.app.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nightvision.app.model.EnhancementMode
import com.nightvision.app.model.ManualCameraSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * OpenGL ES 3.0 GLSurfaceView.Renderer that consumes CameraX frames via an external OES
 * SurfaceTexture and applies real-time night-vision, monochrome, or AI-enhanced shaders.
 */
class NightVisionRenderer(
    private val onSurfaceTextureAvailable: (SurfaceTexture) -> Unit,
    private val onFrameAvailable: () -> Unit = {},
    private val onFpsUpdated: (Int) -> Unit = {}
) : GLSurfaceView.Renderer {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Fullscreen quad: (x, y) + (u, v)
    private val quadCoords = floatArrayOf(
        -1f, -1f, 0f, 0f,
         1f, -1f, 1f, 0f,
        -1f,  1f, 0f, 1f,
         1f,  1f, 1f, 1f
    )
    private val quadBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(quadCoords.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quadCoords)
            position(0)
        }

    // GL handles (GL thread only)
    private var oesProgram = 0
    private var aiProgram = 0
    private var oesTextureId = 0
    private var aiTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null

    // Uniform locations: OES program
    private var oesVertexMatrixLoc = -1
    private var oesTexMatrixLoc = -1
    private var oesCameraTexLoc = -1
    private var oesModeLoc = -1
    private var oesGainLoc = -1
    private var oesContrastLoc = -1
    private var oesScanlinesLoc = -1
    private var oesResolutionLoc = -1

    // Uniform locations: AI 2D program
    private var aiVertexMatrixLoc = -1
    private var aiTexLoc = -1
    private var aiGainLoc = -1
    private var aiContrastLoc = -1

    // Matrices (GL thread only)
    private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val vertexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    // Viewport & aspect tracking
    @Volatile
    private var surfaceWidth = 1

    @Volatile
    private var surfaceHeight = 1

    @Volatile
    private var cameraAspectRatio = 16f / 9f

    private var aiTexWidth = 0
    private var aiTexHeight = 0
    private var hasAiTexture = false

    // State shared from UI / camera threads
    @Volatile
    private var currentMode: EnhancementMode = EnhancementMode.AI_LOW_LIGHT

    @Volatile
    private var digitalGain: Float = 1.6f

    @Volatile
    private var contrast: Float = 1.25f

    @Volatile
    private var scanlinesEnabled: Boolean = true

    private val hasNewCameraFrame = AtomicBoolean(false)
    private val latestAiBitmap = AtomicReference<Bitmap?>(null)
    private val aiBitmapDirty = AtomicBoolean(false)
    private val pendingCaptureCallback = AtomicReference<((Bitmap) -> Unit)?>(null)

    // FPS tracking (GL thread only)
    private var fpsFrameCount = 0
    private var fpsLastReportMs = 0L

    fun setMode(mode: EnhancementMode) {
        currentMode = mode
        if (mode != EnhancementMode.AI_LOW_LIGHT) {
            hasAiTexture = false
            latestAiBitmap.set(null)
            aiBitmapDirty.set(false)
        }
    }

    fun updateSettings(settings: ManualCameraSettings) {
        digitalGain = settings.digitalGain
        contrast = settings.contrast
        scanlinesEnabled = settings.scanlinesEnabled
    }

    fun setCameraAspectRatio(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            cameraAspectRatio = width.toFloat() / height.toFloat()
        }
    }

    fun updateAiBitmap(bitmap: Bitmap) {
        latestAiBitmap.set(bitmap)
        aiBitmapDirty.set(true)
        onFrameAvailable()
    }

    fun clearAiBitmap() {
        latestAiBitmap.set(null)
        aiBitmapDirty.set(false)
        hasAiTexture = false
    }

    /**
     * Captures the next rendered frame (with the active shader or AI effect baked in)
     * via glReadPixels on the GL thread and delivers the resulting upright [Bitmap] on the main thread.
     */
    fun captureNextFrame(onCaptured: (Bitmap) -> Unit) {
        pendingCaptureCallback.set(onCaptured)
        onFrameAvailable()
    }

    fun release() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release()
        surfaceTexture = null
        latestAiBitmap.set(null)
        pendingCaptureCallback.set(null)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        oesProgram = NightVisionShaders.createProgram(
            NightVisionShaders.OES_VERTEX_SHADER,
            NightVisionShaders.OES_FRAGMENT_SHADER
        )
        oesVertexMatrixLoc = GLES30.glGetUniformLocation(oesProgram, "uVertexMatrix")
        oesTexMatrixLoc = GLES30.glGetUniformLocation(oesProgram, "uTexMatrix")
        oesCameraTexLoc = GLES30.glGetUniformLocation(oesProgram, "uCameraTex")
        oesModeLoc = GLES30.glGetUniformLocation(oesProgram, "uMode")
        oesGainLoc = GLES30.glGetUniformLocation(oesProgram, "uGain")
        oesContrastLoc = GLES30.glGetUniformLocation(oesProgram, "uContrast")
        oesScanlinesLoc = GLES30.glGetUniformLocation(oesProgram, "uScanlines")
        oesResolutionLoc = GLES30.glGetUniformLocation(oesProgram, "uResolution")

        aiProgram = NightVisionShaders.createProgram(
            NightVisionShaders.AI_VERTEX_SHADER,
            NightVisionShaders.AI_FRAGMENT_SHADER
        )
        aiVertexMatrixLoc = GLES30.glGetUniformLocation(aiProgram, "uVertexMatrix")
        aiTexLoc = GLES30.glGetUniformLocation(aiProgram, "uAiTex")
        aiGainLoc = GLES30.glGetUniformLocation(aiProgram, "uGain")
        aiContrastLoc = GLES30.glGetUniformLocation(aiProgram, "uContrast")

        val textures = IntArray(2)
        GLES30.glGenTextures(2, textures, 0)
        oesTextureId = textures[0]
        aiTextureId = textures[1]

        // External OES texture for CameraX Preview
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )

        // Standard 2D texture for AI-enhanced Bitmaps
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, aiTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        surfaceTexture?.release()
        val st = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                hasNewCameraFrame.set(true)
                onFrameAvailable()
            }
        }
        surfaceTexture = st
        fpsLastReportMs = SystemClock.elapsedRealtime()
        fpsFrameCount = 0

        mainHandler.post {
            onSurfaceTextureAvailable(st)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val st = surfaceTexture
        if (st != null && hasNewCameraFrame.getAndSet(false)) {
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
            } catch (_: IllegalStateException) {
                // SurfaceTexture may be in teardown
            }
        }

        if (currentMode == EnhancementMode.AI_LOW_LIGHT) {
            uploadPendingAiBitmapIfNeeded()
        }

        val useAiTexture = currentMode == EnhancementMode.AI_LOW_LIGHT && hasAiTexture
        if (useAiTexture) {
            drawAiTexture()
        } else {
            drawOesTexture()
        }

        val captureCallback = pendingCaptureCallback.getAndSet(null)
        if (captureCallback != null) {
            val captured = readPixelsToBitmap(surfaceWidth, surfaceHeight)
            mainHandler.post { captureCallback(captured) }
        }

        updateFps()
    }

    private fun uploadPendingAiBitmapIfNeeded() {
        if (!aiBitmapDirty.getAndSet(false)) return
        val bmp = latestAiBitmap.get() ?: return
        if (bmp.isRecycled) return

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, aiTextureId)
        if (bmp.width != aiTexWidth || bmp.height != aiTexHeight) {
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            aiTexWidth = bmp.width
            aiTexHeight = bmp.height
        } else {
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bmp)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        hasAiTexture = true
    }

    private fun drawOesTexture() {
        computeCenterCropMatrix(cameraAspectRatio)

        GLES30.glUseProgram(oesProgram)
        bindQuadAttributes()

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glUniform1i(oesCameraTexLoc, 0)

        GLES30.glUniformMatrix4fv(oesVertexMatrixLoc, 1, false, vertexMatrix, 0)
        GLES30.glUniformMatrix4fv(oesTexMatrixLoc, 1, false, texMatrix, 0)

        val modeInt = when (currentMode) {
            EnhancementMode.AI_LOW_LIGHT -> 0
            EnhancementMode.GREEN_NIGHT_VISION -> 1
            EnhancementMode.HIGH_CONTRAST_BW -> 2
        }
        GLES30.glUniform1i(oesModeLoc, modeInt)
        GLES30.glUniform1f(oesGainLoc, digitalGain)
        GLES30.glUniform1f(oesContrastLoc, contrast)
        GLES30.glUniform1i(oesScanlinesLoc, if (scanlinesEnabled) 1 else 0)
        GLES30.glUniform2f(oesResolutionLoc, surfaceWidth.toFloat(), surfaceHeight.toFloat())

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        disableQuadAttributes()
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun drawAiTexture() {
        computeCenterCropMatrix(cameraAspectRatio)

        GLES30.glUseProgram(aiProgram)
        bindQuadAttributes()

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, aiTextureId)
        GLES30.glUniform1i(aiTexLoc, 0)

        GLES30.glUniformMatrix4fv(aiVertexMatrixLoc, 1, false, vertexMatrix, 0)
        GLES30.glUniform1f(aiGainLoc, digitalGain)
        GLES30.glUniform1f(aiContrastLoc, contrast)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        disableQuadAttributes()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun computeCenterCropMatrix(contentAspect: Float) {
        Matrix.setIdentityM(vertexMatrix, 0)
        val viewAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        if (contentAspect <= 0f || viewAspect <= 0f) return

        if (contentAspect > viewAspect) {
            // Content is wider than view: scale X up to crop sides
            Matrix.scaleM(vertexMatrix, 0, contentAspect / viewAspect, 1f, 1f)
        } else {
            // Content is taller than view: scale Y up to crop top/bottom
            Matrix.scaleM(vertexMatrix, 0, 1f, viewAspect / contentAspect, 1f)
        }
    }

    private fun bindQuadAttributes() {
        val stride = 4 * Float.SIZE_BYTES
        quadBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, quadBuffer)

        quadBuffer.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, quadBuffer)
    }

    private fun disableQuadAttributes() {
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun readPixelsToBitmap(width: Int, height: Int): Bitmap {
        val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer)

        val rawBytes = ByteArray(width * height * 4)
        pixelBuffer.rewind()
        pixelBuffer.get(rawBytes)

        // Flip vertically because OpenGL glReadPixels returns bottom-to-top rows
        val argbPixels = IntArray(width * height)
        for (y in 0 until height) {
            val srcRowOffset = (height - 1 - y) * width * 4
            val dstRowOffset = y * width
            for (x in 0 until width) {
                val idx = srcRowOffset + x * 4
                val r = rawBytes[idx].toInt() and 0xFF
                val g = rawBytes[idx + 1].toInt() and 0xFF
                val b = rawBytes[idx + 2].toInt() and 0xFF
                val a = rawBytes[idx + 3].toInt() and 0xFF
                argbPixels[dstRowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun updateFps() {
        fpsFrameCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - fpsLastReportMs
        if (elapsed >= 500L) {
            val fps = (fpsFrameCount * 1000f / elapsed).roundToInt()
            fpsFrameCount = 0
            fpsLastReportMs = now
            mainHandler.post { onFpsUpdated(fps) }
        }
    }
}
