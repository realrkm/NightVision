package com.nightvision.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NightVisionDarkColorScheme = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = ObsidianBlack,
    primaryContainer = PhosphorGreenContainer,
    onPrimaryContainer = PhosphorGreen,
    secondary = ThermalAmber,
    onSecondary = ObsidianBlack,
    secondaryContainer = ThermalAmberContainer,
    onSecondaryContainer = ThermalAmber,
    tertiary = IceCyan,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = TacticalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TacticalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TacticalBorder,
    error = AlertRed,
    onError = ObsidianBlack
)

@Composable
fun NightVisionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NightVisionDarkColorScheme,
        typography = NightVisionTypography,
        content = content
    )
}
