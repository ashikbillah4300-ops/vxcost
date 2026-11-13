package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioOutputRoute
import com.example.audio.VoicePreset
import com.example.ui.theme.AmberMeter
import com.example.ui.theme.BroadcastOrange
import com.example.ui.theme.ConsoleOutline
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.CrimsonLive
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BroadcastStatusHeader(
    isMicLive: Boolean,
    isClipPlaying: Boolean,
    activePreset: VoicePreset,
    outputRoute: AudioOutputRoute,
    feedbackGuard: Boolean,
    isFloatingOverlayOn: Boolean,
    onToggleFloatingOverlay: () -> Unit,
    onOutputBadgeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBroadcasting = isMicLive || isClipPlaying
    val pulseTransition = rememberInfiniteTransition(label = "on_air_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val badgeColor by animateColorAsState(
        targetValue = when {
            isMicLive -> BroadcastOrange
            isClipPlaying -> ElectricCyan
            else -> ConsoleSurfaceElevated
        },
        label = "badge_color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = if (isBroadcasting) 0.18f else 0.7f),
                    border = BorderStroke(
                        1.dp,
                        if (isBroadcasting) badgeColor.copy(alpha = 0.7f) else ConsoleOutline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isBroadcasting) {
                                        badgeColor.copy(alpha = pulseAlpha)
                                    } else {
                                        TextMuted
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isMicLive -> stringResource(R.string.status_on_air)
                                isClipPlaying -> stringResource(R.string.status_playing_audio)
                                else -> stringResource(R.string.status_standby)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBroadcasting) badgeColor else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Voice: ${activePreset.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (activePreset == VoicePreset.NATURAL) TextSecondary else ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (feedbackGuard) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Feedback guard active",
                        tint = EmeraldSignal,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onToggleFloatingOverlay,
                shape = RoundedCornerShape(14.dp),
                color = if (isFloatingOverlayOn) ElectricCyan.copy(alpha = 0.18f) else ConsoleSurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (isFloatingOverlayOn) ElectricCyan else ConsoleOutline
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("header_floating_toggle")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PictureInPictureAlt,
                        contentDescription = "Toggle floating over display",
                        tint = if (isFloatingOverlayOn) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                onClick = onOutputBadgeClick,
                shape = RoundedCornerShape(14.dp),
                color = ConsoleSurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (outputRoute.isBluetooth) EmeraldSignal.copy(alpha = 0.6f) else ConsoleOutline
                ),
                modifier = Modifier.testTag("output_route_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            outputRoute.isBluetooth -> Icons.Rounded.BluetoothAudio
                            outputRoute.isWired -> Icons.Rounded.Headphones
                            else -> Icons.Rounded.VolumeUp
                        },
                        contentDescription = "Audio output route",
                        tint = if (outputRoute.isBluetooth) EmeraldSignal else ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = outputRoute.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LiveWaveformCanvas(
    bars: List<Float>,
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = bars.size.coerceAtLeast(1)
        val spacing = size.width * 0.012f
        val totalSpacing = spacing * (count - 1)
        val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until count) {
            val level = if (isActive) bars[i].coerceIn(0.06f, 1f) else 0.05f
            val barHeight = (size.height * 0.88f * level).coerceAtLeast(6f)
            val x = i * (barWidth + spacing)
            val top = centerY - barHeight / 2f

            val color = when {
                !isActive -> ConsoleOutline
                level > 0.85f -> CrimsonLive
                level > 0.62f -> AmberMeter
                else -> accentColor
            }

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = 0.55f))
                ),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun VuMeterBar(
    db: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val normalized = if (isActive) ((db + 60f) / 60f).coerceIn(0f, 1f) else 0f
    val segments = 20

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until segments) {
            val threshold = (i + 1) / segments.toFloat()
            val lit = normalized >= threshold - 0.03f
            val segColor = when {
                !lit -> ConsoleSurfaceElevated
                i >= 17 -> CrimsonLive
                i >= 13 -> AmberMeter
                else -> EmeraldSignal
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(segColor)
            )
        }
    }
}

@Composable
fun MicPermissionCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
        border = BorderStroke(1.dp, BroadcastOrange.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = BroadcastOrange.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = BroadcastOrange
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mic_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.mic_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BroadcastOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("grant_mic_permission_button")
                ) {
                    Text(stringResource(R.string.mic_permission_grant))
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}
