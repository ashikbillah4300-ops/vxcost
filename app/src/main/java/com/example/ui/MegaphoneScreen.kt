package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.BroadcastTelemetry
import com.example.audio.QuickAlertTone
import com.example.audio.VoicePreset
import com.example.data.VoxCastSettings
import com.example.ui.theme.BroadcastOrange
import com.example.ui.theme.ConsoleOutline
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MegaphoneScreen(
    telemetry: BroadcastTelemetry,
    settings: VoxCastSettings,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onToggleMic: () -> Unit,
    onSetMicActive: (Boolean) -> Unit,
    onMicGainChange: (Float) -> Unit,
    onFeedbackGuardChange: (Boolean) -> Unit,
    onHornFilterChange: (Boolean) -> Unit,
    onHoldToTalkChange: (Boolean) -> Unit,
    onFloatingOverlayToggle: (Boolean) -> Unit,
    onTriggerAlert: (QuickAlertTone) -> Unit,
    onOpenVoiceFxTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePreset = VoicePreset.fromId(settings.selectedPresetId)
    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "megaphone_ring")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (telemetry.isMicLive) 1.03f else 1.0f,
        animationSpec = spring(),
        label = "btn_scale"
    )

    val mainButtonColor by animateColorAsState(
        targetValue = if (telemetry.isMicLive) BroadcastOrange else ConsoleSurfaceElevated,
        label = "main_btn_color"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasMicPermission) {
                MicPermissionCard(onRequestPermission = onRequestMicPermission)
            }

            // Live Telemetry & Waveform Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                border = BorderStroke(
                    1.dp,
                    if (telemetry.isMicLive) BroadcastOrange.copy(alpha = 0.55f) else ConsoleOutline
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = if (telemetry.isMicLive || telemetry.isClipPlaying) {
                                    BroadcastOrange
                                } else {
                                    TextMuted
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE AUDIO SIGNAL",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "${telemetry.inputDb.toInt()} dB",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (telemetry.isMicLive) ElectricCyan else TextMuted
                            )
                            Text(
                                text = formatDuration(telemetry.broadcastSeconds * 1000L),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (telemetry.isMicLive) BroadcastOrange else TextMuted
                            )
                        }
                    }

                    LiveWaveformCanvas(
                        bars = telemetry.waveformBars,
                        isActive = telemetry.isMicLive || telemetry.isClipPlaying || telemetry.activeAlertTone != null,
                        accentColor = if (telemetry.isMicLive) BroadcastOrange else ElectricCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                    )

                    VuMeterBar(
                        db = telemetry.inputDb,
                        isActive = telemetry.isMicLive || telemetry.isClipPlaying || telemetry.activeAlertTone != null
                    )
                }
            }

            // Hero Megaphone Broadcast Control
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (telemetry.isMicLive) {
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .scale(ringScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        BroadcastOrange.copy(alpha = 0.28f),
                                        BroadcastOrange.copy(alpha = 0.0f)
                                    )
                                )
                            )
                    )
                }

                val pttModifier = if (settings.holdToTalkMode && hasMicPermission) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onSetMicActive(true)
                            waitForUpOrCancellation()
                            onSetMicActive(false)
                        }
                    }
                } else {
                    Modifier
                }

                Surface(
                    onClick = {
                        if (!hasMicPermission) {
                            onRequestMicPermission()
                        } else if (!settings.holdToTalkMode) {
                            onToggleMic()
                        }
                    },
                    shape = CircleShape,
                    color = mainButtonColor,
                    border = BorderStroke(
                        width = 3.dp,
                        color = if (telemetry.isMicLive) Color.White.copy(alpha = 0.4f) else BroadcastOrange.copy(alpha = 0.65f)
                    ),
                    shadowElevation = if (telemetry.isMicLive) 16.dp else 6.dp,
                    modifier = Modifier
                        .size(174.dp)
                        .scale(buttonScale)
                        .then(pttModifier)
                        .testTag("megaphone_live_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = if (telemetry.isMicLive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                            contentDescription = if (telemetry.isMicLive) "Stop live microphone" else "Start live microphone",
                            tint = if (telemetry.isMicLive) Color.White else BroadcastOrange,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                telemetry.isMicLive && settings.holdToTalkMode -> "SPEAKING"
                                telemetry.isMicLive -> stringResource(R.string.btn_stop_broadcast)
                                settings.holdToTalkMode -> stringResource(R.string.btn_hold_to_talk)
                                else -> stringResource(R.string.btn_start_broadcast)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (telemetry.isMicLive) Color.White else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (settings.holdToTalkMode) "Press & hold" else "Tap to toggle",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.isMicLive) Color.White.copy(alpha = 0.85f) else TextSecondary
                        )
                    }
                }
            }

            // Active Voice Preset Quick Bar
            Surface(
                onClick = onOpenVoiceFxTab,
                shape = MaterialTheme.shapes.medium,
                color = ConsoleSurface,
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_voice_fx_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElectricCyan.copy(alpha = 0.16f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.RecordVoiceOver,
                                    contentDescription = "Active voice effect",
                                    tint = ElectricCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Voice Effect: ${activePreset.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = activePreset.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricCyan.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "CHANGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Quick PA Alert Signals
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                border = BorderStroke(1.dp, ConsoleOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "INSTANT PA SIGNALS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAlertTone.entries.forEach { tone ->
                            val isPlayingTone = telemetry.activeAlertTone == tone
                            Surface(
                                onClick = { onTriggerAlert(tone) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (isPlayingTone) BroadcastOrange.copy(alpha = 0.2f) else ConsoleSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPlayingTone) BroadcastOrange else ConsoleOutline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("alert_tone_${tone.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when (tone) {
                                            QuickAlertTone.CHIME -> Icons.Rounded.NotificationsActive
                                            QuickAlertTone.SIREN -> Icons.Rounded.WarningAmber
                                            QuickAlertTone.AIR_HORN -> Icons.Rounded.Campaign
                                        },
                                        contentDescription = tone.label,
                                        tint = if (isPlayingTone) BroadcastOrange else ElectricCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = tone.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mic Gain & Broadcast Console Controls
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                border = BorderStroke(1.dp, ConsoleOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MIC GAIN BOOST",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "%.1fx".format(settings.micGain),
                            style = MaterialTheme.typography.labelMedium,
                            color = BroadcastOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = settings.micGain,
                        onValueChange = onMicGainChange,
                        valueRange = 0.5f..4.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = BroadcastOrange,
                            activeTrackColor = BroadcastOrange,
                            inactiveTrackColor = ConsoleSurfaceElevated
                        ),
                        modifier = Modifier.testTag("mic_gain_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.megaphoneHornFilter,
                            onClick = { onHornFilterChange(!settings.megaphoneHornFilter) },
                            label = { Text("PA Horn EQ") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Campaign,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BroadcastOrange.copy(alpha = 0.22f),
                                selectedLabelColor = BroadcastOrange,
                                selectedLeadingIconColor = BroadcastOrange
                            ),
                            modifier = Modifier.testTag("horn_filter_chip")
                        )

                        FilterChip(
                            selected = settings.holdToTalkMode,
                            onClick = { onHoldToTalkChange(!settings.holdToTalkMode) },
                            label = { Text("Hold-to-Talk") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.TouchApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.20f),
                                selectedLabelColor = ElectricCyan,
                                selectedLeadingIconColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("hold_to_talk_chip")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = EmeraldSignal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.feedback_guard_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = stringResource(R.string.feedback_guard_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = settings.feedbackGuard,
                            onCheckedChange = onFeedbackGuardChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldSignal
                            ),
                            modifier = Modifier.testTag("feedback_guard_switch")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.floating_display_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = stringResource(R.string.floating_display_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = settings.floatingOverlayEnabled,
                            onCheckedChange = onFloatingOverlayToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("floating_overlay_switch")
                        )
                    }

                    Text(
                        text = stringResource(R.string.route_speaker_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
