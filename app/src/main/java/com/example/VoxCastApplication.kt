package com.example

import android.app.Application
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.example.audio.BroadcastForegroundService
import com.example.audio.BroadcastTelemetry
import com.example.audio.FloatingOverlayService
import com.example.audio.MegaphoneEngine
import com.example.audio.QuickAlertTone
import com.example.audio.VoicePreset
import com.example.data.AppDatabase
import com.example.data.AudioClipEntity
import com.example.data.AudioClipRepository
import com.example.data.ClipMicMixMode
import com.example.data.SettingsPreferences
import com.example.data.VoxCastSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoxCastController(private val application: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase = AppDatabase.getInstance(application)
    val repository: AudioClipRepository = AudioClipRepository(application, database.audioClipDao())
    val settingsPrefs: SettingsPreferences = SettingsPreferences(application)
    val engine: MegaphoneEngine = MegaphoneEngine(application)
    private val audioManager: AudioManager? = application.getSystemService(AudioManager::class.java)

    val telemetry: StateFlow<BroadcastTelemetry> = engine.telemetry

    val settings: StateFlow<VoxCastSettings> = settingsPrefs.settingsFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = VoxCastSettings()
        )

    val clips: StateFlow<List<AudioClipEntity>> = repository.allClips
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            engine.refreshOutputRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            engine.refreshOutputRoute()
        }
    }

    init {
        BroadcastForegroundService.onStopBroadcastRequested = {
            engine.stopAll()
        }
        scope.launch {
            settingsPrefs.settingsFlow.collect { latest ->
                engine.updateSettings(latest)
                syncOverlayService(latest.floatingOverlayEnabled)
            }
        }
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        engine.refreshOutputRoute()
    }

    fun syncOverlayService(enabled: Boolean) {
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(application)
        val intent = Intent(application, FloatingOverlayService::class.java)
        if (enabled && canDraw) {
            intent.action = FloatingOverlayService.ACTION_SHOW
            runCatching { application.startService(intent) }
        } else {
            intent.action = FloatingOverlayService.ACTION_HIDE
            runCatching { application.stopService(intent) }
        }
    }

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        scope.launch {
            settingsPrefs.update { it.copy(floatingOverlayEnabled = enabled) }
        }
    }

    fun toggleMicBroadcast() {
        if (telemetry.value.isMicLive) {
            engine.stopMicBroadcast()
        } else {
            engine.startMicBroadcast()
        }
    }

    fun setMicBroadcastActive(active: Boolean) {
        if (active) {
            if (!telemetry.value.isMicLive) engine.startMicBroadcast()
        } else {
            if (telemetry.value.isMicLive) engine.stopMicBroadcast()
        }
    }

    fun setMicGain(gain: Float) {
        scope.launch {
            settingsPrefs.update { it.copy(micGain = gain.coerceIn(0.5f, 4.0f)) }
        }
    }

    fun setFeedbackGuard(enabled: Boolean) {
        scope.launch {
            settingsPrefs.update { it.copy(feedbackGuard = enabled) }
        }
    }

    fun setMegaphoneHornFilter(enabled: Boolean) {
        scope.launch {
            settingsPrefs.update { it.copy(megaphoneHornFilter = enabled) }
        }
    }

    fun setHoldToTalkMode(enabled: Boolean) {
        if (enabled && telemetry.value.isMicLive) {
            engine.stopMicBroadcast()
        }
        scope.launch {
            settingsPrefs.update { it.copy(holdToTalkMode = enabled) }
        }
    }

    fun selectVoicePreset(preset: VoicePreset) {
        scope.launch {
            settingsPrefs.update { it.copy(selectedPresetId = preset.id) }
        }
    }

    fun setCustomPitchSemitones(semitones: Float) {
        scope.launch {
            settingsPrefs.update {
                it.copy(
                    selectedPresetId = VoicePreset.CUSTOM.id,
                    customPitchSemitones = semitones.coerceIn(-12f, 12f)
                )
            }
        }
    }

    fun setCustomFormantTone(tone: Float) {
        scope.launch {
            settingsPrefs.update {
                it.copy(
                    selectedPresetId = VoicePreset.CUSTOM.id,
                    customFormantTone = tone.coerceIn(-1f, 1f)
                )
            }
        }
    }

    fun setCustomEchoMix(echo: Float) {
        scope.launch {
            settingsPrefs.update {
                it.copy(
                    selectedPresetId = VoicePreset.CUSTOM.id,
                    customEchoMix = echo.coerceIn(0f, 0.6f)
                )
            }
        }
    }

    fun setRouteClipThroughFx(enabled: Boolean) {
        scope.launch {
            settingsPrefs.update { it.copy(routeClipThroughFx = enabled) }
        }
    }

    fun setClipMicMixMode(mode: ClipMicMixMode) {
        scope.launch {
            settingsPrefs.update { it.copy(clipMicMixMode = mode) }
        }
    }

    fun setClipGain(gain: Float) {
        scope.launch {
            settingsPrefs.update { it.copy(clipGain = gain.coerceIn(0.3f, 2.5f)) }
        }
    }

    fun triggerAlertTone(tone: QuickAlertTone) {
        engine.triggerAlertTone(tone)
    }

    fun postBanner(message: String) {
        _statusBanner.value = message
    }

    fun setImporting(importing: Boolean) {
        _isImporting.value = importing
    }

    fun toggleClipPlayback(clip: AudioClipEntity) {
        val t = telemetry.value
        if (t.isClipPlaying && t.playingClipId == clip.id) {
            engine.toggleClipPause()
        } else {
            engine.playClip(clip)
        }
    }

    fun stopClipPlayback() {
        engine.stopClip()
    }

    fun toggleClipLoop() {
        engine.toggleClipLoop()
    }

    fun seekClipTo(positionMs: Long) {
        engine.seekClipTo(positionMs)
    }

    fun toggleRecordClip() {
        if (telemetry.value.isRecordingClip) {
            engine.stopRecordingClip()
        } else {
            val file = repository.createRecordingFile()
            engine.startRecordingClip(file) { savedFile, durationMs ->
                scope.launch {
                    val count = clips.value.count { it.category == "Recorded" } + 1
                    val clip = repository.registerRecordedClip(
                        file = savedFile,
                        title = "Mic Clip #$count",
                        durationMs = durationMs
                    )
                    _statusBanner.value = "Saved \"${clip.title}\""
                }
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
        engine.clearError()
    }
}

class VoxCastApplication : Application() {
    val controller: VoxCastController by lazy { VoxCastController(this) }
}
