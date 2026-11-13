package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VoxCastApplication
import com.example.audio.BroadcastTelemetry
import com.example.audio.QuickAlertTone
import com.example.audio.VoicePreset
import com.example.data.AudioClipEntity
import com.example.data.ClipMicMixMode
import com.example.data.VoxCastSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class BroadcastTab {
    MEGAPHONE,
    SOUNDBOARD,
    VOICE_FX
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = (application as VoxCastApplication).controller

    val telemetry: StateFlow<BroadcastTelemetry> = controller.telemetry
    val settings: StateFlow<VoxCastSettings> = controller.settings
    val clips: StateFlow<List<AudioClipEntity>> = controller.clips
    val statusBanner: StateFlow<String?> = controller.statusBanner
    val isImporting: StateFlow<Boolean> = controller.isImporting

    fun toggleMicBroadcast() = controller.toggleMicBroadcast()

    fun setMicBroadcastActive(active: Boolean) = controller.setMicBroadcastActive(active)

    fun setMicGain(gain: Float) = controller.setMicGain(gain)

    fun setFeedbackGuard(enabled: Boolean) = controller.setFeedbackGuard(enabled)

    fun setMegaphoneHornFilter(enabled: Boolean) = controller.setMegaphoneHornFilter(enabled)

    fun setHoldToTalkMode(enabled: Boolean) = controller.setHoldToTalkMode(enabled)

    fun setFloatingOverlayEnabled(enabled: Boolean) = controller.setFloatingOverlayEnabled(enabled)

    fun syncOverlayAfterResume() {
        controller.syncOverlayService(settings.value.floatingOverlayEnabled)
    }

    fun selectVoicePreset(preset: VoicePreset) = controller.selectVoicePreset(preset)

    fun setCustomPitchSemitones(semitones: Float) = controller.setCustomPitchSemitones(semitones)

    fun setCustomFormantTone(tone: Float) = controller.setCustomFormantTone(tone)

    fun setCustomEchoMix(echo: Float) = controller.setCustomEchoMix(echo)

    fun setRouteClipThroughFx(enabled: Boolean) = controller.setRouteClipThroughFx(enabled)

    fun setClipMicMixMode(mode: ClipMicMixMode) = controller.setClipMicMixMode(mode)

    fun setClipGain(gain: Float) = controller.setClipGain(gain)

    fun triggerAlertTone(tone: QuickAlertTone) = controller.triggerAlertTone(tone)

    fun importAudioClip(uri: Uri) {
        viewModelScope.launch {
            controller.setImporting(true)
            val result = controller.repository.importAudioFromUri(uri)
            controller.setImporting(false)
            result.onSuccess { clip ->
                controller.postBanner("Added \"${clip.title}\" to Soundboard")
            }.onFailure { err ->
                controller.postBanner("Import failed: ${err.message ?: "Unsupported audio"}")
            }
        }
    }

    fun toggleClipPlayback(clip: AudioClipEntity) = controller.toggleClipPlayback(clip)

    fun stopClipPlayback() = controller.stopClipPlayback()

    fun toggleClipLoop() = controller.toggleClipLoop()

    fun seekClipTo(positionMs: Long) = controller.seekClipTo(positionMs)

    fun toggleRecordClip() = controller.toggleRecordClip()

    fun renameClip(clip: AudioClipEntity, newTitle: String) {
        viewModelScope.launch {
            controller.repository.renameClip(clip.id, newTitle)
        }
    }

    fun deleteClip(clip: AudioClipEntity) {
        if (telemetry.value.playingClipId == clip.id) {
            controller.stopClipPlayback()
        }
        viewModelScope.launch {
            controller.repository.deleteClip(clip)
            controller.postBanner("Removed \"${clip.title}\"")
        }
    }

    fun refreshAudioRoute() {
        controller.engine.refreshOutputRoute()
    }

    fun dismissBanner() {
        controller.dismissBanner()
    }
}
