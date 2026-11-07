package ai.venice.app.data

import ai.venice.app.data.local.ConversationEntity
import ai.venice.app.data.local.GeneratedImageEntity
import ai.venice.app.data.local.MessageEntity
import ai.venice.app.data.local.VeniceDao
import ai.venice.app.data.remote.ChatMessageDto
import ai.venice.app.data.remote.StreamEvent
import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.data.remote.VeniceModel
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class VeniceRepository(
    private val dao: VeniceDao,
    val apiClient: VeniceApiClient,
    val prefs: SecurePreferences
) {
    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()
    val generatedImages: Flow<List<GeneratedImageEntity>> = dao.observeGeneratedImages()

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> {
        return dao.observeMessages(conversationId)
    }

    suspend fun getMessages(conversationId: String): List<MessageEntity> {
        return dao.getMessages(conversationId)
    }

    suspend fun getConversation(id: String): ConversationEntity? {
        return dao.getConversation(id)
    }

    suspend fun createConversation(
        title: String = "New Chat",
        modelId: String = prefs.getDefaultTextModel(),
        temperature: Float = prefs.getDefaultTemperature(),
        reasoningEffort: String = prefs.getDefaultReasoningEffort(),
        webSearchEnabled: Boolean = prefs.getDefaultWebSearch()
    ): ConversationEntity {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            modelId = modelId,
            temperature = temperature,
            reasoningEffort = reasoningEffort,
            webSearchEnabled = webSearchEnabled,
            createdAt = now,
            updatedAt = now
        )
        dao.insertConversation(entity)
        return entity
    }

    suspend fun updateConversation(conversation: ConversationEntity) {
        dao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: String) {
        dao.deleteConversation(id)
    }

    suspend fun clearAllHistory() {
        dao.deleteAllConversations()
        dao.deleteAllGeneratedImages()
    }

    suspend fun insertMessage(message: MessageEntity) {
        dao.insertMessage(message)
    }

    suspend fun updateMessage(message: MessageEntity) {
        dao.updateMessage(message)
    }

    suspend fun deleteMessage(id: String) {
        dao.deleteMessage(id)
    }

    fun streamChat(
        model: String,
        history: List<MessageEntity>,
        temperature: Float,
        reasoningEffort: String,
        enableWebSearch: Boolean
    ): Flow<StreamEvent> {
        val systemPrompt = prefs.getSystemPrompt().trim()
        val dtoList = buildList {
            if (systemPrompt.isNotEmpty()) {
                add(ChatMessageDto(role = "system", content = systemPrompt))
            }
            history.forEach { msg ->
                if (!msg.isError && msg.content.isNotBlank()) {
                    add(ChatMessageDto(role = msg.role, content = msg.content))
                }
            }
        }
        return apiClient.streamChatCompletion(
            model = model,
            messages = dtoList,
            temperature = temperature,
            reasoningEffort = reasoningEffort,
            enableWebSearch = enableWebSearch
        )
    }

    suspend fun fetchTextModels(): Result<List<VeniceModel>> {
        return apiClient.fetchTextModels()
    }

    suspend fun fetchImageModels(): Result<List<VeniceModel>> {
        return apiClient.fetchImageModels()
    }

    suspend fun generateAndSaveImage(
        model: String,
        prompt: String,
        size: String
    ): Result<GeneratedImageEntity> {
        return apiClient.generateImage(model = model, prompt = prompt, size = size)
            .map { file ->
                val entity = GeneratedImageEntity(
                    id = UUID.randomUUID().toString(),
                    prompt = prompt,
                    modelId = model,
                    localFilePath = file.absolutePath,
                    size = size,
                    createdAt = System.currentTimeMillis()
                )
                dao.insertGeneratedImage(entity)
                entity
            }
    }

    suspend fun deleteGeneratedImage(image: GeneratedImageEntity) {
        runCatching { File(image.localFilePath).delete() }
        dao.deleteGeneratedImage(image.id)
    }

    suspend fun transcribeAudio(file: File): Result<String> {
        return apiClient.transcribeAudio(file)
    }

    suspend fun synthesizeSpeech(text: String): Result<File> {
        return apiClient.synthesizeSpeech(text = text, voice = prefs.getDefaultVoice())
    }
}
