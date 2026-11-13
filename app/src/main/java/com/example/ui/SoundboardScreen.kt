package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.BroadcastTelemetry
import com.example.data.AudioClipEntity
import com.example.data.ClipMicMixMode
import com.example.data.VoxCastSettings
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
fun SoundboardScreen(
    clips: List<AudioClipEntity>,
    telemetry: BroadcastTelemetry,
    settings: VoxCastSettings,
    isImporting: Boolean,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onUploadClick: () -> Unit,
    onToggleRecordClip: () -> Unit,
    onPlayPauseClip: (AudioClipEntity) -> Unit,
    onStopClip: () -> Unit,
    onToggleLoop: () -> Unit,
    onSeekClip: (Long) -> Unit,
    onRenameClip: (AudioClipEntity, String) -> Unit,
    onDeleteClip: (AudioClipEntity) -> Unit,
    onRouteClipThroughFxChange: (Boolean) -> Unit,
    onMixModeChange: (ClipMicMixMode) -> Unit,
    onClipGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var clipPendingRename by remember { mutableStateOf<AudioClipEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Upload & Record Action Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onUploadClick,
                        enabled = !isImporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BroadcastOrange,
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("upload_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = "Upload audio file"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isImporting) "Importing..." else stringResource(R.string.btn_upload_audio),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (!hasMicPermission) onRequestMicPermission() else onToggleRecordClip()
                        },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.isRecordingClip) CrimsonLive else ElectricCyan.copy(alpha = 0.6f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (telemetry.isRecordingClip) {
                                CrimsonLive.copy(alpha = 0.16f)
                            } else {
                                ConsoleSurface
                            },
                            contentColor = if (telemetry.isRecordingClip) CrimsonLive else ElectricCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("record_clip_button")
                    ) {
                        Icon(
                            imageVector = if (telemetry.isRecordingClip) {
                                Icons.Rounded.Stop
                            } else {
                                Icons.Rounded.FiberManualRecord
                            },
                            contentDescription = "Record audio clip"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (telemetry.isRecordingClip) {
                                "Stop ${formatDuration(telemetry.recordingSeconds * 1000L)}"
                            } else {
                                stringResource(R.string.btn_record_clip)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (isImporting) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = BroadcastOrange,
                        trackColor = ConsoleSurfaceElevated
                    )
                }
            }

            // Now Broadcasting Clip Mini-Console
            if (telemetry.isClipPlaying) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ConsoleSurfaceElevated),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
                        modifier = Modifier.testTag("active_clip_console")
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = ElectricCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "BROADCASTING VIA MIC BUS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                        Text(
                                            text = telemetry.playingClipTitle ?: "Audio Clip",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = onToggleLoop,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("clip_loop_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Repeat,
                                            contentDescription = "Loop audio clip",
                                            tint = if (telemetry.isClipLooping) BroadcastOrange else TextMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = onStopClip,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("clip_stop_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Stop,
                                            contentDescription = "Stop clip broadcast",
                                            tint = CrimsonLive
                                        )
                                    }
                                }
                            }

                            val duration = telemetry.clipDurationMs.coerceAtLeast(1L)
                            val pos = telemetry.clipPositionMs.coerceIn(0L, duration)
                            Slider(
                                value = pos.toFloat(),
                                onValueChange = { onSeekClip(it.toLong()) },
                                valueRange = 0f..duration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricCyan,
                                    activeTrackColor = ElectricCyan,
                                    inactiveTrackColor = ConsoleSurface
                                ),
                                modifier = Modifier.testTag("clip_seek_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(pos),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Broadcast Mixer Routing Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                    border = BorderStroke(1.dp, ConsoleOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "MIC-CHANNEL AUDIO MIXER",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClipMicMixMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.clipMicMixMode == mode,
                                    onClick = { onMixModeChange(mode) },
                                    label = { Text(mode.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = ElectricCyan
                                    ),
                                    modifier = Modifier.testTag("mix_mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Route Clips Through Mic FX",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Applies live megaphone EQ & room echo so clips blend like mic audio",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = settings.routeClipThroughFx,
                                onCheckedChange = onRouteClipThroughFxChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BroadcastOrange
                                ),
                                modifier = Modifier.testTag("route_clip_fx_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Clip Output Level",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${(settings.clipGain * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = settings.clipGain,
                            onValueChange = onClipGainChange,
                            valueRange = 0.3f..2.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = ConsoleSurfaceElevated
                            ),
                            modifier = Modifier.testTag("clip_gain_slider")
                        )
                    }
                }
            }

            // Empty State or Clip List
            if (clips.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                        border = BorderStroke(1.dp, ConsoleOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_soundboard_empty),
                                contentDescription = "Audio soundboard studio illustration",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(156.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                            Text(
                                text = "Your Broadcast Soundboard is Empty",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Upload MP3, WAV, or M4A audio files from your phone, or record a quick voice announcement. Playing any clip broadcasts it straight through your speaker or Bluetooth output just like your mic.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(clips, key = { it.id }) { clip ->
                    val isThisPlaying = telemetry.isClipPlaying && telemetry.playingClipId == clip.id
                    val isThisPaused = isThisPlaying && telemetry.isClipPaused

                    Surface(
                        onClick = { onPlayPauseClip(clip) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isThisPlaying) ConsoleSurfaceElevated else ConsoleSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isThisPlaying) ElectricCyan else ConsoleOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clip_card_${clip.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isThisPlaying && !isThisPaused) {
                                    BroadcastOrange
                                } else {
                                    ElectricCyan.copy(alpha = 0.16f)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isThisPlaying && !isThisPaused) {
                                            Icons.Rounded.Pause
                                        } else {
                                            Icons.Rounded.PlayArrow
                                        },
                                        contentDescription = if (isThisPlaying && !isThisPaused) "Pause clip" else "Play clip",
                                        tint = if (isThisPlaying && !isThisPaused) Color.White else ElectricCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = clip.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (clip.category == "Recorded") {
                                            BroadcastOrange.copy(alpha = 0.16f)
                                        } else {
                                            EmeraldSignal.copy(alpha = 0.16f)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (clip.category == "Recorded") {
                                                    Icons.Rounded.Mic
                                                } else {
                                                    Icons.Rounded.AudioFile
                                                },
                                                contentDescription = null,
                                                tint = if (clip.category == "Recorded") BroadcastOrange else EmeraldSignal,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = clip.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (clip.category == "Recorded") BroadcastOrange else EmeraldSignal
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatDuration(clip.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    clipPendingRename = clip
                                    renameText = clip.title
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("rename_clip_${clip.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Rename clip",
                                    tint = TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { onDeleteClip(clip) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_clip_${clip.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete clip",
                                    tint = CrimsonLive.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (clipPendingRename != null) {
        AlertDialog(
            onDismissRequest = { clipPendingRename = null },
            title = { Text("Rename Audio Clip") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Clip Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipPendingRename?.let { onRenameClip(it, renameText) }
                        clipPendingRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { clipPendingRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
