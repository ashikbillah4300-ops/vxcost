package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.BroadcastTelemetry
import com.example.audio.VoicePreset
import com.example.data.VoxCastSettings
import com.example.ui.theme.BroadcastOrange
import com.example.ui.theme.ConsoleOutline
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.IndigoMale
import com.example.ui.theme.RoseFemale
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletRobot

@Composable
fun VoiceChangerScreen(
    telemetry: BroadcastTelemetry,
    settings: VoxCastSettings,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleRecordPreview: () -> Unit,
    onSelectPreset: (VoicePreset) -> Unit,
    onCustomPitchChange: (Float) -> Unit,
    onCustomToneChange: (Float) -> Unit,
    onCustomEchoChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePreset = VoicePreset.fromId(settings.selectedPresetId)
    val scrollState = rememberScrollState()

    val effectivePitch = if (activePreset == VoicePreset.CUSTOM) {
        settings.customPitchSemitones
    } else {
        activePreset.pitchSemitones
    }
    val effectiveTone = if (activePreset == VoicePreset.CUSTOM) {
        settings.customFormantTone
    } else {
        activePreset.formantTone
    }
    val effectiveEcho = if (activePreset == VoicePreset.CUSTOM) {
        settings.customEchoMix
    } else {
        activePreset.echoMix
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!hasMicPermission) {
                MicPermissionCard(onRequestPermission = onRequestMicPermission)
            }

            // Live Voice Changer Monitor Bar
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                border = BorderStroke(
                    1.dp,
                    if (telemetry.isMicLive) ElectricCyan.copy(alpha = 0.65f) else ConsoleOutline
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "REAL-TIME VOICE CHANGER",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${activePreset.displayName} (${formatSemitones(effectivePitch)})",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!hasMicPermission) onRequestMicPermission() else onToggleMic()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (telemetry.isMicLive) BroadcastOrange else ElectricCyan,
                                contentColor = if (telemetry.isMicLive) Color.White else Color(0xFF001F26)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("voice_fx_live_toggle")
                        ) {
                            Icon(
                                imageVector = if (telemetry.isMicLive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                contentDescription = "Toggle live voice changer"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (telemetry.isMicLive) "LIVE ON" else "LIVE MIC",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (!hasMicPermission) onRequestMicPermission() else onToggleRecordPreview()
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (telemetry.isRecordingClip) BroadcastOrange else ElectricCyan.copy(alpha = 0.65f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (telemetry.isRecordingClip) {
                                    BroadcastOrange.copy(alpha = 0.16f)
                                } else {
                                    ConsoleSurfaceElevated
                                },
                                contentColor = if (telemetry.isRecordingClip) BroadcastOrange else ElectricCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("voice_fx_record_preview")
                        ) {
                            Icon(
                                imageVector = if (telemetry.isRecordingClip) {
                                    Icons.Rounded.Stop
                                } else {
                                    Icons.Rounded.FiberManualRecord
                                },
                                contentDescription = "Record and play voice preview"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (telemetry.isRecordingClip) {
                                    "PLAY (${telemetry.recordingSeconds}s)"
                                } else {
                                    "TEST & HEAR"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    LiveWaveformCanvas(
                        bars = telemetry.waveformBars,
                        isActive = telemetry.isMicLive || telemetry.isRecordingClip || telemetry.isClipPlaying,
                        accentColor = presetAccentColor(activePreset),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )
                }
            }

            Text(
                text = "SELECT VOICE PERSONA",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            // Responsive Persona Grid
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 560.dp) 3 else 2
                val rows = VoicePreset.entries.chunked(columns)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val selected = preset == activePreset
                                val accent = presetAccentColor(preset)
                                VoicePresetCard(
                                    preset = preset,
                                    accent = accent,
                                    selected = selected,
                                    onClick = { onSelectPreset(preset) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowPresets.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Custom Studio Pitch / Formant / Echo Rack
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                border = BorderStroke(
                    1.dp,
                    if (activePreset == VoicePreset.CUSTOM) ElectricCyan.copy(alpha = 0.6f) else ConsoleOutline
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CUSTOM STUDIO TUNER",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        TextButton(
                            onClick = {
                                onCustomPitchChange(0f)
                                onCustomToneChange(0f)
                                onCustomEchoChange(0f)
                            },
                            modifier = Modifier.testTag("reset_custom_voice_button")
                        ) {
                            Text("Reset", color = ElectricCyan)
                        }
                    }

                    // Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pitch (Deep Male <-> High Female)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = formatSemitones(effectivePitch),
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                    Slider(
                        value = effectivePitch,
                        onValueChange = onCustomPitchChange,
                        valueRange = -12f..12f,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan,
                            inactiveTrackColor = ConsoleSurfaceElevated
                        ),
                        modifier = Modifier.testTag("custom_pitch_slider")
                    )

                    // Formant / Tone Brightness Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Vocal Tone (Warm <-> Bright)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${(effectiveTone * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = BroadcastOrange
                        )
                    }
                    Slider(
                        value = effectiveTone,
                        onValueChange = onCustomToneChange,
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = BroadcastOrange,
                            activeTrackColor = BroadcastOrange,
                            inactiveTrackColor = ConsoleSurfaceElevated
                        ),
                        modifier = Modifier.testTag("custom_tone_slider")
                    )

                    // Echo / Hall Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hall Echo Mix",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${(effectiveEcho * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldSignal
                        )
                    }
                    Slider(
                        value = effectiveEcho,
                        onValueChange = onCustomEchoChange,
                        valueRange = 0f..0.6f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldSignal,
                            activeTrackColor = EmeraldSignal,
                            inactiveTrackColor = ConsoleSurfaceElevated
                        ),
                        modifier = Modifier.testTag("custom_echo_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VoicePresetCard(
    preset: VoicePreset,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) accent.copy(alpha = 0.16f) else ConsoleSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else ConsoleOutline
        ),
        modifier = modifier.testTag("voice_preset_${preset.id.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.18f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = presetIcon(preset),
                            contentDescription = preset.displayName,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (preset != VoicePreset.CUSTOM) {
                    Text(
                        text = formatSemitones(preset.pitchSemitones),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) accent else TextMuted
                    )
                }
            }

            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun presetAccentColor(preset: VoicePreset): Color = when (preset) {
    VoicePreset.NATURAL -> EmeraldSignal
    VoicePreset.FEMALE -> RoseFemale
    VoicePreset.MALE -> IndigoMale
    VoicePreset.KID -> ElectricCyan
    VoicePreset.DEEP_GIANT -> IndigoMale
    VoicePreset.MEGAPHONE -> BroadcastOrange
    VoicePreset.ROBOT -> VioletRobot
    VoicePreset.CUSTOM -> ElectricCyan
}

private fun presetIcon(preset: VoicePreset): ImageVector = when (preset) {
    VoicePreset.NATURAL -> Icons.Rounded.RecordVoiceOver
    VoicePreset.FEMALE -> Icons.Rounded.Face3
    VoicePreset.MALE -> Icons.Rounded.Face
    VoicePreset.KID -> Icons.Rounded.ChildCare
    VoicePreset.DEEP_GIANT -> Icons.Rounded.GraphicEq
    VoicePreset.MEGAPHONE -> Icons.Rounded.Campaign
    VoicePreset.ROBOT -> Icons.Rounded.SmartToy
    VoicePreset.CUSTOM -> Icons.Rounded.AutoFixHigh
}

private fun formatSemitones(st: Float): String {
    return when {
        st > 0.05f -> "+%.1f st".format(st)
        st < -0.05f -> "%.1f st".format(st)
        else -> "0.0 st"
    }
}
