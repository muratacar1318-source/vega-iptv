package com.tivimax.player.ui.player.components

import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.decodeBase64IfNeeded
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.dpadFocusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

// ============================================================================
// 1. TIVIMATE OVERLAY GUIDE (SOL KENARA SIFIR 2 SÜTUNLU YARI SAYDAM REHBER)
// ============================================================================

@Composable
fun TiviOverlayGuide(
    visible: Boolean,
    currentStreamId: Int,
    viewModel: MainViewModel,
    onChannelSelected: (LiveStream) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(160), initialOffsetX = { -it }) + fadeIn(tween(160)),
        exit = slideOutHorizontally(tween(140), targetOffsetX = { -it }) + fadeOut(tween(140))
    ) {
        val categories by viewModel.visibleLiveCategories.collectAsState()
        val allLiveStreams by viewModel.liveStreams.collectAsState()
        val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
        val recentChannels by viewModel.recentChannels.collectAsState()
        val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()

        var selectedCategoryKey by remember { mutableStateOf("all") }
        var channelSearchQuery by remember { mutableStateOf("") }

        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val panelWidth = (screenWidthDp.dp * 0.92f).coerceAtMost(520.dp)
        val categoryColWidth = if (panelWidth < 400.dp) 144.dp else 186.dp

        // Seçili kategoriye göre kanal listesini hazırla
        val displayedChannels = remember(selectedCategoryKey, allLiveStreams, favoriteIds, recentChannels, channelSearchQuery) {
            val baseList = when (selectedCategoryKey) {
                "favorites" -> allLiveStreams.filter { favoriteIds.contains(it.streamId.toString()) }
                "recents" -> recentChannels
                "all" -> allLiveStreams
                else -> allLiveStreams.filter { it.categoryId == selectedCategoryKey }
            }
            if (channelSearchQuery.isBlank()) {
                baseList
            } else {
                baseList.filter { it.name.contains(channelSearchQuery, ignoreCase = true) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VegaColors.Scrim)
                .pointerInput(onClose) {
                    detectTapGestures { onClose() }
                } // Boşluğa dokununca kapat (D-pad odak tuzağı oluşturmaz)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .background(VegaColors.OverlayPanel)
                    .pointerInput(Unit) {
                        detectTapGestures { /* Tıklamayı arka plana geçirme */ }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                ) {
                    // SOL SÜTUN: İNCE KATEGORİ LİSTESİ
                    Column(
                        modifier = Modifier
                            .width(categoryColWidth)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.24f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rehber",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.BodyProminent,
                                fontWeight = FontWeight.SemiBold
                            )
                            var closeFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (closeFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f))
                                    .dpadFocusable(
                                        shape = CircleShape,
                                        drawFocusBorder = false,
                                        scale = 1.0f,
                                        onClick = onClose,
                                        onFocusStateChange = { closeFocused = it }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = if (closeFocused) VegaColors.OnAccent else VegaColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border)

                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                GuideCategoryItem(
                                    title = "Favoriler",
                                    icon = Icons.Default.Star,
                                    count = favoriteIds.size,
                                    isSelected = selectedCategoryKey == "favorites",
                                    iconTint = VegaColors.Gold,
                                    onClick = { selectedCategoryKey = "favorites" }
                                )
                            }
                            item {
                                GuideCategoryItem(
                                    title = "Son izlenenler",
                                    icon = Icons.Default.History,
                                    count = recentChannels.size,
                                    isSelected = selectedCategoryKey == "recents",
                                    iconTint = VegaColors.Accent,
                                    onClick = { selectedCategoryKey = "recents" }
                                )
                            }
                            item {
                                GuideCategoryItem(
                                    title = "Tüm kanallar",
                                    icon = Icons.Default.Tv,
                                    count = allLiveStreams.size,
                                    isSelected = selectedCategoryKey == "all",
                                    iconTint = VegaColors.Accent,
                                    onClick = { selectedCategoryKey = "all" }
                                )
                            }

                            item {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = VegaColors.Border
                                )
                            }

                            val dynamicCategories = categories.filter { it.categoryId != "all" }
                            itemsIndexed(dynamicCategories, key = { idx, cat -> "gcat_${cat.categoryId}_$idx" }) { _, cat ->
                                GuideCategoryItem(
                                    title = cat.categoryName,
                                    icon = null,
                                    count = cat.itemCount,
                                    isSelected = selectedCategoryKey == cat.categoryId,
                                    iconTint = VegaColors.TextSecondary,
                                    onClick = { selectedCategoryKey = cat.categoryId }
                                )
                            }
                        }
                    }

                    // Sütun ayırıcı 0.5dp çizgi
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(0.5.dp)
                            .background(VegaColors.Border)
                    )

                    // SAĞ SÜTUN: YOĞUN KANAL LİSTESİ
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Kanal Arama
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            OutlinedTextField(
                                keyboardOptions = tvKeyboardOptions(),
                                value = channelSearchQuery,
                                onValueChange = { channelSearchQuery = it },
                                placeholder = { Text("Kanal ara...", fontSize = VegaText.Caption, color = VegaColors.TextMuted) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VegaColors.Accent,
                                    unfocusedBorderColor = VegaColors.Border,
                                    focusedTextColor = VegaColors.TextPrimary,
                                    unfocusedTextColor = VegaColors.TextPrimary,
                                    focusedContainerColor = VegaColors.SurfaceVariant,
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = VegaShapes.Small,
                                modifier = Modifier
                                    .tvOpenKeyboardOnOk()
                                    .fillMaxWidth()
                                    .height(44.dp)
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border)

                        val listState = rememberLazyListState()
                        val activeChannelFocusRequester = remember { FocusRequester() }

                        // Aktif kanala otomatik kaydır ve TV kumandası için odaklan
                        LaunchedEffect(selectedCategoryKey, visible) {
                            if (visible) {
                                val idx = displayedChannels.indexOfFirst { it.streamId == currentStreamId }
                                if (idx >= 0) {
                                    listState.scrollToItem((idx - 2).coerceAtLeast(0))
                                }
                                delay(150)
                                try {
                                    activeChannelFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(displayedChannels, key = { idx, ch -> "gch_${ch.streamId}_$idx" }) { index, channel ->
                                val isPlayingThis = channel.streamId == currentStreamId
                                val epg = epgSummaryMap[channel.streamId]

                                LaunchedEffect(channel.streamId) {
                                    if (epg == null) {
                                        viewModel.loadEpgSummary(channel.streamId)
                                    }
                                }

                                GuideChannelItem(
                                    channel = channel,
                                    epg = epg,
                                    isPlaying = isPlayingThis,
                                    modifier = if (isPlayingThis) Modifier.focusRequester(activeChannelFocusRequester) else Modifier,
                                    onClick = {
                                        if (isPlayingThis) {
                                            onClose()
                                        } else {
                                            onChannelSelected(channel)
                                        }
                                    }
                                )
                                if (index < displayedChannels.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = VegaColors.Border.copy(alpha = 0.28f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Panel sağ kenarı 0.5dp jilet çizgi
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(VegaColors.Border)
                )
            }
        }
    }
}

@Composable
private fun GuideCategoryItem(
    title: String,
    icon: ImageVector?,
    count: Int,
    isSelected: Boolean,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                when {
                    isFocused -> VegaColors.Accent
                    isSelected -> VegaColors.Accent.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .dpadFocusable(
                shape = RectangleShape,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    when {
                        isSelected && isFocused -> VegaColors.OnAccent
                        isSelected -> VegaColors.Accent
                        else -> Color.Transparent
                    }
                )
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) VegaColors.OnAccent else iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = title,
                    color = when {
                        isFocused -> VegaColors.OnAccent
                        isSelected -> VegaColors.TextPrimary
                        else -> VegaColors.TextSecondary
                    },
                    fontSize = VegaText.Secondary,
                    fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count > 0) {
                Text(
                    text = "$count",
                    color = when {
                        isFocused -> VegaColors.OnAccent.copy(alpha = 0.85f)
                        isSelected -> VegaColors.Accent
                        else -> VegaColors.TextMuted
                    },
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
private fun GuideChannelItem(
    channel: LiveStream,
    epg: EpgProgram?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                when {
                    isFocused -> VegaColors.Accent
                    isPlaying -> VegaColors.Accent.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .dpadFocusable(
                shape = RectangleShape,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Aktif kanal 3dp sol vurgu çubuğu
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    when {
                        isPlaying && isFocused -> VegaColors.OnAccent
                        isPlaying -> VegaColors.Accent
                        else -> Color.Transparent
                    }
                )
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabular Kanal Numarası
            Text(
                text = if (channel.num > 0) "${channel.num}" else "•",
                color = when {
                    isFocused -> VegaColors.OnAccent
                    isPlaying -> VegaColors.Accent
                    else -> VegaColors.TextSecondary
                },
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.width(30.dp)
            )

            // Kare Logo Kutusu
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isFocused) VegaColors.OnAccent.copy(alpha = 0.14f)
                        else VegaColors.SurfaceElevated
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = if (isFocused) VegaColors.OnAccent else VegaColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Kanal Adı + EPG Alt Başlık + 2dp İlerleme Çubuğu
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = channel.name,
                    color = if (isFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                    fontSize = VegaText.Body,
                    fontWeight = if (isFocused || isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val programTitle = epg?.getDecodedTitle()
                    ?: channel.currentProgram.decodeBase64IfNeeded().trim()
                if (programTitle.isNotBlank()) {
                    Text(
                        text = programTitle,
                        color = if (isFocused) VegaColors.OnAccent.copy(alpha = 0.80f) else VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 2dp İnce EPG İlerleme Çubuğu
                if (epg != null && epg.startTimestamp > 0 && epg.stopTimestamp > epg.startTimestamp) {
                    val now = System.currentTimeMillis() / 1000L
                    val progress = ((now - epg.startTimestamp).toFloat() / (epg.stopTimestamp - epg.startTimestamp)).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (isFocused) VegaColors.OnAccent.copy(alpha = 0.24f)
                                else Color.White.copy(alpha = 0.14f)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(if (isFocused) VegaColors.OnAccent else VegaColors.Accent)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. TIVIMATE TARZI TAM GENİŞLİKLİ YAYIN ALT BİLGİ ŞERİDİ & TEKNİK METİN
// ============================================================================

@Composable
fun TiviBottomInfoCard(
    visible: Boolean,
    channel: LiveStream,
    epg: EpgProgram?,
    isFavorite: Boolean,
    diagnostics: StreamDiagnostics,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    upcomingEpg: List<EpgProgram> = emptyList(),
    onDismiss: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    var isExpanded by remember(channel.streamId) { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(130)),
        modifier = modifier
    ) {
        val cardDragModifier = Modifier.pointerInput(isExpanded) {
            detectVerticalDragGestures { change, dragAmount ->
                change.consume()
                if (dragAmount < -20f) {
                    isExpanded = true
                } else if (dragAmount > 20f) {
                    if (isExpanded) {
                        isExpanded = false
                    } else {
                        onDismiss?.invoke()
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(cardDragModifier)
        ) {
            // Jilet inceliğinde 0.5dp üst ayırıcı çizgi
            HorizontalDivider(
                thickness = 0.5.dp,
                color = VegaColors.Border
            )

            // Tam genişlikli sabit yayın paneli (yuvarlak baloncuk/gölge/dış çerçeve yok)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VegaColors.OverlayPanel)
                    .padding(
                        horizontal = if (isPortrait) 12.dp else 20.dp,
                        vertical = if (isPortrait) 10.dp else 12.dp
                    )
            ) {
                val currentTitle = epg?.getDecodedTitle() ?: channel.currentProgram.decodeBase64IfNeeded().trim()
                val timeRange = LiveTimeFormatter.formatTimeRange(epg)
                val progress = LiveTimeFormatter.calculateProgress(
                    epg,
                    if (channel.programProgress > 0f) channel.programProgress else null
                )

                val rawNext = channel.nextProgram ?: epg?.getDecodedDescription()
                val nextProg = rawNext?.decodeBase64IfNeeded()?.trim() ?: ""

                // Düz metin yayın teknik özeti (parlak renkli hap rozet yok)
                val hasRes = diagnostics.resolutionBadge.isNotBlank()
                val hasFps = diagnostics.frameRateBadge.isNotBlank()
                val qualitySummary = when {
                    hasRes && hasFps -> "${diagnostics.resolutionBadge} · ${diagnostics.frameRateBadge}"
                    hasRes -> diagnostics.resolutionBadge
                    hasFps -> diagnostics.frameRateBadge
                    else -> null
                }

                if (!isPortrait) {
                    // ====================================================================
                    // YATAY MOD: TAM GENİŞLİKLİ KOMPAKT YAYIN ŞERİDİ
                    // ====================================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (channel.num > 0) {
                                Text(
                                    text = "${channel.num}",
                                    color = VegaColors.Accent,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                )
                            }

                            // Kare Logo (36dp)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(VegaColors.SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!channel.streamIcon.isNullOrBlank()) {
                                    AsyncImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(3.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = VegaColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Kanal Adı + Program Adı + Muted Teknik Bilgi
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val titleText = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = VegaText.BodyProminent, color = VegaColors.TextPrimary)) {
                                            append(channel.name)
                                        }
                                        if (currentTitle.isNotBlank()) {
                                            withStyle(SpanStyle(color = VegaColors.TextMuted, fontSize = VegaText.Secondary)) {
                                                append(" · ")
                                            }
                                            withStyle(SpanStyle(fontWeight = FontWeight.Medium, fontSize = VegaText.Secondary, color = VegaColors.TextPrimary)) {
                                                append(currentTitle)
                                            }
                                        }
                                    }
                                    Text(
                                        text = titleText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (qualitySummary != null) {
                                        Text(
                                            text = qualitySummary,
                                            color = VegaColors.TextSecondary,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                        )
                                    }
                                }

                                if (timeRange.isNotBlank() || nextProg.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (timeRange.isNotBlank()) {
                                            Text(
                                                text = timeRange,
                                                color = VegaColors.TextSecondary,
                                                fontSize = VegaText.Caption,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                            )
                                        }
                                        if (nextProg.isNotBlank()) {
                                            Text(
                                                text = "Sonraki · $nextProg",
                                                color = VegaColors.TextMuted,
                                                fontSize = VegaText.Caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Aksiyon Butonları
                        if (actions != null) {
                            actions()
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Favori Butonu
                        BottomInfoIconAction(
                            icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorilere ekle veya çıkar",
                            tint = if (isFavorite) VegaColors.Gold else VegaColors.TextSecondary,
                            onClick = onToggleFavorite
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Genişlet Oku
                        BottomInfoIconAction(
                            icon = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Detayları kapat" else "Detayları göster",
                            tint = VegaColors.TextSecondary,
                            onClick = { isExpanded = !isExpanded }
                        )
                    }

                    // 3dp jilet keskinliğinde tam genişlikli ilerleme çubuğu
                    if (progress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(VegaColors.Accent)
                            )
                        }
                    }
                } else {
                    // ====================================================================
                    // DİKEY MOD: KOMPAKT YAYIN ŞERİDİ + AKSİYON SATIRI
                    // ====================================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (channel.num > 0) {
                                Text(
                                    text = "${channel.num}",
                                    color = VegaColors.Accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                )
                            }

                            // Kare Logo (34dp)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(VegaColors.SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!channel.streamIcon.isNullOrBlank()) {
                                    AsyncImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(3.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = VegaColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                val titleText = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = VegaText.BodyProminent, color = VegaColors.TextPrimary)) {
                                        append(channel.name)
                                    }
                                    if (currentTitle.isNotBlank()) {
                                        withStyle(SpanStyle(color = VegaColors.TextMuted, fontSize = VegaText.Secondary)) {
                                            append(" · ")
                                        }
                                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = VegaText.Secondary, color = VegaColors.TextPrimary)) {
                                            append(currentTitle)
                                        }
                                    }
                                }
                                Text(
                                    text = titleText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (timeRange.isNotBlank() || qualitySummary != null) {
                                    val metaLine = listOfNotNull(
                                        timeRange.takeIf { it.isNotBlank() },
                                        qualitySummary
                                    ).joinToString(" · ")
                                    Text(
                                        text = metaLine,
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }

                        BottomInfoIconAction(
                            icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorilere ekle veya çıkar",
                            tint = if (isFavorite) VegaColors.Gold else VegaColors.TextSecondary,
                            onClick = onToggleFavorite
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        BottomInfoIconAction(
                            icon = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Detayları kapat" else "Detayları göster",
                            tint = VegaColors.TextSecondary,
                            onClick = { isExpanded = !isExpanded }
                        )
                    }

                    // 3dp jilet keskinliğinde tam genişlikli ilerleme çubuğu
                    if (progress != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(VegaColors.Accent)
                            )
                        }
                    }

                    // 2. Satır: Aksiyon İkonları
                    if (actions != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        actions()
                    }
                }

                // ====================================================================
                // GENİŞLETİLMİŞ DETAY BÖLÜMÜ (Yukarı kaydırınca veya oka dokununca)
                // ====================================================================
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(tween(150)) + fadeIn(tween(150)),
                    exit = shrinkVertically(tween(120)) + fadeOut(tween(120))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = VegaColors.Border.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Saat Aralığı & Düz Metin Kalite Bilgisi
                        if (qualitySummary != null || timeRange.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (timeRange.isNotBlank()) {
                                    Text(
                                        text = timeRange,
                                        color = VegaColors.Accent,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.SemiBold,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                if (qualitySummary != null) {
                                    TechBadge(text = qualitySummary)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Program Açıklaması (en fazla 3 satır)
                        val desc = epg?.getDecodedDescription()
                        if (!desc.isNullOrBlank()) {
                            Text(
                                text = desc,
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                lineHeight = 16.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Sıradaki Program
                        if (nextProg.isNotBlank()) {
                            Text(
                                text = "Sonraki · $nextProg",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Sonraki Programlar (en fazla 2)
                        if (upcomingEpg.isNotEmpty()) {
                            Text(
                                text = "Sonraki programlar",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            upcomingEpg.take(2).forEach { prog ->
                                val progTime = LiveTimeFormatter.formatTimeRange(prog)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = progTime,
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Medium,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                        modifier = Modifier.width(86.dp)
                                    )
                                    Text(
                                        text = prog.getDecodedTitle(),
                                        color = VegaColors.TextPrimary,
                                        fontSize = VegaText.Caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomInfoIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f))
            .dpadFocusable(
                shape = CircleShape,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) VegaColors.OnAccent else tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun TechBadge(text: String, isHighlight: Boolean = false) {
    Text(
        text = text,
        color = if (isHighlight) VegaColors.TextPrimary else VegaColors.TextSecondary,
        fontSize = VegaText.Caption,
        fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Medium,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        softWrap = false,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    )
}

// ============================================================================
// 3. SABİT YATAY "SON İZLENEN KANALLAR" ŞERİDİ (TIVIMATE STİLİ)
// ============================================================================

@Composable
fun TiviRecentChannelsBar(
    visible: Boolean,
    recentChannels: List<LiveStream>,
    currentStreamId: Int,
    onChannelSelected: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && recentChannels.isNotEmpty(),
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VegaColors.OverlayPanel)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Son izlenenler (${recentChannels.size})",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Geçiş yapmak için seç",
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Caption
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(recentChannels, key = { idx, ch -> "rec_bar_${ch.streamId}_$idx" }) { _, ch ->
                        val isCurrent = ch.streamId == currentStreamId
                        var isCardFocused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .widthIn(min = 136.dp, max = 200.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isCardFocused -> VegaColors.Accent
                                        isCurrent -> VegaColors.Accent.copy(alpha = 0.14f)
                                        else -> Color.White.copy(alpha = 0.06f)
                                    }
                                )
                                .dpadFocusable(
                                    shape = RoundedCornerShape(6.dp),
                                    drawFocusBorder = false,
                                    scale = 1.0f,
                                    onClick = { onChannelSelected(ch) },
                                    onFocusStateChange = { isCardFocused = it }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(if (isCardFocused) VegaColors.OnAccent else VegaColors.Accent)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isCardFocused) VegaColors.OnAccent.copy(alpha = 0.14f)
                                            else VegaColors.SurfaceElevated
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!ch.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.streamIcon,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(3.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = if (isCardFocused) VegaColors.OnAccent else VegaColors.TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.name,
                                        color = when {
                                            isCardFocused -> VegaColors.OnAccent
                                            isCurrent -> VegaColors.TextPrimary
                                            else -> VegaColors.TextPrimary
                                        },
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (isCardFocused || isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (ch.num > 0) {
                                        Text(
                                            text = "${ch.num}",
                                            color = if (isCardFocused) VegaColors.OnAccent.copy(alpha = 0.80f) else VegaColors.TextMuted,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.Medium,
                                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border.copy(alpha = 0.5f))
        }
    }
}

// ============================================================================
// 4. HIZLI KANAL NUMARASIYLA GEÇİŞ (YAYIN TİPİ TABULAR ZAPPER & TUŞ TAKIMI)
// ============================================================================

@Composable
fun TiviNumericZapperBox(
    visible: Boolean,
    zapperNumber: String,
    matchedChannel: LiveStream?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && zapperNumber.isNotEmpty(),
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        Surface(
            shape = VegaShapes.Small,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(0.75.dp, VegaColors.Border),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier.widthIn(min = 160.dp, max = 260.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // 34sp Bold Beyaz Tabular Kanal Numarası
                    Text(
                        text = zapperNumber,
                        color = VegaColors.TextPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Eşleşen Kanal Logosu & Adı
                    if (matchedChannel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(VegaColors.SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!matchedChannel.streamIcon.isNullOrBlank()) {
                                    AsyncImage(
                                        model = matchedChannel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = VegaColors.TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = matchedChannel.name,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "Kanal aranıyor...",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(VegaColors.Accent)
                )
            }
        }
    }
}

@Composable
fun NumericKeypadDialog(
    onDigitClicked: (Int) -> Unit,
    onEnterClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstDigitFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(4) {
            delay(60)
            if (runCatching { firstDigitFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = VegaShapes.Medium,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(0.75.dp, VegaColors.Border),
            modifier = Modifier.width(260.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Kanal numarası",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                val digits = listOf(
                    listOf(1, 2, 3),
                    listOf(4, 5, 6),
                    listOf(7, 8, 9)
                )

                digits.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { d ->
                            KeypadButton(
                                text = "$d",
                                modifier = if (d == 1) Modifier.focusRequester(firstDigitFocus) else Modifier
                            ) { onDigitClicked(d) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    KeypadButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Kapat",
                        color = VegaColors.Danger
                    ) { onDismiss() }
                    KeypadButton(text = "0") { onDigitClicked(0) }
                    KeypadButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Onayla",
                        color = VegaColors.Accent
                    ) { onEnterClicked(); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String = "",
    icon: ImageVector? = null,
    contentDescription: String? = null,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(VegaShapes.Small)
            .background(if (isFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f))
            .dpadFocusable(
                shape = VegaShapes.Small,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) VegaColors.OnAccent else color,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                color = if (isFocused) VegaColors.OnAccent else color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
            )
        }
    }
}

// ============================================================================
// 5. KOMPAKT VE DUYARLI PLAYER HUD (KONTROL MERKEZİ & YAYIN BİLGİSİ)
// ============================================================================

@Composable
fun TiviPlayerHUD(
    visible: Boolean,
    initialTab: Int = 0, // 0: Kontroller (Ses/Parlaklık/Oran), 1: Yayın Teşhisi (Diagnostics)
    diagnostics: StreamDiagnostics,
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    currentVolume: Int,
    maxVolume: Int,
    onVolumeChange: (Int) -> Unit,
    aspectRatio: AspectRatioMode,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onClose: () -> Unit,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(visible, initialTab) { mutableIntStateOf(initialTab) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        Surface(
            modifier = if (isPortrait) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            } else {
                Modifier.width(360.dp)
            },
            shape = VegaShapes.Medium,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(0.75.dp, VegaColors.Border)
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // ÜST BAŞLIK & KAPATMA BUTONU
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.Tune else Icons.Default.Info,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = if (selectedTab == 0) "Kontrol merkezi" else "Yayın bilgisi",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.BodyProminent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    BottomInfoIconAction(
                        icon = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = VegaColors.TextSecondary,
                        onClick = onClose
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SEGMENTED TAB BUTONLARI (KONTROLLER & YAYIN BİLGİSİ)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VegaShapes.Small)
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 1: Ses ve ekran
                    var tab0Focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    tab0Focused -> VegaColors.Accent
                                    selectedTab == 0 -> VegaColors.Accent.copy(alpha = 0.18f)
                                    else -> Color.Transparent
                                }
                            )
                            .dpadFocusable(
                                shape = RoundedCornerShape(6.dp),
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = { selectedTab = 0 },
                                onFocusStateChange = { tab0Focused = it }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (tab0Focused) VegaColors.OnAccent else if (selectedTab == 0) VegaColors.Accent else VegaColors.TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Ses ve ekran",
                                color = if (tab0Focused) VegaColors.OnAccent else if (selectedTab == 0) VegaColors.TextPrimary else VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                fontWeight = if (selectedTab == 0 || tab0Focused) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }

                    // Tab 2: Yayın bilgisi
                    var tab1Focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    tab1Focused -> VegaColors.Accent
                                    selectedTab == 1 -> VegaColors.Accent.copy(alpha = 0.18f)
                                    else -> Color.Transparent
                                }
                            )
                            .dpadFocusable(
                                shape = RoundedCornerShape(6.dp),
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = { selectedTab = 1 },
                                onFocusStateChange = { tab1Focused = it }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (tab1Focused) VegaColors.OnAccent else if (selectedTab == 1) VegaColors.Accent else VegaColors.TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Yayın bilgisi",
                                color = if (tab1Focused) VegaColors.OnAccent else if (selectedTab == 1) VegaColors.TextPrimary else VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                fontWeight = if (selectedTab == 1 || tab1Focused) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SEKME İÇERİKLERİ
                if (selectedTab == 0) {
                    val brightnessPct = (currentBrightness * 100).toInt().coerceIn(1, 100)
                    val volumePct = if (maxVolume > 0) ((currentVolume.toFloat() / maxVolume) * 100).toInt() else 0

                    // 1. EKRAN PARLAKLIĞI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BrightnessMedium,
                                contentDescription = null,
                                tint = VegaColors.Gold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Parlaklık",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "%$brightnessPct",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                        )
                    }

                    Slider(
                        value = currentBrightness.coerceIn(0.01f, 1f),
                        onValueChange = onBrightnessChange,
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = VegaColors.Gold,
                            activeTrackColor = VegaColors.Gold,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.25f to "%25", 0.50f to "%50", 0.75f to "%75", 1.0f to "%100").forEach { (valF, lbl) ->
                            val isSelected = kotlin.math.abs(currentBrightness - valF) < 0.08f
                            Surface(
                                onClick = { onBrightnessChange(valF) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) VegaColors.Gold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = lbl,
                                        color = if (isSelected) VegaColors.Gold else VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. MEDYA SESİ
                    val volIcon = when {
                        currentVolume == 0 -> Icons.AutoMirrored.Filled.VolumeMute
                        volumePct < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = volIcon,
                                contentDescription = null,
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ses seviyesi",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "%$volumePct",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                        )
                    }

                    Slider(
                        value = currentVolume.toFloat(),
                        onValueChange = { onVolumeChange(it.toInt().coerceIn(0, maxVolume)) },
                        valueRange = 0f..maxVolume.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = VegaColors.Accent,
                            activeTrackColor = VegaColors.Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                if (currentVolume > 0) {
                                    onVolumeChange(0)
                                } else {
                                    onVolumeChange((maxVolume / 2).coerceAtLeast(1))
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentVolume == 0) VegaColors.Danger.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (currentVolume == 0) "Sesi aç" else "Sessiz",
                                    color = if (currentVolume == 0) VegaColors.Danger else VegaColors.TextPrimary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        listOf(0.30f to "%30", 0.60f to "%60", 1.0f to "%100").forEach { (f, lbl) ->
                            val targetVol = (maxVolume * f).toInt()
                            val isSelected = kotlin.math.abs(currentVolume - targetVol) <= 1 && currentVolume > 0
                            Surface(
                                onClick = { onVolumeChange(targetVol) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) VegaColors.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = lbl,
                                        color = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. EN-BOY ORANI HIZLI SEÇİCİ
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("En-boy oranı", color = VegaColors.TextPrimary, fontSize = VegaText.Secondary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AspectRatioMode.FIT to "Orijinal",
                            AspectRatioMode.FILL to "Doldur",
                            AspectRatioMode.ZOOM to "Yakınlaştır",
                            AspectRatioMode.RATIO_16_9 to "16:9"
                        ).forEach { (mode, label) ->
                            val isCurrent = aspectRatio == mode
                            var isModeFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isModeFocused -> VegaColors.Accent
                                            isCurrent -> VegaColors.Accent.copy(alpha = 0.20f)
                                            else -> Color.White.copy(alpha = 0.06f)
                                        }
                                    )
                                    .dpadFocusable(
                                        shape = RoundedCornerShape(6.dp),
                                        drawFocusBorder = false,
                                        scale = 1.0f,
                                        onClick = { onAspectRatioChange(mode) },
                                        onFocusStateChange = { isModeFocused = it }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isModeFocused) VegaColors.OnAccent else if (isCurrent) VegaColors.Accent else VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = if (isCurrent || isModeFocused) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    // TAB 1: YAYIN BİLGİSİ & AKIŞ DETAYLARI
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticMetricCard(
                                title = "Çözünürlük",
                                value = diagnostics.resolution,
                                badge = diagnostics.resolutionBadge,
                                badgeColor = VegaColors.TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricCard(
                                title = "Kare hızı",
                                value = diagnostics.frameRate,
                                badge = diagnostics.frameRateBadge,
                                badgeColor = VegaColors.TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticMetricCard(
                                title = "Video biçimi",
                                value = diagnostics.videoCodec,
                                badge = diagnostics.videoCodecBadge,
                                badgeColor = VegaColors.TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricCard(
                                title = "Ses biçimi",
                                value = diagnostics.audioCodec,
                                badge = diagnostics.audioCodecBadge,
                                badgeColor = VegaColors.TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticMetricCard(
                                title = "Veri hızı",
                                value = diagnostics.bitrate,
                                badge = "Ağ",
                                badgeColor = VegaColors.TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricCard(
                                title = "Önbellek durumu",
                                value = diagnostics.bufferHealthSecs,
                                badge = "Canlı",
                                badgeColor = VegaColors.TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val bufferSecs = diagnostics.bufferHealthSecs.replace("s", "").trim().toFloatOrNull() ?: 3f
                        val bufferProgress = (bufferSecs / 10f).coerceIn(0.05f, 1f)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Önbellek doluluk oranı", color = VegaColors.TextSecondary, fontSize = VegaText.Caption)
                                val bufferText = if (diagnostics.bufferHealthSecs.isNotBlank()) "${diagnostics.bufferHealthSecs} / 10 sn" else "—"
                                Text(
                                    text = bufferText,
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.SemiBold,
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bufferProgress)
                                        .fillMaxHeight()
                                        .background(if (bufferSecs >= 3f) VegaColors.Accent else VegaColors.Gold)
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
private fun DiagnosticMetricCard(
    title: String,
    value: String,
    badge: String,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = VegaColors.TextMuted, fontSize = VegaText.Caption)
            if (badge.isNotBlank()) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value.ifBlank { "—" },
            color = VegaColors.TextPrimary,
            fontSize = VegaText.Secondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
        )
    }
}

// ============================================================================
// 6. HIZLI, ANLIK TEPKİLİ TIVIMATE AKSİYON DÜĞMELERİ (SIFIR GECİKMELİ GEÇİŞ)
// ============================================================================

val LocalPlayerInteraction = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun PlayerPillButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = VegaColors.Accent,
    isPortrait: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val registerInteraction = LocalPlayerInteraction.current
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isActive -> activeColor.copy(alpha = 0.18f)
        isPressed -> Color.White.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val contentColor = when {
        isFocused -> VegaColors.OnAccent
        isActive -> activeColor
        else -> VegaColors.TextPrimary
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            registerInteraction()
            onClick()
        },
        interactionSource = interactionSource,
        shape = VegaShapes.Pill,
        color = containerColor,
        modifier = modifier
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    registerInteraction()
                }
            }
            .height(if (isPortrait) 32.dp else 36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isPortrait) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = if (isPortrait) VegaText.Caption else VegaText.Secondary,
                fontWeight = if (isFocused || isActive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = Color.White,
    pressedScale: Float = 0.85f,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val registerInteraction = LocalPlayerInteraction.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    val resolvedBg = when {
        isFocused -> VegaColors.Accent
        isPressed -> if (backgroundColor == Color.Transparent) {
            Color.White.copy(alpha = 0.18f)
        } else {
            backgroundColor.copy(alpha = (backgroundColor.alpha + 0.18f).coerceAtMost(1f))
        }
        else -> backgroundColor
    }

    Box(
        modifier = modifier
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    registerInteraction()
                }
            }
            .clip(shape)
            .background(resolvedBg)
            .then(
                if (!isFocused && border != null) Modifier.border(border, shape) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = contentColor.copy(alpha = 0.30f)),
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    registerInteraction()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (isFocused) VegaColors.OnAccent else contentColor) {
            content()
        }
    }
}


