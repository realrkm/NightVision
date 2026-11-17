# NightVision

A real-time low-light camera and tactical night-vision Android application built with a hybrid **CameraX + Camera2Interop + OpenGL ES 3.0 + TensorFlow Lite (Zero-DCE)** pipeline and a modern **Jetpack Compose** tactical HUD.

---

## Features

- **3 Real-Time Enhancement Modes**
  - **AI Low-Light (`AI`)**: On-device Zero-DCE neural low-light enhancement (`zero_dce_lite.tflite`, 512×512 FP32, GPU-accelerated when available) blended with live camera feed in OpenGL ES 3.0.
  - **Night Vision (`NVG`)**: Classic phosphor-emerald (`#00FF88`) night-vision shader with digital gain, contrast shaping, and toggleable CRT scanlines.
  - **Mono Boost (`B&W`)**: High-contrast monochrome luminance boost for crisp low-light detail.
- **Hybrid Camera2 Control**:
  - Automatic low-light AE boost or full **Manual Mode** via `Camera2Interop` (ISO, Shutter Speed, Digital Gain, Contrast).
  - Pinch-to-zoom and quick zoom chips (`1.0x`, `2.0x`, `3.0x`), plus Torch toggle.
- **Tactical HUD & Pro Controls**:
  - Live telemetry pill displaying **FPS**, **ISO**, **Shutter Speed**, and **AI inference latency (ms)**.
  - Toggleable vector **Tactical Reticle** (center crosshair, rangefinder ticks, corner brackets) and **Scanlines**.
  - Expandable **Pro Controls Drawer** for fine-grained Camera2 and shader tuning.
- **Use Cases & Presets Modal**:
  - One-tap **Use Cases & Presets** dialog (`PresetsModal`) in the top HUD bar explaining the ideal real-world situation and applying optimal mode, ISO, shutter, gain, contrast, reticle, scanline, and torch settings.
- **Instant Photo Capture**:
  - Captures the exact enhanced OpenGL ES 3.0 framebuffer (`glReadPixels`) and saves high-quality JPEGs to `Pictures/NightVision` via `MediaStore`, with a tappable gallery thumbnail preview.

---

## Use Cases & Presets

Open the **Use Cases & Presets** modal (sparkle icon in the top HUD bar) to view ideal scenarios and apply a tuned configuration in one tap:

| Preset | Mode | Ideal Situation | Key Settings |
| :--- | :--- | :--- | :--- |
| **Dim Room Recovery** | `AI Low-Light` | Indoors at night, hallways, basements, or rooms with faint ambient light | Auto AE, Gain `1.4x`, Contrast `1.15`, Torch OFF |
| **Outdoor Trail & Recon** | `Night Vision` | Moonlit trails, backyards, campsites, or distant streetlamp environments | Auto AE, Gain `1.9x`, Contrast `1.35`, Reticle + Scanlines ON |
| **Ultra-Dark Still Shot** | `AI Low-Light` | Near-pitch-black stationary scenes when bracing the phone on a surface or tripod | Manual ISO `3200`, `1/10s` shutter, Gain `1.8x`, Reticle ON |
| **Lens Glint & Edge Scan** | `Mono Boost` | Inspecting dark corners, vents, or objects for reflective glints and fine textures | Manual ISO `800`, `1/30s`, Gain `1.7x`, Contrast `1.65`, Torch ON |
| **Power Outage Utility** | `Mono Boost` | Reading breaker panels, finding dropped items, or navigating zero-light rooms | Auto AE, Gain `1.3x`, Contrast `1.30`, Torch ON |

> **Physical Limitation Note:** Smartphone cameras still require some ambient light or the built-in **Torch** to see in 100% pitch-black rooms, and standard rear cameras include an IR-cut filter that blocks most thermal/infrared radiation.

---

## Architecture

```
┌───────────────────────────┐
│  Jetpack Compose Tactical │  TacticalHudOverlay, TacticalReticle,
│           UI              │  BottomControlDeck, ProControlsDrawer
└─────────────┬─────────────┘
              │ StateFlow<CameraTelemetry> / ManualCameraSettings
┌─────────────▼─────────────┐
│ NightVisionCameraController│  CameraX ProcessCameraProvider + Camera2Interop
│  - Preview -> OES Texture │  Manual ISO / Exposure, Torch, Zoom
│  - ImageAnalysis (RGBA)   │
└──────┬─────────────┬──────┘
       │             │ (AI_LOW_LIGHT mode)
       │             ▼
       │    ┌─────────────────┐
       │    │ ZeroDceEnhancer │  TFLite 2.16.1 (GPU Delegate + CPU fallback)
       │    │ [1,512,512,3]   │  zero_dce_lite.tflite
       │    └────────┬────────┘
       │             │ Enhanced Bitmap
       ▼             ▼
┌───────────────────────────┐
│   NightVisionRenderer     │  OpenGL ES 3.0 GLSurfaceView.Renderer
│  - OES External Texture   │  Custom fragment shaders per EnhancementMode
│  - AI Blend / Gain / CRT  │  glReadPixels -> GallerySaver (MediaStore)
└───────────────────────────┘
```

### Key Packages
- `com.nightvision.app.model` — Shared state models (`EnhancementMode`, `CameraTelemetry`, `ManualCameraSettings`, `CaptureState`, `NightVisionPreset`, `NightVisionPresets`).
- `com.nightvision.app.camera` — `NightVisionCameraController` (CameraX + `Camera2Interop`).
- `com.nightvision.app.gl` — `NightVisionRenderer` (OpenGL ES 3.0 OES + 2D AI texture shaders & frame capture).
- `com.nightvision.app.ml` — `ZeroDceEnhancer` (TensorFlow Lite Zero-DCE inference).
- `com.nightvision.app.storage` — `GallerySaver` (Scoped Storage `MediaStore` JPEG export).
- `com.nightvision.app.ui` — `NightVisionScreen`, `components/`, and `theme/`.

---

## Requirements

- **Android Studio** / **JDK 17**
- **Android SDK**: `compileSdk 35`, `minSdk 26`, `targetSdk 35`
- Physical Android device with Camera2 support (recommended for live camera & GPU TFLite testing)

---

## Build & Install

1. **Configure SDK & JDK**:
   Ensure `local.properties` points to your Android SDK (`sdk.dir=...`) and `gradle.properties` sets `org.gradle.java.home` to JDK 17 (or export `JAVA_HOME`).

2. **Build Debug APK**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
   .\gradlew.bat assembleDebug
   ```

3. **Install on Device**:
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

4. **Launch & Verify Logs**:
   ```powershell
   adb shell pm grant com.nightvision.app android.permission.CAMERA
   adb shell am start -n com.nightvision.app/.MainActivity
   adb logcat -d -s AndroidRuntime:E NightVision:* CameraX:* tflite:*
   ```

---

## Tech Stack

- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose (BOM `2024.12.01`), Material 3
- **Camera**: CameraX `1.4.1` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) + `Camera2Interop`
- **Graphics**: OpenGL ES 3.0 (`GLSurfaceView`)
- **ML**: TensorFlow Lite `2.16.1` (`tensorflow-lite`, `tensorflow-lite-gpu`, `tensorflow-lite-gpu-api`) with Zero-DCE (`zero_dce_lite.tflite`)
