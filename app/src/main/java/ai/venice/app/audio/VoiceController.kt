package ai.venice.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceController(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

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

    fun stopRecording(): File? {
        val recorder = mediaRecorder
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
    }
}
