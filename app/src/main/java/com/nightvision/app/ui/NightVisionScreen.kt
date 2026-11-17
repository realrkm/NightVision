package com.nightvision.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nightvision.app.camera.NightVisionCameraController
import com.nightvision.app.gl.NightVisionRenderer
import com.nightvision.app.ml.ZeroDceEnhancer
import com.nightvision.app.model.CaptureState
import com.nightvision.app.model.EnhancementMode
import com.nightvision.app.model.ManualCameraSettings
import com.nightvision.app.storage.GallerySaver
import com.nightvision.app.ui.components.BottomControlDeck
import com.nightvision.app.ui.components.CameraPermissionScreen
import com.nightvision.app.ui.components.GlassObsidian
import com.nightvision.app.ui.components.PresetsModal
import com.nightvision.app.ui.components.ProControlsDrawer
import com.nightvision.app.ui.components.TacticalReticle
import com.nightvision.app.ui.components.TopTelemetryBar
import com.nightvision.app.ui.components.modeAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Root composable for the NightVision viewfinder, OpenGL ES 3.0 viewport,
 * tactical HUD, and Pro camera controls.
 */
@Composable
fun NightVisionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Re-check permission on resume (e.g., returning from App Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        val activity = context.findActivity()
        val shouldShowRationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

        CameraPermissionScreen(
            shouldShowRationale = shouldShowRationale,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            },
            modifier = modifier
        )
        return
    }

    NightVisionCameraView(modifier = modifier)
}

