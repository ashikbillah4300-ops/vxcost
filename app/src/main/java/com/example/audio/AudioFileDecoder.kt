package com.example.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

enum class QuickAlertTone(val label: String) {
    CHIME("Attention"),
    SIREN("Siren"),
    AIR_HORN("Air Horn")
}

/**
 * Pure-math synthesizer for built-in megaphone alert signals (zero external assets).
 */
class AlertToneSynthesizer(private val sampleRate: Int = 44100) {
    @Volatile
    var activeTone: QuickAlertTone? = null
        private set

    private var sampleCursor = 0L
    private var maxSamples = 0L

    fun trigger(tone: QuickAlertTone) {
        sampleCursor = 0L
        maxSamples = when (tone) {
            QuickAlertTone.CHIME -> (sampleRate * 1.15f).toLong()
            QuickAlertTone.SIREN -> (sampleRate * 2.4f).toLong()
            QuickAlertTone.AIR_HORN -> (sampleRate * 1.1f).toLong()
        }
        activeTone = tone
    }

    fun stop() {
        activeTone = null
    }

    fun mixInto(buffer: FloatArray, length: Int) {
        val tone = activeTone ?: return
        for (i in 0 until length) {
            if (sampleCursor >= maxSamples) {
                activeTone = null
                return
            }
            val t = sampleCursor.toDouble() / sampleRate
            val env = when {
                sampleCursor < 400 -> sampleCursor / 400f
                sampleCursor > maxSamples - 1500 -> (maxSamples - sampleCursor) / 1500f
                else -> 1f
            }.coerceIn(0f, 1f)

            val raw = when (tone) {
                QuickAlertTone.CHIME -> {
                    // Clean two-tone PA announcement chime (E5 -> A5)
                    val freq = if (t < 0.38) 659.25 else 880.0
                    val noteTime = if (t < 0.38) t else t - 0.38
                    val decay = kotlin.math.exp(-noteTime * 3.2).toFloat()
                    (sin(2.0 * PI * freq * noteTime).toFloat() * 0.65f +
                        sin(4.0 * PI * freq * noteTime).toFloat() * 0.15f) * decay
                }
                QuickAlertTone.SIREN -> {
                    // Sweeping megaphone siren
                    val mod = 0.5 - 0.5 * kotlin.math.cos(2.0 * PI * 1.6 * t)
                    val freq = 520.0 + 540.0 * mod
                    // Accumulate phase smoothly via analytical integral of cos
                    val phase = 2.0 * PI * (790.0 * t - (540.0 / (4.0 * PI * 1.6)) * sin(2.0 * PI * 1.6 * t))
                    sin(phase).toFloat() * 0.55f
                }
                QuickAlertTone.AIR_HORN -> {
                    // Stadium air-horn brass chord
                    val f1 = 440.0
                    val f2 = 554.37
                    val s1 = sawtooth(f1 * t)
                    val s2 = sawtooth(f2 * t)
                    ((s1 + s2) * 0.28f)
                }
            }
            buffer[i] = (buffer[i] + raw * env * 0.85f).coerceIn(-1f, 1f)
            sampleCursor++
        }
    }

    private fun sawtooth(cycles: Double): Float {
        val frac = cycles - kotlin.math.floor(cycles)
        return (2.0 * frac - 1.0).toFloat()
    }
}

/**
 * Streaming MediaExtractor + MediaCodec decoder that converts any Android-supported audio file
 * (MP3, WAV, M4A, AAC, OGG, FLAC) into 44,100 Hz mono PCM shorts on the fly.
 */
