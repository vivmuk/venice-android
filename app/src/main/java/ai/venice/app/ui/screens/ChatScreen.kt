package ai.venice.app.ui.screens

import ai.venice.app.data.local.MessageEntity
import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.data.remote.VeniceModel
import ai.venice.app.ui.AppSection
import ai.venice.app.ui.ChatUiState
import ai.venice.app.ui.components.MarkdownText
import ai.venice.app.ui.theme.TextMuted
import ai.venice.app.ui.theme.TextPrimary
import ai.venice.app.ui.theme.TextSecondary
import ai.venice.app.ui.theme.VeniceAmber
import ai.venice.app.ui.theme.VeniceBorder
import ai.venice.app.ui.theme.VeniceCard
import ai.venice.app.ui.theme.VeniceCyan
import ai.venice.app.ui.theme.VeniceMagenta
import ai.venice.app.ui.theme.VeniceMint
import ai.venice.app.ui.theme.VeniceObsidian
import ai.venice.app.ui.theme.VeniceRed
import ai.venice.app.ui.theme.VeniceSurface
import ai.venice.app.ui.theme.VeniceSurfaceElevated
import ai.venice.app.ui.theme.VeniceViolet
import ai.venice.app.ui.theme.rememberFuturisticGradientBrush
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    messages: List<MessageEntity>,
    hasApiKey: Boolean,
    isRecording: Boolean,
    playingMessageId: String?,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onSelectModel: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onWebSearchToggle: (Boolean) -> Unit,
    onInputDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopStreaming: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeak: (MessageEntity) -> Unit,
    onNavigateSettings: () -> Unit,
    onRefreshModels: () -> Unit,
    onShowStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val gradientBrush = rememberFuturisticGradientBrush()

    var showModelSheet by remember { mutableStateOf(false) }
    var showParamsSheet by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleMic()
        } else {
            onShowStatus("Microphone permission is required for voice transcription.")
        }
    }

    LaunchedEffect(messages.size, uiState.streamingContent) {
        val totalItems = messages.size + if (uiState.isStreaming) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VeniceObsidian)
    ) {
        // Futuristic Top Bar
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
                    contentDescription = "Conversations",
                    tint = TextPrimary
                )
            }

            // Model Selector Pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VeniceSurfaceElevated)
                    .border(1.dp, gradientBrush, RoundedCornerShape(20.dp))
                    .clickable {
                        onRefreshModels()
                        showModelSheet = true
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = VeniceCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = VeniceApiClient.formatModelName(uiState.selectedTextModel),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Change model",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(onClick = { showParamsSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Chat Parameters",
                    tint = if (uiState.webSearchEnabled || uiState.reasoningEffort != "none") VeniceCyan else TextSecondary
                )
            }

            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = "New Chat",
                    tint = VeniceCyan
                )
            }
        }

        // Quick Parameter Bar (Web Search, Reasoning Effort, Temperature)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VeniceSurface.copy(alpha = 0.7f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Web Search quick chip
            QuickParamChip(
                icon = Icons.Default.Language,
                label = if (uiState.webSearchEnabled) "Web Search: ON" else "Web Search: OFF",
                active = uiState.webSearchEnabled,
                activeColor = VeniceCyan,
                onClick = { onWebSearchToggle(!uiState.webSearchEnabled) }
            )

            // Reasoning effort quick chip
            val nextEffort = when (uiState.reasoningEffort) {
                "none" -> "low"
                "low" -> "medium"
                "medium" -> "high"
                else -> "none"
            }
            QuickParamChip(
                icon = Icons.Default.Psychology,
                label = "Reasoning: ${uiState.reasoningEffort.uppercase()}",
                active = uiState.reasoningEffort != "none",
                activeColor = VeniceMagenta,
                onClick = { onReasoningEffortChange(nextEffort) }
            )

            // Temperature chip
            QuickParamChip(
                icon = Icons.Default.Tune,
                label = "Temp: ${uiState.temperature}",
                active = false,
                activeColor = VeniceViolet,
                onClick = { showParamsSheet = true }
            )
        }

        HorizontalDivider(color = VeniceBorder.copy(alpha = 0.6f))

        // API key banner if missing
        if (!hasApiKey) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF281524))
                    .clickable { onNavigateSettings() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = VeniceMagenta,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Add your Venice API key in Settings to start chatting.",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "SETUP →",
                    color = VeniceCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Message List or Futuristic Empty State
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !uiState.isStreaming) {
                EmptyChatHero(
                    selectedModel = uiState.selectedTextModel,
                    gradientBrush = gradientBrush,
                    onPromptClick = { starter ->
                        onInputDraftChange(starter)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, item -> item.id }
                    ) { index, message ->
                        val isLastAssistant = message.role == "assistant" && index == messages.lastIndex && !uiState.isStreaming
                        MessageBubble(
                            message = message,
                            isLastAssistant = isLastAssistant,
                            isPlayingAudio = playingMessageId == message.id,
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                onShowStatus("Copied to clipboard")
                            },
                            onSpeak = { onToggleSpeak(message) },
                            onRegenerate = onRegenerate
                        )
                    }

                    if (uiState.isStreaming) {
                        item(key = "streaming_bubble") {
                            StreamingMessageBubble(
                                modelId = uiState.selectedTextModel,
                                content = uiState.streamingContent,
                                reasoning = uiState.streamingReasoning,
                                gradientBrush = gradientBrush
                            )
                        }
                    }
                }
            }
        }

        // Bottom Composer Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VeniceSurface)
                .border(1.dp, VeniceBorder.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (isRecording || uiState.isTranscribing || uiState.isSynthesizingTts) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = if (isRecording) VeniceRed else VeniceCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRecording -> "Recording voice... Tap mic to transcribe"
                            uiState.isTranscribing -> "Transcribing via Venice Whisper API..."
                            else -> "Synthesizing Venice voice reply..."
                        },
                        fontSize = 12.sp,
                        color = if (isRecording) VeniceRed else VeniceCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Voice Microphone Button
                IconButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            onToggleMic()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) VeniceRed.copy(alpha = 0.2f)
                            else VeniceSurfaceElevated
                        )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isRecording) VeniceRed else VeniceCyan
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = uiState.inputDraft,
                    onValueChange = onInputDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp, max = 140.dp),
                    placeholder = {
                        Text(
                            text = "Ask Venice anything...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VeniceSurfaceElevated,
                        unfocusedContainerColor = VeniceSurfaceElevated,
                        focusedBorderColor = VeniceCyan,
                        unfocusedBorderColor = VeniceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = VeniceCyan
                    ),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (uiState.isStreaming) {
                    // Stop Streaming Button
                    IconButton(
                        onClick = onStopStreaming,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(VeniceRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop generating",
                            tint = Color.White
                        )
                    }
                } else {
                    val canSend = uiState.inputDraft.isNotBlank()
                    IconButton(
                        onClick = onSendMessage,
                        enabled = canSend,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(VeniceCyan, VeniceViolet))
                                else Brush.linearGradient(listOf(VeniceSurfaceElevated, VeniceSurfaceElevated))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (canSend) VeniceObsidian else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Model Picker Bottom Sheet
    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VeniceSurface,
            contentColor = TextPrimary
        ) {
            ModelPickerSheetContent(
                models = uiState.textModels,
                selectedModelId = uiState.selectedTextModel,
                isLoading = uiState.isLoadingModels,
                onRefresh = onRefreshModels,
                onSelect = { modelId ->
                    onSelectModel(modelId)
                    showModelSheet = false
                }
            )
        }
    }

    // Parameters Bottom Sheet (Temperature, Reasoning Effort, Web Search)
    if (showParamsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showParamsSheet = false },
            containerColor = VeniceSurface,
            contentColor = TextPrimary
        ) {
            ChatParametersSheetContent(
                temperature = uiState.temperature,
                reasoningEffort = uiState.reasoningEffort,
                webSearchEnabled = uiState.webSearchEnabled,
                onTemperatureChange = onTemperatureChange,
                onReasoningEffortChange = onReasoningEffortChange,
                onWebSearchToggle = onWebSearchToggle
            )
        }
    }
}

