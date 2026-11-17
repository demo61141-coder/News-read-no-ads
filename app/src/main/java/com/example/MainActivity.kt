package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ai.AudioRecorderHelper
import com.example.data.ai.GeminiMediaRepository
import com.example.data.local.CleanReadDatabase
import com.example.data.local.ReaderPreferencesRepository
import com.example.data.reader.ArticleRepository
import com.example.data.reader.UrlCleaner
import com.example.firebase.configureAppCheck
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.medialab.MediaLabScreen
import com.example.ui.medialab.MediaLabViewModel
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderViewModel
import com.example.ui.theme.CleanReadTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class AppDestination(val labelRes: Int) {
    READER(R.string.nav_reader),
    LIBRARY(R.string.nav_library),
    MEDIA_LAB(R.string.nav_media_lab)
}

class MainActivity : ComponentActivity() {

    private val incomingSharedUrl = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        handleIncomingIntent(intent)

        val database = CleanReadDatabase.getInstance(applicationContext)
        val articleRepository = ArticleRepository(database.articleDao())
        val preferencesRepository = ReaderPreferencesRepository(applicationContext)
        val geminiRepository = GeminiMediaRepository(applicationContext)
        val audioRecorderHelper = AudioRecorderHelper(applicationContext)

        setContent {
            CleanReadTheme {
                val readerViewModel: ReaderViewModel = viewModel(
                    factory = ReaderViewModel.Factory(
                        articleRepository = articleRepository,
                        preferencesRepository = preferencesRepository,
                        geminiRepository = geminiRepository
                    )
                )
                val libraryViewModel: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.Factory(articleRepository)
                )
                val mediaLabViewModel: MediaLabViewModel = viewModel(
                    factory = MediaLabViewModel.Factory(
                        geminiRepository = geminiRepository,
                        audioRecorderHelper = audioRecorderHelper
                    )
                )

                val sharedUrl by incomingSharedUrl.collectAsStateWithLifecycle()

                CleanReadApp(
                    readerViewModel = readerViewModel,
                    libraryViewModel = libraryViewModel,
                    mediaLabViewModel = mediaLabViewModel,
                    sharedUrl = sharedUrl,
                    onConsumeSharedUrl = { incomingSharedUrl.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val rawText = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return

        UrlCleaner.extractAndClean(rawText)?.let { cleaned ->
            incomingSharedUrl.value = cleaned.cleanUrl
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanReadApp(
    readerViewModel: ReaderViewModel,
    libraryViewModel: LibraryViewModel,
    mediaLabViewModel: MediaLabViewModel,
    sharedUrl: String?,
    onConsumeSharedUrl: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.READER) }

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            currentDestination = AppDestination.READER
            readerViewModel.onUrlInputChange(sharedUrl)
            readerViewModel.openArticle(sharedUrl)
            onConsumeSharedUrl()
        }
    }

    if (currentDestination != AppDestination.READER) {
        BackHandler {
            currentDestination = AppDestination.READER
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar("$label copied to clipboard")
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            val selected = currentDestination == destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    DestinationIcon(destination = destination, selected = selected)
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                                modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AppDestination.entries.forEach { destination ->
                            val selected = currentDestination == destination
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    DestinationIcon(destination = destination, selected = selected)
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                                modifier = Modifier.testTag("rail_${destination.name.lowercase()}")
                            )
                        }
                    }
                }

