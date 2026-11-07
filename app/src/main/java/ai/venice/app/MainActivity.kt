package ai.venice.app

import ai.venice.app.data.local.ConversationEntity
import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.ui.AppSection
import ai.venice.app.ui.VeniceViewModel
import ai.venice.app.ui.screens.ChatScreen
import ai.venice.app.ui.screens.ImageScreen
import ai.venice.app.ui.screens.SettingsScreen
import ai.venice.app.ui.theme.TextMuted
import ai.venice.app.ui.theme.TextPrimary
import ai.venice.app.ui.theme.TextSecondary
import ai.venice.app.ui.theme.VeniceBorder
import ai.venice.app.ui.theme.VeniceCyan
import ai.venice.app.ui.theme.VeniceMagenta
import ai.venice.app.ui.theme.VeniceObsidian
import ai.venice.app.ui.theme.VeniceRed
import ai.venice.app.ui.theme.VeniceSurface
import ai.venice.app.ui.theme.VeniceSurfaceElevated
import ai.venice.app.ui.theme.VeniceTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: VeniceViewModel by viewModels {
        VeniceViewModel.provideFactory(application as VeniceApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeniceTheme {
                VeniceAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun VeniceAppRoot(viewModel: VeniceViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val images by viewModel.generatedImages.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val isRecording by viewModel.voiceController.isRecording.collectAsState()
    val playingMessageId by viewModel.voiceController.playingMessageId.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = VeniceSurface,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(300.dp)
            ) {
                ConversationDrawerContent(
                    conversations = conversations,
                    currentConversationId = uiState.currentConversationId,
                    onNewChat = {
                        viewModel.startNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = { conv ->
                        viewModel.selectConversation(conv)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { convId ->
                        viewModel.deleteConversation(convId)
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = VeniceObsidian,
            bottomBar = {
                NavigationBar(
                    containerColor = VeniceSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(1.dp, VeniceBorder.copy(alpha = 0.5f))
                ) {
                    NavigationBarItem(
                        selected = uiState.activeSection == AppSection.CHAT,
                        onClick = { viewModel.setSection(AppSection.CHAT) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Chat"
                            )
                        },
                        label = { Text("Chat", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VeniceCyan,
                            selectedTextColor = VeniceCyan,
                            indicatorColor = VeniceCyan.copy(alpha = 0.14f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = uiState.activeSection == AppSection.IMAGINE,
                        onClick = { viewModel.setSection(AppSection.IMAGINE) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Image"
                            )
                        },
                        label = { Text("Image", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VeniceMagenta,
                            selectedTextColor = VeniceMagenta,
                            indicatorColor = VeniceMagenta.copy(alpha = 0.14f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = uiState.activeSection == AppSection.SETTINGS,
                        onClick = { viewModel.setSection(AppSection.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VeniceCyan,
                            selectedTextColor = VeniceCyan,
                            indicatorColor = VeniceCyan.copy(alpha = 0.14f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Status/Toast Banner
                AnimatedVisibility(visible = uiState.statusBanner != null) {
                    uiState.statusBanner?.let { banner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VeniceSurfaceElevated)
                                .border(1.dp, VeniceCyan.copy(alpha = 0.4f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = banner,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearStatusBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (uiState.activeSection) {
                        AppSection.CHAT -> {
                            ChatScreen(
                                uiState = uiState,
                                messages = messages,
                                hasApiKey = hasApiKey,
                                isRecording = isRecording,
                                playingMessageId = playingMessageId,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onNewChat = { viewModel.startNewChat() },
                                onSelectModel = { viewModel.setSelectedTextModel(it) },
                                onTemperatureChange = { viewModel.setTemperature(it) },
                                onReasoningEffortChange = { viewModel.setReasoningEffort(it) },
                                onWebSearchToggle = { viewModel.setWebSearchEnabled(it) },
                                onInputDraftChange = { viewModel.updateInputDraft(it) },
                                onSendMessage = { viewModel.sendMessage() },
                                onStopStreaming = { viewModel.stopStreaming() },
                                onRegenerate = { viewModel.regenerateLastResponse() },
                                onToggleMic = { viewModel.toggleMicRecording() },
                                onToggleSpeak = { viewModel.toggleSpeakMessage(it) },
                                onNavigateSettings = { viewModel.setSection(AppSection.SETTINGS) },
                                onRefreshModels = { viewModel.refreshCatalogs() },
                                onShowStatus = { viewModel.showStatus(it) }
                            )
                        }
                        AppSection.IMAGINE -> {
                            ImageScreen(
                                uiState = uiState,
                                images = images,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onSelectImageModel = { viewModel.setSelectedImageModel(it) },
                                onSelectImageSize = { viewModel.setSelectedImageSize(it) },
                                onGenerateImage = { viewModel.generateImage(it) },
                                onSaveToGallery = { viewModel.saveImageToGallery(context, it) },
                                onDeleteImage = { viewModel.deleteGeneratedImage(it) },
                                onRefreshModels = { viewModel.refreshCatalogs() }
                            )
                        }
                        AppSection.SETTINGS -> {
                            SettingsScreen(
                                uiState = uiState,
                                prefs = viewModel.prefs,
                                hasApiKey = hasApiKey,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onSaveApiKey = { viewModel.saveApiKey(it) },
                                onClearHistory = { viewModel.clearAllHistory() },
                                onSelectDefaultTextModel = { viewModel.setSelectedTextModel(it) },
                                onSelectDefaultImageModel = { viewModel.setSelectedImageModel(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationDrawerContent(
    conversations: List<ConversationEntity>,
    currentConversationId: String?,
    onNewChat: () -> Unit,
    onSelectConversation: (ConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "VENICE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = VeniceCyan
            )
            Text(
                text = "v1.0",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VeniceCyan,
                contentColor = VeniceObsidian
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "New Conversation",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = VeniceBorder)
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "CONVERSATION HISTORY (${conversations.size})",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No conversations yet",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == currentConversationId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) VeniceCyan.copy(alpha = 0.14f) else VeniceSurfaceElevated)
                            .border(
                                1.dp,
                                if (isSelected) VeniceCyan else VeniceBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectConversation(conv) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conv.title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) VeniceCyan else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = VeniceApiClient.formatModelName(conv.modelId),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { onDeleteConversation(conv.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete chat",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
