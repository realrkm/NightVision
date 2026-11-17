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

data class NightVisionPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val idealSituation: String,
    val tips: String,
    val mode: EnhancementMode,
    val autoMode: Boolean,
    val iso: Int,
    val exposureTimeNs: Long,
    val digitalGain: Float,
    val contrast: Float,
    val torchEnabled: Boolean,
    val reticleEnabled: Boolean,
    val scanlinesEnabled: Boolean
)

object NightVisionPresets {
    val all: List<NightVisionPreset> = listOf(
        NightVisionPreset(
            id = "dim_indoor",
            title = "Dim Room Recovery",
            subtitle = "Natural AI shadow lift",
            idealSituation = "Indoors at night, hallways, basements, or rooms with faint ambient light.",
            tips = "Keeps natural colors while Zero-DCE AI lifts deep shadows without blowing out bright spots.",
            mode = EnhancementMode.AI_LOW_LIGHT,
            autoMode = true,
            iso = 1600,
            exposureTimeNs = 33_333_333L,
            digitalGain = 1.4f,
            contrast = 1.15f,
            torchEnabled = false,
            reticleEnabled = false,
            scanlinesEnabled = false
        ),
        NightVisionPreset(
            id = "outdoor_recon",
            title = "Outdoor Trail & Recon",
            subtitle = "Classic phosphor night vision",
            idealSituation = "Moonlit trails, backyards, campsites, or distant streetlamp environments.",
            tips = "Phosphor green mapping + scanlines preserve night-adapted eyes and boost midtone contrast.",
            mode = EnhancementMode.GREEN_NIGHT_VISION,
            autoMode = true,
            iso = 1600,
            exposureTimeNs = 33_333_333L,
            digitalGain = 1.9f,
            contrast = 1.35f,
            torchEnabled = false,
            reticleEnabled = true,
            scanlinesEnabled = true
        ),
        NightVisionPreset(
            id = "ultra_dark_still",
            title = "Ultra-Dark Still Shot",
            subtitle = "Long-shutter manual boost",
            idealSituation = "Near-pitch-black stationary scenes when bracing the phone on a surface or tripod.",
            tips = "Uses 1/10s shutter and ISO 3200 to gather maximum light; hold steady to avoid motion blur.",
            mode = EnhancementMode.AI_LOW_LIGHT,
            autoMode = false,
            iso = 3200,
            exposureTimeNs = 100_000_000L,
            digitalGain = 1.8f,
            contrast = 1.20f,
            torchEnabled = false,
            reticleEnabled = true,
            scanlinesEnabled = false
        ),
        NightVisionPreset(
            id = "glint_inspection",
            title = "Lens Glint & Edge Scan",
            subtitle = "High-contrast torch sweep",
            idealSituation = "Inspecting dark corners, vents, or objects for reflective glints and fine textures.",
            tips = "Pairs torch illumination with crisp monochrome contrast so tiny specular reflections stand out.",
            mode = EnhancementMode.HIGH_CONTRAST_BW,
            autoMode = false,
            iso = 800,
            exposureTimeNs = 33_333_333L,
            digitalGain = 1.7f,
            contrast = 1.65f,
            torchEnabled = true,
            reticleEnabled = true,
            scanlinesEnabled = false
        ),
        NightVisionPreset(
            id = "power_outage",
            title = "Power Outage Utility",
            subtitle = "Close-up clarity with torch",
            idealSituation = "Reading breaker panels, finding dropped items, or navigating zero-light rooms.",
            tips = "Turns on the torch with balanced monochrome contrast for sharp close-range visibility.",
            mode = EnhancementMode.HIGH_CONTRAST_BW,
            autoMode = true,
            iso = 1200,
            exposureTimeNs = 33_333_333L,
            digitalGain = 1.3f,
            contrast = 1.30f,
            torchEnabled = true,
            reticleEnabled = false,
            scanlinesEnabled = false
        )
    )
}