@Composable
private fun QuickParamChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) activeColor.copy(alpha = 0.16f) else VeniceSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (active) activeColor else VeniceBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) activeColor else TextSecondary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) activeColor else TextSecondary
        )
    }
}

@Composable
private fun EmptyChatHero(
    selectedModel: String,
    gradientBrush: Brush,
    onPromptClick: (String) -> Unit
) {
    val prompts = listOf(
        "Explain quantum computing using a cyberpunk analogy",
        "Write an ultra-fast Kotlin coroutine flow debounce operator",
        "Compare DeepSeek V4.1 Flash vs DeepSeek V4 Pro for reasoning",
        "Synthesize the latest breakthroughs in autonomous AI agents"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(VeniceSurfaceElevated)
                .border(2.dp, gradientBrush, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = VeniceCyan,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "VENICE INTELLIGENCE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Active Model: ${VeniceApiClient.formatModelName(selectedModel)}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = VeniceCyan
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            prompts.forEach { starter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VeniceSurfaceElevated)
                        .border(1.dp, VeniceBorder, RoundedCornerShape(14.dp))
                        .clickable { onPromptClick(starter) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = VeniceCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = starter,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    isLastAssistant: Boolean,
    isPlayingAudio: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"
    var showReasoning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role & Model badge
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isUser) "YOU" else VeniceApiClient.formatModelName(
                    message.modelId.ifBlank { "Venice AI" }
                ).uppercase(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (isUser) VeniceMagenta else VeniceCyan
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.88f else 0.98f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Color(0xFF1A233A)
                    else if (message.isError) Color(0xFF2D121E)
                    else VeniceSurfaceElevated
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) VeniceCyan.copy(alpha = 0.35f)
                    else if (message.isError) VeniceRed
                    else VeniceBorder,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            // Reasoning trace toggle if present
            if (message.reasoningContent.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VeniceObsidian.copy(alpha = 0.7f))
                        .border(1.dp, VeniceViolet.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { showReasoning = !showReasoning }
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = VeniceMagenta,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reasoning Trace",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VeniceMagenta
                            )
                        }
                        Icon(
                            imageVector = if (showReasoning) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(visible = showReasoning) {
                        Text(
                            text = message.reasoningContent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            MarkdownText(
                markdown = message.content,
                textColor = if (message.isError) VeniceRed else TextPrimary
            )

            // Message Action Row (Copy, Speak TTS, Regenerate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIconTextButton(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    tint = TextSecondary,
                    onClick = onCopy
                )

                if (!isUser && !message.isError) {
                    Spacer(modifier = Modifier.width(12.dp))
                    ActionIconTextButton(
                        icon = if (isPlayingAudio) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                        label = if (isPlayingAudio) "Stop" else "Speak",
                        tint = if (isPlayingAudio) VeniceRed else VeniceCyan,
                        onClick = onSpeak
                    )
                }

                if (isLastAssistant) {
                    Spacer(modifier = Modifier.width(12.dp))
                    ActionIconTextButton(
                        icon = Icons.Default.Refresh,
                        label = "Regenerate",
                        tint = VeniceMint,
                        onClick = onRegenerate
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionIconTextButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StreamingMessageBubble(
    modelId: String,
    content: String,
    reasoning: String,
    gradientBrush: Brush
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${VeniceApiClient.formatModelName(modelId).uppercase()} • STREAMING",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VeniceCyan
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .clip(RoundedCornerShape(16.dp))
                .background(VeniceSurfaceElevated)
                .border(1.5.dp, gradientBrush, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            if (reasoning.isNotBlank()) {
                Text(
                    text = "Thinking: ${reasoning.takeLast(240)}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = VeniceMagenta,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (content.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = VeniceCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Receiving stream from Venice API...",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            } else {
                MarkdownText(markdown = "$content ▍")
            }
        }
    }
}

@Composable
private fun ModelPickerSheetContent(
    models: List<VeniceModel>,
    selectedModelId: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit
) {
    val pinned = models.filter { it.isPinned }
    val others = models.filter { !it.isPinned }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Select Venice Text Model",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Pinned DeepSeek flagship models + live /models?type=text catalog",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onRefresh) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = VeniceCyan
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh catalog",
                        tint = VeniceCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "PINNED DEEPSEEK MODELS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = VeniceCyan,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            itemsIndexed(pinned) { _, model ->
                ModelRowItem(
                    model = model,
                    isSelected = model.id == selectedModelId,
                    onClick = { onSelect(model.id) }
                )
            }

            if (others.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ALL VENICE TEXT MODELS (${others.size})",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                itemsIndexed(others) { _, model ->
                    ModelRowItem(
                        model = model,
                        isSelected = model.id == selectedModelId,
                        onClick = { onSelect(model.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRowItem(
    model: VeniceModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) VeniceCyan.copy(alpha = 0.14f) else VeniceSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) VeniceCyan else VeniceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) VeniceCyan else TextPrimary
                )
                if (model.isPinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (model.id == "deepseek-v4-1-flash") "DEFAULT" else "PINNED",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VeniceObsidian,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (model.id == "deepseek-v4-1-flash") VeniceCyan else VeniceAmber)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = model.id,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
            if (model.description.isNotBlank()) {
                Text(
                    text = model.description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatParametersSheetContent(
    temperature: Float,
    reasoningEffort: String,
    webSearchEnabled: Boolean,
    onTemperatureChange: (Float) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onWebSearchToggle: (Boolean) -> Unit
) {
    val efforts = listOf("none", "low", "medium", "high")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Venice Generation Parameters",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Temperature Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Temperature",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = String.format("%.1f", temperature),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = VeniceCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = temperature,
                onValueChange = onTemperatureChange,
                valueRange = 0f..2f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = VeniceCyan,
                    activeTrackColor = VeniceCyan,
                    inactiveTrackColor = VeniceCard
                )
            )
            Text(
                text = "Lower values are more deterministic and focused; higher values are more creative.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Reasoning Effort Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Reasoning Effort (reasoning_effort)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                efforts.forEach { level ->
                    val selected = reasoningEffort == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) VeniceMagenta.copy(alpha = 0.2f) else VeniceSurfaceElevated)
                            .border(
                                1.dp,
                                if (selected) VeniceMagenta else VeniceBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onReasoningEffortChange(level) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) VeniceMagenta else TextSecondary
                        )
                    }
                }
            }
        }

        // Web Search Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VeniceSurfaceElevated)
                .border(1.dp, VeniceBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Venice Web Search",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Sends venice_parameters.enable_web_search = \"on\" for live citations",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = webSearchEnabled,
                onCheckedChange = onWebSearchToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VeniceObsidian,
                    checkedTrackColor = VeniceCyan
                )
            )
        }
    }
}
