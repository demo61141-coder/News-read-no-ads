package com.example.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ai.BriefMode
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderSurfaceMode
import com.example.data.reader.ExtractedArticle
import com.example.ui.theme.CharcoalText
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.NewsreaderFontFamily
import com.example.ui.theme.NightBg
import com.example.ui.theme.NightInk
import com.example.ui.theme.NightSurface
import com.example.ui.theme.PaperCream
import com.example.ui.theme.PlusJakartaFontFamily
import kotlin.math.roundToInt

private const val SAMPLE_ITTEFAQ_URL =
    "https://www.ittefaq.com.bd/821351?fbclid=IwZXh0bgNhZW0CMTEAcGRvZgNzcnRjBmFwcF9pZA8yNzUyNTQ2OTI1OTgyNzkAAR-jz2sp1vfFavCdiUFbCcyjoIR5cPV4QaW5DMFfDHRAOnFi2o8eDSL7wiX0Aw_aem_Jb3fG43fJ0Uqdu6dHSrQCA"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    urlInput: String,
    readerState: ReaderUiState,
    briefState: BriefUiState,
    preferences: ReaderPreferences,
    onUrlChange: (String) -> Unit,
    onOpenArticle: (String, Boolean) -> Unit,
    onClearReader: () -> Unit,
    onToggleBookmark: () -> Unit,
    onGenerateBrief: (BriefMode) -> Unit,
    onDismissBrief: () -> Unit,
    onCopyText: (label: String, text: String) -> Unit,
    onFormatFullCopy: (ExtractedArticle) -> String,
    onSetFontSize: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetUseSerif: (Boolean) -> Unit,
    onSetSurfaceMode: (ReaderSurfaceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showTypographySheet by remember { mutableStateOf(false) }

    val readingBgColor = when {
        readerState is ReaderUiState.Success -> when (preferences.surfaceMode) {
            ReaderSurfaceMode.PAPER -> PaperCream
            ReaderSurfaceMode.CRISP -> CrispWhite
            ReaderSurfaceMode.NIGHT -> NightBg
        }
        else -> MaterialTheme.colorScheme.background
    }

    val readingInkColor = when {
        readerState is ReaderUiState.Success -> when (preferences.surfaceMode) {
            ReaderSurfaceMode.PAPER -> CharcoalText
            ReaderSurfaceMode.CRISP -> Color(0xFF111827)
            ReaderSurfaceMode.NIGHT -> NightInk
        }
        else -> MaterialTheme.colorScheme.onBackground
    }

    val readingCardBg = when {
        readerState is ReaderUiState.Success -> when (preferences.surfaceMode) {
            ReaderSurfaceMode.PAPER -> Color(0xFFF3EDE0)
            ReaderSurfaceMode.CRISP -> Color(0xFFF4F6F8)
            ReaderSurfaceMode.NIGHT -> NightSurface
        }
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readingBgColor),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "url_bar") {
                UrlInputBar(
                    urlInput = urlInput,
                    isSuccessState = readerState is ReaderUiState.Success,
                    onUrlChange = onUrlChange,
                    onPasteClick = {
                        val pasted = readClipboardText(context)
                        if (!pasted.isNullOrBlank()) {
                            onUrlChange(pasted)
                            onOpenArticle(pasted, false)
                        }
                    },
                    onClearClick = onClearReader,
                    onSubmit = {
                        keyboardController?.hide()
                        onOpenArticle(urlInput, false)
                    }
                )
            }

            when (readerState) {
                ReaderUiState.Empty -> {
                    item(key = "empty_hero") {
                        ReaderEmptyHero(
                            onTryUrl = { sampleUrl ->
                                onUrlChange(sampleUrl)
                                onOpenArticle(sampleUrl, false)
                            }
                        )
                    }
                }

                is ReaderUiState.Loading -> {
                    item(key = "loading_card") {
                        ReaderLoadingCard(url = readerState.url)
                    }
                }

                is ReaderUiState.Error -> {
                    item(key = "error_card") {
                        ReaderErrorCard(
                            message = readerState.message,
                            attemptedUrl = readerState.attemptedUrl,
                            onRetry = { onOpenArticle(readerState.attemptedUrl, true) },
                            onOpenBrowser = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(readerState.attemptedUrl))
                                    )
                                }
                            }
                        )
                    }
                }

                is ReaderUiState.Success -> {
                    val article = readerState.article
                    val readerFontFamily = if (preferences.useSerifFont) {
                        NewsreaderFontFamily
                    } else {
                        PlusJakartaFontFamily
                    }

                    item(key = "reader_toolbar") {
                        ReaderActionToolbar(
                            isBookmarked = readerState.isBookmarked,
                            cardBg = readingCardBg,
                            inkColor = readingInkColor,
                            onCopyAll = {
                                onCopyText("Full Article", onFormatFullCopy(article))
                            },
                            onCopyTitle = {
                                onCopyText("Headline", article.title)
                            },
                            onCopyCleanUrl = {
                                onCopyText("Clean URL", article.cleanUrl)
                            },
                            onShareText = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, article.title)
                                    putExtra(Intent.EXTRA_TEXT, onFormatFullCopy(article))
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share clean article"))
                            },
                            onToggleBookmark = onToggleBookmark,
                            onRefresh = { onOpenArticle(article.cleanUrl, true) },
                            onOpenTypography = { showTypographySheet = true }
                        )
                    }

                    item(key = "article_header") {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Shield,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.badge_ad_free),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (article.trackersStripped > 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "${article.trackersStripped} tracker stripped",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = readingCardBg,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "${article.readingTimeMinutes} min read",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = readingInkColor.copy(alpha = 0.78f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = article.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = readerFontFamily,
                                        fontSize = (preferences.fontSizeSp + 8f).sp,
                                        lineHeight = ((preferences.fontSizeSp + 8f) * 1.28f).sp
                                    ),
                                    color = readingInkColor
                                )

                                val metaParts = listOfNotNull(
                                    article.siteName.takeIf { it.isNotBlank() },
                                    article.author.takeIf { it.isNotBlank() },
                                    article.publishedDate.takeIf { it.isNotBlank() }
                                )
                                if (metaParts.isNotEmpty()) {
                                    Text(
                                        text = metaParts.joinToString("  •  "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = readingInkColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    if (article.leadImageUrl.isNotBlank()) {
                        item(key = "lead_image") {
                            AsyncImage(
                                model = article.leadImageUrl,
                                contentDescription = stringResource(R.string.cd_article_cover),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    item(key = "ai_quick_brief") {
                        AiQuickBriefCard(
                            briefState = briefState,
                            cardBg = readingCardBg,
                            inkColor = readingInkColor,
                            onSelectMode = onGenerateBrief,
                            onCopyBrief = { text -> onCopyText("AI Brief", text) },
                            onDismiss = onDismissBrief
                        )
                    }

                    item(key = "copy_hint_divider") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = readingInkColor.copy(alpha = 0.14f)
                            )
                            Text(
                                text = "Select any text or tap copy per paragraph",
                                style = MaterialTheme.typography.labelSmall,
                                color = readingInkColor.copy(alpha = 0.55f)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = readingInkColor.copy(alpha = 0.14f)
                            )
                        }
                    }

                    itemsIndexed(
                        items = article.paragraphs,
                        key = { index, _ -> "para_$index" }
                    ) { index, paragraph ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = paragraph,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = readerFontFamily,
                                        fontSize = preferences.fontSizeSp.sp,
                                        lineHeight = (preferences.fontSizeSp * preferences.lineSpacingMultiplier).sp
                                    ),
                                    color = readingInkColor
                                )
                            }
                            IconButton(
                                onClick = { onCopyText("Paragraph ${index + 1}", paragraph) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_paragraph_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_paragraph),
                                    tint = readingInkColor.copy(alpha = 0.48f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    item(key = "bottom_footer_actions") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = readingCardBg),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Source: ${article.sourceDomain}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = readingInkColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${article.paragraphs.size} clean paragraphs",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = readingInkColor.copy(alpha = 0.7f)
                                    )
                                }
                                Button(
                                    onClick = { onCopyText("Full Article", onFormatFullCopy(article)) },
                                    modifier = Modifier.testTag("footer_copy_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(R.string.action_copy_all))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTypographySheet) {
        ModalBottomSheet(onDismissRequest = { showTypographySheet = false }) {
            ReaderSettingsSheetContent(
                preferences = preferences,
                onSetFontSize = onSetFontSize,
                onSetLineSpacing = onSetLineSpacing,
                onSetUseSerif = onSetUseSerif,
                onSetSurfaceMode = onSetSurfaceMode
            )
        }
    }
}

