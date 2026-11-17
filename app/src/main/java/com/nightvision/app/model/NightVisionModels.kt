package com.nightvision.app.model

enum class EnhancementMode(val label: String, val shortTag: String) {
    AI_LOW_LIGHT("AI Low-Light", "AI"),
    GREEN_NIGHT_VISION("Night Vision", "NVG"),
    HIGH_CONTRAST_BW("Mono Boost", "B&W")
}

data class CameraTelemetry(
    val fps: Int = 0,
    val iso: Int = 0,
    val exposureTimeNs: Long = 0L,
    val estimatedLux: Float = 0f,
    val isAiActive: Boolean = false,
    val aiInferenceMs: Long = 0L
)

data class ManualCameraSettings(
    val autoMode: Boolean = true,
    val iso: Int = 1600,
    val isoRange: IntRange = 100..6400,
    val exposureTimeNs: Long = 33_333_333L,
    val exposureRangeNs: LongRange = 1_000_000L..100_000_000L,
    val digitalGain: Float = 1.6f,
    val contrast: Float = 1.25f,
    val zoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 4.0f,
    val torchEnabled: Boolean = false,
    val reticleEnabled: Boolean = true,
    val scanlinesEnabled: Boolean = true
)

sealed interface CaptureState {
    data object Idle : CaptureState
    data object Capturing : CaptureState
    data class Saved(val uriString: String) : CaptureState
    data class Error(val message: String) : CaptureState
}
