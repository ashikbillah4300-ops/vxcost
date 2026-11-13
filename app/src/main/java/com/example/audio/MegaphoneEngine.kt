package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.example.data.AudioClipEntity
import com.example.data.ClipMicMixMode
import com.example.data.VoxCastSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.tanh

data class AudioOutputRoute(
    val label: String,
    val isBluetooth: Boolean,
    val isWired: Boolean
)

data class BroadcastTelemetry(
    val isMicLive: Boolean = false,
    val isClipPlaying: Boolean = false,
    val isClipPaused: Boolean = false,
    val playingClipId: Long? = null,
    val playingClipTitle: String? = null,
    val clipPositionMs: Long = 0L,
    val clipDurationMs: Long = 0L,
    val isClipLooping: Boolean = false,
    val activeAlertTone: QuickAlertTone? = null,
    val inputDb: Float = -60f,
    val waveformBars: List<Float> = List(28) { 0f },
    val broadcastSeconds: Long = 0L,
    val outputRoute: AudioOutputRoute = AudioOutputRoute("Phone Speaker", isBluetooth = false, isWired = false),
    val isRecordingClip: Boolean = false,
    val recordingSeconds: Long = 0L,
    val errorMessage: String? = null
)

class MegaphoneEngine(private val context: Context) {

    private val sampleRate = 44100
    private val frameSize = 512

    private val micDsp = VoiceDspProcessor(sampleRate)
    private val clipHornDsp = VoiceDspProcessor(sampleRate)
    private val alertSynthesizer = AlertToneSynthesizer(sampleRate)

    private val _telemetry = MutableStateFlow(BroadcastTelemetry())
    val telemetry: StateFlow<BroadcastTelemetry> = _telemetry.asStateFlow()

    @Volatile private var currentSettings = VoxCastSettings()
    @Volatile private var micRequested = false
    @Volatile private var clipRequested = false
    @Volatile private var clipPaused = false
    @Volatile private var clipLooping = false
    @Volatile private var pendingClip: AudioClipEntity? = null
    @Volatile private var pendingSeekMs: Long? = null
    @Volatile private var activeClipPlayer: StreamingAudioClipPlayer? = null

    // Clip recording state
    @Volatile private var recordingFile: File? = null
    private var recordingStream: RandomAccessFile? = null
    private var recordedBytesCount = 0L
    private var onRecordingSavedCallback: ((File, Long) -> Unit)? = null

    private var workerThread: Thread? = null
    private val engineLock = Any()

    fun updateSettings(settings: VoxCastSettings) {
        currentSettings = settings
        val preset = VoicePreset.fromId(settings.selectedPresetId)
        val pitch = if (preset == VoicePreset.CUSTOM) settings.customPitchSemitones else preset.pitchSemitones
        val tone = if (preset == VoicePreset.CUSTOM) settings.customFormantTone else preset.formantTone
        val echo = if (preset == VoicePreset.CUSTOM) settings.customEchoMix else preset.echoMix
        val robot = if (preset == VoicePreset.CUSTOM) 0f else preset.robotMix
        val horn = preset.hornFilter || settings.megaphoneHornFilter

        micDsp.pitchSemitones = pitch
        micDsp.formantTone = tone
        micDsp.echoMix = echo
        micDsp.robotMix = robot
        micDsp.hornFilter = horn
        micDsp.gain = settings.micGain
        micDsp.noiseGateEnabled = settings.feedbackGuard

        // Clip DSP uses optional horn/voice FX when routeClipThroughFx is toggled
        if (settings.routeClipThroughFx) {
            clipHornDsp.pitchSemitones = 0f
            clipHornDsp.formantTone = 0.2f
            clipHornDsp.echoMix = echo * 0.5f
            clipHornDsp.robotMix = 0f
            clipHornDsp.hornFilter = settings.megaphoneHornFilter || preset == VoicePreset.MEGAPHONE
            clipHornDsp.gain = settings.clipGain
            clipHornDsp.noiseGateEnabled = false
        } else {
            clipHornDsp.pitchSemitones = 0f
            clipHornDsp.formantTone = 0f
            clipHornDsp.echoMix = 0f
            clipHornDsp.robotMix = 0f
            clipHornDsp.hornFilter = false
            clipHornDsp.gain = settings.clipGain
            clipHornDsp.noiseGateEnabled = false
        }
    }

