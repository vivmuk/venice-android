package ai.venice.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceController(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    // ---- Live capture (AudioRecord PCM) for the real-time voice loop ----
    // One continuous 16kHz mono PCM capture for the whole voice session. It emits
    // WAV chunks every ~2.5s (transcribed live for the on-screen transcript) and
    // keeps a per-turn buffer so the full turn can be transcribed as a fallback.
    private var liveRecorder: AudioRecord? = null
    private var liveCaptureThread: Thread? = null
    @Volatile private var liveCaptureRunning = false
    private val liveLock = Any()
    private var liveChunkBuffer = ByteArrayOutputStream()
    private var liveTurnBuffer = ByteArrayOutputStream()
    private var liveChunkBytesTarget = (LIVE_SAMPLE_RATE * 2 * LIVE_CHUNK_MS / 1000).toInt()
    private var liveChunkCallback: ((File) -> Unit)? = null

    private val _liveAmplitude = MutableStateFlow(0)
    /** Peak mic amplitude (0..32767) from the live capture; used for silence + barge-in. */
    val liveAmplitude: StateFlow<Int> = _liveAmplitude.asStateFlow()

    fun startRecording(): Result<File> = runCatching {
        stopRecording()
        stopPlayback()

        val dir = File(context.cacheDir, "voice_recordings").apply { mkdirs() }
        val outFile = File(dir, "venice_mic_${System.currentTimeMillis()}.m4a")

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outFile.absolutePath)
            prepare()
            start()
        }

        mediaRecorder = recorder
        currentRecordingFile = outFile
        _isRecording.value = true
        outFile
    }

    /** Live mic level (0..32767) while recording; 0 when not recording. Used for silence detection. */
    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun stopRecording(): File? {        val recorder = mediaRecorder
        val file = currentRecordingFile
        mediaRecorder = null
        currentRecordingFile = null
        _isRecording.value = false

        if (recorder != null) {
            try {
                recorder.stop()
            } catch (_: Exception) {
            } finally {
                try {
                    recorder.release()
                } catch (_: Exception) {
                }
            }
        }
        return file?.takeIf { it.exists() && it.length() > 0 }
    }

    fun playAudioFile(messageId: String, file: File, onComplete: () -> Unit = {}) {
        stopPlayback()
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _playingMessageId.value = null
                    onComplete()
                    release()
                }
                setOnErrorListener { mp, _, _ ->
                    _playingMessageId.value = null
                    onComplete()
                    mp.release()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _playingMessageId.value = messageId
        } catch (e: Exception) {
            _playingMessageId.value = null
            onComplete()
        }
    }

    fun stopPlayback() {
        val player = mediaPlayer
        mediaPlayer = null
        _playingMessageId.value = null
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {
            } finally {
                try {
                    player.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun release() {
        stopRecording()
        stopPlayback()
        stopLiveCapture()
    }

    // ---- Live capture: continuous PCM for the real-time voice loop ----

    /**
     * Starts a continuous 16kHz mono PCM capture for the whole voice session.
     * Every [chunkDurationMs] the buffered audio is written to a WAV file and handed
     * to [onChunk] (the caller transcribes it for the live transcript). Amplitude is
     * published to [liveAmplitude] on every read for silence detection and barge-in.
     * Safe to call from a coroutine; the capture runs on its own thread.
     */
    fun startLiveCapture(
        chunkDurationMs: Long = LIVE_CHUNK_MS,
        onChunk: (File) -> Unit
    ): Result<Unit> = runCatching {
        stopLiveCapture()
        liveChunkBytesTarget = (LIVE_SAMPLE_RATE * 2 * chunkDurationMs / 1000).toInt()
        liveChunkCallback = onChunk
        synchronized(liveLock) {
            liveChunkBuffer = ByteArrayOutputStream()
            liveTurnBuffer = ByteArrayOutputStream()
        }

        val minBuf = AudioRecord.getMinBufferSize(
            LIVE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val recorder = AudioRecord.Builder()
            // VOICE_COMMUNICATION enables the device's acoustic echo canceller, so
            // the reply playing through the speaker does not feed back into the mic
            // and trip the barge-in detector.
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(LIVE_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone could not be initialized.")
        }
        liveRecorder = recorder
        liveCaptureRunning = true
        _isRecording.value = true
        recorder.startRecording()

        val thread = Thread({
            val shorts = ShortArray(minBuf / 2)
            val byteBuf = ByteArray(shorts.size * 2)
            while (liveCaptureRunning) {
                val read = try {
                    recorder.read(shorts, 0, shorts.size)
                } catch (_: Exception) {
                    break
                }
                if (read <= 0) {
                    if (!liveCaptureRunning) break
                    continue
                }
                // Peak amplitude for silence / barge-in detection.
                var peak = 0
                for (i in 0 until read) {
                    val a = kotlin.math.abs(shorts[i].toInt())
                    if (a > peak) peak = a
                }
                _liveAmplitude.value = peak
                // PCM16 little-endian bytes.
                ByteBuffer.wrap(byteBuf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .put(shorts, 0, read)
                val bytes = byteBuf.copyOf(read * 2)
                var chunkFile: File? = null
                synchronized(liveLock) {
                    liveChunkBuffer.write(bytes)
                    liveTurnBuffer.write(bytes)
                    if (liveChunkBuffer.size() >= liveChunkBytesTarget) {
                        chunkFile = writeWavFile(
                            newVoiceChunkFile(),
                            liveChunkBuffer.toByteArray()
                        )
                        liveChunkBuffer = ByteArrayOutputStream()
                    }
                }
                chunkFile?.let { liveChunkCallback?.invoke(it) }
            }
        }, "venice-live-capture")
        liveCaptureThread = thread
        thread.start()
    }

    /** Resets the per-turn audio buffer + chunk timer (call when a new LISTENING turn starts). */
    fun beginVoiceTurn() {
        synchronized(liveLock) {
            liveTurnBuffer = ByteArrayOutputStream()
            liveChunkBuffer = ByteArrayOutputStream()
        }
    }

    /**
     * Writes whatever is in the current chunk buffer to a WAV file now and resets the
     * chunk timer. Used at turn end (final partial chunk) and on barge-in, so the
     * interrupting speech is transcribed instead of being dropped. Null when empty.
     */
    fun flushVoiceChunk(): File? {
        val pcm = synchronized(liveLock) {
            val bytes = liveChunkBuffer.toByteArray()
            liveChunkBuffer = ByteArrayOutputStream()
            bytes
        }
        if (pcm.isEmpty()) return null
        return writeWavFile(newVoiceChunkFile(), pcm)
    }

    /** Full audio of the current turn as a WAV file (fallback if chunk transcripts fail). */
    fun endVoiceTurn(): File? {
        val pcm = synchronized(liveLock) { liveTurnBuffer.toByteArray() }
        if (pcm.isEmpty()) return null
        return writeWavFile(newVoiceChunkFile(), pcm)
    }

    /** Stops the live capture (session end). Does not delete chunk files. */
    fun stopLiveCapture() {
        liveCaptureRunning = false
        liveChunkCallback = null
        _liveAmplitude.value = 0
        _isRecording.value = false
        try {
            liveRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            liveRecorder?.release()
        } catch (_: Exception) {
        }
        liveRecorder = null
        val thread = liveCaptureThread
        liveCaptureThread = null
        thread?.join(500)
    }

    private fun newVoiceChunkFile(): File {
        val dir = File(context.cacheDir, "voice_chunks").apply { mkdirs() }
        return File(dir, "venice_chunk_${System.currentTimeMillis()}_${(0..9999).random()}.wav")
    }

    private fun writeWavFile(file: File, pcmBytes: ByteArray): File {
        val dataSize = pcmBytes.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM
        header.putShort(1) // audio format PCM
        header.putShort(1) // mono
        header.putInt(LIVE_SAMPLE_RATE)
        header.putInt(LIVE_SAMPLE_RATE * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        FileOutputStream(file).use { out ->
            out.write(header.array())
            out.write(pcmBytes)
        }
        return file
    }

    // ---- Streaming PCM playback (AudioTrack) for streaming TTS ----

    /**
     * A streaming PCM player for Venice's chunked TTS audio (24kHz signed-16 LE).
     * Create one per reply, [writePcm] each chunk as it arrives, [stop] at the end.
     * Playback starts as soon as the first chunk is written — no waiting for the
     * full file. [isPlaying] is false once the queued audio has drained.
     */
    inner class StreamingPcmPlayer {
        private val track: AudioTrack
        @Volatile var isPlaying = false
            private set
        /** Frames written so far (1 frame = 1 mono s16 sample); used to detect drain. */
        @Volatile private var framesWritten = 0L

        init {
            val minBuf = AudioTrack.getMinBufferSize(
                TTS_PCM_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(TTS_PCM_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf * 4)
                .build()
            track.play()
            isPlaying = true
        }

        fun writePcm(bytes: ByteArray) {
            if (!isPlaying) return
            try {
                val written = track.write(bytes, 0, bytes.size)
                if (written > 0) framesWritten += written / 2
            } catch (_: Exception) {
            }
        }

        /** Blocks until the queued audio has played out (or the timeout), then releases. */
        fun stopAndAwaitDrain(timeoutMs: Long = 30000L) {
            if (!isPlaying) return
            isPlaying = false
            try {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val pos = try { track.playbackHeadPosition.toLong() } catch (_: Exception) { -1L }
                    if (pos < 0 || pos >= framesWritten) break
                    Thread.sleep(50)
                }
            } catch (_: Exception) {
            } finally {
                try { track.stop() } catch (_: Exception) {}
                try { track.release() } catch (_: Exception) {}
            }
        }

        /** Stops immediately (barge-in / cancel) and releases. */
        fun stopNow() {
            if (!isPlaying) return
            isPlaying = false
            try { track.pause() } catch (_: Exception) {}
            try { track.flush() } catch (_: Exception) {}
            try { track.stop() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
        }
    }

    fun createPcmPlayer(): StreamingPcmPlayer = StreamingPcmPlayer()

    companion object {
        /** Live-capture sample rate (Whisper/Parakeet accept 16kHz WAV). */
        const val LIVE_SAMPLE_RATE = 16000
        /** Live-transcription chunk length. */
        const val LIVE_CHUNK_MS = 2500L
        /** Venice streaming TTS PCM format: 24kHz signed-16 LE mono. */
        const val TTS_PCM_SAMPLE_RATE = 24000
    }
}
