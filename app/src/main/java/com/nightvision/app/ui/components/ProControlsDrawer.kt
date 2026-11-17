package com.nightvision.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightvision.app.model.EnhancementMode
import com.nightvision.app.model.ManualCameraSettings
import com.nightvision.app.ui.theme.TextPrimary
import com.nightvision.app.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Expandable glassmorphic Pro Controls Drawer for Camera2 manual exposure/ISO
 * and OpenGL shader digital gain & contrast tuning.
 */
@Composable
fun ProControlsDrawer(
    visible: Boolean,
    mode: EnhancementMode,
    settings: ManualCameraSettings,
    onSettingsChange: (ManualCameraSettings) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = modeAccentColor(mode)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xDC080B0E),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "PRO SENSOR & SHADER",
                            color = accent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onReset)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = "Reset Pro Controls",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "RESET",
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Pro Drawer",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Auto / Manual Camera2 Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (settings.autoMode) "AUTO EXPOSURE (AE)" else "MANUAL CAMERA2",
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (settings.autoMode) {
                                "Camera2 auto low-light ISO & shutter"
                            } else {
                                "Manual ISO & shutter lock active"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = !settings.autoMode,
                        onCheckedChange = { manual ->
                            onSettingsChange(settings.copy(autoMode = !manual))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF080B0E),
                            checkedTrackColor = accent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                            uncheckedBorderColor = GlassHairline
                        )
                    )
                }

                // Manual ISO Slider
                ProSliderRow(
                    label = "ISO",
                    valueLabel = if (settings.autoMode) "AUTO" else "${settings.iso}",
                    value = settings.iso.toFloat(),
                    valueRange = settings.isoRange.first.toFloat()..settings.isoRange.last.toFloat(),
                    enabled = !settings.autoMode,
                    accentColor = accent,
                    onValueChange = { newIso ->
                        onSettingsChange(settings.copy(iso = newIso.roundToInt()))
                    }
                )

                // Manual Shutter Speed Slider
                ProSliderRow(
                    label = "SHUTTER",
                    valueLabel = if (settings.autoMode) "AUTO" else formatShutterSpeed(settings.exposureTimeNs),
                    value = settings.exposureTimeNs.toFloat(),
                    valueRange = settings.exposureRangeNs.first.toFloat()..settings.exposureRangeNs.last.toFloat(),
                    enabled = !settings.autoMode,
                    accentColor = accent,
                    onValueChange = { newExposure ->
                        onSettingsChange(settings.copy(exposureTimeNs = newExposure.roundToLong()))
                    }
                )

                // Digital Gain Slider
                ProSliderRow(
                    label = "DIGITAL GAIN",
                    valueLabel = "%.2fx".format(settings.digitalGain),
                    value = settings.digitalGain,
                    valueRange = 0.5f..4.0f,
                    enabled = true,
                    accentColor = accent,
                    onValueChange = { newGain ->
                        onSettingsChange(settings.copy(digitalGain = newGain))
                    }
                )

                // Contrast Slider
                ProSliderRow(
                    label = "CONTRAST",
                    valueLabel = "%.2fx".format(settings.contrast),
                    value = settings.contrast,
                    valueRange = 0.5f..2.5f,
                    enabled = true,
                    accentColor = accent,
                    onValueChange = { newContrast ->
                        onSettingsChange(settings.copy(contrast = newContrast))
                    }
                )

                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun ProSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
            Text(
                text = valueLabel,
                color = if (enabled) accentColor else TextSecondary.copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                disabledThumbColor = TextSecondary.copy(alpha = 0.4f),
                disabledActiveTrackColor = TextSecondary.copy(alpha = 0.25f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.08f)
            )
        )
    }
}