@Composable
private fun UrlInputBar(
    urlInput: String,
    isSuccessState: Boolean,
    onUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input_field"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.url_input_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                trailingIcon = {
                    if (urlInput.isNotBlank()) {
                        IconButton(
                            onClick = onClearClick,
                            modifier = Modifier.testTag("clear_url_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_clear_link)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPasteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("paste_url_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_paste_link))
                }

                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("read_article_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSuccessState) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_read_ad_free))
                }
            }
        }
    }
}

@Composable
private fun ReaderEmptyHero(
    onTryUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_reader_hero),
                    contentDescription = stringResource(R.string.cd_hero_illustration),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Direct Ad-Free Reader Mode",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "যেকোনো নিউজ ওয়েবসাইটের লিংক পেস্ট করুন। বিজ্ঞাপন, পপ-আপ ও ট্র্যাকার বাদ দিয়ে সরাসরি পড়ুন এবং প্রয়োজনমতো কপি করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickFeaturePill(icon = Icons.Filled.Shield, label = "Zero Ads")
                        QuickFeaturePill(icon = Icons.Filled.ContentCopy, label = "1-Tap Copy")
                        QuickFeaturePill(icon = Icons.Filled.AutoAwesome, label = "AI Brief")
                    }
                }
            }
        }

        Text(
            text = "Try a sample news link",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            onClick = { onTryUrl(SAMPLE_ITTEFAQ_URL) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sample_ittefaq_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "The Daily Ittefaq — #821351",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Auto-strips fbclid tracker & loads clean Bengali news",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onTryUrl("https://www.bbc.com/news") },
                label = { Text("BBC News") },
                modifier = Modifier.testTag("sample_bbc_chip")
            )
            AssistChip(
                onClick = { onTryUrl("https://www.prothomalo.com") },
                label = { Text("Prothom Alo") },
                modifier = Modifier.testTag("sample_prothomalo_chip")
            )
            AssistChip(
                onClick = { onTryUrl("https://www.reuters.com") },
                label = { Text("Reuters") },
                modifier = Modifier.testTag("sample_reuters_chip")
            )
        }
    }
}

