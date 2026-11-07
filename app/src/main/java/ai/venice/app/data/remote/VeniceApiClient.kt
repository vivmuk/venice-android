package ai.venice.app.data.remote

import ai.venice.app.data.SecurePreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

class VeniceApiClient(
    private val securePreferences: SecurePreferences,
    private val cacheDir: File
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun requireApiKey(): String {
        val key = securePreferences.getApiKey()
        if (key.isBlank()) {
            throw IllegalStateException("Missing Venice API key. Please add your API key in Settings.")
        }
        return key
    }

    suspend fun fetchTextModels(): Result<List<VeniceModel>> = withContext(Dispatchers.IO) {
        runCatching {
            // The Venice /models catalog is public: it answers 200 without an API key,
            // so the full catalog loads even before the user enters a key. The key is
            // attached when present (some catalog entries are account-gated).
            val key = securePreferences.getApiKey()
            val fetchedModels = run {
                val builder = Request.Builder()
                    .url("$BASE_URL/models?type=text")
                    .header("Accept", "application/json")
                    .get()
                if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
                val request = builder.build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()?.take(300) ?: "HTTP ${response.code}"
                        throw IOException("Failed to load text models (${response.code}): $errBody")
                    }
                    val bodyStr = response.body?.string() ?: "{}"
                    val parsed = json.decodeFromString<ModelsResponse>(bodyStr)
                    parsed.data.map { dto ->
                        VeniceModel(
                            id = dto.id,
                            displayName = dto.modelSpec?.name?.takeIf { it.isNotBlank() } ?: formatModelName(dto.id),
                            description = dto.modelSpec?.description ?: "",
                            isPinned = dto.id in SecurePreferences.PINNED_TEXT_MODELS,
                            type = "text",
                            supportsReasoning = dto.modelSpec?.capabilities?.supportsReasoning == true ||
                                dto.id.contains("deepseek", ignoreCase = true) ||
                                dto.id.contains("r1", ignoreCase = true) ||
                                dto.id.contains("qwq", ignoreCase = true),
                            supportsWebSearch = dto.modelSpec?.capabilities?.supportsWebSearch ?: true
                        )
                    }
                }
            }

            mergeWithPinnedTextModels(fetchedModels)
        }
    }

    fun getDefaultTextModelCatalog(): List<VeniceModel> {
        return mergeWithPinnedTextModels(emptyList())
    }

    private fun mergeWithPinnedTextModels(apiModels: List<VeniceModel>): List<VeniceModel> {
        val byId = apiModels.associateBy { it.id }
        val pinnedList = SecurePreferences.PINNED_TEXT_MODELS.map { pinnedId ->
            val existing = byId[pinnedId]
            existing?.copy(isPinned = true) ?: VeniceModel(
                id = pinnedId,
                displayName = formatModelName(pinnedId),
                description = getPinnedModelDescription(pinnedId),
                isPinned = true,
                type = "text",
                supportsReasoning = true,
                supportsWebSearch = true
            )
        }

        val pinnedSet = SecurePreferences.PINNED_TEXT_MODELS.toSet()
        val remaining = apiModels
            .filter { it.id !in pinnedSet }
            .sortedBy { it.displayName.lowercase() }

        return pinnedList + remaining
    }

    suspend fun fetchImageModels(): Result<List<VeniceModel>> = withContext(Dispatchers.IO) {
        runCatching {
            // Public catalog: loads without an API key (see fetchTextModels).
            val key = securePreferences.getApiKey()
            val fetched = run {
                val builder = Request.Builder()
                    .url("$BASE_URL/models?type=image")
                    .header("Accept", "application/json")
                    .get()
                if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
                val request = builder.build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()?.take(300) ?: "HTTP ${response.code}"
                        throw IOException("Failed to load image models (${response.code}): $errBody")
                    }
                    val bodyStr = response.body?.string() ?: "{}"
                    val parsed = json.decodeFromString<ModelsResponse>(bodyStr)
                    parsed.data.map { dto ->
                        VeniceModel(
                            id = dto.id,
                            displayName = dto.modelSpec?.name?.takeIf { it.isNotBlank() } ?: formatModelName(dto.id),
                            description = dto.modelSpec?.description ?: "Venice image generation model",
                            isPinned = false,
                            type = "image"
                        )
                    }
                }
            }

            if (fetched.isEmpty()) {
                FALLBACK_IMAGE_MODELS
            } else {
                fetched.sortedBy { it.displayName.lowercase() }
            }
        }
    }

    /** All Venice text-to-speech models (GET /models?type=tts). Public catalog. */
    suspend fun fetchTtsModels(): Result<List<VeniceModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = securePreferences.getApiKey()
            val builder = Request.Builder()
                .url("$BASE_URL/models?type=tts")
                .header("Accept", "application/json")
                .get()
            if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")

            okHttpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: "HTTP ${response.code}"
                    throw IOException("Failed to load TTS models (${response.code}): $errBody")
                }
                val parsed = json.decodeFromString<ModelsResponse>(response.body?.string() ?: "{}")
                val fetched = parsed.data.map { dto ->
                    VeniceModel(
                        id = dto.id,
                        displayName = dto.modelSpec?.name?.takeIf { it.isNotBlank() } ?: formatModelName(dto.id),
                        description = dto.modelSpec?.description ?: "Venice text-to-speech model",
                        isPinned = false,
                        type = "tts"
                    )
                }.sortedBy { it.displayName.lowercase() }
                if (fetched.isEmpty()) FALLBACK_TTS_MODELS else fetched
            }
        }
    }

    /** All Venice speech-recognition models (GET /models?type=asr). Public catalog. */
    suspend fun fetchAsrModels(): Result<List<VeniceModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = securePreferences.getApiKey()
            val builder = Request.Builder()
                .url("$BASE_URL/models?type=asr")
                .header("Accept", "application/json")
                .get()
            if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")

            okHttpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: "HTTP ${response.code}"
                    throw IOException("Failed to load speech-recognition models (${response.code}): $errBody")
                }
                val parsed = json.decodeFromString<ModelsResponse>(response.body?.string() ?: "{}")
                val fetched = parsed.data.map { dto ->
                    VeniceModel(
                        id = dto.id,
                        displayName = dto.modelSpec?.name?.takeIf { it.isNotBlank() } ?: formatModelName(dto.id),
                        description = dto.modelSpec?.description ?: "Venice speech-recognition model",
                        isPinned = false,
                        type = "asr"
                    )
                }.sortedBy { it.displayName.lowercase() }
                if (fetched.isEmpty()) FALLBACK_ASR_MODELS else fetched
            }
        }
    }

    fun streamChatCompletion(
        model: String,
        messages: List<ChatMessageDto>,
        temperature: Float,
        reasoningEffort: String,
        enableWebSearch: Boolean
    ): Flow<StreamEvent> = callbackFlow {
        val apiKey = try {
            requireApiKey()
        } catch (e: Exception) {
            trySend(StreamEvent.Error(e.message ?: "Missing API Key"))
            close()
            return@callbackFlow
        }

        val effortParam = reasoningEffort.takeIf { it in listOf("low", "medium", "high") }
        val payload = ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = temperature,
            reasoningEffort = effortParam,
            veniceParameters = VeniceParametersDto(
                enableWebSearch = if (enableWebSearch) "on" else "off",
                includeVeniceSystemPrompt = true
            )
        )

        val requestBody = json.encodeToString(payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    trySend(StreamEvent.Error(e.message ?: "Network error while streaming response"))
                }
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val rawError = resp.body?.string()?.take(500) ?: "HTTP ${resp.code}"
                        trySend(StreamEvent.Error("Venice API error (${resp.code}): $rawError"))
                        close()
                        return
                    }

                    val body = resp.body
                    if (body == null) {
                        trySend(StreamEvent.Error("Empty response stream from Venice API"))
                        close()
                        return
                    }

                    try {
                        val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
                        var line: String? = null
                        // Token-speed timing: first content token -> last token (usage chunk).
                        val streamStartNanos = System.nanoTime()
                        var firstTokenNanos = 0L
                        while (!call.isCanceled() && reader.readLine().also { line = it } != null) {
                            val trimmed = line?.trim() ?: continue
                            if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue

                            val dataStr = trimmed.removePrefix("data:").trim()
                            if (dataStr == "[DONE]") {
                                trySend(StreamEvent.Done("stop"))
                                break
                            }

                            try {
                                val chunk = json.decodeFromString<ChatCompletionChunk>(dataStr)
                                val choice = chunk.choices.firstOrNull()
                                val delta = choice?.delta
                                if (delta != null) {
                                    val c = delta.content.orEmpty()
                                    val r = delta.reasoningContent.orEmpty()
                                    if (c.isNotEmpty() || r.isNotEmpty()) {
                                        if (firstTokenNanos == 0L) firstTokenNanos = System.nanoTime()
                                        trySend(StreamEvent.Delta(content = c, reasoning = r))
                                    }
                                }
                                if (choice?.finishReason != null) {
                                    trySend(StreamEvent.Done(choice.finishReason))
                                }
                                // Final chunk carries usage + cost (stream_options.include_usage).
                                val usage = chunk.usage
                                if (usage != null && usage.totalTokens > 0) {
                                    val endNanos = System.nanoTime()
                                    val startNanos = if (firstTokenNanos != 0L) firstTokenNanos else streamStartNanos
                                    val seconds = ((endNanos - startNanos) / 1_000_000_000.0)
                                        .coerceAtLeast(0.01)
                                    val tps = if (usage.completionTokens > 0) {
                                        usage.completionTokens / seconds
                                    } else 0.0
                                    trySend(
                                        StreamEvent.Usage(
                                            promptTokens = usage.promptTokens,
                                            completionTokens = usage.completionTokens,
                                            totalTokens = usage.totalTokens,
                                            cachedTokens = usage.promptTokensDetails?.cachedTokens ?: 0,
                                            reasoningTokens = usage.completionTokensDetails?.reasoningTokens ?: 0,
                                            costUsd = chunk.cost?.usd ?: 0.0,
                                            costDiem = chunk.cost?.diem ?: 0.0,
                                            tokensPerSecond = tps
                                        )
                                    )
                                }
                            } catch (_: Exception) {
                                // Ignore non-standard keepalive or metadata SSE lines
                            }
                        }
                        trySend(StreamEvent.Done())
                    } catch (e: Exception) {
                        if (!call.isCanceled()) {
                            trySend(StreamEvent.Error(e.message ?: "Stream interrupted"))
                        } else {
                            // Stream was cancelled; nothing to report.
                        }
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateImage(
        model: String,
        prompt: String,
        size: String = "1024x1024"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireApiKey()
            val reqObj = ImageGenerationRequest(
                model = model,
                prompt = prompt,
                n = 1,
                size = size,
                responseFormat = "b64_json"
            )
            val body = json.encodeToString(reqObj)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/images/generations")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(400) ?: "HTTP ${response.code}"
                    throw IOException("Image generation failed (${response.code}): $err")
                }

                val respStr = response.body?.string()
                    ?: throw IOException("Empty response from /images/generations")

                val parsed = json.decodeFromString<ImageGenerationResponse>(respStr)
                val firstData = parsed.data.firstOrNull()
                val base64Data = firstData?.b64Json ?: parsed.images.firstOrNull()
                val imageUrl = firstData?.url

                val imagesDir = File(cacheDir, "generated_images").apply { mkdirs() }
                val outFile = File(imagesDir, "venice_img_${UUID.randomUUID()}.png")

                when {
                    !base64Data.isNullOrBlank() -> {
                        val cleanB64 = base64Data.substringAfter("base64,", base64Data)
                        val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
                        outFile.writeBytes(bytes)
                    }
                    !imageUrl.isNullOrBlank() -> {
                        val imgReq = Request.Builder().url(imageUrl).get().build()
                        okHttpClient.newCall(imgReq).execute().use { imgResp ->
                            if (!imgResp.isSuccessful) {
                                throw IOException("Failed to download generated image URL (${imgResp.code})")
                            }
                            val bytes = imgResp.body?.bytes()
                                ?: throw IOException("Empty image bytes from URL")
                            outFile.writeBytes(bytes)
                        }
                    }
                    else -> throw IOException("No image data returned by Venice API")
                }

                outFile
            }
        }
    }

    suspend fun transcribeAudio(
        audioFile: File,
        model: String = "openai/whisper-large-v3"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireApiKey()
            val mediaType = "audio/mp4".toMediaType()
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody(mediaType)
                )
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(multipartBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IOException("Transcription failed (${response.code}): ${bodyStr.take(300)}")
                }
                val parsed = try {
                    json.decodeFromString<AudioTranscriptionResponse>(bodyStr).text
                } catch (_: Exception) {
                    bodyStr.trim()
                }
                parsed
            }
        }
    }

    suspend fun synthesizeSpeech(
        text: String,
        voice: String = securePreferences.getDefaultVoice(),
        model: String = "tts-kokoro"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireApiKey()
            val cleanedText = text
                .replace(Regex("```[\\s\\S]*?```"), " [code block] ")
                .replace(Regex("[*_#>`~]"), "")
                .trim()
                .take(4000)

            val payload = AudioSpeechRequest(
                model = model,
                input = cleanedText.ifEmpty { "Empty message" },
                voice = voice,
                responseFormat = "mp3",
                speed = 1.0f
            )

            val body = json.encodeToString(payload)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/audio/speech")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(300) ?: "HTTP ${response.code}"
                    throw IOException("Speech synthesis failed (${response.code}): $err")
                }
                val bytes = response.body?.bytes()
                    ?: throw IOException("Empty audio stream from /audio/speech")

                val audioDir = File(cacheDir, "tts_audio").apply { mkdirs() }
                val outFile = File(audioDir, "venice_tts_${System.currentTimeMillis()}.mp3")
                outFile.writeBytes(bytes)
                outFile
            }
        }
    }

    companion object {
        const val BASE_URL = "https://api.venice.ai/api/v1"

        val FALLBACK_IMAGE_MODELS = listOf(
            VeniceModel("fluently-xl", "Fluently XL", "Fast, photorealistic image generation", type = "image"),
            VeniceModel("flux-dev", "FLUX.1 Dev", "High-fidelity state-of-the-art image synthesis", type = "image"),
            VeniceModel("flux-dev-uncensored", "FLUX.1 Dev Uncensored", "Uncensored high-detail FLUX model", type = "image"),
            VeniceModel("pony-realism", "Pony Realism", "Stylized and realistic character generation", type = "image"),
            VeniceModel("lustify-sdxl", "Lustify SDXL", "Uncensored SDXL artistic generation", type = "image")
        )

        /** Used only if GET /models?type=tts is unreachable; the live catalog replaces it. */
        val FALLBACK_TTS_MODELS = listOf(
            VeniceModel("tts-kokoro", "Kokoro TTS", "Fast, natural text-to-speech", type = "tts"),
            VeniceModel("tts-qwen3-0-6b", "Qwen3 0.6B TTS", "Compact Qwen text-to-speech", type = "tts"),
            VeniceModel("tts-qwen3-1-7b", "Qwen3 1.7B TTS", "Qwen text-to-speech", type = "tts"),
            VeniceModel("tts-elevenlabs-turbo-v2-5", "ElevenLabs Turbo v2.5", "ElevenLabs high-quality TTS", type = "tts"),
            VeniceModel("tts-minimax-speech-02-hd", "MiniMax Speech 02 HD", "MiniMax HD text-to-speech", type = "tts")
        )

        /** Used only if GET /models?type=asr is unreachable; the live catalog replaces it. */
        val FALLBACK_ASR_MODELS = listOf(
            VeniceModel("openai/whisper-large-v3", "Whisper Large v3", "OpenAI speech recognition", type = "asr"),
            VeniceModel("nvidia/parakeet-tdt-0.6b-v3", "Parakeet TDT 0.6B v3", "NVIDIA fast speech recognition", type = "asr"),
            VeniceModel("elevenlabs/scribe-v2", "ElevenLabs Scribe v2", "ElevenLabs speech recognition", type = "asr")
        )

        fun formatModelName(id: String): String {
            return when (id) {
                "deepseek-v4-1-flash" -> "DeepSeek V4.1 Flash"
                "deepseek-v4-pro" -> "DeepSeek V4 Pro"
                "deepseek-v4-flash" -> "DeepSeek V4 Flash"
                "deepseek-v3.2" -> "DeepSeek V3.2"
                "deepseek-v4-1-flash-uncensored" -> "DeepSeek V4.1 Flash (Uncensored)"
                else -> id.split("-", "_")
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
            }
        }

        fun getPinnedModelDescription(id: String): String {
            return when (id) {
                "deepseek-v4-1-flash" -> "Default ultra-fast reasoning & chat flagship"
                "deepseek-v4-pro" -> "DeepSeek V4 Pro — maximum frontier intelligence"
                "deepseek-v4-flash" -> "High-speed DeepSeek V4 balanced model"
                "deepseek-v3.2" -> "Proven DeepSeek V3.2 versatile workhorse"
                "deepseek-v4-1-flash-uncensored" -> "Unfiltered DeepSeek V4.1 Flash with zero guardrails"
                else -> "Venice AI language model"
            }
        }
    }
}
