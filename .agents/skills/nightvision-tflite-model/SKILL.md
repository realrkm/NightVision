---
name: nightvision-tflite-model
description: >-
  Replace, update, or debug the on-device TensorFlow Lite low-light enhancement model
  in NightVision. Use when swapping zero_dce_lite.tflite, changing input/output tensor
  shapes or normalization, or tuning GPU/CPU delegate inference.
---

# NightVision TFLite Model Management

## Current Model Contract

- **Asset path**: `app/src/main/assets/zero_dce_lite.tflite`
- **Packaging**: `app/build.gradle.kts` must keep `androidResources { noCompress += "tflite" }` so `AssetFileDescriptor` memory-mapping works at runtime.
- **Tensor format**:
  - Input: `[1, 512, 512, 3]` `float32`, NHWC, RGB normalized to `[0.0, 1.0]` (`pixel / 255.0f`).
  - Output: `[1, 512, 512, 3]` `float32`, NHWC, RGB in `[0.0, 1.0]`.

## Updating the Model

1. Place the new `.tflite` file at `app/src/main/assets/zero_dce_lite.tflite` (verify it starts with `TFL3` magic bytes).
2. Inspect its input/output tensor shapes and quantization (`float32` vs `uint8`).
3. Update `app/src/main/java/com/nightvision/app/ml/ZeroDceEnhancer.kt` if input dimensions, normalization, or output clamping change.
4. Keep the `GpuDelegate` initialization with clean fallback to 4-thread CPU `Interpreter.Options` so devices without compatible OpenGL/OpenCL TFLite GPU support do not crash.

## Verification

- Build and install using `nightvision-build-and-deploy`.
- Switch to `AI Low-Light` mode on the device, confirm `AI ms` telemetry updates in the top HUD bar, and check `logcat` (`tflite:*`, `NightVision:*`) for delegate or tensor shape errors.
