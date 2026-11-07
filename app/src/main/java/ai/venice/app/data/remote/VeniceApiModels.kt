package ai.venice.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ModelsResponse(
    val `data`: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String,
    val type: String? = null,
    @SerialName("model_spec")
    val modelSpec: ModelSpecDto? = null
)

@Serializable
data class ModelSpecDto(
    val name: String? = null,
    val description: String? = null,
    val capabilities: ModelCapabilitiesDto? = null,
    val traits: List<String>? = null
)

@Serializable
data class ModelCapabilitiesDto(
    val supportsReasoning: Boolean? = null,
    val supportsWebSearch: Boolean? = null,
    val supportsVision: Boolean? = null
)

data class VeniceModel(
    val id: String,
    val displayName: String,
    val description: String = "",
    val isPinned: Boolean = false,
    val type: String = "text",
    val supportsReasoning: Boolean = false,
    val supportsWebSearch: Boolean = true
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null,
    @SerialName("venice_parameters")
    val veniceParameters: VeniceParametersDto? = null
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class VeniceParametersDto(
    @SerialName("enable_web_search")
    val enableWebSearch: String = "off", // "on", "off", "auto"
    @SerialName("include_venice_system_prompt")
    val includeVeniceSystemPrompt: Boolean = true
)

@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChunkChoiceDto> = emptyList()
)

@Serializable
data class ChunkChoiceDto(
    val index: Int = 0,
    val delta: ChunkDeltaDto? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ChunkDeltaDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

sealed class StreamEvent {
    data class Delta(val content: String = "", val reasoning: String = "") : StreamEvent()
    data class Done(val finishReason: String? = null) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

@Serializable
data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    @SerialName("response_format")
    val responseFormat: String = "b64_json"
)

@Serializable
data class ImageGenerationResponse(
    val created: Long? = null,
    val `data`: List<ImageDataDto> = emptyList(),
    val images: List<String> = emptyList()
)

@Serializable
data class ImageDataDto(
    val url: String? = null,
    @SerialName("b64_json")
    val b64Json: String? = null,
    @SerialName("revised_prompt")
    val revisedPrompt: String? = null
)

@Serializable
data class AudioTranscriptionResponse(
    val text: String = ""
)

@Serializable
data class AudioSpeechRequest(
    val model: String = "tts-kokoro",
    val input: String,
    val voice: String = "af_sky",
    @SerialName("response_format")
    val responseFormat: String = "mp3",
    val speed: Float = 1.0f
)

@Serializable
data class ApiErrorEnvelope(
    val error: JsonObject? = null,
    val message: String? = null
)