                when (currentDestination) {
                    AppDestination.READER -> {
                        val urlInput by readerViewModel.urlInput.collectAsStateWithLifecycle()
                        val readerState by readerViewModel.readerState.collectAsStateWithLifecycle()
                        val briefState by readerViewModel.briefState.collectAsStateWithLifecycle()
                        val prefs by readerViewModel.readerPreferences.collectAsStateWithLifecycle()

                        ReaderScreen(
                            urlInput = urlInput,
                            readerState = readerState,
                            briefState = briefState,
                            preferences = prefs,
                            onUrlChange = readerViewModel::onUrlInputChange,
                            onOpenArticle = readerViewModel::openArticle,
                            onClearReader = readerViewModel::clearReader,
                            onToggleBookmark = readerViewModel::toggleBookmark,
                            onGenerateBrief = readerViewModel::generateQuickBrief,
                            onDismissBrief = readerViewModel::dismissBrief,
                            onCopyText = ::copyToClipboard,
                            onFormatFullCopy = readerViewModel::formatArticleForCopy,
                            onSetFontSize = readerViewModel::setFontSize,
                            onSetLineSpacing = readerViewModel::setLineSpacing,
                            onSetUseSerif = readerViewModel::setUseSerif,
                            onSetSurfaceMode = readerViewModel::setSurfaceMode,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AppDestination.LIBRARY -> {
                        val articles by libraryViewModel.articles.collectAsStateWithLifecycle()
                        val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
                        val bookmarkedOnly by libraryViewModel.bookmarkedOnly.collectAsStateWithLifecycle()

                        LibraryScreen(
                            articles = articles,
                            searchQuery = searchQuery,
                            bookmarkedOnly = bookmarkedOnly,
                            onSearchQueryChange = libraryViewModel::onSearchQueryChange,
                            onSetBookmarkedOnly = libraryViewModel::setBookmarkedOnly,
                            onOpenSavedArticle = { cleanUrl ->
                                currentDestination = AppDestination.READER
                                readerViewModel.openArticle(cleanUrl)
                            },
                            onToggleBookmark = libraryViewModel::toggleBookmark,
                            onCopyArticle = { entity ->
                                val full = "${entity.title}\n${entity.cleanUrl}\n\n${entity.contentText}"
                                copyToClipboard("Article", full)
                            },
                            onDeleteArticle = libraryViewModel::deleteArticle,
                            onGoToReader = { currentDestination = AppDestination.READER },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AppDestination.MEDIA_LAB -> {
                        val selectedTab by mediaLabViewModel.selectedTab.collectAsStateWithLifecycle()
                        val selectedBitmap by mediaLabViewModel.selectedBitmap.collectAsStateWithLifecycle()
                        val selectedVideo by mediaLabViewModel.selectedVideo.collectAsStateWithLifecycle()
                        val selectedAudio by mediaLabViewModel.selectedAudio.collectAsStateWithLifecycle()
                        val customPrompt by mediaLabViewModel.customPrompt.collectAsStateWithLifecycle()
                        val isRecording by mediaLabViewModel.isRecording.collectAsStateWithLifecycle()
                        val recordingSeconds by mediaLabViewModel.recordingSeconds.collectAsStateWithLifecycle()
                        val uiState by mediaLabViewModel.uiState.collectAsStateWithLifecycle()

                        MediaLabScreen(
                            selectedTab = selectedTab,
                            selectedBitmap = selectedBitmap,
                            selectedVideo = selectedVideo,
                            selectedAudio = selectedAudio,
                            customPrompt = customPrompt,
                            isRecording = isRecording,
                            recordingSeconds = recordingSeconds,
                            uiState = uiState,
                            onSelectTab = mediaLabViewModel::selectTab,
                            onPromptChange = mediaLabViewModel::onCustomPromptChange,
                            onPickedImage = mediaLabViewModel::setPickedImage,
                            onAnalyzeImage = mediaLabViewModel::analyzeCurrentImage,
                            onPickedVideo = mediaLabViewModel::setPickedVideo,
                            onAnalyzeVideo = mediaLabViewModel::analyzeCurrentVideo,
                            onPickedAudio = mediaLabViewModel::setPickedAudio,
                            onRetranscribeAudio = mediaLabViewModel::reTranscribeCurrentAudio,
                            onStartRecording = mediaLabViewModel::startRecording,
                            onStopRecording = mediaLabViewModel::stopRecordingAndTranscribe,
                            onCopyOutput = ::copyToClipboard,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: AppDestination, selected: Boolean) {
    val icon = when (destination) {
        AppDestination.READER -> if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
        AppDestination.LIBRARY -> if (selected) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks
        AppDestination.MEDIA_LAB -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    }
    Icon(imageVector = icon, contentDescription = null)
}
