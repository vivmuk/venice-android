package ai.venice.app.ui.screens

import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.ui.ChatUiState
import ai.venice.app.ui.VoicePhase
import ai.venice.app.ui.theme.TextMuted
import ai.venice.app.ui.theme.TextPrimary
import ai.venice.app.ui.theme.TextSecondary
import ai.venice.app.ui.theme.VeniceAmber
import ai.venice.app.ui.theme.VeniceBorder
import ai.venice.app.ui.theme.VeniceCyan
import ai.venice.app.ui.theme.VeniceMagenta
import ai.venice.app.ui.theme.VeniceMint
import ai.venice.app.ui.theme.VeniceObsidian
import ai.venice.app.ui.theme.VeniceRed
import ai.venice.app.ui.theme.VeniceSurfaceElevated
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen voice-to-voice conversation: speak -> transcribe -> chat -> speak the reply,
 * then back to listening until the user ends it.
 */
@Composable
fun VoiceConversationOverlay(
    uiState: ChatUiState,
    onFinishTurn: () -> Unit,
    onEnd: () -> Unit
) {
    val phase = uiState.voicePhase
    val phaseColor: Color = when (phase) {
        VoicePhase.LISTENING -> VeniceCyan
        VoicePhase.TRANSCRIBING -> VeniceAmber
        VoicePhase.THINKING -> VeniceMagenta
        VoicePhase.SPEAKING -> VeniceMint
        VoicePhase.IDLE -> TextSecondary
    }
    val phaseLabel: String = when (phase) {
        VoicePhase.LISTENING -> "Listening… speak now"
        VoicePhase.TRANSCRIBING -> "Hearing you…"
        VoicePhase.THINKING -> "Thinking…"
        VoicePhase.SPEAKING -> "Speaking…"
        VoicePhase.IDLE -> "Voice chat"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice-orb")
    val orbScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (phase) {
            VoicePhase.LISTENING -> 1.16f
            VoicePhase.SPEAKING -> 1.10f
            else -> 1.03f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-scale"
    )

    Dialog(
        onDismissRequest = onEnd,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VeniceObsidian)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VOICE CHAT",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = VeniceApiClient.formatModelName(uiState.selectedTextModel),
                    fontSize = 13.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Pulsing orb
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(orbScale)
                        .clip(CircleShape)
                        .background(phaseColor.copy(alpha = 0.16f))
                        .border(2.dp, phaseColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (phase == VoicePhase.THINKING || phase == VoicePhase.TRANSCRIBING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = phaseColor,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = phaseColor,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = phaseLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = phaseColor
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Transcript + reply
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.voiceHeardText.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(VeniceSurfaceElevated)
                                .border(1.dp, VeniceBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "YOU",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VeniceCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.voiceHeardText,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }
                    }
                    if (uiState.voiceReplyText.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(VeniceSurfaceElevated)
                                .border(1.dp, VeniceBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = VeniceApiClient.formatModelName(uiState.selectedTextModel).uppercase(),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VeniceMint
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.voiceReplyText,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (phase == VoicePhase.LISTENING) {
                        Button(
                            onClick = onFinishTurn,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VeniceCyan,
                                contentColor = VeniceObsidian
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Tap to send", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = onEnd,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VeniceRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "End voice chat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