    fun refreshOutputRoute() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val wired = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_AUX_LINE
        }
        val route = when {
            bt != null -> AudioOutputRoute(
                label = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Speaker",
                isBluetooth = true,
                isWired = false
            )
            wired != null -> AudioOutputRoute(
                label = wired.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired AUX / Headset",
                isBluetooth = false,
                isWired = true
            )
            else -> AudioOutputRoute("Phone Speaker", isBluetooth = false, isWired = false)
        }
        _telemetry.update { it.copy(outputRoute = route) }
    }

    fun startMicBroadcast() {
        synchronized(engineLock) {
            micRequested = true
            ensureEngineRunning()
            syncForegroundService()
        }
    }

    fun stopMicBroadcast() {
        synchronized(engineLock) {
            micRequested = false
            if (recordingFile != null) {
                finishClipRecordingLocked()
            }
            _telemetry.update { it.copy(isMicLive = false, broadcastSeconds = 0L) }
            syncForegroundService()
        }
    }

    fun playClip(clip: AudioClipEntity) {
        synchronized(engineLock) {
            pendingClip = clip
            clipPaused = false
            clipRequested = true
            _telemetry.update {
                it.copy(
                    isClipPlaying = true,
                    isClipPaused = false,
                    playingClipId = clip.id,
                    playingClipTitle = clip.title,
                    clipDurationMs = clip.durationMs,
                    clipPositionMs = 0L,
                    errorMessage = null
                )
            }
            ensureEngineRunning()
            syncForegroundService()
        }
    }

    fun toggleClipPause() {
        synchronized(engineLock) {
            if (!clipRequested) return
            clipPaused = !clipPaused
            _telemetry.update { it.copy(isClipPaused = clipPaused) }
        }
    }

    fun stopClip() {
        synchronized(engineLock) {
            clipRequested = false
            clipPaused = false
            pendingClip = null
            activeClipPlayer?.release()
            activeClipPlayer = null
            _telemetry.update {
                it.copy(
                    isClipPlaying = false,
                    isClipPaused = false,
                    playingClipId = null,
                    playingClipTitle = null,
                    clipPositionMs = 0L,
                    clipDurationMs = 0L
                )
            }
            syncForegroundService()
        }
    }

    fun toggleClipLoop() {
        synchronized(engineLock) {
            clipLooping = !clipLooping
            activeClipPlayer?.isLooping = clipLooping
            _telemetry.update { it.copy(isClipLooping = clipLooping) }
        }
    }

    fun seekClipTo(positionMs: Long) {
        pendingSeekMs = positionMs
        _telemetry.update { it.copy(clipPositionMs = positionMs) }
    }

    fun triggerAlertTone(tone: QuickAlertTone) {
        synchronized(engineLock) {
            alertSynthesizer.trigger(tone)
            _telemetry.update { it.copy(activeAlertTone = tone) }
            ensureEngineRunning()
        }
    }

    fun startRecordingClip(outputFile: File, onSaved: (File, Long) -> Unit) {
        synchronized(engineLock) {
            if (recordingFile != null) return
            try {
                val raf = RandomAccessFile(outputFile, "rw")
                raf.setLength(0L)
                // Reserve 44-byte WAV header
                raf.write(ByteArray(44))
                recordingStream = raf
                recordedBytesCount = 0L
                recordingFile = outputFile
                onRecordingSavedCallback = onSaved
                _telemetry.update { it.copy(isRecordingClip = true, recordingSeconds = 0L) }
                ensureEngineRunning()
            } catch (e: Exception) {
                _telemetry.update { it.copy(errorMessage = "Recording failed: ${e.message}") }
            }
        }
    }

    fun stopRecordingClip() {
        synchronized(engineLock) {
            finishClipRecordingLocked()
        }
    }

    private fun finishClipRecordingLocked() {
        val file = recordingFile ?: return
        val raf = recordingStream
        val bytes = recordedBytesCount
        val callback = onRecordingSavedCallback
        recordingFile = null
        recordingStream = null
        recordedBytesCount = 0L
        onRecordingSavedCallback = null

        runCatching {
            if (raf != null) {
                writeWavHeader(raf, bytes, sampleRate, 1, 16)
                raf.close()
            }
        }
        val durationMs = (bytes * 1000L) / (sampleRate * 2L)
        _telemetry.update { it.copy(isRecordingClip = false, recordingSeconds = 0L) }
        if (bytes > 0L) {
            callback?.invoke(file, durationMs)
        } else {
            runCatching { file.delete() }
        }
    }

    fun stopAll() {
        synchronized(engineLock) {
            micRequested = false
            clipRequested = false
            clipPaused = false
            pendingClip = null
            alertSynthesizer.stop()
            if (recordingFile != null) finishClipRecordingLocked()
            activeClipPlayer?.release()
            activeClipPlayer = null
            _telemetry.update {
                it.copy(
                    isMicLive = false,
                    isClipPlaying = false,
                    isClipPaused = false,
                    playingClipId = null,
                    playingClipTitle = null,
                    clipPositionMs = 0L,
                    activeAlertTone = null,
                    inputDb = -60f,
                    waveformBars = List(28) { 0f },
                    broadcastSeconds = 0L
                )
            }
            syncForegroundService()
        }
    }

    fun clearError() {
        _telemetry.update { it.copy(errorMessage = null) }
    }

    private fun ensureEngineRunning() {
        if (workerThread?.isAlive == true) return
        workerThread = Thread({ runAudioLoop() }, "VoxCastAudioEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun runAudioLoop() {
        micDsp.reset()
        clipHornDsp.reset()

        val minRecBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSize * 4)

        val minPlayBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSize * 4)

        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minPlayBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()
                .apply {
                    setVolume(1.0f)
                }
        } catch (e: Exception) {
            _telemetry.update { it.copy(errorMessage = "Audio output error: ${e.message}") }
            return
        }

        var audioRecord: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        var lastGuardState = currentSettings.feedbackGuard

        fun tryCreateRecord(source: Int, enableFx: Boolean): AudioRecord? {
            return try {
                val rec = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minRecBuf
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    if (enableFx) {
                        if (AcousticEchoCanceler.isAvailable()) {
                            aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
                        }
                        if (NoiseSuppressor.isAvailable()) {
                            ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
                        }
                    }
                    rec.startRecording()
                    if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        rec
                    } else {
                        rec.release()
                        null
                    }
                } else {
                    rec.release()
                    null
                }
            } catch (_: SecurityException) {
                _telemetry.update { it.copy(errorMessage = "Microphone permission is required for live broadcast.") }
                micRequested = false
                null
            } catch (_: Exception) {
                null
            }
        }

        fun openAudioRecord(guardEnabled: Boolean): AudioRecord? {
            val primary = if (guardEnabled) {
                tryCreateRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, enableFx = true)
                    ?: tryCreateRecord(MediaRecorder.AudioSource.MIC, enableFx = false)
            } else {
                tryCreateRecord(MediaRecorder.AudioSource.MIC, enableFx = false)
            }
            if (primary == null && (micRequested || recordingFile != null)) {
                micRequested = false
                _telemetry.update {
                    it.copy(
                        isMicLive = false,
                        errorMessage = "Microphone is busy (another app or phone call is using it)."
                    )
                }
            }
            return primary
        }

        fun closeAudioRecord() {
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            aec = null
            ns = null
            runCatching { audioRecord?.stop() }
            runCatching { audioRecord?.release() }
            audioRecord = null
        }

        audioTrack.play()
        refreshOutputRoute()

        val micShort = ShortArray(frameSize)
        val micFloat = FloatArray(frameSize)
        val clipShort = ShortArray(frameSize)
        val clipFloat = FloatArray(frameSize)
        val mixFloat = FloatArray(frameSize)
        val outShort = ShortArray(frameSize)
        val recByteBuf = ByteArray(frameSize * 2)
        val barsBuffer = FloatArray(28)

        var micStartTimeMs = 0L
        var recStartTimeMs = 0L
        var lastUiPushMs = 0L
        var consecutiveZeroReads = 0

        try {
            while (micRequested || clipRequested || alertSynthesizer.activeTone != null || recordingFile != null) {
                val settings = currentSettings
                val needMic = micRequested || recordingFile != null

                // Handle AudioRecord lifecycle dynamically
                if (needMic) {
                    if (audioRecord == null || lastGuardState != settings.feedbackGuard) {
                        closeAudioRecord()
                        lastGuardState = settings.feedbackGuard
                        audioRecord = openAudioRecord(settings.feedbackGuard)
                        if (audioRecord != null && micStartTimeMs == 0L) {
                            micStartTimeMs = System.currentTimeMillis()
                        }
                    }
                } else {
                    if (audioRecord != null) {
                        closeAudioRecord()
                        micStartTimeMs = 0L
                    }
                }

                // Handle pending clip switch
                val nextClip = pendingClip
                if (nextClip != null) {
                    pendingClip = null
                    activeClipPlayer?.release()
                    val player = StreamingAudioClipPlayer(nextClip.filePath, sampleRate)
                    if (player.prepare()) {
                        player.isLooping = clipLooping
                        activeClipPlayer = player
                    } else {
                        clipRequested = false
                        _telemetry.update {
                            it.copy(
                                isClipPlaying = false,
                                playingClipId = null,
                                errorMessage = "Could not decode '${nextClip.title}'"
                            )
                        }
                    }
                }

                val seekTarget = pendingSeekMs
                if (seekTarget != null) {
                    pendingSeekMs = null
                    activeClipPlayer?.seekTo(seekTarget)
                }

                mixFloat.fill(0f)

                // 1. Read & process live mic
                var micActiveNow = false
                if (needMic && audioRecord != null) {
                    val read = audioRecord!!.read(micShort, 0, frameSize)
                    if (read > 0) {
                        var nonZero = false
                        for (i in 0 until read) {
                            if (micShort[i] != 0.toShort()) {
                                nonZero = true
                                break
                            }
                        }
                        if (!nonZero) {
                            consecutiveZeroReads++
                            if (consecutiveZeroReads == 90) {
                                _telemetry.update {
                                    it.copy(errorMessage = "No mic audio detected (another app or call may be locking the mic).")
                                }
                            }
                        } else {
                            consecutiveZeroReads = 0
                        }
                        micActiveNow = true
                        micDsp.processFrame(micShort, read, micFloat)

                        // If recording a soundboard clip, capture processed mic audio cleanly
                        if (recordingFile != null) {
                            if (recStartTimeMs == 0L) recStartTimeMs = System.currentTimeMillis()
                            synchronized(engineLock) {
                                val raf = recordingStream
                                if (raf != null) {
                                    for (i in 0 until read) {
                                        val s = (micFloat[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                        recByteBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                                        recByteBuf[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                                    }
                                    runCatching {
                                        raf.write(recByteBuf, 0, read * 2)
                                        recordedBytesCount += read * 2L
                                    }
                                }
                            }
                        } else {
                            recStartTimeMs = 0L
                        }

                        // Mix mic into live broadcast output (unless clip is in CLIP_ONLY mode)
                        if (micRequested) {
                            val micScale = when {
                                clipRequested && !clipPaused && settings.clipMicMixMode == ClipMicMixMode.CLIP_ONLY -> 0f
                                clipRequested && !clipPaused && settings.clipMicMixMode == ClipMicMixMode.AUTO_DUCK -> 0.35f
                                else -> 1.0f
                            }
                            if (micScale > 0f) {
                                for (i in 0 until read) {
                                    mixFloat[i] += micFloat[i] * micScale
                                }
                            }
                        }
                    }
                }

                // 2. Read & process active soundboard clip directly into the broadcast pipeline
                val player = activeClipPlayer
                if (clipRequested && !clipPaused && player != null) {
                    val read = player.readMonoFrame(clipShort, frameSize)
                    if (read > 0) {
                        clipHornDsp.processFrame(clipShort, read, clipFloat)
                        for (i in 0 until read) {
                            mixFloat[i] += clipFloat[i]
                        }
                    }
                    if (player.isFinished) {
                        clipRequested = false
                        activeClipPlayer?.release()
                        activeClipPlayer = null
                        _telemetry.update {
                            it.copy(
                                isClipPlaying = false,
                                isClipPaused = false,
                                playingClipId = null,
                                playingClipTitle = null,
                                clipPositionMs = 0L
                            )
                        }
                        syncForegroundService()
                    }
                }

                // 3. Mix alert tones (chime / siren / air horn)
                alertSynthesizer.mixInto(mixFloat, frameSize)

                // 4. Write final mixed PCM to AudioTrack (unless only recording silently without live broadcast)
                val shouldPlayAudio = micRequested || (clipRequested && !clipPaused) || alertSynthesizer.activeTone != null
                if (shouldPlayAudio) {
                    for (i in 0 until frameSize) {
                        val limited = tanh(mixFloat[i])
                        outShort[i] = (limited * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    audioTrack.write(outShort, 0, frameSize)
                } else if (!needMic) {
                    Thread.sleep(10)
                }

                // 5. Update UI telemetry at ~25 FPS
                val now = System.currentTimeMillis()
                if (now - lastUiPushMs >= 40L) {
                    lastUiPushMs = now
                    val dbSource = if (shouldPlayAudio) mixFloat else micFloat
                    val db = VoiceDspProcessor.computeTelemetry(dbSource, frameSize, barsBuffer)
                    val secLive = if (micRequested && micStartTimeMs > 0L) (now - micStartTimeMs) / 1000L else 0L
                    val secRec = if (recordingFile != null && recStartTimeMs > 0L) (now - recStartTimeMs) / 1000L else 0L

                    _telemetry.update {
                        it.copy(
                            isMicLive = micRequested && micActiveNow,
                            inputDb = db,
                            waveformBars = barsBuffer.toList(),
                            broadcastSeconds = secLive,
                            recordingSeconds = secRec,
                            clipPositionMs = activeClipPlayer?.currentPositionMs ?: it.clipPositionMs,
                            clipDurationMs = max(it.clipDurationMs, activeClipPlayer?.durationMs ?: 0L),
                            activeAlertTone = alertSynthesizer.activeTone
                        )
                    }
                }
            }
        } finally {
            closeAudioRecord()
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            activeClipPlayer?.release()
            activeClipPlayer = null
            _telemetry.update {
                it.copy(
                    isMicLive = false,
                    inputDb = -60f,
                    waveformBars = List(28) { 0f },
                    activeAlertTone = null,
                    broadcastSeconds = 0L
                )
            }
            syncForegroundService()
        }
    }

    private fun syncForegroundService() {
        val active = micRequested || clipRequested
        val intent = Intent(context, BroadcastForegroundService::class.java).apply {
            action = if (active) BroadcastForegroundService.ACTION_START else BroadcastForegroundService.ACTION_STOP
            putExtra(BroadcastForegroundService.EXTRA_HAS_MIC, micRequested)
        }
        runCatching {
            if (active) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }

    private fun writeWavHeader(
        raf: RandomAccessFile,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8L
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate.toInt())
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(totalAudioLen.toInt())
        raf.seek(0L)
        raf.write(header.array())
    }
}
