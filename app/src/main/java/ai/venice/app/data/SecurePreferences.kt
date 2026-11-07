package ai.venice.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback only if hardware keystore fails on broken emulator image
        context.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
    }

    private val _hasApiKey = MutableStateFlow(getApiKey().isNotBlank())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "")?.trim() ?: ""
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_API_KEY, trimmed).apply()
        _hasApiKey.value = trimmed.isNotBlank()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
        _hasApiKey.value = false
    }

    fun getDefaultTextModel(): String {
        return prefs.getString(KEY_DEFAULT_TEXT_MODEL, DEFAULT_TEXT_MODEL) ?: DEFAULT_TEXT_MODEL
    }

    fun setDefaultTextModel(modelId: String) {
        prefs.edit().putString(KEY_DEFAULT_TEXT_MODEL, modelId).apply()
    }

    fun getDefaultImageModel(): String {
        return prefs.getString(KEY_DEFAULT_IMAGE_MODEL, DEFAULT_IMAGE_MODEL) ?: DEFAULT_IMAGE_MODEL
    }

    fun setDefaultImageModel(modelId: String) {
        prefs.edit().putString(KEY_DEFAULT_IMAGE_MODEL, modelId).apply()
    }

    fun getDefaultTemperature(): Float {
        return prefs.getFloat(KEY_DEFAULT_TEMPERATURE, 0.7f)
    }

    fun setDefaultTemperature(temp: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_TEMPERATURE, temp.coerceIn(0f, 2f)).apply()
    }

    fun getDefaultReasoningEffort(): String {
        return prefs.getString(KEY_DEFAULT_REASONING, "none") ?: "none"
    }

    fun setDefaultReasoningEffort(effort: String) {
        prefs.edit().putString(KEY_DEFAULT_REASONING, effort).apply()
    }

    fun getDefaultWebSearch(): Boolean {
        return prefs.getBoolean(KEY_DEFAULT_WEB_SEARCH, false)
    }

    fun setDefaultWebSearch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_WEB_SEARCH, enabled).apply()
    }

    fun getDefaultVoice(): String {
        return prefs.getString(KEY_DEFAULT_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
    }

    fun setDefaultVoice(voice: String) {
        prefs.edit().putString(KEY_DEFAULT_VOICE, voice).apply()
    }

    fun getDefaultTtsModel(): String {
        return prefs.getString(KEY_DEFAULT_TTS_MODEL, DEFAULT_TTS_MODEL) ?: DEFAULT_TTS_MODEL
    }

    fun setDefaultTtsModel(modelId: String) {
        prefs.edit().putString(KEY_DEFAULT_TTS_MODEL, modelId).apply()
    }

    fun getDefaultAsrModel(): String {
        return prefs.getString(KEY_DEFAULT_ASR_MODEL, DEFAULT_ASR_MODEL) ?: DEFAULT_ASR_MODEL
    }

    fun setDefaultAsrModel(modelId: String) {
        prefs.edit().putString(KEY_DEFAULT_ASR_MODEL, modelId).apply()
    }

    fun getSystemPrompt(): String {
        return prefs.getString(KEY_SYSTEM_PROMPT, "") ?: ""
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "venice_secure_prefs"
        private const val KEY_API_KEY = "venice_api_key"
        private const val KEY_DEFAULT_TEXT_MODEL = "default_text_model"
        private const val KEY_DEFAULT_IMAGE_MODEL = "default_image_model"
        private const val KEY_DEFAULT_TEMPERATURE = "default_temperature"
        private const val KEY_DEFAULT_REASONING = "default_reasoning_effort"
        private const val KEY_DEFAULT_WEB_SEARCH = "default_web_search"
        private const val KEY_DEFAULT_VOICE = "default_tts_voice"
        private const val KEY_DEFAULT_TTS_MODEL = "default_tts_model"
        private const val KEY_DEFAULT_ASR_MODEL = "default_asr_model"
        private const val KEY_SYSTEM_PROMPT = "default_system_prompt"

        const val DEFAULT_TEXT_MODEL = "deepseek-v4-1-flash"
        const val DEFAULT_IMAGE_MODEL = "fluently-xl"
        const val DEFAULT_TTS_MODEL = "tts-kokoro"
        const val DEFAULT_ASR_MODEL = "openai/whisper-large-v3"
        /** Default voice for the default TTS model (Kokoro). */
        const val DEFAULT_VOICE = "af_sky"

        val PINNED_TEXT_MODELS = listOf(
            "deepseek-v4-1-flash",
            "deepseek-v4-pro",
            "deepseek-v4-flash",
            "deepseek-v3.2",
            "deepseek-v4-1-flash-uncensored"
        )
    }
}
