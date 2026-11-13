package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "voxcast_settings")

enum class ClipMicMixMode(val label: String) {
    AUTO_DUCK("Duck Mic"),
    MIX_BOTH("Mix + Mic"),
    CLIP_ONLY("Audio Only")
}

data class VoxCastSettings(
    val micGain: Float = 2.2f,
    val feedbackGuard: Boolean = false,
    val megaphoneHornFilter: Boolean = false,
    val holdToTalkMode: Boolean = false,
    val selectedPresetId: String = "NATURAL",
    val customPitchSemitones: Float = 0f,
    val customFormantTone: Float = 0f,
    val customEchoMix: Float = 0f,
    val routeClipThroughFx: Boolean = true,
    val clipMicMixMode: ClipMicMixMode = ClipMicMixMode.MIX_BOTH,
    val clipGain: Float = 1.0f,
    val speakerphoneForced: Boolean = true,
    val floatingOverlayEnabled: Boolean = false
)

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val MIC_GAIN = floatPreferencesKey("mic_gain")
        val FEEDBACK_GUARD = booleanPreferencesKey("echo_guard_v2")
        val MEGAPHONE_HORN = booleanPreferencesKey("megaphone_horn")
        val HOLD_TO_TALK = booleanPreferencesKey("hold_to_talk")
        val PRESET_ID = stringPreferencesKey("preset_id")
        val CUSTOM_PITCH = floatPreferencesKey("custom_pitch")
        val CUSTOM_FORMANT = floatPreferencesKey("custom_formant")
        val CUSTOM_ECHO = floatPreferencesKey("custom_echo")
        val ROUTE_CLIP_FX = booleanPreferencesKey("route_clip_fx")
        val CLIP_MIX_MODE = stringPreferencesKey("clip_mix_mode")
        val CLIP_GAIN = floatPreferencesKey("clip_gain")
        val SPEAKERPHONE_FORCED = booleanPreferencesKey("speakerphone_forced")
        val FLOATING_OVERLAY = booleanPreferencesKey("floating_overlay")
    }

    val settingsFlow: Flow<VoxCastSettings> = context.dataStore.data.map { prefs ->
        val mixModeName = prefs[Keys.CLIP_MIX_MODE] ?: ClipMicMixMode.MIX_BOTH.name
        val mixMode = runCatching { ClipMicMixMode.valueOf(mixModeName) }
            .getOrDefault(ClipMicMixMode.MIX_BOTH)

        VoxCastSettings(
            micGain = prefs[Keys.MIC_GAIN] ?: 2.2f,
            feedbackGuard = prefs[Keys.FEEDBACK_GUARD] ?: false,
            megaphoneHornFilter = prefs[Keys.MEGAPHONE_HORN] ?: false,
            holdToTalkMode = prefs[Keys.HOLD_TO_TALK] ?: false,
            selectedPresetId = prefs[Keys.PRESET_ID] ?: "NATURAL",
            customPitchSemitones = prefs[Keys.CUSTOM_PITCH] ?: 0f,
            customFormantTone = prefs[Keys.CUSTOM_FORMANT] ?: 0f,
            customEchoMix = prefs[Keys.CUSTOM_ECHO] ?: 0f,
            routeClipThroughFx = prefs[Keys.ROUTE_CLIP_FX] ?: true,
            clipMicMixMode = mixMode,
            clipGain = prefs[Keys.CLIP_GAIN] ?: 1.0f,
            speakerphoneForced = prefs[Keys.SPEAKERPHONE_FORCED] ?: true,
            floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY] ?: false
        )
    }

    suspend fun update(transform: (VoxCastSettings) -> VoxCastSettings) {
        context.dataStore.edit { prefs ->
            val current = VoxCastSettings(
                micGain = prefs[Keys.MIC_GAIN] ?: 2.2f,
                feedbackGuard = prefs[Keys.FEEDBACK_GUARD] ?: false,
                megaphoneHornFilter = prefs[Keys.MEGAPHONE_HORN] ?: false,
                holdToTalkMode = prefs[Keys.HOLD_TO_TALK] ?: false,
                selectedPresetId = prefs[Keys.PRESET_ID] ?: "NATURAL",
                customPitchSemitones = prefs[Keys.CUSTOM_PITCH] ?: 0f,
                customFormantTone = prefs[Keys.CUSTOM_FORMANT] ?: 0f,
                customEchoMix = prefs[Keys.CUSTOM_ECHO] ?: 0f,
                routeClipThroughFx = prefs[Keys.ROUTE_CLIP_FX] ?: true,
                clipMicMixMode = runCatching {
                    ClipMicMixMode.valueOf(prefs[Keys.CLIP_MIX_MODE] ?: ClipMicMixMode.MIX_BOTH.name)
                }.getOrDefault(ClipMicMixMode.MIX_BOTH),
                clipGain = prefs[Keys.CLIP_GAIN] ?: 1.0f,
                speakerphoneForced = prefs[Keys.SPEAKERPHONE_FORCED] ?: true,
                floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY] ?: false
            )
            val next = transform(current)
            prefs[Keys.MIC_GAIN] = next.micGain
            prefs[Keys.FEEDBACK_GUARD] = next.feedbackGuard
            prefs[Keys.MEGAPHONE_HORN] = next.megaphoneHornFilter
            prefs[Keys.HOLD_TO_TALK] = next.holdToTalkMode
            prefs[Keys.PRESET_ID] = next.selectedPresetId
            prefs[Keys.CUSTOM_PITCH] = next.customPitchSemitones
            prefs[Keys.CUSTOM_FORMANT] = next.customFormantTone
            prefs[Keys.CUSTOM_ECHO] = next.customEchoMix
            prefs[Keys.ROUTE_CLIP_FX] = next.routeClipThroughFx
            prefs[Keys.CLIP_MIX_MODE] = next.clipMicMixMode.name
            prefs[Keys.CLIP_GAIN] = next.clipGain
            prefs[Keys.SPEAKERPHONE_FORCED] = next.speakerphoneForced
            prefs[Keys.FLOATING_OVERLAY] = next.floatingOverlayEnabled
        }
    }
}
