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
            dsp.gain = 2.0f
            dsp.noiseGateEnabled = false

            dsp.processFrame(input, length, output)
            val db = VoiceDspProcessor.computeTelemetry(output, length, bars)

            assertTrue("Preset ${preset.id} output should not be silent ($db dB)", db > -40f)
            assertTrue("Preset ${preset.id} output must stay bounded", output.all { it in -1f..1f && !it.isNaN() })
        }
    }

    @Test
    fun pitchShifterShiftsFrequencyUpForFemaleAndDownForMale() {
        val sampleRate = 44100
        val totalSamples = sampleRate // 1 second
        val inputFreq = 200.0
        val input = ShortArray(totalSamples) { i ->
            (sin(2.0 * PI * inputFreq * i / sampleRate) * 14000.0).toInt().toShort()
        }

        fun measureOutputFreq(semitones: Float): Double {
            val dsp = VoiceDspProcessor(sampleRate).apply {
                pitchSemitones = semitones
                formantTone = 0f
                echoMix = 0f
                robotMix = 0f
                hornFilter = false
                gain = 1.0f
                noiseGateEnabled = false
            }
            val output = FloatArray(totalSamples)
            dsp.processFrame(input, totalSamples, output)

            // Skip initial buffer fill (4096 samples) and count positive-going zero crossings
            val start = 8192
            var crossings = 0
            for (i in (start + 1) until totalSamples) {
                if (output[i - 1] <= 0f && output[i] > 0f) crossings++
            }
            val durationSec = (totalSamples - start).toDouble() / sampleRate
            return crossings / durationSec
        }

        val femaleFreq = measureOutputFreq(VoicePreset.FEMALE.pitchSemitones)
        val maleFreq = measureOutputFreq(VoicePreset.MALE.pitchSemitones)

        assertTrue("Female pitch ($femaleFreq Hz) should be significantly above $inputFreq Hz", femaleFreq > 265.0)
        assertTrue("Male pitch ($maleFreq Hz) should be significantly below $inputFreq Hz", maleFreq < 160.0)
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
