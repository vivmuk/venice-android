package ai.venice.app.ui.screens

import ai.venice.app.data.local.GeneratedImageEntity
import ai.venice.app.data.remote.VeniceApiClient
import ai.venice.app.ui.ChatUiState
import ai.venice.app.ui.theme.TextMuted
import ai.venice.app.ui.theme.TextPrimary
import ai.venice.app.ui.theme.TextSecondary
import ai.venice.app.ui.theme.VeniceBorder
import ai.venice.app.ui.theme.VeniceCyan
import ai.venice.app.ui.theme.VeniceTangerine
import ai.venice.app.ui.theme.VeniceMint
import ai.venice.app.ui.theme.VeniceObsidian
import ai.venice.app.ui.theme.VeniceRed
import ai.venice.app.ui.theme.VeniceSurface
import ai.venice.app.ui.theme.VeniceSurfaceElevated
import ai.venice.app.ui.theme.VenicePhosphor
import ai.venice.app.ui.theme.rememberFuturisticGradientBrush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    uiState: ChatUiState,
    images: List<GeneratedImageEntity>,
    onOpenDrawer: () -> Unit,
    onSelectImageModel: (String) -> Unit,
    onSelectImageSize: (String) -> Unit,
    onGenerateImage: (String) -> Unit,
    onSaveToGallery: (GeneratedImageEntity) -> Unit,
    onDeleteImage: (GeneratedImageEntity) -> Unit,
    onRefreshModels: () -> Unit
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    val gradientBrush = rememberFuturisticGradientBrush()
    val sizes = listOf("1024x1024", "1024x768", "768x1024")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VeniceObsidian)
    ) {
        // Top Header
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
                text = "VENICE IMAGE STUDIO",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            // Image Model Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(VeniceSurfaceElevated)
                    .border(1.dp, gradientBrush, RoundedCornerShape(18.dp))
                    .clickable {
                        onRefreshModels()
                        showModelPicker = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = VeniceTangerine,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = VeniceApiClient.formatModelName(uiState.selectedImageModel),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        HorizontalDivider(color = VeniceBorder.copy(alpha = 0.6f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prompt Composer Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VeniceSurfaceElevated)
                        .border(1.dp, VeniceBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Describe a futuristic scene, portrait, or concept...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = VeniceObsidian,
                            unfocusedContainerColor = VeniceObsidian,
                            focusedBorderColor = VeniceTangerine,
                            unfocusedBorderColor = VeniceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Aspect Ratio / Size Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SIZE:",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                        sizes.forEach { sz ->
                            val active = uiState.selectedImageSize == sz
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) VeniceCyan.copy(alpha = 0.18f) else VeniceObsidian)
                                    .border(
                                        1.dp,
                                        if (active) VeniceCyan else VeniceBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectImageSize(sz) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = sz,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) VeniceCyan else TextSecondary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onGenerateImage(prompt) },
                        enabled = prompt.isNotBlank() && !uiState.isGeneratingImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VeniceTangerine,
                            contentColor = Color.White,
                            disabledContainerColor = VeniceObsidian,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        if (uiState.isGeneratingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = VeniceCyan
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "SYNTHESIZING IMAGE...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GENERATE WITH ${uiState.selectedImageModel.uppercase()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (images.isEmpty() && !uiState.isGeneratingImage) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your generated Venice artwork will appear here",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(images, key = { it.id }) { img ->
                GeneratedImageCard(
                    image = img,
                    onSaveToGallery = { onSaveToGallery(img) },
                    onDelete = { onDeleteImage(img) }
                )
            }
        }
    }

    if (showModelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            containerColor = VeniceSurface,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Venice Image Model",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Fetched from GET /models?type=image",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onRefreshModels) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = VeniceCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.imageModels, key = { it.id }) { model ->
                        val selected = model.id == uiState.selectedImageModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) VeniceTangerine.copy(alpha = 0.16f) else VeniceSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (selected) VeniceTangerine else VeniceBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectImageModel(model.id)
                                    showModelPicker = false
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) VeniceTangerine else TextPrimary
                                )
                                Text(
                                    text = model.id,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratedImageCard(
    image: GeneratedImageEntity,
    onSaveToGallery: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VeniceSurfaceElevated)
            .border(1.dp, VeniceBorder, RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = File(image.localFilePath),
            contentDescription = image.prompt,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(VeniceObsidian)
        )

        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = image.prompt,
                fontSize = 14.sp,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${image.modelId} • ${image.size}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = VeniceCyan
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(VeniceCyan.copy(alpha = 0.16f))
                            .border(1.dp, VeniceCyan, RoundedCornerShape(8.dp))
                            .clickable(onClick = onSaveToGallery)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save to Gallery",
                            tint = VeniceCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Save to Gallery",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VeniceCyan
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete image",
                            tint = VeniceRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
