package ai.venice.app.car

import ai.venice.app.VeniceApplication
import ai.venice.app.audio.VoiceController
import ai.venice.app.data.SecurePreferences
import ai.venice.app.data.VeniceRepository
import ai.venice.app.data.local.MessageEntity
import ai.venice.app.data.remote.ChatMessageDto
import ai.venice.app.data.remote.StreamEvent
import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.ui.VoicePhase
import android.Manifest
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.messaging.model.CarMessage
import androidx.car.app.messaging.model.ConversationCallback
import androidx.car.app.messaging.model.ConversationItem
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Car-optimized hands-free voice chat screen for Android Auto.
 *
 * Pipeline:
 * 1. Driver speaks into the car microphone ([VoiceController.startRecording] + silence detection via [VoiceController.getMaxAmplitude])
 * 2. Transcribe audio with Venice ASR ([VeniceApiClient.transcribeAudio])
 * 3. Stream chat reply from Venice LLM ([VeniceApiClient.streamChatCompletion])
 * 4. Synthesize speech with Venice TTS ([VeniceApiClient.synthesizeSpeech]) and play through car speakers ([VoiceController.playAudioFile])
 * 5. Automatically loop back to listening for hands-free driving conversation.
 */
class VeniceCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val app: VeniceApplication = carContext.applicationContext as VeniceApplication
    private val repository: VeniceRepository = app.repository
    private val apiClient: VeniceApiClient = repository.apiClient
    private val prefs: SecurePreferences = app.securePreferences
    private val voiceController: VoiceController = app.voiceController

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var voiceJob: Job? = null

    @Volatile
    private var finishTurnRequested: Boolean = false

    private var voicePhase: VoicePhase = VoicePhase.IDLE
    private var isVoiceActive: Boolean = false
    private var heardText: String = ""
    private var replyText: String = ""
    private var errorText: String? = null
    private var currentConversationId: String? = null
    private var showConversationList: Boolean = false

    private data class TurnRecord(
        val id: String,
        val role: String,
        val text: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val recentTurns = mutableListOf<TurnRecord>()

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (prefs.getApiKey().isNotBlank() && hasMicPermission() && !isVoiceActive) {
            startHandsFreeVoiceChat()
        } else {
            invalidate()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        stopHandsFreeVoiceChat()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stopHandsFreeVoiceChat()
        screenScope.cancel()
    }

    override fun onGetTemplate(): Template {
        if (showConversationList) {
            return buildMessagingListTemplate()
        }
        return buildVoiceMessageTemplate()
    }

    private fun buildVoiceMessageTemplate(): MessageTemplate {
        val modelName = VeniceApiClient.formatModelName(prefs.getDefaultTextModel())
        val headerBuilder = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle("Venice Voice • $modelName")

        if (recentTurns.isNotEmpty()) {
            headerBuilder.addEndHeaderAction(
                Action.Builder()
                    .setTitle("Thread")
                    .setOnClickListener {
                        showConversationList = true
                        invalidate()
                    }
                    .build()
            )
        } else {
            headerBuilder.addEndHeaderAction(
                Action.Builder()
                    .setTitle("New Chat")
                    .setOnClickListener { resetConversation() }
                    .build()
            )
        }

        val bodyText = buildStatusBodyText()
        val builder = MessageTemplate.Builder(bodyText)
            .setHeader(headerBuilder.build())

        when {
            prefs.getApiKey().isBlank() -> {
                builder.setIcon(CarIcon.ALERT)
                builder.addAction(
                    Action.Builder()
                        .setTitle("Check Again")
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            if (prefs.getApiKey().isNotBlank()) {
                                startHandsFreeVoiceChat()
                            } else {
                                CarToast.makeText(
                                    carContext,
                                    "Add your Venice API key in the phone app Settings",
                                    CarToast.LENGTH_LONG
                                ).show()
                                invalidate()
                            }
                        }
                        .build()
                )
            }

            !hasMicPermission() -> {
                builder.setIcon(CarIcon.ALERT)
                builder.addAction(
                    Action.Builder()
                        .setTitle("Grant Mic Access")
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener { requestMicPermissionAndStart() }
                        .build()
                )
            }

            voicePhase == VoicePhase.LISTENING -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Send Now")
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener { finishVoiceTurn() }
                        .build()
                )
                builder.addAction(
                    Action.Builder()
                        .setTitle("Pause Voice")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { stopHandsFreeVoiceChat() }
                        .build()
                )
            }

            voicePhase == VoicePhase.SPEAKING -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Next Turn")
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            voiceController.stopPlayback()
                        }
                        .build()
                )
                builder.addAction(
                    Action.Builder()
                        .setTitle("Pause Voice")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { stopHandsFreeVoiceChat() }
                        .build()
                )
            }

            voicePhase == VoicePhase.TRANSCRIBING || voicePhase == VoicePhase.THINKING -> {
                builder.setLoading(true)
                builder.addAction(
                    Action.Builder()
                        .setTitle("Cancel")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { stopHandsFreeVoiceChat() }
                        .build()
                )
            }

            else -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Speak to Venice")
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener { startHandsFreeVoiceChat() }
                        .build()
                )
                if (replyText.isNotBlank()) {
                    builder.addAction(
                        Action.Builder()
                            .setTitle("Replay Reply")
                            .setOnClickListener { replayLastReply() }
                            .build()
                    )
                } else {
                    builder.addAction(
                        Action.Builder()
                            .setTitle("New Chat")
                            .setOnClickListener { resetConversation() }
                            .build()
                    )
                }
            }
        }

        return builder.build()
    }

    /**
     * Car messaging conversation template view using [ConversationItem], [CarMessage], and [ConversationCallback].
     */
    private fun buildMessagingListTemplate(): ListTemplate {
        val modelName = VeniceApiClient.formatModelName(prefs.getDefaultTextModel())
        val selfPerson = Person.Builder()
            .setName("Driver")
            .setKey("venice_driver")
            .build()
        val venicePerson = Person.Builder()
            .setName(modelName)
            .setKey("venice_assistant")
            .build()

        val carMessages = if (recentTurns.isEmpty()) {
            listOf(
                CarMessage.Builder()
                    .setSender(venicePerson)
                    .setBody(CarText.create("Say something to start chatting with Venice."))
                    .setReceivedTimeEpochMillis(System.currentTimeMillis())
                    .setRead(true)
                    .build()
            )
        } else {
            recentTurns.takeLast(10).map { turn ->
                val sender = if (turn.role == "user") selfPerson else venicePerson
                CarMessage.Builder()
                    .setSender(sender)
                    .setBody(CarText.create(turn.text.take(280)))
                    .setReceivedTimeEpochMillis(turn.timestampMs)
                    .setRead(true)
                    .build()
            }
        }

        val conversationItem = ConversationItem.Builder(
            currentConversationId ?: "venice_car_conversation",
            CarText.create("Venice Voice Chat ($modelName)"),
            selfPerson,
            carMessages,
            object : ConversationCallback {
                override fun onMarkAsRead() {
                    replayLastReply()
                }

                override fun onTextReply(replyText: String) {
                    handleVoiceDictatedTextReply(replyText)
                }
            }
        ).build()

        val itemListBuilder = ItemList.Builder()
            .addItem(conversationItem)

        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle("Venice Conversation")
            .addEndHeaderAction(
                Action.Builder()
                    .setTitle("Voice Mode")
                    .setOnClickListener {
                        showConversationList = false
                        startHandsFreeVoiceChat()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(itemListBuilder.build())
            .build()
    }

    private fun buildStatusBodyText(): String {
        if (prefs.getApiKey().isBlank()) {
            return "Missing Venice API key.\nPlease open the Venice app on your phone and save your API key in Settings."
        }
        if (!hasMicPermission()) {
            return "Microphone permission is required for hands-free voice chat in Android Auto."
        }

        val statusLine = when (voicePhase) {
            VoicePhase.LISTENING -> "🎤 Listening… speak now (auto-sends on pause)"
            VoicePhase.TRANSCRIBING -> "⏳ Transcribing your voice…"
            VoicePhase.THINKING -> "💭 Venice is thinking…"
            VoicePhase.SPEAKING -> "🔊 Speaking reply…"
            VoicePhase.IDLE -> errorText?.let { "⚠️ $it" } ?: "Tap 'Speak to Venice' for hands-free voice chat."
        }

        val parts = mutableListOf(statusLine)
        if (heardText.isNotBlank()) {
            parts.add("You: ${heardText.take(160)}")
        }
        if (replyText.isNotBlank()) {
            parts.add("Venice: ${replyText.take(260)}")
        }
        return parts.joinToString("\n\n")
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicPermissionAndStart() {
        carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted, _ ->
            if (granted.contains(Manifest.permission.RECORD_AUDIO)) {
                startHandsFreeVoiceChat()
            } else {
                errorText = "Microphone permission was denied."
                invalidate()
            }
        }
    }

    fun startHandsFreeVoiceChat() {
        if (prefs.getApiKey().isBlank()) {
            errorText = "Missing Venice API key."
            invalidate()
            return
        }
        if (!hasMicPermission()) {
            requestMicPermissionAndStart()
            return
        }
        if (isVoiceActive) return

        voiceController.stopPlayback()
        voiceController.stopRecording()
        voiceJob?.cancel()

        isVoiceActive = true
        finishTurnRequested = false
        errorText = null
        voicePhase = VoicePhase.LISTENING
        invalidate()

        voiceJob = screenScope.launch {
            try {
                runHandsFreeVoiceLoop()
            } finally {
                voiceController.stopRecording()
                voiceController.stopPlayback()
                isVoiceActive = false
                voicePhase = VoicePhase.IDLE
                invalidate()
            }
        }
    }

    fun stopHandsFreeVoiceChat() {
        voiceJob?.cancel()
        voiceJob = null
        finishTurnRequested = false
        isVoiceActive = false
        voicePhase = VoicePhase.IDLE
        voiceController.stopRecording()
        voiceController.stopPlayback()
        invalidate()
    }

    fun finishVoiceTurn() {
        finishTurnRequested = true
    }

    private fun resetConversation() {
        stopHandsFreeVoiceChat()
        currentConversationId = null
        heardText = ""
        replyText = ""
        errorText = null
        recentTurns.clear()
        startHandsFreeVoiceChat()
    }

    private fun replayLastReply() {
        val textToSpeak = replyText.trim()
        if (textToSpeak.isEmpty()) return
        stopHandsFreeVoiceChat()
        voiceJob = screenScope.launch {
            try {
                isVoiceActive = true
                speakWithVeniceTts(textToSpeak)
            } finally {
                isVoiceActive = false
                voicePhase = VoicePhase.IDLE
                invalidate()
            }
        }
    }

    private fun handleVoiceDictatedTextReply(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return
        stopHandsFreeVoiceChat()
        showConversationList = false
        voiceJob = screenScope.launch {
            try {
                isVoiceActive = true
                heardText = trimmed
                recentTurns.add(TurnRecord(UUID.randomUUID().toString(), "user", trimmed))
                val reply = sendToVeniceChat(trimmed)
                if (reply.isNotBlank()) {
                    speakWithVeniceTts(reply)
                }
                runHandsFreeVoiceLoop()
            } finally {
                voiceController.stopRecording()
                voiceController.stopPlayback()
                isVoiceActive = false
                voicePhase = VoicePhase.IDLE
                invalidate()
            }
        }
    }

    private suspend fun runHandsFreeVoiceLoop() {
        while (currentCoroutineContext().isActive) {
            // 1. LISTENING (car mic via VoiceController)
            voicePhase = VoicePhase.LISTENING
            errorText = null
            invalidate()

            val recResult = voiceController.startRecording()
            if (recResult.isFailure) {
                errorText = "Could not access microphone: ${recResult.exceptionOrNull()?.message ?: "unknown error"}"
                return
            }

            val recordedFile = awaitVoiceTurnEnd()
            if (recordedFile == null) {
                continue
            }

            // 2. TRANSCRIBING (reuse VeniceApiClient transcription)
            voicePhase = VoicePhase.TRANSCRIBING
            invalidate()

            val transcribed = apiClient.transcribeAudio(
                audioFile = recordedFile,
                model = prefs.getDefaultAsrModel()
            ).getOrElse { err ->
                errorText = err.message ?: "Transcription failed"
                ""
            }.trim()

            runCatching { recordedFile.delete() }

            if (transcribed.isBlank()) {
                continue
            }

            heardText = transcribed
            recentTurns.add(TurnRecord(UUID.randomUUID().toString(), "user", transcribed))
            invalidate()

            // 3. THINKING / STREAMING CHAT (reuse VeniceApiClient streaming chat)
            val assistantReply = sendToVeniceChat(transcribed)
            if (assistantReply.isBlank()) {
                continue
            }

            // 4. SPEAKING (reuse VeniceApiClient TTS + VoiceController playback)
            speakWithVeniceTts(assistantReply)
        }
    }

    private suspend fun sendToVeniceChat(userMessageText: String): String {
        voicePhase = VoicePhase.THINKING
        replyText = ""
        invalidate()

        val convId = ensureCarConversation(userMessageText)
        val modelId = prefs.getDefaultTextModel()
        val temperature = prefs.getDefaultTemperature()
        val reasoningEffort = prefs.getDefaultReasoningEffort()
        val enableWebSearch = prefs.getDefaultWebSearch()

        val userEntity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "user",
            content = userMessageText,
            modelId = modelId
        )
        repository.insertMessage(userEntity)

        val history = repository.getMessages(convId)
        val systemPrompt = prefs.getSystemPrompt().trim()
        val dtoMessages = buildList {
            if (systemPrompt.isNotEmpty()) {
                add(ChatMessageDto(role = "system", content = systemPrompt))
            }
            history.forEach { msg ->
                if (!msg.isError && msg.content.isNotBlank()) {
                    add(ChatMessageDto(role = msg.role, content = msg.content))
                }
            }
        }

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var streamError: String? = null
        var usageStats: StreamEvent.Usage? = null
        var lastInvalidateMs = 0L

        apiClient.streamChatCompletion(
            model = modelId,
            messages = dtoMessages,
            temperature = temperature,
            reasoningEffort = reasoningEffort,
            enableWebSearch = enableWebSearch
        ).collect { event ->
            when (event) {
                is StreamEvent.Delta -> {
                    if (event.content.isNotEmpty()) {
                        contentBuilder.append(event.content)
                    }
                    if (event.reasoning.isNotEmpty()) {
                        reasoningBuilder.append(event.reasoning)
                    }
                    replyText = contentBuilder.toString().trim()
                    val now = System.currentTimeMillis()
                    if (now - lastInvalidateMs >= 750L) {
                        lastInvalidateMs = now
                        invalidate()
                    }
                }
                is StreamEvent.Usage -> {
                    usageStats = event
                }
                is StreamEvent.Error -> {
                    streamError = event.message
                }
                is StreamEvent.Done -> Unit
            }
        }

        val finalReply = contentBuilder.toString().trim()
        val finalReasoning = reasoningBuilder.toString().trim()

        if (finalReply.isNotEmpty() || streamError != null) {
            val stats = usageStats
            repository.insertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "assistant",
                    content = if (finalReply.isNotEmpty()) finalReply else (streamError ?: ""),
                    reasoningContent = finalReasoning,
                    modelId = modelId,
                    isError = finalReply.isEmpty() && streamError != null,
                    promptTokens = stats?.promptTokens ?: 0,
                    completionTokens = stats?.completionTokens ?: 0,
                    costUsd = stats?.costUsd ?: 0.0,
                    tokensPerSecond = stats?.tokensPerSecond ?: 0.0
                )
            )
        }

        if (finalReply.isNotEmpty()) {
            replyText = finalReply
            recentTurns.add(TurnRecord(UUID.randomUUID().toString(), "assistant", finalReply))
        } else if (streamError != null) {
            errorText = streamError
        }
        invalidate()
        return finalReply
    }

    private suspend fun speakWithVeniceTts(text: String) {
        voicePhase = VoicePhase.SPEAKING
        invalidate()

        val ttsResult = apiClient.synthesizeSpeech(
            text = text,
            voice = prefs.getDefaultVoice(),
            model = prefs.getDefaultTtsModel()
        )
        ttsResult.onSuccess { audioFile ->
            try {
                playAudioAndAwait(audioFile, "car-voice-${System.currentTimeMillis()}")
            } finally {
                runCatching { audioFile.delete() }
            }
        }.onFailure { err ->
            errorText = err.message ?: "Speech synthesis failed"
            invalidate()
        }
    }

    /**
     * Waits until the driver finishes speaking (silence detected via [VoiceController.getMaxAmplitude]),
     * taps "Send Now", or the turn reaches its timeout.
     */
    private suspend fun awaitVoiceTurnEnd(): File? {
        val silenceThreshold = 800
        val silenceNeededMs = 2500L
        val noSpeechTimeoutMs = 8000L
        val maxTurnMs = 30000L
        val startedAt = System.currentTimeMillis()
        var quietSince = 0L
        var heardSpeech = false

        while (currentCoroutineContext().isActive) {
            if (finishTurnRequested) {
                finishTurnRequested = false
                break
            }
            val amp = voiceController.getMaxAmplitude()
            val now = System.currentTimeMillis()
            if (amp > silenceThreshold) {
                heardSpeech = true
                quietSince = 0L
            } else if (heardSpeech) {
                if (quietSince == 0L) {
                    quietSince = now
                } else if (now - quietSince >= silenceNeededMs) {
                    break
                }
            } else if (now - startedAt >= noSpeechTimeoutMs) {
                break
            }
            if (now - startedAt >= maxTurnMs) {
                break
            }
            delay(200)
        }
        return voiceController.stopRecording()
    }

    private suspend fun playAudioAndAwait(file: File, messageId: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            voiceController.playAudioFile(messageId, file) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { voiceController.stopPlayback() }
        }
    }

    private suspend fun ensureCarConversation(firstText: String): String {
        currentConversationId?.let { return it }
        val created = repository.createConversation(
            title = "Car: ${firstText.take(36).lines().firstOrNull() ?: "Voice Chat"}",
            modelId = prefs.getDefaultTextModel(),
            temperature = prefs.getDefaultTemperature(),
            reasoningEffort = prefs.getDefaultReasoningEffort(),
            webSearchEnabled = prefs.getDefaultWebSearch()
        )
        currentConversationId = created.id
        return created.id
    }
}
