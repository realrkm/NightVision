package com.nightvision.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nightvision.app.gl.NightVisionRenderer
import com.nightvision.app.ml.ZeroDceEnhancer
import com.nightvision.app.model.CameraTelemetry
import com.nightvision.app.model.EnhancementMode
import com.nightvision.app.model.ManualCameraSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates CameraX (Preview -> OpenGL OES SurfaceTexture, ImageAnalysis -> ZeroDceEnhancer)
 * and Camera2Interop manual/auto exposure, torch, zoom, and live CaptureResult telemetry.
 */
@OptIn(ExperimentalCamera2Interop::class)
class NightVisionCameraController(
    private val context: Context,
    private val renderer: NightVisionRenderer,
    private val enhancer: ZeroDceEnhancer
) {
    companion object {
        private const val TAG = "NightVisionCamera"
    }

    private val _telemetry = MutableStateFlow(CameraTelemetry())
    val telemetry: StateFlow<CameraTelemetry> = _telemetry.asStateFlow()

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val isProcessingAi = AtomicBoolean(false)

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var surfaceTexture: SurfaceTexture? = null

    @Volatile
    private var currentMode: EnhancementMode = EnhancementMode.AI_LOW_LIGHT

    private var currentSettings: ManualCameraSettings = ManualCameraSettings()
    private var lastAppliedAutoMode: Boolean? = null
    private var lastAppliedIso: Int? = null
    private var lastAppliedExposureNs: Long? = null

    /**
     * Binds CameraX Preview and ImageAnalysis to [lifecycleOwner] using [surfaceTexture]
     * provided by [NightVisionRenderer]. Safe to call before or after SurfaceTexture creation.
     */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceTexture: SurfaceTexture,
        initialSettings: ManualCameraSettings = currentSettings,
        initialMode: EnhancementMode = currentMode
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.surfaceTexture = surfaceTexture
        this.currentSettings = initialSettings
        this.currentMode = initialMode
        renderer.setMode(initialMode)
        renderer.updateSettings(initialSettings)

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                bindUseCases(provider, lifecycleOwner, surfaceTexture)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to obtain ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setMode(mode: EnhancementMode) {
        currentMode = mode
        renderer.setMode(mode)
        _telemetry.update {
            it.copy(
                isAiActive = mode == EnhancementMode.AI_LOW_LIGHT,
                aiInferenceMs = if (mode == EnhancementMode.AI_LOW_LIGHT) it.aiInferenceMs else 0L
            )
        }
    }

    fun onFpsUpdated(fps: Int) {
        _telemetry.update { it.copy(fps = fps) }
    }

    fun applySettings(settings: ManualCameraSettings) {
        val prev = currentSettings
        currentSettings = settings
        renderer.updateSettings(settings)

        if (settings.torchEnabled != prev.torchEnabled) {
            setTorchEnabled(settings.torchEnabled)
        }
        if (settings.zoomRatio != prev.zoomRatio) {
            setZoomRatio(settings.zoomRatio)
        }
        if (settings.autoMode != lastAppliedAutoMode ||
            settings.iso != lastAppliedIso ||
            settings.exposureTimeNs != lastAppliedExposureNs
        ) {
            applyCamera2ExposureOptions(settings)
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1.0f
        val maxZoom = zoomState?.maxZoomRatio ?: 4.0f
        cam.cameraControl.setZoomRatio(ratio.coerceIn(minZoom, maxZoom))
    }

    fun release() {
        cameraProvider?.unbindAll()
        camera = null
        lifecycleOwner = null
        surfaceTexture = null
        analysisExecutor.shutdown()
    }

    @SuppressLint("RestrictedApi")
    private fun bindUseCases(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        st: SurfaceTexture
    ) {
        val previewBuilder = Preview.Builder()
        val extender = Camera2Interop.Extender(previewBuilder)

        applyInitialExtenderOptions(extender, currentSettings)
        extender.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                val estimatedLux = estimateLux(iso, exposureNs)
                _telemetry.update {
                    it.copy(
                        iso = iso,
                        exposureTimeNs = exposureNs,
                        estimatedLux = estimatedLux
                    )
                }
            }
        })

        val preview = previewBuilder.build().apply {
            setSurfaceProvider { request ->
                val res = request.resolution
                st.setDefaultBufferSize(res.width, res.height)
                val rotation = camera?.cameraInfo?.sensorRotationDegrees ?: 90
                if (rotation == 90 || rotation == 270) {
                    renderer.setCameraAspectRatio(res.height, res.width)
                } else {
                    renderer.setCameraAspectRatio(res.width, res.height)
                }
                val surface = Surface(st)
                request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
                    surface.release()
                }
            }
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(analysisExecutor) { imageProxy ->
                    if (currentMode != EnhancementMode.AI_LOW_LIGHT) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    if (!isProcessingAi.compareAndSet(false, true)) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    try {
                        val result = enhancer.enhance(imageProxy)
                        if (result != null && currentMode == EnhancementMode.AI_LOW_LIGHT) {
                            renderer.updateAiBitmap(result.bitmap)
                            _telemetry.update {
                                it.copy(
                                    isAiActive = true,
                                    aiInferenceMs = result.inferenceTimeMs
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "AI frame enhancement error", e)
                    } finally {
                        isProcessingAi.set(false)
                        imageProxy.close()
                    }
                }
            }

        try {
            provider.unbindAll()
            val boundCamera = provider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            camera = boundCamera

            // Update orientation aspect ratio now that cameraInfo is available
            preview.resolutionInfo?.let { info ->
                val res = info.resolution
                val rot = boundCamera.cameraInfo.sensorRotationDegrees
                if (rot == 90 || rot == 270) {
                    renderer.setCameraAspectRatio(res.height, res.width)
                } else {
                    renderer.setCameraAspectRatio(res.width, res.height)
                }
            }

            setTorchEnabled(currentSettings.torchEnabled)
            setZoomRatio(currentSettings.zoomRatio)
            applyCamera2ExposureOptions(currentSettings)
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    private fun applyInitialExtenderOptions(
        extender: Camera2Interop.Extender<Preview>,
        settings: ManualCameraSettings
    ) {
        if (settings.autoMode) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
        } else {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                settings.iso
            )
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                settings.exposureTimeNs
            )
        }
    }

    private fun applyCamera2ExposureOptions(settings: ManualCameraSettings) {
        val cam = camera ?: return
        val c2Control = Camera2CameraControl.from(cam.cameraControl)
        val optionsBuilder = CaptureRequestOptions.Builder()

        if (settings.autoMode) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            val expRange = cam.cameraInfo.exposureState.exposureCompensationRange
            if (expRange.upper > 0) {
                val boostIndex = (expRange.upper / 2).coerceAtLeast(1)
                cam.cameraControl.setExposureCompensationIndex(boostIndex)
            }
        } else {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                settings.iso
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                settings.exposureTimeNs
            )
        }

        c2Control.setCaptureRequestOptions(optionsBuilder.build())
        lastAppliedAutoMode = settings.autoMode
        lastAppliedIso = settings.iso
        lastAppliedExposureNs = settings.exposureTimeNs
    }

    private fun estimateLux(iso: Int, exposureNs: Long): Float {
        if (iso <= 0 || exposureNs <= 0L) return 0f
        val exposureSec = exposureNs / 1_000_000_000f
        val raw = (1f / (exposureSec * (iso / 100f))) * 2.5f
        return raw.coerceIn(0.01f, 10000f)
    }
}
