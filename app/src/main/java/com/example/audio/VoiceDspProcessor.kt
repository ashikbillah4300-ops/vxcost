package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure Kotlin real-time audio DSP pipeline operating at 44,100 Hz mono:
 * Noise Gate -> Dual-Head Hann Pitch Shifter -> Formant Shelf EQ ->
 * Optional PA Horn Bandpass -> Optional Ring-Mod Robot -> Echo Delay -> Soft Limiter.
 */
class VoiceDspProcessor(private val sampleRate: Int = 44100) {

    // Pitch shifter circular buffer (power-of-two capacity for fast masking)
    private val pitchBufSize = 4096
    private val pitchBufMask = pitchBufSize - 1
    private val pitchBuffer = FloatArray(pitchBufSize)
    private val windowSize = 1024f
    private var writeIndex = 0
    private var phase = 0f

    // Echo delay buffer (~200 ms)
    private val echoDelaySamples = (sampleRate * 0.185f).toInt()
    private val echoBuffer = FloatArray(echoDelaySamples)
    private var echoIndex = 0

    // Noise gate envelope
    private var gateEnvelope = 0f

    // Ring modulator phase for Robot FX
    private var robotPhase = 0.0

    // Biquad states: Low-shelf/HPF & High-shelf/LPF
    private var bq1X1 = 0f; private var bq1X2 = 0f; private var bq1Y1 = 0f; private var bq1Y2 = 0f
    private var bq2X1 = 0f; private var bq2X2 = 0f; private var bq2Y1 = 0f; private var bq2Y2 = 0f
    private var hornHpX1 = 0f; private var hornHpY1 = 0f
    private var hornLpY1 = 0f

    // Cached filter coefficients
    private var lastToneParam = Float.NaN
    private var b1B0 = 1f; private var b1B1 = 0f; private var b1B2 = 0f; private var b1A1 = 0f; private var b1A2 = 0f
    private var b2B0 = 1f; private var b2B1 = 0f; private var b2B2 = 0f; private var b2A1 = 0f; private var b2A2 = 0f

    @Volatile var pitchSemitones: Float = 0f
    @Volatile var formantTone: Float = 0f
    @Volatile var echoMix: Float = 0f
    @Volatile var robotMix: Float = 0f
    @Volatile var hornFilter: Boolean = false
    @Volatile var gain: Float = 2.0f
    @Volatile var noiseGateEnabled: Boolean = false

    fun reset() {
        pitchBuffer.fill(0f)
        echoBuffer.fill(0f)
        writeIndex = 0
        phase = 0f
        echoIndex = 0
        gateEnvelope = 0f
        robotPhase = 0.0
        bq1X1 = 0f; bq1X2 = 0f; bq1Y1 = 0f; bq1Y2 = 0f
        bq2X1 = 0f; bq2X2 = 0f; bq2Y1 = 0f; bq2Y2 = 0f
        hornHpX1 = 0f; hornHpY1 = 0f; hornLpY1 = 0f
    }

