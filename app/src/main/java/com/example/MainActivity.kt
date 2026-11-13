package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.VoicePreset
import com.example.ui.BroadcastStatusHeader
import com.example.ui.BroadcastTab
import com.example.ui.MainViewModel
import com.example.ui.MegaphoneScreen
import com.example.ui.SoundboardScreen
import com.example.ui.VoiceChangerScreen
import com.example.ui.theme.BroadcastOrange
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoxCastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxCastTheme {
                VoxCastApp()
            }
        }
    }
}

@Composable
fun VoxCastApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(BroadcastTab.MEGAPHONE) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var pendingEnableOverlay by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Optional foreground notification */ }
    )

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importAudioClip(uri)
            }
        }
    )

    fun requestOrToggleFloatingOverlay(enable: Boolean) {
        if (!enable) {
            pendingEnableOverlay = false
            viewModel.setFloatingOverlayEnabled(false)
            return
        }
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        if (canDraw) {
            pendingEnableOverlay = false
            viewModel.setFloatingOverlayEnabled(true)
        } else {
            pendingEnableOverlay = true
            showOverlayPermissionDialog = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
                if (pendingEnableOverlay && canDraw) {
                    pendingEnableOverlay = false
                    viewModel.setFloatingOverlayEnabled(true)
                } else {
                    viewModel.syncOverlayAfterResume()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(statusBanner, telemetry.errorMessage) {
        val message = telemetry.errorMessage ?: statusBanner
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissBanner()
        }
    }

    BackHandler(enabled = currentTab != BroadcastTab.MEGAPHONE) {
        currentTab = BroadcastTab.MEGAPHONE
    }

    val activePreset = VoicePreset.fromId(settings.selectedPresetId)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = ConsoleSurface,
                        contentColor = TextPrimary
                    ) {
                        BroadcastTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tabLabel(tab)
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BroadcastOrange,
                                    selectedTextColor = BroadcastOrange,
                                    indicatorColor = BroadcastOrange.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = ConsoleSurface,
                        contentColor = TextPrimary,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        BroadcastTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tabLabel(tab)
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = BroadcastOrange,
                                    selectedTextColor = BroadcastOrange,
                                    indicatorColor = BroadcastOrange.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    BroadcastStatusHeader(
                        isMicLive = telemetry.isMicLive,
                        isClipPlaying = telemetry.isClipPlaying,
                        activePreset = activePreset,
                        outputRoute = telemetry.outputRoute,
                        feedbackGuard = settings.feedbackGuard,
                        isFloatingOverlayOn = settings.floatingOverlayEnabled,
                        onToggleFloatingOverlay = {
                            requestOrToggleFloatingOverlay(!settings.floatingOverlayEnabled)
                        },
                        onOutputBadgeClick = {
                            viewModel.refreshAudioRoute()
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (currentTab) {
                            BroadcastTab.MEGAPHONE -> {
                                MegaphoneScreen(
                                    telemetry = telemetry,
                                    settings = settings,
                                    hasMicPermission = hasMicPermission,
                                    onRequestMicPermission = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    onToggleMic = viewModel::toggleMicBroadcast,
                                    onSetMicActive = viewModel::setMicBroadcastActive,
                                    onMicGainChange = viewModel::setMicGain,
                                    onFeedbackGuardChange = viewModel::setFeedbackGuard,
                                    onHornFilterChange = viewModel::setMegaphoneHornFilter,
                                    onHoldToTalkChange = viewModel::setHoldToTalkMode,
                                    onFloatingOverlayToggle = { enable ->
                                        requestOrToggleFloatingOverlay(enable)
                                    },
                                    onTriggerAlert = viewModel::triggerAlertTone,
                                    onOpenVoiceFxTab = { currentTab = BroadcastTab.VOICE_FX }
                                )
                            }

                            BroadcastTab.SOUNDBOARD -> {
                                SoundboardScreen(
                                    clips = clips,
                                    telemetry = telemetry,
                                    settings = settings,
                                    isImporting = isImporting,
                                    hasMicPermission = hasMicPermission,
                                    onRequestMicPermission = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    onUploadClick = {
                                        audioPickerLauncher.launch(arrayOf("audio/*"))
                                    },
                                    onToggleRecordClip = viewModel::toggleRecordClip,
                                    onPlayPauseClip = viewModel::toggleClipPlayback,
                                    onStopClip = viewModel::stopClipPlayback,
                                    onToggleLoop = viewModel::toggleClipLoop,
                                    onSeekClip = viewModel::seekClipTo,
                                    onRenameClip = viewModel::renameClip,
                                    onDeleteClip = viewModel::deleteClip,
                                    onRouteClipThroughFxChange = viewModel::setRouteClipThroughFx,
                                    onMixModeChange = viewModel::setClipMicMixMode,
                                    onClipGainChange = viewModel::setClipGain
                                )
                            }

                            BroadcastTab.VOICE_FX -> {
                                VoiceChangerScreen(
                                    telemetry = telemetry,
                                    settings = settings,
                                    hasMicPermission = hasMicPermission,
                                    onRequestMicPermission = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    onToggleMic = viewModel::toggleMicBroadcast,
                                    onSelectPreset = viewModel::selectVoicePreset,
                                    onCustomPitchChange = viewModel::setCustomPitchSemitones,
                                    onCustomToneChange = viewModel::setCustomFormantTone,
                                    onCustomEchoChange = viewModel::setCustomEchoMix
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverlayPermissionDialog = false
                pendingEnableOverlay = false
            },
            title = { Text(stringResource(R.string.overlay_permission_title)) },
            text = { Text(stringResource(R.string.overlay_permission_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayPermissionDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Text(stringResource(R.string.overlay_permission_grant))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverlayPermissionDialog = false
                        pendingEnableOverlay = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun tabLabel(tab: BroadcastTab): String = when (tab) {
    BroadcastTab.MEGAPHONE -> stringResource(R.string.nav_megaphone)
    BroadcastTab.SOUNDBOARD -> stringResource(R.string.nav_soundboard)
    BroadcastTab.VOICE_FX -> stringResource(R.string.nav_voice_fx)
}

private fun tabIcon(tab: BroadcastTab, selected: Boolean) = when (tab) {
    BroadcastTab.MEGAPHONE -> if (selected) Icons.Rounded.Campaign else Icons.Outlined.Campaign
    BroadcastTab.SOUNDBOARD -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
    BroadcastTab.VOICE_FX -> if (selected) Icons.Rounded.RecordVoiceOver else Icons.Outlined.RecordVoiceOver
}