@Composable
private fun NightVisionCameraView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var currentMode by remember { mutableStateOf(EnhancementMode.AI_LOW_LIGHT) }
    var settings by remember { mutableStateOf(ManualCameraSettings()) }
    var proDrawerOpen by remember { mutableStateOf(false) }
    var presetsModalOpen by remember { mutableStateOf(false) }
    var activePresetId by remember { mutableStateOf<String?>("dim_indoor") }
    var captureState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
    var lastPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var lastThumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val flashAlpha = remember { Animatable(0f) }

    // Pipeline instances
    val enhancer = remember { ZeroDceEnhancer(context.applicationContext) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    var controllerRef by remember { mutableStateOf<NightVisionCameraController?>(null) }

    val latestSettings by rememberUpdatedState(settings)
    val latestMode by rememberUpdatedState(currentMode)

    val renderer = remember {
        NightVisionRenderer(
            onSurfaceTextureAvailable = { surfaceTexture ->
                mainHandler.post {
                    controllerRef?.bind(
                        lifecycleOwner = lifecycleOwner,
                        surfaceTexture = surfaceTexture,
                        initialSettings = latestSettings,
                        initialMode = latestMode
                    )
                }
            },
            onFrameAvailable = {
                glSurfaceViewRef?.requestRender()
            },
            onFpsUpdated = { fps ->
                controllerRef?.onFpsUpdated(fps)
            }
        )
    }

    val cameraController = remember {
        NightVisionCameraController(context.applicationContext, renderer, enhancer).also {
            controllerRef = it
        }
    }

    val telemetry by cameraController.telemetry.collectAsStateWithLifecycle()

    // Lifecycle & cleanup
    DisposableEffect(lifecycleOwner, glSurfaceViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.release()
            renderer.release()
            enhancer.close()
        }
    }

    // Auto-dismiss transient status banner
    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(2200L)
            statusBanner = null
        }
    }

    val accent = modeAccentColor(currentMode)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080B0E))
            .pointerInput(settings.maxZoomRatio) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        val updatedZoom = (latestSettings.zoomRatio * zoom)
                            .coerceIn(1.0f, latestSettings.maxZoomRatio)
                        if (updatedZoom != latestSettings.zoomRatio) {
                            settings = latestSettings.copy(zoomRatio = updatedZoom)
                            cameraController.setZoomRatio(updatedZoom)
                        }
                    }
                }
            }
    ) {
        // 1. OpenGL ES 3.0 Camera Viewport
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glSurfaceViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Toggleable Tactical Reticle Overlay (non-blocking)
        if (settings.reticleEnabled) {
            TacticalReticle(accentColor = accent)
        }

        // 3. Shutter capture flash overlay
        if (flashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }

        // 4. Top Telemetry Bar
        TopTelemetryBar(
            telemetry = telemetry,
            mode = currentMode,
            settings = settings,
            onToggleTorch = {
                val next = !settings.torchEnabled
                settings = settings.copy(torchEnabled = next)
                cameraController.setTorchEnabled(next)
            },
            onToggleReticle = {
                settings = settings.copy(reticleEnabled = !settings.reticleEnabled)
            },
            onToggleScanlines = {
                val next = !settings.scanlinesEnabled
                val updated = settings.copy(scanlinesEnabled = next)
                settings = updated
                renderer.updateSettings(updated)
            },
            onOpenPresets = {
                presetsModalOpen = true
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Transient capture / feedback pill
        AnimatedVisibility(
            visible = statusBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 92.dp)
        ) {
            Surface(
                color = GlassObsidian,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
            ) {
                Text(
                    text = statusBanner.orEmpty(),
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 5. Bottom Control Deck + Expandable Pro Drawer
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomControlDeck(
                currentMode = currentMode,
                zoomRatio = settings.zoomRatio,
                maxZoomRatio = settings.maxZoomRatio,
                captureState = captureState,
                lastThumbnail = lastThumbnail,
                hasLastPhoto = lastPhotoUri != null,
                proDrawerOpen = proDrawerOpen,
                onSelectMode = { mode ->
                    currentMode = mode
                    cameraController.setMode(mode)
                    renderer.setMode(mode)
                },
                onSelectZoom = { zoom ->
                    settings = settings.copy(zoomRatio = zoom)
                    cameraController.setZoomRatio(zoom)
                },
                onCaptureClick = {
                    if (captureState is CaptureState.Capturing) return@BottomControlDeck
                    captureState = CaptureState.Capturing
                    scope.launch {
                        flashAlpha.snapTo(0.6f)
                        flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 220))
                    }
                    renderer.captureNextFrame { bitmap ->
                        scope.launch {
                            val thumb = createThumbnailBitmap(bitmap)
                            val result = GallerySaver.saveBitmap(context.applicationContext, bitmap)
                            result.onSuccess { uri ->
                                lastPhotoUri = uri
                                lastThumbnail = thumb
                                captureState = CaptureState.Saved(uri.toString())
                                statusBanner = "SAVED TO GALLERY"
                            }.onFailure { err ->
                                captureState = CaptureState.Error(err.message ?: "Save failed")
                                statusBanner = "CAPTURE FAILED"
                            }
                        }
                    }
                },
                onThumbnailClick = {
                    val uri = lastPhotoUri ?: return@BottomControlDeck
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/jpeg")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        Toast.makeText(context, "No gallery app available", Toast.LENGTH_SHORT).show()
                    }
                },
                onToggleProDrawer = {
                    proDrawerOpen = !proDrawerOpen
                }
            )

            ProControlsDrawer(
                visible = proDrawerOpen,
                mode = currentMode,
                settings = settings,
                onSettingsChange = { updated ->
                    settings = updated
                    cameraController.applySettings(updated)
                    renderer.updateSettings(updated)
                },
                onReset = {
                    val defaults = ManualCameraSettings(
                        torchEnabled = settings.torchEnabled,
                        reticleEnabled = settings.reticleEnabled,
                        scanlinesEnabled = settings.scanlinesEnabled,
                        zoomRatio = settings.zoomRatio,
                        maxZoomRatio = settings.maxZoomRatio,
                        isoRange = settings.isoRange,
                        exposureRangeNs = settings.exposureRangeNs
                    )
                    settings = defaults
                    cameraController.applySettings(defaults)
                    renderer.updateSettings(defaults)
                },
                onClose = { proDrawerOpen = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 178.dp)
            )
        }

        PresetsModal(
            visible = presetsModalOpen,
            activePresetId = activePresetId,
            onSelectPreset = { preset ->
                activePresetId = preset.id
                currentMode = preset.mode
                val updated = settings.copy(
                    autoMode = preset.autoMode,
                    iso = preset.iso.coerceIn(settings.isoRange),
                    exposureTimeNs = preset.exposureTimeNs.coerceIn(settings.exposureRangeNs),
                    digitalGain = preset.digitalGain,
                    contrast = preset.contrast,
                    torchEnabled = preset.torchEnabled,
                    reticleEnabled = preset.reticleEnabled,
                    scanlinesEnabled = preset.scanlinesEnabled
                )
                settings = updated
                cameraController.setMode(preset.mode)
                cameraController.applySettings(updated)
                cameraController.setTorchEnabled(preset.torchEnabled)
                renderer.setMode(preset.mode)
                renderer.updateSettings(updated)
                presetsModalOpen = false
                statusBanner = "PRESET: ${preset.title.uppercase()}"
            },
            onDismiss = { presetsModalOpen = false }
        )
    }
}

private fun createThumbnailBitmap(source: Bitmap): ImageBitmap {
    val targetSize = 160
    val scale = targetSize.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, w, h, true).asImageBitmap()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
