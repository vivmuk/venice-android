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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

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

    fun refreshCatalogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }
            repository.fetchTextModels().onSuccess { models ->
                _uiState.update { it.copy(textModels = models) }
            }
            repository.fetchImageModels().onSuccess { imgModels ->
                _uiState.update { state ->
                    val chosen = if (imgModels.any { it.id == state.selectedImageModel }) {
                        state.selectedImageModel
                    } else {
                        imgModels.firstOrNull()?.id ?: state.selectedImageModel
                    }
                    state.copy(imageModels = imgModels, selectedImageModel = chosen)
                }
            }
            repository.fetchTtsModels().onSuccess { ttsModels ->
                _uiState.update { state ->
                    val chosen = if (ttsModels.any { it.id == state.selectedTtsModel }) {
                        state.selectedTtsModel
                    } else {
                        ttsModels.firstOrNull()?.id ?: state.selectedTtsModel
                    }
                    state.copy(ttsModels = ttsModels, selectedTtsModel = chosen)
                }
            }
            repository.fetchAsrModels().onSuccess { asrModels ->
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
        _uiState.update { it.copy(selectedTtsModel = modelId) }
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
     */
    private suspend fun runAssistantStream(conversationId: String): Pair<String, String?> {
        val state = _uiState.value
        val history = repository.getMessages(conversationId)

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var errorMessage: String? = null
        var usageStats: StreamEvent.Usage? = null

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

    // ---- Voice-to-voice conversation mode ----
    // Loop: listen (mic) -> transcribe (ASR) -> chat (text model) -> speak reply (TTS)
    // -> back to listening, until the user ends it.

    private var voiceJob: Job? = null

    @Volatile
    private var finishTurnRequested = false

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
                voiceController.stopRecording()
                voiceController.stopPlayback()
                _uiState.update { it.copy(isVoiceMode = false, voicePhase = VoicePhase.IDLE) }
            }
        }
    }

    fun stopVoiceConversation() {
        voiceJob?.cancel()
        voiceJob = null
        finishTurnRequested = false
        voiceController.stopPlayback()
        voiceController.stopRecording()
        _uiState.update { it.copy(isVoiceMode = false, voicePhase = VoicePhase.IDLE) }
    }

    /** End the current listening turn early and send what was said so far. */
    fun finishVoiceTurn() {
        finishTurnRequested = true
    }

    private suspend fun runVoiceLoop() {
        while (currentCoroutineContext().isActive) {
            // --- LISTENING ---
            _uiState.update { it.copy(voicePhase = VoicePhase.LISTENING) }
            val started = voiceController.startRecording()
            if (started.isFailure) {
                showStatus("Could not start the microphone.")
                return
            }
            val recordedFile = awaitVoiceTurnEnd()
            if (recordedFile == null) continue

            // --- TRANSCRIBING ---
            _uiState.update { it.copy(voicePhase = VoicePhase.TRANSCRIBING) }
            val heard = repository.transcribeAudio(recordedFile)
                .getOrElse { "" }
                .trim()
            runCatching { recordedFile.delete() }
            if (heard.isBlank()) continue
            _uiState.update { it.copy(voiceHeardText = heard) }

            // --- THINKING (chat) ---
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
            val (reply, errorMessage) = runAssistantStream(convId)
            if (reply.isBlank()) {
                if (errorMessage != null) showStatus(errorMessage)
                continue
            }
            _uiState.update { it.copy(voiceReplyText = reply) }

            // --- SPEAKING (TTS) ---
            _uiState.update { it.copy(voicePhase = VoicePhase.SPEAKING) }
            val audio = repository.synthesizeSpeech(reply)
            audio.onSuccess { audioFile ->
                try {
                    playAudioAndAwait(audioFile, "voice-${System.currentTimeMillis()}")
                } finally {
                    runCatching { audioFile.delete() }
                }
            }.onFailure { err ->
                showStatus(err.message ?: "Speech synthesis failed")
            }
            // Loop back to listening for the next turn.
        }
    }

    /** Waits until the user stops talking (silence), taps to send, or the turn times out. */
    private suspend fun awaitVoiceTurnEnd(): File? {
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
            val amp = voiceController.getMaxAmplitude()
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
    }
}
