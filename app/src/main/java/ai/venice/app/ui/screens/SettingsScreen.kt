package ai.venice.app.ui.screens

import ai.venice.app.data.SecurePreferences
import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.ui.ChatUiState
import ai.venice.app.ui.theme.TextMuted
import ai.venice.app.ui.theme.TextPrimary
import ai.venice.app.ui.theme.TextSecondary
import ai.venice.app.ui.theme.VeniceBorder
import ai.venice.app.ui.theme.VeniceCard
import ai.venice.app.ui.theme.VeniceCyan
import ai.venice.app.ui.theme.VeniceTangerine
import ai.venice.app.ui.theme.VeniceMint
import ai.venice.app.ui.theme.VeniceObsidian
import ai.venice.app.ui.theme.VeniceRed
import ai.venice.app.ui.theme.VeniceSurface
import ai.venice.app.ui.theme.VeniceSurfaceElevated
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    uiState: ChatUiState,
    prefs: SecurePreferences,
    hasApiKey: Boolean,
    onOpenDrawer: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSelectDefaultTextModel: (String) -> Unit,
    onSelectDefaultImageModel: (String) -> Unit,
    onSelectDefaultTtsModel: (String) -> Unit,
    onSelectDefaultAsrModel: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var apiKeyInput by remember { mutableStateOf(prefs.getApiKey()) }
    var showKey by remember { mutableStateOf(false) }

    var defaultModel by remember { mutableStateOf(prefs.getDefaultTextModel()) }
    var defaultTemp by remember { mutableFloatStateOf(prefs.getDefaultTemperature()) }
    var defaultEffort by remember { mutableStateOf(prefs.getDefaultReasoningEffort()) }
    var defaultWebSearch by remember { mutableStateOf(prefs.getDefaultWebSearch()) }
    var defaultVoice by remember { mutableStateOf(prefs.getDefaultVoice()) }
    var defaultTtsModel by remember { mutableStateOf(prefs.getDefaultTtsModel()) }
    var defaultAsrModel by remember { mutableStateOf(prefs.getDefaultAsrModel()) }
    var systemPrompt by remember { mutableStateOf(prefs.getSystemPrompt()) }

    var showClearConfirm by remember { mutableStateOf(false) }

    val voices = listOf(
        "af_sky" to "Sky (US Female)",
        "af_bella" to "Bella (US Female)",
        "am_adam" to "Adam (US Male)",
        "am_michael" to "Michael (US Male)",
        "bf_emma" to "Emma (UK Female)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VeniceObsidian)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VeniceSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = TextPrimary
                )
            }
            Text(
                text = "VENICE SETTINGS",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextPrimary
            )
        }

        HorizontalDivider(color = VeniceBorder.copy(alpha = 0.6f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Venice API Key Card (EncryptedSharedPreferences)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VeniceSurfaceElevated)
                        .border(1.dp, VeniceCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = VeniceCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Venice API Key",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hasApiKey) VeniceMint else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasApiKey) "ENCRYPTED & ACTIVE" else "NOT CONFIGURED",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (hasApiKey) VeniceMint else TextMuted
                            )
                        }
                    }

                    Text(
                        text = "Your API key is encrypted on-device using Android Keystore (AES256-GCM EncryptedSharedPreferences) and sent only as a Bearer token to https://api.venice.ai/api/v1.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // Note on where to get a Venice key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VeniceObsidian)
                            .border(1.dp, VeniceBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                runCatching {
                                    uriHandler.openUri("https://venice.ai/settings/api")
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Where to get a Venice API key:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VeniceCyan
                            )
                            Text(
                                text = "Visit venice.ai/settings/api → Generate a new API key and paste it below.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open venice.ai",
                            tint = VeniceCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Bearer API Key") },
                        placeholder = { Text("vn_...", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key visibility",
                                    tint = TextSecondary
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = VeniceObsidian,
                            unfocusedContainerColor = VeniceObsidian,
                            focusedBorderColor = VeniceCyan,
                            unfocusedBorderColor = VeniceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onSaveApiKey(apiKeyInput) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VeniceCyan,
                                contentColor = VeniceObsidian
                            )
                        ) {
                            Text(
                                text = "Save API Key",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (hasApiKey) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyInput = ""
                                    prefs.clearApiKey()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Clear Key", color = VeniceRed)
                            }
                        }
                    }
                }
            }

            // 2. Model Defaults Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VeniceSurfaceElevated)
                        .border(1.dp, VeniceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Model & Chat Defaults",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Default Text Model (Pinned Flagships)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecurePreferences.PINNED_TEXT_MODELS.forEach { modelId ->
                            val selected = defaultModel == modelId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) VeniceCyan.copy(alpha = 0.15f) else VeniceObsidian)
                                    .border(
                                        1.dp,
                                        if (selected) VeniceCyan else VeniceBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        defaultModel = modelId
                                        prefs.setDefaultTextModel(modelId)
                                        onSelectDefaultTextModel(modelId)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = VeniceApiClient.formatModelName(modelId),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) VeniceCyan else TextPrimary
                                    )
                                    Text(
                                        text = modelId,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                }
                                if (selected) {
                                    Text(
                                        text = "DEFAULT",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = VeniceCyan
                                    )
                                }
                            }
                        }
                    }

                    // Default Temperature
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Default Temperature",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = String.format("%.1f", defaultTemp),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = VeniceCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = defaultTemp,
                            onValueChange = {
                                val r = (it * 10f).toInt() / 10f
                                defaultTemp = r
                                prefs.setDefaultTemperature(r)
                            },
                            valueRange = 0f..2f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = VeniceCyan,
                                activeTrackColor = VeniceCyan,
                                inactiveTrackColor = VeniceCard
                            )
                        )
                    }

                    // Default Reasoning Effort
                    Text(
                        text = "Default Reasoning Effort",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("none", "low", "medium", "high").forEach { effort ->
                            val selected = defaultEffort == effort
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) VeniceTangerine.copy(alpha = 0.2f) else VeniceObsidian)
                                    .border(
                                        1.dp,
                                        if (selected) VeniceTangerine else VeniceBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        defaultEffort = effort
                                        prefs.setDefaultReasoningEffort(effort)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = effort.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) VeniceTangerine else TextSecondary
                                )
                            }
                        }
                    }

                    // Default Web Search Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Web Search by Default",
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "venice_parameters.enable_web_search",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = defaultWebSearch,
                            onCheckedChange = {
                                defaultWebSearch = it
                                prefs.setDefaultWebSearch(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VeniceObsidian,
                                checkedTrackColor = VeniceCyan
                            )
                        )
                    }

                    // Default TTS Voice
                    Text(
                        text = "Default TTS Voice (/audio/speech)",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voices.forEach { (voiceId, label) ->
                            val selected = defaultVoice == voiceId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) VeniceCyan.copy(alpha = 0.18f) else VeniceObsidian)
                                    .border(
                                        1.dp,
                                        if (selected) VeniceCyan else VeniceBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        defaultVoice = voiceId
                                        prefs.setDefaultVoice(voiceId)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (selected) VeniceCyan else TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Default TTS Model — every Venice text-to-speech model (live catalog)
                    Text(
                        text = "Default TTS Model (/audio/speech) — ${uiState.ttsModels.size} models",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.ttsModels.forEach { model ->
                            val selected = defaultTtsModel == model.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) VeniceCyan.copy(alpha = 0.18f) else VeniceObsidian)
                                    .border(
                                        1.dp,
                                        if (selected) VeniceCyan else VeniceBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        defaultTtsModel = model.id
                                        prefs.setDefaultTtsModel(model.id)
                                        onSelectDefaultTtsModel(model.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = model.displayName,
                                    fontSize = 12.sp,
                                    color = if (selected) VeniceCyan else TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Default Speech Recognition Model — every Venice ASR model (live catalog)
                    Text(
                        text = "Speech Recognition Model (/audio/transcriptions) — ${uiState.asrModels.size} models",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.asrModels.forEach { model ->
                            val selected = defaultAsrModel == model.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) VeniceCyan.copy(alpha = 0.18f) else VeniceObsidian)
                                    .border(
                                        1.dp,
                                        if (selected) VeniceCyan else VeniceBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        defaultAsrModel = model.id
                                        prefs.setDefaultAsrModel(model.id)
                                        onSelectDefaultAsrModel(model.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = model.displayName,
                                    fontSize = 12.sp,
                                    color = if (selected) VeniceCyan else TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Custom System Prompt
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = {
                            systemPrompt = it
                            prefs.setSystemPrompt(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom System Prompt (Optional)") },
                        placeholder = { Text("You are a helpful, direct AI assistant...", color = TextMuted) },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = VeniceObsidian,
                            unfocusedContainerColor = VeniceObsidian,
                            focusedBorderColor = VeniceCyan,
                            unfocusedBorderColor = VeniceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }

            // 3. Clear History Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VeniceSurfaceElevated)
                        .border(1.dp, VeniceRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Local Storage & Privacy",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "All conversations and generated images are stored locally on your device in Room SQLite.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VeniceRed.copy(alpha = 0.18f),
                            contentColor = VeniceRed
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear All Chat & Image History",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = VeniceSurfaceElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Clear Local History?") },
            text = {
                Text("This will permanently delete all saved conversations, messages, and generated images from local Room storage.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearHistory()
                    }
                ) {
                    Text("Clear Everything", color = VeniceRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
