package ai.venice.app.ui

import ai.venice.app.VeniceApplication
import ai.venice.app.audio.VoiceController
import ai.venice.app.data.SecurePreferences
import ai.venice.app.data.VeniceRepository
import ai.venice.app.data.local.ConversationEntity
import ai.venice.app.data.local.GeneratedImageEntity
import ai.venice.app.data.local.MessageEntity
import ai.venice.app.data.remote.StreamEvent
import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.data.remote.VeniceModel
import ai.venice.app.util.GallerySaver
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppSection {
    CHAT,
    IMAGINE,
    SETTINGS
}

enum class VoicePhase {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    SPEAKING
}

data class ChatUiState(
    val activeSection: AppSection = AppSection.CHAT,
    val currentConversationId: String? = null,
    val selectedTextModel: String = SecurePreferences.DEFAULT_TEXT_MODEL,
    val temperature: Float = 0.7f,
    val reasoningEffort: String = "none", // "none", "low", "medium", "high"
    val webSearchEnabled: Boolean = false,
    val textModels: List<VeniceModel> = emptyList(),
    val imageModels: List<VeniceModel> = VeniceApiClient.FALLBACK_IMAGE_MODELS,
    val selectedImageModel: String = SecurePreferences.DEFAULT_IMAGE_MODEL,
    val selectedImageSize: String = "1024x1024",
    val ttsModels: List<VeniceModel> = VeniceApiClient.FALLBACK_TTS_MODELS,
    val selectedTtsModel: String = SecurePreferences.DEFAULT_TTS_MODEL,
    /** Voice ID for the selected TTS model (model-specific; see voicesForTtsModel). */
    val selectedVoice: String = SecurePreferences.DEFAULT_VOICE,
    val asrModels: List<VeniceModel> = VeniceApiClient.FALLBACK_ASR_MODELS,
    val selectedAsrModel: String = SecurePreferences.DEFAULT_ASR_MODEL,
    val isLoadingModels: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val isTranscribing: Boolean = false,
    val isSynthesizingTts: Boolean = false,
    val isGeneratingImage: Boolean = false,
    val statusBanner: String? = null,
    val inputDraft: String = "",
    // Voice-to-voice conversation mode
    val isVoiceMode: Boolean = false,
    val voicePhase: VoicePhase = VoicePhase.IDLE,
    val voiceHeardText: String = "",
    val voiceReplyText: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class VeniceViewModel(
    private val repository: VeniceRepository,
    val prefs: SecurePreferences,
    val voiceController: VoiceController
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            selectedTextModel = prefs.getDefaultTextModel(),
            temperature = prefs.getDefaultTemperature(),
            reasoningEffort = prefs.getDefaultReasoningEffort(),
            webSearchEnabled = prefs.getDefaultWebSearch(),
            selectedImageModel = prefs.getDefaultImageModel(),
            selectedTtsModel = prefs.getDefaultTtsModel(),
            selectedVoice = prefs.getDefaultVoice(),
            selectedAsrModel = prefs.getDefaultAsrModel(),
            textModels = repository.apiClient.getDefaultTextModelCatalog()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val hasApiKey: StateFlow<Boolean> = prefs.hasApiKey

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generatedImages: StateFlow<List<GeneratedImageEntity>> = repository.generatedImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentConvIdFlow = MutableStateFlow<String?>(null)

    val currentMessages: StateFlow<List<MessageEntity>> = currentConvIdFlow
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList())
            else repository.observeMessages(convId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var streamingJob: Job? = null

    init {
        refreshCatalogs()
        viewModelScope.launch {
            repository.conversations.collect { list ->
                if (currentConvIdFlow.value == null && list.isNotEmpty()) {
                    selectConversation(list.first())
                }
            }
        }
    }

    fun setSection(section: AppSection) {
        _uiState.update { it.copy(activeSection = section) }
    }

    fun updateInputDraft(text: String) {
        _uiState.update { it.copy(inputDraft = text) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun showStatus(message: String) {
        _uiState.update { it.copy(statusBanner = message) }
    }

    /**
     * Loads all four model catalogs in parallel (was four sequential network calls)
     * and applies them together. The client caches each catalog on disk for 6h, so
     * repeat launches are instant. After the TTS catalog lands, the voice is
     * auto-switched if the stored voice is not valid for the selected TTS model
     * (a wrong voice/model combo is a 400 from Venice).
     */
    fun refreshCatalogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }
            val textDeferred = async { repository.fetchTextModels() }
            val imageDeferred = async { repository.fetchImageModels() }
            val ttsDeferred = async { repository.fetchTtsModels() }
            val asrDeferred = async { repository.fetchAsrModels() }

            textDeferred.await().onSuccess { models ->
                _uiState.update { it.copy(textModels = models) }
            }
            imageDeferred.await().onSuccess { imgModels ->
                _uiState.update { state ->
                    val chosen = if (imgModels.any { it.id == state.selectedImageModel }) {
                        state.selectedImageModel
                    } else {
                        imgModels.firstOrNull()?.id ?: state.selectedImageModel
                    }
                    state.copy(imageModels = imgModels, selectedImageModel = chosen)
                }
            }
            ttsDeferred.await().onSuccess { ttsModels ->
                _uiState.update { state ->
                    val chosen = if (ttsModels.any { it.id == state.selectedTtsModel }) {
                        state.selectedTtsModel
                    } else {
                        ttsModels.firstOrNull()?.id ?: state.selectedTtsModel
                    }
                    val validVoices = VeniceApiClient.voicesForTtsModel(ttsModels, chosen)
                    val voice = if (validVoices.isEmpty() || state.selectedVoice in validVoices) {
                        state.selectedVoice
                    } else {
                        validVoices.first().also { prefs.setDefaultVoice(it) }
                    }
                    state.copy(ttsModels = ttsModels, selectedTtsModel = chosen, selectedVoice = voice)
                }
            }
            asrDeferred.await().onSuccess { asrModels ->
                _uiState.update { state ->
                    val chosen = if (asrModels.any { it.id == state.selectedAsrModel }) {
                        state.selectedAsrModel
                    } else {
                        asrModels.firstOrNull()?.id ?: state.selectedAsrModel
                    }
                    state.copy(asrModels = asrModels, selectedAsrModel = chosen)
                }
            }
            _uiState.update { it.copy(isLoadingModels = false) }
        }
    }

    fun setSelectedTtsModel(modelId: String) {
        prefs.setDefaultTtsModel(modelId)
        _uiState.update { state ->
            // Voices are model-specific: if the current voice is not valid for the new
            // model, switch to that model's first voice (else /audio/speech 400s).
            val validVoices = VeniceApiClient.voicesForTtsModel(state.ttsModels, modelId)
            val voice = if (validVoices.isEmpty() || state.selectedVoice in validVoices) {
                state.selectedVoice
            } else {
                validVoices.first().also { prefs.setDefaultVoice(it) }
            }
            state.copy(selectedTtsModel = modelId, selectedVoice = voice)
        }
    }

    fun setSelectedVoice(voiceId: String) {
        prefs.setDefaultVoice(voiceId)
        _uiState.update { it.copy(selectedVoice = voiceId) }
    }

    fun setSelectedAsrModel(modelId: String) {
        prefs.setDefaultAsrModel(modelId)
        _uiState.update { it.copy(selectedAsrModel = modelId) }
    }

    fun startNewChat() {
        stopStreaming()
        currentConvIdFlow.value = null
        _uiState.update {
            it.copy(
                activeSection = AppSection.CHAT,
                currentConversationId = null,
                selectedTextModel = prefs.getDefaultTextModel(),
                temperature = prefs.getDefaultTemperature(),
                reasoningEffort = prefs.getDefaultReasoningEffort(),
                webSearchEnabled = prefs.getDefaultWebSearch(),
                streamingContent = "",
                streamingReasoning = ""
            )
        }
    }

    fun selectConversation(conversation: ConversationEntity) {
        stopStreaming()
        currentConvIdFlow.value = conversation.id
        _uiState.update {
            it.copy(
                activeSection = AppSection.CHAT,
                currentConversationId = conversation.id,
                selectedTextModel = conversation.modelId,
                temperature = conversation.temperature,
                reasoningEffort = conversation.reasoningEffort,
                webSearchEnabled = conversation.webSearchEnabled,
                streamingContent = "",
                streamingReasoning = ""
            )
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (currentConvIdFlow.value == conversationId) {
                startNewChat()
            }
            repository.deleteConversation(conversationId)
        }
    }

    fun setSelectedTextModel(modelId: String) {
        _uiState.update { it.copy(selectedTextModel = modelId) }
        syncCurrentConversationSettings()
    }

    fun setTemperature(temp: Float) {
        val rounded = (temp * 10f).toInt() / 10f
        _uiState.update { it.copy(temperature = rounded) }
        syncCurrentConversationSettings()
    }

    fun setReasoningEffort(effort: String) {
        _uiState.update { it.copy(reasoningEffort = effort) }
        syncCurrentConversationSettings()
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(webSearchEnabled = enabled) }
        syncCurrentConversationSettings()
    }

    private fun syncCurrentConversationSettings() {
        val convId = currentConvIdFlow.value ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val existing = repository.getConversation(convId) ?: return@launch
            repository.updateConversation(
                existing.copy(
                    modelId = state.selectedTextModel,
                    temperature = state.temperature,
                    reasoningEffort = state.reasoningEffort,
                    webSearchEnabled = state.webSearchEnabled
                )
            )
        }
    }

    fun sendMessage(userText: String = _uiState.value.inputDraft) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isStreaming) return

        _uiState.update { it.copy(inputDraft = "", statusBanner = null) }

        viewModelScope.launch {
            val state = _uiState.value
            val convId = currentConvIdFlow.value ?: run {
                val created = repository.createConversation(
                    title = trimmed.take(44).lines().firstOrNull() ?: "New Chat",
                    modelId = state.selectedTextModel,
                    temperature = state.temperature,
                    reasoningEffort = state.reasoningEffort,
                    webSearchEnabled = state.webSearchEnabled
                )
                currentConvIdFlow.value = created.id
                _uiState.update { it.copy(currentConversationId = created.id) }
                created.id
            }

            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "user",
                content = trimmed,
                modelId = state.selectedTextModel
            )
            repository.insertMessage(userMsg)

            repository.getConversation(convId)?.let { conv ->
                val updatedTitle = if (conv.title == "New Chat") {
                    trimmed.take(44).lines().firstOrNull() ?: conv.title
                } else conv.title
                repository.updateConversation(conv.copy(title = updatedTitle))
            }

            triggerAssistantStream(convId)
        }
    }

    fun regenerateLastResponse() {
        val convId = currentConvIdFlow.value ?: return
        if (_uiState.value.isStreaming) return

        viewModelScope.launch {
            val messages = repository.getMessages(convId)
            if (messages.isEmpty()) return@launch
            val last = messages.last()
            if (last.role == "assistant") {
                repository.deleteMessage(last.id)
            }
            triggerAssistantStream(convId)
        }
    }

    private fun triggerAssistantStream(conversationId: String) {
        stopStreaming()
        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    streamingContent = "",
                    streamingReasoning = ""
                )
            }

            val (_, errorMessage) = runAssistantStream(conversationId)

            _uiState.update {
                it.copy(
                    isStreaming = false,
                    streamingContent = "",
                    streamingReasoning = "",
                    statusBanner = errorMessage
                )
            }
        }
    }

    /**
     * Streams one assistant reply for [conversationId], saves it (with token/cost stats),
     * and returns the reply text plus any stream error. Shared by text chat and voice mode.
     * [onSentence] is invoked for each complete sentence as it streams in, so the voice
     * loop can start speaking the reply before the full response has arrived.
     */
    private suspend fun runAssistantStream(
        conversationId: String,
        onSentence: ((String) -> Unit)? = null
    ): Pair<String, String?> {
        val state = _uiState.value
        val history = repository.getMessages(conversationId)

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var errorMessage: String? = null
        var usageStats: StreamEvent.Usage? = null
        // Sentence splitter: accumulate streamed text; emit each complete sentence.
        val sentenceBuffer = StringBuilder()
        fun flushSentence(force: Boolean = false) {
            val text = sentenceBuffer.toString()
            if (text.isBlank()) return
            val endIdx = findSentenceEnd(text)
            if (endIdx > 0) {
                val sentence = text.substring(0, endIdx).trim()
                sentenceBuffer.delete(0, endIdx)
                if (sentence.isNotBlank()) onSentence?.invoke(sentence)
            } else if (force) {
                val sentence = text.trim()
                sentenceBuffer.clear()
                if (sentence.isNotBlank()) onSentence?.invoke(sentence)
            }
        }

        repository.streamChat(
            model = state.selectedTextModel,
            history = history,
            temperature = state.temperature,
            reasoningEffort = state.reasoningEffort,
            enableWebSearch = state.webSearchEnabled
        ).collect { event ->
            when (event) {
                is StreamEvent.Delta -> {
                    if (event.content.isNotEmpty()) {
                        contentBuilder.append(event.content)
                        if (onSentence != null) {
                            sentenceBuffer.append(event.content)
                            flushSentence()
                        }
                    }
                    if (event.reasoning.isNotEmpty()) {
                        reasoningBuilder.append(event.reasoning)
                    }
                    _uiState.update {
                        it.copy(
                            streamingContent = contentBuilder.toString(),
                            streamingReasoning = reasoningBuilder.toString()
                        )
                    }
                }
                is StreamEvent.Error -> {
                    errorMessage = event.message
                }
                is StreamEvent.Usage -> {
                    usageStats = event
                }
                is StreamEvent.Done -> {
                    // Stream completed
                }
            }
        }
        // Emit whatever is left of the reply as a final sentence.
        flushSentence(force = true)

        val finalContent = contentBuilder.toString().trim()
        val finalReasoning = reasoningBuilder.toString().trim()

        if (finalContent.isNotEmpty() || errorMessage != null) {
            val stats = usageStats
            val assistantMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "assistant",
                content = if (finalContent.isNotEmpty()) finalContent else (errorMessage ?: ""),
                reasoningContent = finalReasoning,
                modelId = state.selectedTextModel,
                isError = finalContent.isEmpty() && errorMessage != null,
                promptTokens = stats?.promptTokens ?: 0,
                completionTokens = stats?.completionTokens ?: 0,
                costUsd = stats?.costUsd ?: 0.0,
                tokensPerSecond = stats?.tokensPerSecond ?: 0.0
            )
            repository.insertMessage(assistantMsg)
        }

        return finalContent to errorMessage
    }

    // ---- Voice-to-voice conversation mode (real-time) ----
    // Loop: live capture (mic, chunked) -> transcribe chunks as they arrive (the
    // transcript appears live) -> streaming chat (sentence by sentence) -> streaming
    // TTS (PCM playback starts as the first sentence's audio arrives) -> listening.
    // Barge-in: if the user speaks while the reply is playing, playback stops and
    // their speech becomes the next turn.

    private var voiceJob: Job? = null

    @Volatile
    private var finishTurnRequested = false

    /** Set by the barge-in monitor when the user talks over the reply. */
    @Volatile
    private var bargeInRequested = false

    /** The PCM player for the reply currently being spoken (for instant stop). */
    @Volatile
    private var activePcmPlayer: VoiceController.StreamingPcmPlayer? = null

    // Live-transcription bookkeeping for the current turn (chunk index -> text).
    private val liveChunkTranscripts = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val liveChunkNextIndex = java.util.concurrent.atomic.AtomicInteger(0)
    private val liveChunkPending = java.util.concurrent.atomic.AtomicInteger(0)
    private val liveChunkChannel = Channel<File>(Channel.UNLIMITED)

    fun startVoiceConversation() {
        if (_uiState.value.isVoiceMode) return
        stopStreaming()
        voiceController.stopPlayback()
        voiceJob?.cancel()
        _uiState.update {
            it.copy(
                isVoiceMode = true,
                voicePhase = VoicePhase.LISTENING,
                voiceHeardText = "",
                voiceReplyText = "",
                statusBanner = null
            )
        }
        voiceJob = viewModelScope.launch {
            try {
                runVoiceLoop()
            } finally {
                activePcmPlayer?.stopNow()
                activePcmPlayer = null
                voiceController.stopLiveCapture()
                voiceController.stopPlayback()
                _uiState.update { it.copy(isVoiceMode = false, voicePhase = VoicePhase.IDLE) }
            }
        }
    }

    fun stopVoiceConversation() {
        voiceJob?.cancel()
        voiceJob = null
        finishTurnRequested = false
        bargeInRequested = false
        activePcmPlayer?.stopNow()
        activePcmPlayer = null
        voiceController.stopLiveCapture()
        voiceController.stopPlayback()
        _uiState.update { it.copy(isVoiceMode = false, voicePhase = VoicePhase.IDLE) }
    }

    /** End the current listening turn early and send what was said so far. */
    fun finishVoiceTurn() {
        finishTurnRequested = true
    }

    /** Index just past the first complete sentence in [text], or -1 if none is complete. */
    private fun findSentenceEnd(text: String): Int {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                val prevIsDigit = i > 0 && text[i - 1].isDigit()
                val nextIsBoundary = i + 1 >= text.length || text[i + 1].isWhitespace()
                if (!prevIsDigit && nextIsBoundary) return i + 1
            } else if (c == '\n') {
                return i + 1
            }
            i++
        }
        return -1
    }

    private suspend fun runVoiceLoop() = coroutineScope {
        // Continuous live capture for the whole session; chunks flow through the channel.
        val captureStarted = voiceController.startLiveCapture(
            chunkDurationMs = VoiceController.LIVE_CHUNK_MS,
            onChunk = { file -> liveChunkChannel.trySend(file) }
        )
        if (captureStarted.isFailure) {
            showStatus("Could not start the microphone.")
            return@coroutineScope
        }

        // Consumer: transcribes live chunks in order and updates the on-screen transcript.
        val chunkConsumer = launch {
            for (file in liveChunkChannel) {
                if (_uiState.value.voicePhase != VoicePhase.LISTENING) {
                    runCatching { file.delete() }
                    continue
                }
                val index = liveChunkNextIndex.getAndIncrement()
                liveChunkPending.incrementAndGet()
                repository.transcribeAudio(file)
                    .onSuccess { text ->
                        if (text.isNotBlank()) {
                            liveChunkTranscripts[index] = text.trim()
                            updateLiveTranscript()
                        }
                    }
                liveChunkPending.decrementAndGet()
                runCatching { file.delete() }
            }
        }

        try {
            while (currentCoroutineContext().isActive) {
                // --- LISTENING (live) ---
                bargeInRequested = false
                liveChunkTranscripts.clear()
                liveChunkNextIndex.set(0)
                _uiState.update {
                    it.copy(
                        voicePhase = VoicePhase.LISTENING,
                        voiceHeardText = "",
                        voiceReplyText = ""
                    )
                }
                val heard = awaitVoiceTurnEndLive()
                if (heard.isNullOrBlank()) continue
                _uiState.update { it.copy(voiceHeardText = heard) }

                // --- THINKING (streaming chat) + SPEAKING (streaming TTS) ---
                _uiState.update { it.copy(voicePhase = VoicePhase.THINKING) }
                val convId = ensureVoiceConversation(heard)
                val userMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "user",
                    content = heard,
                    modelId = _uiState.value.selectedTextModel
                )
                repository.insertMessage(userMsg)

                // Sentences stream from the chat into this channel; the speaker job
                // synthesizes + plays each one as it arrives.
                val sentenceChannel = Channel<String>(Channel.UNLIMITED)
                val speakJob = launch {
                    var player: VoiceController.StreamingPcmPlayer? = null
                    try {
                        for (sentence in sentenceChannel) {
                            if (bargeInRequested) break
                            repository.synthesizeSpeechStream(sentence) { pcm ->
                                val p = player ?: voiceController.createPcmPlayer().also {
                                    player = it
                                    activePcmPlayer = it
                                    _uiState.update { s -> s.copy(voicePhase = VoicePhase.SPEAKING) }
                                }
                                p.writePcm(pcm)
                            }.onFailure { err ->
                                showStatus(err.message ?: "Speech synthesis failed")
                            }
                            if (bargeInRequested) break
                        }
                    } finally {
                        val p = player
                        player = null
                        activePcmPlayer = null
                        if (bargeInRequested || !isActive) p?.stopNow()
                        else p?.stopAndAwaitDrain()
                    }
                }
                // Barge-in monitor: once the reply is actually playing, sustained loud
                // mic input means the user is talking over it — cut the audio.
                val bargeInJob = launch {
                    var loudSince = 0L
                    while (isActive) {
                        val speaking = activePcmPlayer != null
                        val amp = voiceController.liveAmplitude.value
                        val now = System.currentTimeMillis()
                        if (speaking && amp > BARGE_IN_AMPLITUDE) {
                            if (loudSince == 0L) loudSince = now
                            else if (now - loudSince >= BARGE_IN_HOLD_MS) {
                                bargeInRequested = true
                                speakJob.cancel()
                                break
                            }
                        } else {
                            loudSince = 0L
                        }
                        delay(100)
                    }
                }

                val chatDeferred = async {
                    runAssistantStream(convId) { sentence ->
                        sentenceChannel.trySend(sentence)
                    }
                }
                val (reply, errorMessage) = chatDeferred.await()
                sentenceChannel.close()
                speakJob.join()
                bargeInJob.cancel()

                if (reply.isBlank()) {
                    if (errorMessage != null) showStatus(errorMessage)
                    continue
                }
                _uiState.update { it.copy(voiceReplyText = reply) }

                if (bargeInRequested) {
                    // The interrupting speech is already in the live capture; flush its
                    // partial chunk so it is transcribed as the next turn's first chunk.
                    voiceController.flushVoiceChunk()?.let { file ->
                        liveChunkChannel.trySend(file)
                    }
                }
                // Loop back to listening for the next turn.
            }
        } finally {
            chunkConsumer.cancel()
            liveChunkChannel.close()
        }
    }

    /** Joins the current turn's chunk transcripts in order for the live transcript. */
    private fun updateLiveTranscript() {
        val ordered = liveChunkTranscripts.entries
            .sortedBy { it.key }
            .joinToString(" ") { it.value }
            .trim()
        if (ordered.isNotEmpty()) {
            _uiState.update { it.copy(voiceHeardText = ordered) }
        }
    }

    /**
     * Waits until the user stops talking (silence), taps to send, or the turn times
     * out, using the live mic amplitude. Then flushes the final partial chunk and
     * waits briefly for the in-flight chunk transcriptions, and returns the turn's
     * text (or null when nothing was said).
     */
    private suspend fun awaitVoiceTurnEndLive(): String? {
        val silenceThreshold = 800 // mic amplitude 0..32767; quiet room is near 0
        val silenceNeededMs = 2500L
        val noSpeechTimeoutMs = 8000L
        val maxTurnMs = 30000L
        val startedAt = System.currentTimeMillis()
        var quietSince = 0L
        var heardSpeech = false
        while (true) {
            if (finishTurnRequested) {
                finishTurnRequested = false
                break
            }
            val amp = voiceController.liveAmplitude.value
            val now = System.currentTimeMillis()
            if (amp > silenceThreshold) {
                heardSpeech = true
                quietSince = 0L
            } else if (heardSpeech) {
                if (quietSince == 0L) quietSince = now
                else if (now - quietSince >= silenceNeededMs) break
            } else if (now - startedAt >= noSpeechTimeoutMs) {
                break // nothing said; go back to listening
            }
            if (now - startedAt >= maxTurnMs) break
            delay(200)
        }
        if (!heardSpeech && liveChunkTranscripts.isEmpty()) return null
        // Flush the final partial chunk and wait for its (and any in-flight)
        // transcriptions to land, so the turn text is complete.
        voiceController.flushVoiceChunk()?.let { file ->
            liveChunkChannel.trySend(file)
        }
        val deadline = System.currentTimeMillis() + 4000L
        while (liveChunkPending.get() > 0 && System.currentTimeMillis() < deadline) {
            delay(100)
        }
        val heard = liveChunkTranscripts.entries
            .sortedBy { it.key }
            .joinToString(" ") { it.value }
            .trim()
        return heard.ifBlank { null }
    }

    private suspend fun ensureVoiceConversation(firstText: String): String {
        currentConvIdFlow.value?.let { return it }
        val state = _uiState.value
        val created = repository.createConversation(
            title = firstText.take(44).lines().firstOrNull() ?: "Voice Chat",
            modelId = state.selectedTextModel,
            temperature = state.temperature,
            reasoningEffort = state.reasoningEffort,
            webSearchEnabled = state.webSearchEnabled
        )
        currentConvIdFlow.value = created.id
        _uiState.update { it.copy(currentConversationId = created.id) }
        return created.id
    }


    fun stopStreaming() {
        val job = streamingJob ?: return
        streamingJob = null
        val partialContent = _uiState.value.streamingContent.trim()
        val partialReasoning = _uiState.value.streamingReasoning.trim()
        val convId = currentConvIdFlow.value
        val modelId = _uiState.value.selectedTextModel

        job.cancel()
        _uiState.update {
            it.copy(
                isStreaming = false,
                streamingContent = "",
                streamingReasoning = ""
            )
        }

        if (convId != null && partialContent.isNotEmpty()) {
            viewModelScope.launch {
                repository.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = "$partialContent\n\n_[Stopped]_",
                        reasoningContent = partialReasoning,
                        modelId = modelId
                    )
                )
            }
        }
    }

    // Voice Recording -> POST /audio/transcriptions
    fun toggleMicRecording() {
        if (voiceController.isRecording.value) {
            val recordedFile = voiceController.stopRecording()
            if (recordedFile != null) {
                transcribeAudioFile(recordedFile)
            }
        } else {
            voiceController.startRecording().onFailure { err ->
                showStatus("Microphone error: ${err.message}")
            }
        }
    }

    private fun transcribeAudioFile(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribing = true, statusBanner = null) }
            repository.transcribeAudio(file)
                .onSuccess { text ->
                    if (text.isNotBlank()) {
                        _uiState.update { state ->
                            val sep = if (state.inputDraft.isBlank()) "" else " "
                            state.copy(inputDraft = state.inputDraft + sep + text.trim())
                        }
                    } else {
                        showStatus("No speech detected in audio recording.")
                    }
                }
                .onFailure { err ->
                    showStatus(err.message ?: "Audio transcription failed")
                }
            runCatching { file.delete() }
            _uiState.update { it.copy(isTranscribing = false) }
        }
    }

    // TTS Speech -> POST /audio/speech
    fun toggleSpeakMessage(message: MessageEntity) {
        if (voiceController.playingMessageId.value == message.id) {
            voiceController.stopPlayback()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSynthesizingTts = true, statusBanner = null) }
            repository.synthesizeSpeech(message.content)
                .onSuccess { audioFile ->
                    voiceController.playAudioFile(message.id, audioFile) {
                        runCatching { audioFile.delete() }
                    }
                }
                .onFailure { err ->
                    showStatus(err.message ?: "Speech synthesis failed")
                }
            _uiState.update { it.copy(isSynthesizingTts = false) }
        }
    }

    // Image generation -> POST /images/generations
    fun setSelectedImageModel(modelId: String) {
        _uiState.update { it.copy(selectedImageModel = modelId) }
    }

    fun setSelectedImageSize(size: String) {
        _uiState.update { it.copy(selectedImageSize = size) }
    }

    fun generateImage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _uiState.value.isGeneratingImage) return

        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isGeneratingImage = true, statusBanner = null) }
            repository.generateAndSaveImage(
                model = state.selectedImageModel,
                prompt = trimmed,
                size = state.selectedImageSize
            ).onFailure { err ->
                showStatus(err.message ?: "Image generation failed")
            }
            _uiState.update { it.copy(isGeneratingImage = false) }
        }
    }

    fun saveImageToGallery(context: Context, image: GeneratedImageEntity) {
        viewModelScope.launch {
            GallerySaver.saveImageToGallery(
                context = context,
                imageFile = File(image.localFilePath),
                title = image.prompt
            ).onSuccess { msg ->
                showStatus(msg)
            }.onFailure { err ->
                showStatus("Save failed: ${err.message}")
            }
        }
    }

    fun deleteGeneratedImage(image: GeneratedImageEntity) {
        viewModelScope.launch {
            repository.deleteGeneratedImage(image)
        }
    }

    // Settings
    fun saveApiKey(rawKey: String) {
        prefs.setApiKey(rawKey)
        showStatus("Venice API key saved securely in EncryptedSharedPreferences.")
        refreshCatalogs()
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            stopStreaming()
            repository.clearAllHistory()
            startNewChat()
            showStatus("All local conversations and generated images cleared.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }

    companion object {
        fun provideFactory(app: VeniceApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VeniceViewModel(
                        repository = app.repository,
                        prefs = app.securePreferences,
                        voiceController = app.voiceController
                    ) as T
                }
            }

        /** Mic amplitude that counts as the user talking over the reply (barge-in). */
        const val BARGE_IN_AMPLITUDE = 3000
        /** Sustained loudness needed to trigger a barge-in. */
        const val BARGE_IN_HOLD_MS = 600L
    }
}
