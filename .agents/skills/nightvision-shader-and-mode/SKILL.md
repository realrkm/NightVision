---
name: nightvision-shader-and-mode
description: >-
  Add, modify, or remove an enhancement mode or OpenGL ES 3.0 shader in NightVision.
  Use when changing EnhancementMode options, GLSL fragment shaders, mode accent colors,
  or mode-specific UI/HUD behavior.
---

# NightVision Shader and Mode Updates

Every enhancement mode spans four synchronized layers. Update all of them together to prevent mode-index mismatches or non-exhaustive `when` errors.

## Synchronization Checklist

1. **Domain Enum** — `app/src/main/java/com/nightvision/app/model/NightVisionModels.kt`
   - Add, rename, or remove the entry in `enum class EnhancementMode(val label: String, val shortTag: String)`.
2. **GL Renderer Mapping** — `app/src/main/java/com/nightvision/app/gl/NightVisionRenderer.kt`
   - Update the `when (currentMode)` block that maps `EnhancementMode` to the `uMode` integer uniform (`0 = AI fallback, 1 = NVG Green, 2 = Mono Boost`, etc.).
   - If the new mode is non-AI, ensure `setMode()` clears `hasAiTexture` / `latestAiBitmap` as existing shader modes do.
3. **GLSL Fragment Shader** — `app/src/main/java/com/nightvision/app/gl/NightVisionShaders.kt`
   - Update `OES_FRAGMENT_SHADER` to handle the exact `uMode` integer emitted by `NightVisionRenderer`.
   - Respect `uGain`, `uContrast`, `uScanlines`, and `uResolution` uniforms so Pro Controls sliders and HUD toggles work consistently.
4. **UI Accent & Copy**
   - `app/src/main/java/com/nightvision/app/ui/components/TacticalHudOverlay.kt`: Update `modeAccentColor(mode: EnhancementMode)`.
   - `app/src/main/java/com/nightvision/app/ui/components/CameraPermissionScreen.kt` & `README.md`: Update mode descriptions if user-facing modes changed.

## Verification

- Search `app/src/main/java` for `EnhancementMode.` to verify all branches match.
- Run `.\gradlew.bat compileDebugKotlin` and test switching every mode pill on a physical device.