    /**
     * Processes [input] PCM shorts in-place or into [output] float buffer normalized [-1f, 1f].
     * Returns peak dBFS in [-60f, 0f].
     */
    fun processFrame(input: ShortArray, length: Int, output: FloatArray) {
        val pitchRatio = 2.0.pow((pitchSemitones / 12.0)).toFloat().coerceIn(0.45f, 2.2f)
        val tone = formantTone.coerceIn(-1f, 1f)
        val echo = echoMix.coerceIn(0f, 0.65f)
        val robot = robotMix.coerceIn(0f, 1f)
        val useHorn = hornFilter
        val localGain = gain.coerceIn(0.1f, 4.5f)
        val gateOn = noiseGateEnabled

        if (abs(tone - lastToneParam) > 0.01f) {
            updateToneFilters(tone)
            lastToneParam = tone
        }

        val phaseStep = (1f - pitchRatio) / windowSize
        val robotFreq = 58.0
        val robotStep = 2.0 * PI * robotFreq / sampleRate

        // First-order horn bandpass coefficients (~420Hz HPF, ~3100Hz LPF)
        val hpAlpha = 0.942f
        val lpAlpha = 0.36f

        for (i in 0 until length) {
            var sample = input[i] / 32768f

            // 1. Noise gate (gentle envelope so normal speech is never muted)
            if (gateOn) {
                val absS = abs(sample)
                gateEnvelope = if (absS > gateEnvelope) {
                    0.9f * absS + 0.1f * gateEnvelope
                } else {
                    0.002f * absS + 0.998f * gateEnvelope
                }
                val gateThreshold = 0.0025f
                val gateGain = if (gateEnvelope < gateThreshold) {
                    (gateEnvelope / gateThreshold).pow(2f).coerceIn(0.15f, 1f)
                } else {
                    1f
                }
                sample *= gateGain
            }

            // 2. Dual-head Hann-windowed real-time pitch shifter
            pitchBuffer[writeIndex] = sample
            if (abs(pitchSemitones) > 0.05f) {
                val phaseA = phase
                val phaseB = (phase + 0.5f).let { if (it >= 1f) it - 1f else it }

                val delayA = phaseA * windowSize
                val delayB = phaseB * windowSize

                val readA = readInterpolated(writeIndex - delayA)
                val readB = readInterpolated(writeIndex - delayB)

                val sinA = sin(PI * phaseA).toFloat()
                val winA = sinA * sinA
                val winB = 1f - winA // sin^2(pi*(p+0.5)) == cos^2(pi*p) == 1 - sin^2(pi*p)

                sample = readA * winA + readB * winB

                phase += phaseStep
                while (phase >= 1f) phase -= 1f
                while (phase < 0f) phase += 1f
            }
            writeIndex = (writeIndex + 1) and pitchBufMask

            // 3. Formant / Tone EQ biquads
            if (abs(tone) > 0.02f) {
                val y1 = b1B0 * sample + b1B1 * bq1X1 + b1B2 * bq1X2 - b1A1 * bq1Y1 - b1A2 * bq1Y2
                bq1X2 = bq1X1; bq1X1 = sample
                bq1Y2 = bq1Y1; bq1Y1 = y1

                val y2 = b2B0 * y1 + b2B1 * bq2X1 + b2B2 * bq2X2 - b2A1 * bq2Y1 - b2A2 * bq2Y2
                bq2X2 = bq2X1; bq2X1 = y1
                bq2Y2 = bq2Y1; bq2Y1 = y2
                sample = y2
            }

            // 4. PA Megaphone Bullhorn filter
            if (useHorn) {
                val hp = hpAlpha * (hornHpY1 + sample - hornHpX1)
                hornHpX1 = sample
                hornHpY1 = hp
                hornLpY1 += lpAlpha * (hp - hornLpY1)
                // Gentle megaphone horn saturation
                sample = tanh(hornLpY1 * 1.9f) * 0.85f
            }

            // 5. Cyber Robot ring modulator
            if (robot > 0.01f) {
                val carrier = sin(robotPhase).toFloat()
                robotPhase += robotStep
                if (robotPhase >= 2.0 * PI) robotPhase -= 2.0 * PI
                val modulated = sample * carrier * 1.45f
                sample = sample * (1f - robot) + modulated * robot
            }

            // 6. Echo delay
            if (echo > 0.01f) {
                val delayed = echoBuffer[echoIndex]
                val nextEcho = (sample + delayed * 0.42f).coerceIn(-1f, 1f)
                echoBuffer[echoIndex] = nextEcho
                echoIndex = (echoIndex + 1) % echoDelaySamples
                sample += delayed * echo
            }

            // 7. Gain + Soft Limiter
            output[i] = tanh(sample * localGain)
        }
    }

