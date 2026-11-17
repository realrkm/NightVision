package com.nightvision.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightvision.app.model.CaptureState
import com.nightvision.app.model.EnhancementMode
import com.nightvision.app.ui.theme.TextPrimary
import com.nightvision.app.ui.theme.TextSecondary
import kotlin.math.abs

private val ZoomPresets = listOf(1.0f, 2.0f, 3.0f)

/**
 * Bottom tactical control deck: zoom quick-chips, enhancement mode pills,
 * gallery thumbnail, animated shutter button, and Pro drawer toggle.
 */
@Composable
fun BottomControlDeck(
    currentMode: EnhancementMode,
    zoomRatio: Float,
    maxZoomRatio: Float,
    captureState: CaptureState,
    lastThumbnail: ImageBitmap?,
    hasLastPhoto: Boolean,
    proDrawerOpen: Boolean,
    onSelectMode: (EnhancementMode) -> Unit,
    onSelectZoom: (Float) -> Unit,
    onCaptureClick: () -> Unit,
    onThumbnailClick: () -> Unit,
    onToggleProDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = modeAccentColor(currentMode)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Zoom quick-selector chips
        ZoomSelectorRow(
            zoomRatio = zoomRatio,
            maxZoomRatio = maxZoomRatio,
            accentColor = accent,
            onSelectZoom = onSelectZoom
        )

        // Horizontal Enhancement Mode selector pills
        ModeSelectorRow(
            currentMode = currentMode,
            onSelectMode = onSelectMode
        )

        // Primary action row: Thumbnail | Shutter | Pro Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Last captured photo thumbnail
            GalleryThumbnailButton(
                thumbnail = lastThumbnail,
                enabled = hasLastPhoto,
                accentColor = accent,
                onClick = onThumbnailClick
            )

            // Center animated Shutter button
            TacticalShutterButton(
                accentColor = accent,
                isCapturing = captureState is CaptureState.Capturing,
                onClick = onCaptureClick
            )

            // Pro Drawer Toggle button
            ProDrawerToggleButton(
                isOpen = proDrawerOpen,
                accentColor = accent,
                onClick = onToggleProDrawer
            )
        }
    }
}

@Composable
private fun ZoomSelectorRow(
    zoomRatio: Float,
    maxZoomRatio: Float,
    accentColor: Color,
    onSelectZoom: (Float) -> Unit
) {
    val availablePresets = remember(maxZoomRatio) {
        ZoomPresets.filter { it <= maxZoomRatio + 0.05f }
    }
    val matchesPreset = availablePresets.any { abs(it - zoomRatio) < 0.08f }

    Surface(
        color = GlassObsidian,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, GlassHairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            availablePresets.forEach { preset ->
                val isSelected = abs(preset - zoomRatio) < 0.08f
                ZoomChip(
                    label = "%.1fx".format(preset),
                    selected = isSelected,
                    accentColor = accentColor,
                    onClick = { onSelectZoom(preset) }
                )
            }
            if (!matchesPreset) {
                ZoomChip(
                    label = "%.1fx".format(zoomRatio),
                    selected = true,
                    accentColor = accentColor,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun ZoomChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.22f) else Color.Transparent,
        label = "zoomBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) accentColor else TextSecondary,
        label = "zoomText"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .then(
                if (selected) Modifier.border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ModeSelectorRow(
    currentMode: EnhancementMode,
    onSelectMode: (EnhancementMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EnhancementMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val accent = modeAccentColor(mode)
            val bgColor by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.18f) else GlassObsidian,
                label = "modeBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.8f) else GlassHairline,
                label = "modeBorder"
            )

            Surface(
                color = bgColor,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelectMode(mode) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                    Text(
                        text = mode.label,
                        color = if (selected) accent else TextPrimary.copy(alpha = 0.85f),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TacticalShutterButton(
    accentColor: Color,
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isCapturing -> 0.88f
            isPressed -> 0.92f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "shutterScale"
    )

    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(2.5.dp, accentColor.copy(alpha = 0.9f), CircleShape)
            .background(GlassObsidian)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isCapturing,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = accentColor,
                strokeWidth = 2.5.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(TextPrimary)
                    .border(2.dp, accentColor.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

@Composable
private fun GalleryThumbnailButton(
    thumbnail: ImageBitmap?,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = GlassObsidian,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) accentColor.copy(alpha = 0.5f) else GlassHairline
        ),
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = "Open last captured photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = "No captured photo yet",
                    tint = TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ProDrawerToggleButton(
    isOpen: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isOpen) accentColor.copy(alpha = 0.2f) else GlassObsidian,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOpen) accentColor.copy(alpha = 0.75f) else GlassHairline
        ),
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Pro Controls",
                tint = if (isOpen) accentColor else TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "PRO",
                color = if (isOpen) accentColor else TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            )
        }
    }
}
