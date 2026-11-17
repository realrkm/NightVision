---
name: nightvision-build-and-deploy
description: >-
  Build, sign, install, and runtime-verify the NightVision Android app on a connected
  device. Use when compiling debug or release APKs, installing via adb, or checking
  logcat for CameraX, OpenGL ES, or TFLite runtime errors.
---

# NightVision Build and Deploy

## Environment Requirements

- **JDK 17 is required** (`C:\Program Files\Java\jdk-17`). Do not use the system default JDK 27, which is incompatible with AGP 8.7.3. `gradle.properties` sets `org.gradle.java.home=C:/Program Files/Java/jdk-17`, and PowerShell sessions should also export `JAVA_HOME`:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
  ```
- **Android SDK / ADB**: `adb` is not on the global `PATH`; use:
  ```powershell
  $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
  ```

## Build Steps

1. **Compile check (fast)**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```
2. **Release APK (installable)**:
   `app/build.gradle.kts` signs `buildTypes.release` with `signingConfigs.getByName("debug")` so `assembleRelease` produces an installable APK:
   ```powershell
   .\gradlew.bat assembleRelease
   ```
   Output: `app\build\outputs\apk\release\app-release.apk`

## Install & Verify on Connected Device

1. **List devices**:
   ```powershell
   & $adb devices
   ```
2. **Install APK** (use `-r` to replace; if switching between debug and release signatures fails, uninstall `com.nightvision.app` first):
   ```powershell
   & $adb -s <DEVICE_ID> install -r app\build\outputs\apk\release\app-release.apk
   ```
3. **Launch and inspect `logcat`**:
   ```powershell
   & $adb -s <DEVICE_ID> logcat -c
   & $adb -s <DEVICE_ID> shell am start -n com.nightvision.app/.MainActivity
   Start-Sleep -Seconds 3
   & $adb -s <DEVICE_ID> logcat -d -s AndroidRuntime:E NightVision:* CameraX:* tflite:*
   ```
   Confirm zero `AndroidRuntime:E` exceptions and active `CameraX` / `SurfaceTexture` streaming.