    private fun readInterpolated(pos: Float): Float {
        var wrapped = pos
        while (wrapped < 0f) wrapped += pitchBufSize
        while (wrapped >= pitchBufSize) wrapped -= pitchBufSize
        val idx0 = wrapped.toInt() and pitchBufMask
        val idx1 = (idx0 + 1) and pitchBufMask
        val frac = wrapped - wrapped.toInt()
        return pitchBuffer[idx0] + (pitchBuffer[idx1] - pitchBuffer[idx0]) * frac
    }

    private fun updateToneFilters(tone: Float) {
        if (tone >= 0f) {
            // Bright / Female / Kid profile: trim mud around 170 Hz, boost presence around 2500 Hz
            computeShelf(freq = 170.0, gainDb = -4.5 * tone, isHighShelf = false) { b0, b1, b2, a1, a2 ->
                b1B0 = b0; b1B1 = b1; b1B2 = b2; b1A1 = a1; b1A2 = a2
            }
            computeShelf(freq = 2500.0, gainDb = 6.5 * tone, isHighShelf = true) { b0, b1, b2, a1, a2 ->
                b2B0 = b0; b2B1 = b1; b2B2 = b2; b2A1 = a1; b2A2 = a2
            }
        } else {
            // Warm / Male / Deep Bass profile: boost chest warmth around 230 Hz, soften highs above 3200 Hz
            val mag = -tone
            computeShelf(freq = 230.0, gainDb = 6.0 * mag, isHighShelf = false) { b0, b1, b2, a1, a2 ->
                b1B0 = b0; b1B1 = b1; b1B2 = b2; b1A1 = a1; b1A2 = a2
            }
            computeShelf(freq = 3200.0, gainDb = -4.5 * mag, isHighShelf = true) { b0, b1, b2, a1, a2 ->
                b2B0 = b0; b2B1 = b1; b2B2 = b2; b2A1 = a1; b2A2 = a2
            }
        }
    }

    private inline fun computeShelf(
        freq: Double,
        gainDb: Double,
        isHighShelf: Boolean,
        assign: (Float, Float, Float, Float, Float) -> Unit
    ) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double

        if (isHighShelf) {
            b0 = a * ((a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha)
            b1 = -2 * a * ((a - 1) + (a + 1) * cosW0)
            b2 = a * ((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha)
            a0 = (a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha
            a1 = 2 * ((a - 1) - (a + 1) * cosW0)
            a2 = (a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha
        } else {
            b0 = a * ((a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha)
            b1 = 2 * a * ((a - 1) - (a + 1) * cosW0)
            b2 = a * ((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha)
            a0 = (a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha
            a1 = -2 * ((a - 1) + (a + 1) * cosW0)
            a2 = (a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha
        }

        assign(
            (b0 / a0).toFloat(),
            (b1 / a0).toFloat(),
            (b2 / a0).toFloat(),
            (a1 / a0).toFloat(),
            (a2 / a0).toFloat()
        )
    }

    companion object {
        fun computeTelemetry(
            samples: FloatArray,
            length: Int,
            barsOut: FloatArray
        ): Float {
            if (length <= 0) {
                barsOut.fill(0f)
                return -60f
            }
            var sumSq = 0f
            for (i in 0 until length) {
                val s = samples[i]
                sumSq += s * s
            }
            val rms = sqrt(sumSq / length)
            val db = if (rms > 1e-6f) (20f * log10(rms)).coerceIn(-60f, 0f) else -60f

            val barCount = barsOut.size
            val chunkSize = max(1, length / barCount)
            for (b in 0 until barCount) {
                val start = b * chunkSize
                val end = min(length, start + chunkSize)
                var peak = 0f
                for (i in start until end) {
                    val v = abs(samples[i])
                    if (v > peak) peak = v
                }
                // Smooth perceptual scaling for bars
                barsOut[b] = sqrt(peak).coerceIn(0f, 1f)
            }
            return db
        }
    }
}