class StreamingAudioClipPlayer(
    private val filePath: String,
    private val targetSampleRate: Int = 44100
) {
    private val extractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private var srcSampleRate = targetSampleRate
    private var srcChannels = 1
    private var isPcmFloat = false

    @Volatile var durationMs: Long = 0L
        private set

    @Volatile var currentPositionMs: Long = 0L
        private set

    @Volatile var isFinished: Boolean = false
        private set

    @Volatile var isLooping: Boolean = false

    private var leftoverMono = ShortArray(0)
    private var leftoverOffset = 0
    private var inputDone = false
    private var outputDone = false

    fun prepare(): Boolean {
        return try {
            extractor.setDataSource(filePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return false
            srcSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            srcChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            durationMs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
            } else 0L

            if (audioFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                val enc = audioFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                isPcmFloat = (enc == AudioFormat.ENCODING_PCM_FLOAT)
            }

            val createdCodec = MediaCodec.createDecoderByType(mime)
            createdCodec.configure(audioFormat, null, null, 0)
            createdCodec.start()
            codec = createdCodec
            true
        } catch (_: Exception) {
            release()
            false
        }
    }

    fun seekTo(positionMs: Long) {
        val c = codec ?: return
        try {
            extractor.seekTo(positionMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            c.flush()
            leftoverMono = ShortArray(0)
            leftoverOffset = 0
            inputDone = false
            outputDone = false
            isFinished = false
            currentPositionMs = positionMs
        } catch (_: Exception) {
        }
    }

    /**
     * Fills [dest] with up to [count] mono 44.1kHz PCM samples.
     * Returns actual sample count read (0 if finished).
     */
    fun readMonoFrame(dest: ShortArray, count: Int): Int {
        val c = codec ?: return 0
        var written = 0

        while (written < count) {
            if (leftoverOffset < leftoverMono.size) {
                val toCopy = min(count - written, leftoverMono.size - leftoverOffset)
                System.arraycopy(leftoverMono, leftoverOffset, dest, written, toCopy)
                written += toCopy
                leftoverOffset += toCopy
                continue
            }

            if (outputDone) {
                if (isLooping) {
                    seekTo(0L)
                    continue
                } else {
                    isFinished = true
                    break
                }
            }

            decodeMore(c)
        }
        return written
    }

    private fun decodeMore(c: MediaCodec) {
        if (!inputDone) {
            val inIdx = c.dequeueInputBuffer(4000L)
            if (inIdx >= 0) {
                val inBuf = c.getInputBuffer(inIdx)
                if (inBuf != null) {
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        c.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        c.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }
            }
        }

        val info = MediaCodec.BufferInfo()
        val outIdx = c.dequeueOutputBuffer(info, 4000L)
        when {
            outIdx >= 0 -> {
                val outBuf = c.getOutputBuffer(outIdx)
                if (outBuf != null && info.size > 0) {
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    currentPositionMs = (info.presentationTimeUs / 1000L).coerceAtLeast(0L)

                    val monoRaw = if (isPcmFloat) {
                        val floatBuf = outBuf.order(ByteOrder.nativeOrder()).asFloatBuffer()
                        val totalFloats = floatBuf.remaining()
                        val frames = totalFloats / srcChannels
                        ShortArray(frames) { f ->
                            var sum = 0f
                            for (ch in 0 until srcChannels) {
                                sum += floatBuf.get()
                            }
                            ((sum / srcChannels).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        }
                    } else {
                        val shortBuf = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val totalShorts = shortBuf.remaining()
                        val frames = totalShorts / srcChannels
                        ShortArray(frames) { f ->
                            var sum = 0
                            for (ch in 0 until srcChannels) {
                                sum += shortBuf.get().toInt()
                            }
                            (sum / srcChannels).coerceIn(-32768, 32767).toShort()
                        }
                    }

                    leftoverMono = resampleMono(monoRaw, srcSampleRate, targetSampleRate)
                    leftoverOffset = 0
                }
                c.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true
                }
            }
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFmt = c.outputFormat
                srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                srcChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                if (newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    isPcmFloat = newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
            }
        }
    }

    private fun resampleMono(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val outLen = ((input.size.toDouble() * dstRate) / srcRate).toInt().coerceAtLeast(1)
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        return ShortArray(outLen) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = (srcPos - idx0).toFloat()
            val s0 = input[idx0].toFloat()
            val s1 = input[idx1].toFloat()
            (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor.release() }
        codec = null
    }
}
