package com.nightvision.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightvision.app.model.CameraTelemetry
import com.nightvision.app.model.EnhancementMode
import com.nightvision.app.model.ManualCameraSettings
import com.nightvision.app.ui.theme.IceCyan
import com.nightvision.app.ui.theme.TextPrimary
import com.nightvision.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

val GlassObsidian = Color(0xBD080B0E)
val GlassHairline = Color(0x24FFFFFF)
val AccentEmerald = Color(0xFF00FF88)

fun modeAccentColor(mode: EnhancementMode): Color = when (mode) {
    EnhancementMode.AI_LOW_LIGHT -> AccentEmerald
    EnhancementMode.GREEN_NIGHT_VISION -> AccentEmerald
    EnhancementMode.HIGH_CONTRAST_BW -> IceCyan
}

fun formatShutterSpeed(exposureTimeNs: Long): String {
    if (exposureTimeNs <= 0L) return "--"
    if (exposureTimeNs >= 1_000_000_000L) {
        return "%.1fs".format(exposureTimeNs / 1_000_000_000f)
    }
    val denominator = (1_000_000_000.0 / exposureTimeNs).roundToInt().coerceAtLeast(1)
    return "1/${denominator}s"
}

/**
 * Top tactical HUD bar showing live camera telemetry (FPS, ISO, Shutter, AI inference ms)
 * and quick toggles for Torch, Reticle, and Scanlines.
 */
@Composable
fun TopTelemetryBar(
    telemetry: CameraTelemetry,
    mode: EnhancementMode,
    settings: ManualCameraSettings,
    onToggleTorch: () -> Unit,
    onToggleReticle: () -> Unit,
    onToggleScanlines: () -> Unit,
    onOpenPresets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = modeAccentColor(mode)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact glassmorphic telemetry pill
        Surface(
            color = GlassObsidian,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GlassHairline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live indicator dot + mode short tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        text = mode.shortTag,
                        color = accent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp
                    )
                }

                TelemetryDivider()
                TelemetryItem(label = "FPS", value = if (telemetry.fps > 0) "${telemetry.fps}" else "--")
                TelemetryDivider()
                TelemetryItem(label = "ISO", value = if (telemetry.iso > 0) "${telemetry.iso}" else "--")
                TelemetryDivider()
                TelemetryItem(label = "SHT", value = formatShutterSpeed(telemetry.exposureTimeNs))

                AnimatedVisibility(
                    visible = mode == EnhancementMode.AI_LOW_LIGHT,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryDivider()
                        TelemetryItem(
                            label = "AI",
                            value = if (telemetry.aiInferenceMs > 0L) "${telemetry.aiInferenceMs}ms" else "--",
                            valueColor = accent
                        )
                    }
                }
            }
        }

        // Quick toggle pills: Torch, Reticle, Scanlines
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudToggleButton(
                icon = if (settings.torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                contentDescription = "Toggle Torch",
                active = settings.torchEnabled,
                accentColor = accent,
                onClick = onToggleTorch
            )
            HudToggleButton(
                icon = Icons.Rounded.CenterFocusStrong,
                contentDescription = "Toggle Reticle",
                active = settings.reticleEnabled,
                accentColor = accent,
                onClick = onToggleReticle
            )
            HudToggleButton(
                icon = Icons.Rounded.GraphicEq,
                contentDescription = "Toggle Scanlines",
                active = settings.scanlinesEnabled,
                accentColor = accent,
                onClick = onToggleScanlines
            )
            HudToggleButton(
                icon = Icons.Rounded.AutoAwesome,
                contentDescription = "Use Cases & Presets",
                active = false,
                accentColor = accent,
                onClick = onOpenPresets
            )
        }
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 8.sp,
            lineHeight = 9.sp
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = valueColor,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun TelemetryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

@Composable
private fun HudToggleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) accentColor.copy(alpha = 0.18f) else GlassObsidian,
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) accentColor.copy(alpha = 0.65f) else GlassHairline
        ),
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) accentColor else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
