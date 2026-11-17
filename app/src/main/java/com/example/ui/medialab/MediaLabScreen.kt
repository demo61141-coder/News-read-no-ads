package com.example.ui.medialab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_VIDEO_READ_BYTES = 18L * 1024L * 1024L

@Composable
fun MediaLabScreen(
    selectedTab: MediaToolTab,
    selectedBitmap: Bitmap?,
    selectedVideo: SelectedVideoInfo?,
    customPrompt: String,
    isRecording: Boolean,
    recordingSeconds: Int,
    uiState: MediaLabUiState,
    onSelectTab: (MediaToolTab) -> Unit,
    onPromptChange: (String) -> Unit,
    onPickedImage: (Bitmap?) -> Unit,
    onAnalyzeImage: (String?) -> Unit,
    onPickedVideo: (SelectedVideoInfo?) -> Unit,
    onAnalyzeVideo: (String?) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCopyOutput: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mediaLoadError by remember { mutableStateOf<String?>(null) }
    var micPermissionDenied by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            mediaLoadError = null
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { decodeBitmapFromUri(context, uri) }
                if (loaded != null) {
                    onPickedImage(loaded)
                } else {
                    mediaLoadError = "Could not load selected image."
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            mediaLoadError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { readVideoFromUri(context, uri) }
                result.fold(
                    onSuccess = { info -> onPickedVideo(info) },
                    onFailure = { err -> mediaLoadError = err.localizedMessage }
                )
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            onStartRecording()
        } else {
            micPermissionDenied = true
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "media_lab_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Gemini Media Lab",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Scan news clippings & photos, analyze news videos, or transcribe voice notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        MediaToolTab.entries.forEachIndexed { index, tab ->
                            SegmentedButton(
                                selected = selectedTab == tab,
                                onClick = {
                                    mediaLoadError = null
                                    onSelectTab(tab)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = MediaToolTab.entries.size
                                ),
                                icon = {
                                    val icon = when (tab) {
                                        MediaToolTab.PHOTO -> Icons.Filled.DocumentScanner
                                        MediaToolTab.VIDEO -> Icons.Filled.VideoLibrary
                                        MediaToolTab.AUDIO -> Icons.Filled.Mic
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("media_tab_${tab.name.lowercase()}")
                            ) {
                                Text(tab.label, maxLines = 1)
                            }
                        }
                    }
                }
            }

            item(key = "media_prompt_input") {
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_prompt_field"),
                    label = { Text("Optional instruction (English or বাংলা)") },
                    placeholder = {
                        Text(
                            when (selectedTab) {
                                MediaToolTab.PHOTO -> "e.g., Extract text or summarize this news photo"
                                MediaToolTab.VIDEO -> "e.g., Extract key facts and timeline from video"
                                MediaToolTab.AUDIO -> "e.g., Transcribe in Bengali and clean punctuation"
                            }
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            item(key = "media_tool_panel") {
                when (selectedTab) {
                    MediaToolTab.PHOTO -> {
                        PhotoToolPanel(
                            bitmap = selectedBitmap,
                            onPickPhoto = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPresetPrompt = { preset ->
                                onPromptChange(preset)
                                onAnalyzeImage(preset)
                            }
                        )
                    }

                    MediaToolTab.VIDEO -> {
                        VideoToolPanel(
                            videoInfo = selectedVideo,
                            onPickVideo = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onPresetPrompt = { preset ->
                                onPromptChange(preset)
                                onAnalyzeVideo(preset)
                            }
                        )
                    }

                    MediaToolTab.AUDIO -> {
                        AudioToolPanel(
                            isRecording = isRecording,
                            recordingSeconds = recordingSeconds,
                            micPermissionDenied = micPermissionDenied,
                            onToggleRecord = {
                                if (isRecording) {
                                    onStopRecording()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        micPermissionDenied = false
                                        onStartRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (mediaLoadError != null) {
                item(key = "media_load_error") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = mediaLoadError.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            item(key = "media_output_panel") {
                when (uiState) {
                    MediaLabUiState.Idle -> Unit
                    is MediaLabUiState.Loading -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = uiState.statusLabel,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    is MediaLabUiState.Success -> {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = uiState.modelTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onCopyOutput("Analysis Result", uiState.outputText) },
                                        modifier = Modifier.testTag("copy_media_output_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Text")
                                    }
                                }

                                SelectionContainer {
                                    Text(
                                        text = uiState.outputText,
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp)
                                    )
                                }
                            }
                        }
                    }

                    is MediaLabUiState.Error -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoToolPanel(
    bitmap: Bitmap?,
    onPickPhoto: () -> Unit,
    onPresetPrompt: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Selected photo for analysis",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Button(
                onClick = onPickPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pick_photo_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (bitmap == null) "Upload Photo to Analyze" else "Choose Another Photo")
            }

            if (bitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onPresetPrompt("Extract all readable text cleanly without clutter.") },
                        label = { Text("Extract Text") }
                    )
                    AssistChip(
                        onClick = { onPresetPrompt("Summarize the key news facts visible in this image.") },
                        label = { Text("Summarize") }
                    )
                    AssistChip(
                        onClick = { onPresetPrompt("এই ছবির তথ্যগুলো বাংলায় সংক্ষেপে লিখুন।") },
                        label = { Text("বাংলায় ব্যাখ্যা") }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoToolPanel(
    videoInfo: SelectedVideoInfo?,
    onPickVideo: () -> Unit,
    onPresetPrompt: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (videoInfo != null) {
                val mb = String.format("%.1f MB", videoInfo.bytes.size / (1024.0 * 1024.0))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(videoInfo.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("${videoInfo.mimeType} • $mb", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Button(
                onClick = onPickVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pick_video_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.VideoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (videoInfo == null) "Select Video Clip (< 18 MB)" else "Choose Another Video")
            }

            if (videoInfo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onPresetPrompt("Extract key information, spoken quotes, and timeline from this video.") },
                        label = { Text("Key Information") }
                    )
                    AssistChip(
                        onClick = { onPresetPrompt("ভিডিওটির মূল ঘটনা ও বক্তব্য বাংলায় সংক্ষেপে লিখুন।") },
                        label = { Text("বাংলা সারাংশ") }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioToolPanel(
    isRecording: Boolean,
    recordingSeconds: Int,
    micPermissionDenied: Boolean,
    onToggleRecord: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isRecording) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (isRecording) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            if (isRecording) {
                val mins = recordingSeconds / 60
                val secs = recordingSeconds % 60
                Text(
                    text = String.format("Recording  %02d:%02d", mins, secs),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "Tap to record voice or news clip",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (micPermissionDenied) {
                Text(
                    text = stringResource(R.string.mic_permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onToggleRecord,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("record_audio_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRecording) "Stop & Transcribe" else "Start Recording")
            }
        }
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()
}

private fun readVideoFromUri(context: Context, uri: Uri): Result<SelectedVideoInfo> {
    return runCatching {
        val resolver = context.contentResolver
        val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (declaredSize > MAX_VIDEO_READ_BYTES) {
            throw IllegalArgumentException(
                "Selected video is ${declaredSize / (1024 * 1024)} MB. Please choose a clip under 18 MB."
            )
        }

        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Selected Video"

        val mimeType = resolver.getType(uri) ?: "video/mp4"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read selected video.")

        if (bytes.size.toLong() > MAX_VIDEO_READ_BYTES) {
            throw IllegalArgumentException(
                "Selected video is ${bytes.size / (1024 * 1024)} MB. Please choose a clip under 18 MB."
            )
        }
        SelectedVideoInfo(displayName = displayName, mimeType = mimeType, bytes = bytes)
    }
}
