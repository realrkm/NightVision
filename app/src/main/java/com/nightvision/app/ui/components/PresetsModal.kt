package com.nightvision.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nightvision.app.model.NightVisionPreset
import com.nightvision.app.model.NightVisionPresets
import com.nightvision.app.ui.theme.TacticalSurface
import com.nightvision.app.ui.theme.TextPrimary
import com.nightvision.app.ui.theme.TextSecondary

/**
 * Modal dialog presenting curated real-world NightVision use cases and one-tap presets
 * so users know the ideal scenario and camera configuration for each situation.
 */
@Composable
fun PresetsModal(
    visible: Boolean,
    activePresetId: String?,
    onSelectPreset: (NightVisionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = TacticalSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, GlassHairline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AccentEmerald.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "USE CASES & PRESETS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.1.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Tap a scenario to apply optimal settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Presets",
                            tint = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(GlassHairline)
                )

                // Preset Cards
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(NightVisionPresets.all, key = { it.id }) { preset ->
                        PresetCard(
                            preset = preset,
                            isActive = preset.id == activePresetId,
                            onClick = { onSelectPreset(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: NightVisionPreset,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val accent = modeAccentColor(preset.mode)
    val borderColor = if (isActive) accent.copy(alpha = 0.75f) else GlassHairline
    val cardBg = if (isActive) accent.copy(alpha = 0.08f) else GlassObsidian

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title row + Mode tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = preset.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Active preset",
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = preset.subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = preset.mode.label.uppercase(),
                        color = accent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Ideal Situation
            Text(
                text = "IDEAL FOR: ${preset.idealSituation}",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = TextPrimary.copy(alpha = 0.9f)
            )

            // Tip row
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(13.dp)
                )
                Text(
                    text = preset.tips,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary
                )
            }

            // Parameter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresetParamChip(
                    text = if (preset.autoMode) "AUTO AE" else "ISO ${preset.iso}",
                    accent = accent
                )
                if (!preset.autoMode) {
                    PresetParamChip(
                        text = formatShutterSpeed(preset.exposureTimeNs),
                        accent = accent
                    )
                }
                PresetParamChip(
                    text = "GAIN %.1fx".format(preset.digitalGain),
                    accent = accent
                )
                PresetParamChip(
                    text = if (preset.torchEnabled) "TORCH ON" else "TORCH OFF",
                    accent = if (preset.torchEnabled) accent else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PresetParamChip(
    text: String,
    accent: Color
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            color = accent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