@Composable
private fun QuickFeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReaderLoadingCard(url: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text(
                    text = "Extracting ad-free article...",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReaderErrorCard(
    message: String,
    attemptedUrl: String,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Could not load Reader Mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_article_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
                if (attemptedUrl.startsWith("http")) {
                    OutlinedButton(onClick = onOpenBrowser) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Browser")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderActionToolbar(
    isBookmarked: Boolean,
    cardBg: Color,
    inkColor: Color,
    onCopyAll: () -> Unit,
    onCopyTitle: () -> Unit,
    onCopyCleanUrl: () -> Unit,
    onShareText: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTypography: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onCopyAll,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("copy_all_button"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.action_copy_all))
        }

        AssistChip(
            onClick = onCopyTitle,
            label = { Text(stringResource(R.string.action_copy_headline), color = inkColor) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = cardBg),
            modifier = Modifier.testTag("copy_title_button")
        )

        AssistChip(
            onClick = onCopyCleanUrl,
            label = { Text(stringResource(R.string.action_copy_clean_url), color = inkColor) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = cardBg),
            modifier = Modifier.testTag("copy_link_button")
        )

        AssistChip(
            onClick = onShareText,
            label = { Text(stringResource(R.string.action_share_clean), color = inkColor) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = cardBg),
            modifier = Modifier.testTag("share_article_button")
        )

        AssistChip(
            onClick = onToggleBookmark,
            label = {
                Text(
                    text = if (isBookmarked) {
                        stringResource(R.string.action_bookmarked)
                    } else {
                        stringResource(R.string.action_bookmark)
                    },
                    color = inkColor
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    tint = if (isBookmarked) MaterialTheme.colorScheme.secondary else inkColor,
                    modifier = Modifier.size(17.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = cardBg),
            modifier = Modifier.testTag("bookmark_button")
        )

        AssistChip(
            onClick = onOpenTypography,
            label = { Text(stringResource(R.string.action_reader_settings), color = inkColor) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.FormatSize,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(17.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = cardBg),
            modifier = Modifier.testTag("reader_settings_button")
        )

        AssistChip(
            onClick = onRefresh,
            label = { Text("Reload", color = inkColor) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = cardBg),
            modifier = Modifier.testTag("reload_article_button")
        )
    }
}

@Composable
private fun AiQuickBriefCard(
    briefState: BriefUiState,
    cardBg: Color,
    inkColor: Color,
    onSelectMode: (BriefMode) -> Unit,
    onCopyBrief: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Instant AI Brief",
                        style = MaterialTheme.typography.titleMedium,
                        color = inkColor
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Flash-Lite",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (briefState !is BriefUiState.Idle) {
                    TextButton(onClick = onDismiss) {
                        Text("Hide")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BriefMode.entries.forEach { mode ->
                    val isSelected = when (briefState) {
                        is BriefUiState.Loading -> briefState.mode == mode
                        is BriefUiState.Streaming -> briefState.mode == mode
                        is BriefUiState.Success -> briefState.mode == mode
                        else -> false
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("brief_mode_${mode.name.lowercase()}")
                    )
                }
            }

            AnimatedVisibility(
                visible = briefState !is BriefUiState.Idle,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                when (briefState) {
                    BriefUiState.Idle -> Unit
                    is BriefUiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Generating ${briefState.mode.label}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = inkColor.copy(alpha = 0.8f)
                            )
                        }
                    }

                    is BriefUiState.Streaming -> {
                        SelectionContainer {
                            Text(
                                text = briefState.text,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = inkColor
                            )
                        }
                    }

                    is BriefUiState.Success -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectionContainer {
                                Text(
                                    text = briefState.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    color = inkColor
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { onCopyBrief(briefState.text) },
                                    modifier = Modifier.testTag("copy_brief_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Brief")
                                }
                            }
                        }
                    }

                    is BriefUiState.Error -> {
                        Text(
                            text = briefState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheetContent(
    preferences: ReaderPreferences,
    onSetFontSize: (Float) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onSetUseSerif: (Boolean) -> Unit,
    onSetSurfaceMode: (ReaderSurfaceMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Reader Display",
            style = MaterialTheme.typography.titleLarge
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Paper Surface",
                style = MaterialTheme.typography.labelLarge
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReaderSurfaceMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = preferences.surfaceMode == mode,
                        onClick = { onSetSurfaceMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ReaderSurfaceMode.entries.size
                        ),
                        icon = {
                            if (preferences.surfaceMode == mode) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    ) {
                        Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Text Size", style = MaterialTheme.typography.labelLarge)
                Text("${preferences.fontSizeSp.roundToInt()} sp", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = preferences.fontSizeSp,
                onValueChange = onSetFontSize,
                valueRange = 14f..26f,
                steps = 5
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Line Spacing", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = String.format("%.1fx", preferences.lineSpacingMultiplier),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = preferences.lineSpacingMultiplier,
                onValueChange = onSetLineSpacing,
                valueRange = 1.3f..2.0f,
                steps = 6
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Editorial Serif Font", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = if (preferences.useSerifFont) "Newsreader Serif" else "Plus Jakarta Sans",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.useSerifFont,
                onCheckedChange = onSetUseSerif
            )
        }
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip: ClipData = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
