package com.example

import com.example.audio.AlertToneSynthesizer
import com.example.audio.QuickAlertTone
import com.example.audio.VoiceDspProcessor
import com.example.audio.VoicePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceDspProcessorTest {

    @Test
    fun femaleAndMalePresetsProduceBoundedNonSilentOutput() {
        val sampleRate = 44100
        val length = 2048
        val input = ShortArray(length) { i ->
            (sin(2.0 * PI * 220.0 * i / sampleRate) * 12000.0).toInt().toShort()
        }

        val presetsToTest = listOf(
            VoicePreset.NATURAL,
            VoicePreset.FEMALE,
            VoicePreset.MALE,
            VoicePreset.KID,
            VoicePreset.DEEP_GIANT,
            VoicePreset.MEGAPHONE,
            VoicePreset.ROBOT
        )

        val dsp = VoiceDspProcessor(sampleRate)
        val output = FloatArray(length)
        val bars = FloatArray(28)

        for (preset in presetsToTest) {
            dsp.reset()
            dsp.pitchSemitones = preset.pitchSemitones
            dsp.formantTone = preset.formantTone
            dsp.echoMix = preset.echoMix
            dsp.robotMix = preset.robotMix
            dsp.hornFilter = preset.hornFilter
            dsp.gain = 1.5f
            dsp.noiseGateEnabled = true

            dsp.processFrame(input, length, output)
            val db = VoiceDspProcessor.computeTelemetry(output, length, bars)

            assertTrue("Preset ${preset.id} output should not be silent ($db dB)", db > -40f)
            assertTrue("Preset ${preset.id} output must stay bounded", output.all { it in -1f..1f && !it.isNaN() })
        }
    }

    @Test
    fun alertToneSynthesizerGeneratesSignalAndStopsAfterDuration() {
        val synth = AlertToneSynthesizer(sampleRate = 44100)
        synth.trigger(QuickAlertTone.CHIME)
        assertEquals(QuickAlertTone.CHIME, synth.activeTone)

        val buffer = FloatArray(512)
        synth.mixInto(buffer, 512)
        val peak = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("Alert tone should produce audible amplitude", peak > 0.05f)
    }
}
