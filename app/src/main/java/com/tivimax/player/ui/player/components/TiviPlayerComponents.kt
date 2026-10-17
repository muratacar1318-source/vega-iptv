package com.tivimax.player.ui.player.components

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.decodeBase64IfNeeded
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

// ============================================================================
// 1. TIVIMATE OVERLAY GUIDE (CANLI YAYIN KESİLMEDEN YARI SAYDAM REHBER KATMANI)
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
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    ) {
        val categories by viewModel.visibleLiveCategories.collectAsState()
        val allLiveStreams by viewModel.liveStreams.collectAsState()
        val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
        val recentChannels by viewModel.recentChannels.collectAsState()
        val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()

        var selectedCategoryKey by remember { mutableStateOf("all") }
        var channelSearchQuery by remember { mutableStateOf("") }

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
                .clickable(onClick = onClose) // Boşluğa dokununca kapat
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(520.dp)
                    .clickable(enabled = false) {} // Tıklamayı arka plana geçirme
                    .border(
                        1.dp,
                        VegaColors.Border,
                        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    ),
                color = VegaColors.OverlayPanel,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                ) {
                    // SOL SÜTUN: KATEGORİLER (200.dp)
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .background(VegaColors.Background.copy(alpha = 0.6f))
                            .border(width = 0.5.dp, color = VegaColors.Border)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rehber",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = VegaColors.TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Sabit Hızlı Kategoriler: Favoriler, Son İzlenenler, Tüm Kanallar
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
                                    title = "Son İzlenenler",
                                    icon = Icons.Default.History,
                                    count = recentChannels.size,
                                    isSelected = selectedCategoryKey == "recents",
                                    iconTint = VegaColors.Accent,
                                    onClick = { selectedCategoryKey = "recents" }
                                )
                            }
                            item {
                                GuideCategoryItem(
                                    title = "Tüm Kanallar",
                                    icon = Icons.Default.Tv,
                                    count = allLiveStreams.size,
                                    isSelected = selectedCategoryKey == "all",
                                    iconTint = VegaColors.Accent,
                                    onClick = { selectedCategoryKey = "all" }
                                )
                            }

                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = VegaColors.Border
                                )
                            }

                            // Dinamik Kategoriler
                            items(categories.filter { it.categoryId != "all" }, key = { it.categoryId }) { cat ->
                                GuideCategoryItem(
                                    title = cat.categoryName,
                                    icon = null,
                                    count = cat.itemCount,
                                    isSelected = selectedCategoryKey == cat.categoryId,
                                    iconTint = VegaColors.TextPrimary,
                                    onClick = { selectedCategoryKey = cat.categoryId }
                                )
                            }
                        }
                    }

                    // SAĞ SÜTUN: KANALLAR (320.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(10.dp)
                    ) {
                        // Kanal Arama
                        OutlinedTextField(
                            value = channelSearchQuery,
                            onValueChange = { channelSearchQuery = it },
                            placeholder = { Text("Kanal ara...", fontSize = VegaText.Caption, color = VegaColors.TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VegaColors.Accent,
                                unfocusedBorderColor = VegaColors.Border,
                                focusedTextColor = VegaColors.TextPrimary,
                                unfocusedTextColor = VegaColors.TextPrimary,
                                focusedContainerColor = VegaColors.SurfaceVariant,
                                unfocusedContainerColor = VegaColors.SurfaceVariant
                            ),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                                } catch (e: Exception) { }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(displayedChannels, key = { it.streamId }) { channel ->
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
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideCategoryItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    count: Int,
    isSelected: Boolean,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Small,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .clip(VegaShapes.Small)
            .background(
                if (isFocused) VegaColors.Accent.copy(alpha = 0.25f)
                else if (isSelected) VegaColors.Accent.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isFocused) VegaColors.Accent else if (isSelected) VegaColors.Accent.copy(alpha = 0.6f) else Color.Transparent,
                shape = VegaShapes.Small
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = if (isFocused) VegaColors.Accent else iconTint, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = title,
                    color = if (isFocused || isSelected) VegaColors.TextPrimary else VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count > 0) {
                Text(
                    text = "$count",
                    color = if (isFocused || isSelected) VegaColors.Accent else VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .clip(VegaShapes.Medium)
            .background(
                if (isFocused) VegaColors.Accent.copy(alpha = 0.25f)
                else if (isPlaying) VegaColors.Accent.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else if (isPlaying) 1.dp else 0.5.dp,
                color = if (isFocused) VegaColors.Accent else if (isPlaying) VegaColors.Accent.copy(alpha = 0.8f) else VegaColors.Border,
                shape = VegaShapes.Medium
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kanal Numarası
            Text(
                text = if (channel.num > 0) "${channel.num}" else "•",
                color = if (isFocused || isPlaying) VegaColors.Accent else VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.width(28.dp)
            )

            // Logo
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(VegaShapes.Small)
                    .background(VegaColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = VegaColors.TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Kanal Adı & EPG
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.Body,
                    fontWeight = if (isFocused || isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram.decodeBase64IfNeeded().ifBlank { "Yayın Akışı Mevcut Değil" }
                Text(
                    text = programTitle,
                    color = if (isFocused || isPlaying) VegaColors.Accent.copy(alpha = 0.9f) else VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // EPG İlerleme Çubuğu
                if (epg != null && epg.startTimestamp > 0 && epg.stopTimestamp > epg.startTimestamp) {
                    val now = System.currentTimeMillis() / 1000L
                    val progress = ((now - epg.startTimestamp).toFloat() / (epg.stopTimestamp - epg.startTimestamp)).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (isFocused || isPlaying) VegaColors.Accent else VegaColors.Accent.copy(alpha = 0.7f),
                        trackColor = VegaColors.SurfaceVariant
                    )
                }
            }

            if (isPlaying) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VegaColors.Accent)
                )
            }
        }
    }
}

// ============================================================================
// 2. TIVIMATE TARZI ALT BİLGİ PANELİ (BOTTOM CHANNEL INFO OSD) & TEKNİK ROZETLER
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
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
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

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .then(if (!isPortrait) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth())
                    .padding(
                        horizontal = if (isPortrait) 8.dp else 16.dp,
                        vertical = if (isPortrait) 4.dp else 8.dp
                    )
                    .then(cardDragModifier),
                shape = VegaShapes.Large,
                color = VegaColors.OverlayPanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Sahte "Canlı Yayın" kaldırıldı; yoksa boş string
                    val currentTitle = epg?.getDecodedTitle() ?: channel.currentProgram.decodeBase64IfNeeded().trim()
                    val timeRange = LiveTimeFormatter.formatTimeRange(epg)
                    // Sahte 0.45 kaldırıldı; EPG yoksa null
                    val progress = LiveTimeFormatter.calculateProgress(epg, if (channel.programProgress > 0f) channel.programProgress else null)

                    val rawNext = channel.nextProgram ?: epg?.getDecodedDescription()
                    val nextProg = rawNext?.decodeBase64IfNeeded()?.trim() ?: ""

                    // Gerçek kalite rozeti kontrolü (uydurma FHD 1080p / 50 FPS yok)
                    val hasRes = diagnostics.resolutionBadge.isNotBlank()
                    val hasFps = diagnostics.frameRateBadge.isNotBlank()
                    val qualityBadge = when {
                        hasRes && hasFps -> "${diagnostics.resolutionBadge} • ${diagnostics.frameRateBadge}"
                        hasRes -> diagnostics.resolutionBadge
                        hasFps -> diagnostics.frameRateBadge
                        else -> null
                    }

                    if (!isPortrait) {
                        // ====================================================================
                        // YATAY MOD: TEK SATIR (Hedef yükseklik <= 72dp)
                        // [kanal no] [logo 32dp] [kanal adı 15sp · program adı 13sp] [saat] [aksiyonlar] [favori] [genişlet]
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
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Logo (32dp)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.SurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!channel.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = channel.streamIcon,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(2.dp)
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

                                // Kanal Adı 15sp bold · Program Adı 13sp (tek satır, ellipsis)
                                val titleText = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = VegaText.BodyProminent, color = VegaColors.TextPrimary)) {
                                        append(channel.name)
                                    }
                                    if (currentTitle.isNotBlank()) {
                                        withStyle(SpanStyle(color = VegaColors.TextSecondary, fontSize = VegaText.Secondary)) {
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
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (timeRange.isNotBlank()) {
                                    Text(
                                        text = timeRange,
                                        color = VegaColors.Accent.copy(alpha = 0.9f),
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Aksiyon Butonları (Yatayda tek satırın sağına eklenir)
                            if (actions != null) {
                                actions()
                                Spacer(modifier = Modifier.width(2.dp))
                            }

                            // Favori Butonu (40dp)
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorilere ekle veya çıkar",
                                    tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Genişlet Oku (40dp)
                            IconButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isExpanded) "Detayları kapat" else "Detayları göster",
                                    tint = VegaColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // SADECE gerçek EPG varsa 2dp ince ilerleme çubuğu
                        if (progress != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = VegaColors.Accent,
                                trackColor = VegaColors.Border
                            )
                        }
                    } else {
                        // ====================================================================
                        // DİKEY MOD: İKİ SATIR (Hedef yükseklik <= 104dp)
                        // 1. satır: kanal no, logo 32dp, kanal adı + program adı, favori, genişlet
                        // 2. satır: aksiyon ikonları eşit aralıklı
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
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Logo (32dp)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.SurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!channel.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = channel.streamIcon,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(2.dp)
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

                                // Kanal Adı 15sp bold · Program Adı 13sp (tek satır, ellipsis)
                                val titleText = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = VegaText.BodyProminent, color = VegaColors.TextPrimary)) {
                                        append(channel.name)
                                    }
                                    if (currentTitle.isNotBlank()) {
                                        withStyle(SpanStyle(color = VegaColors.TextSecondary, fontSize = VegaText.Secondary)) {
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
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            // Favori Butonu (40dp)
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorilere ekle veya çıkar",
                                    tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Genişlet Oku (40dp)
                            IconButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isExpanded) "Detayları kapat" else "Detayları göster",
                                    tint = VegaColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // SADECE gerçek EPG varsa 2dp ince ilerleme çubuğu
                        if (progress != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = VegaColors.Accent,
                                trackColor = VegaColors.Border
                            )
                        }

                        // 2. Satır: Aksiyon İkonları Eşit Aralıklı
                        if (actions != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            actions()
                        }
                    }

                    // ====================================================================
                    // GENİŞLETİLMİŞ DETAY BÖLÜMÜ (Yukarı kaydırınca veya oka dokununca)
                    // ====================================================================
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            // Kalite rozeti (yalnızca gerçek değer varsa) & Saat Aralığı
                            if (qualityBadge != null || timeRange.isNotBlank()) {
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
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    if (qualityBadge != null) {
                                        Surface(
                                            shape = VegaShapes.Small,
                                            color = VegaColors.SurfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(0.6.dp, VegaColors.Border)
                                        ) {
                                            Text(
                                                text = qualityBadge,
                                                color = VegaColors.TextSecondary,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
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

                            // Sıradaki Program (varsayılandan kaldırıldı, sadece genişletilmiş bölümde)
                            if (nextProg.isNotBlank()) {
                                Text(
                                    text = "Sıradaki: $nextProg",
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
                                    color = VegaColors.Accent,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                upcomingEpg.take(2).forEach { prog ->
                                    val progTime = LiveTimeFormatter.formatTimeRange(prog)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = progTime,
                                            color = VegaColors.TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.width(82.dp)
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
}

@Composable
fun TechBadge(text: String, isHighlight: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(VegaShapes.Small)
            .background(if (isHighlight) VegaColors.Accent.copy(alpha = 0.2f) else VegaColors.SurfaceVariant)
            .border(
                0.8.dp,
                if (isHighlight) VegaColors.Accent else VegaColors.Border,
                VegaShapes.Small
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = if (isHighlight) VegaColors.Accent else VegaColors.TextPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ============================================================================
// 3. ALTTAN AÇILAN "SON İZLENEN KANALLAR" ŞERİDİ & ÖNCEKİ KANAL (RECALL)
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
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = VegaShapes.Large,
            color = VegaColors.OverlayPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Son İzlenen Kanallar (${recentChannels.size})",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Geçiş yapmak için dokun",
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Caption
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentChannels, key = { it.streamId }) { ch ->
                        val isCurrent = ch.streamId == currentStreamId
                        Surface(
                            onClick = { onChannelSelected(ch) },
                            shape = VegaShapes.Medium,
                            color = if (isCurrent) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrent) VegaColors.Accent else VegaColors.Border
                            ),
                            modifier = Modifier.width(135.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.Background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!ch.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.streamIcon,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                        )
                                    } else {
                                        Icon(Icons.Default.Tv, contentDescription = null, tint = VegaColors.TextMuted, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = ch.name,
                                    color = if (isCurrent) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (ch.num > 0) {
                                    Text(
                                        text = "Kanal ${ch.num}",
                                        color = VegaColors.TextSecondary,
                                        fontSize = 9.5.sp
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

// ============================================================================
// 4. HIZLI KANAL NUMARASIYLA GEÇİŞ (NUMERIC ZAPPER KUTUSU & TUŞ TAKIMI)
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
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.OverlayPanel,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, VegaColors.Accent),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rakam Kutusu: [ 0 2 4 ]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val padded = zapperNumber.padStart(3, '0')
                    padded.forEach { char ->
                        Box(
                            modifier = Modifier
                                .size(36.dp, 44.dp)
                                .clip(VegaShapes.Small)
                                .background(VegaColors.SurfaceVariant)
                                .border(1.dp, VegaColors.Accent.copy(alpha = 0.7f), VegaShapes.Small),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$char",
                                color = VegaColors.Accent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Önizleme Kanal Adı & Logo
                if (matchedChannel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!matchedChannel.streamIcon.isNullOrBlank()) {
                            AsyncImage(
                                model = matchedChannel.streamIcon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                        Text(
                            text = matchedChannel.name,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Bold,
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

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(100.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = VegaColors.Accent,
                    trackColor = VegaColors.Border
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
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier.width(260.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Kanal numarası gir",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.Bold
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
                            KeypadButton(text = "$d") { onDigitClicked(d) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    KeypadButton(text = "✕", color = VegaColors.Danger) { onDismiss() }
                    KeypadButton(text = "0") { onDigitClicked(0) }
                    KeypadButton(text = "✓", color = VegaColors.Accent) { onEnterClicked(); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = VegaShapes.Medium,
        color = VegaColors.SurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================================
// 5. MODERN, KOMPAKT VE DUYARLI PLAYER HUD (KONTROL MERKEZİ & YAYIN TEŞHİS)
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
        enter = fadeIn(androidx.compose.animation.core.tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(180)),
        exit = fadeOut(androidx.compose.animation.core.tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(140)),
        modifier = modifier
    ) {
        Surface(
            modifier = if (isPortrait) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            } else {
                Modifier.width(360.dp)
            },
            shape = VegaShapes.Large,
            color = VegaColors.OverlayPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
            shadowElevation = 16.dp
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.Tune else Icons.Default.Info,
                                contentDescription = null,
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedTab == 0) "Kontrol merkezi" else "Yayın teşhisi (HUD)",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = VegaColors.TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SEGMENTED TAB BUTONLARI (KONTROLLER & YAYIN BİLGİSİ)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VegaShapes.Small)
                        .background(VegaColors.Background)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 1: Ses ve Ekran
                    Surface(
                        onClick = { selectedTab = 0 },
                        shape = VegaShapes.Small,
                        color = if (selectedTab == 0) VegaColors.Accent.copy(alpha = 0.22f) else Color.Transparent,
                        border = if (selectedTab == 0) androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.6f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) VegaColors.Accent else VegaColors.TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Ses ve ekran",
                                    color = if (selectedTab == 0) VegaColors.Accent else VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Tab 2: Yayın Teşhis
                    Surface(
                        onClick = { selectedTab = 1 },
                        shape = VegaShapes.Small,
                        color = if (selectedTab == 1) VegaColors.Accent.copy(alpha = 0.22f) else Color.Transparent,
                        border = if (selectedTab == 1) androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.6f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) VegaColors.Accent else VegaColors.TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Yayın teşhis",
                                    color = if (selectedTab == 1) VegaColors.Accent else VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SEKME İÇERİKLERİ
                if (selectedTab == 0) {
                    // TAB 0: SES & PARLAKLIK & EN-BOY KONTROLLERİ
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
                            Text(text = "Parlaklık", color = VegaColors.TextPrimary, fontSize = VegaText.Secondary, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Gold.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "%$brightnessPct", color = VegaColors.Gold, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                        }
                    }

                    Slider(
                        value = currentBrightness.coerceIn(0.01f, 1f),
                        onValueChange = onBrightnessChange,
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = VegaColors.Gold,
                            activeTrackColor = VegaColors.Gold,
                            inactiveTrackColor = VegaColors.Border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )

                    // Hızlı Parlaklık Önayarları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.25f to "%25", 0.50f to "%50", 0.75f to "%75", 1.0f to "%100").forEach { (valF, lbl) ->
                            val isSelected = kotlin.math.abs(currentBrightness - valF) < 0.08f
                            Surface(
                                onClick = { onBrightnessChange(valF) },
                                shape = VegaShapes.Small,
                                color = if (isSelected) VegaColors.Gold.copy(alpha = 0.25f) else VegaColors.SurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isSelected) VegaColors.Gold else VegaColors.Border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = lbl,
                                        color = if (isSelected) VegaColors.Gold else VegaColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                            Text(text = "Ses seviyesi", color = VegaColors.TextPrimary, fontSize = VegaText.Secondary, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Accent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "%$volumePct", color = VegaColors.Accent, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                        }
                    }

                    Slider(
                        value = currentVolume.toFloat(),
                        onValueChange = { onVolumeChange(it.toInt().coerceIn(0, maxVolume)) },
                        valueRange = 0f..maxVolume.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = VegaColors.Accent,
                            activeTrackColor = VegaColors.Accent,
                            inactiveTrackColor = VegaColors.Border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )

                    // Hızlı Ses Önayarları & Mute Butonu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sessiz Toggle
                        Surface(
                            onClick = {
                                if (currentVolume > 0) {
                                    onVolumeChange(0)
                                } else {
                                    onVolumeChange((maxVolume / 2).coerceAtLeast(1))
                                }
                            },
                            shape = VegaShapes.Small,
                            color = if (currentVolume == 0) VegaColors.Danger.copy(alpha = 0.25f) else VegaColors.SurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, if (currentVolume == 0) VegaColors.Danger else VegaColors.Border),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (currentVolume == 0) "Sesi aç" else "Sessiz",
                                    color = if (currentVolume == 0) VegaColors.Danger else VegaColors.TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        listOf(0.30f to "%30", 0.60f to "%60", 1.0f to "%100").forEach { (f, lbl) ->
                            val targetVol = (maxVolume * f).toInt()
                            val isSelected = kotlin.math.abs(currentVolume - targetVol) <= 1 && currentVolume > 0
                            Surface(
                                onClick = { onVolumeChange(targetVol) },
                                shape = VegaShapes.Small,
                                color = if (isSelected) VegaColors.Accent.copy(alpha = 0.25f) else VegaColors.SurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isSelected) VegaColors.Accent else VegaColors.Border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = lbl,
                                        color = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. EN-BOY ORANI (ASPECT RATIO) HIZLI SEÇİCİ
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
                            AspectRatioMode.FIT to "FIT",
                            AspectRatioMode.FILL to "Doldur",
                            AspectRatioMode.ZOOM to "Zoom",
                            AspectRatioMode.RATIO_16_9 to "16:9"
                        ).forEach { (mode, label) ->
                            val isCurrent = aspectRatio == mode
                            Surface(
                                onClick = { onAspectRatioChange(mode) },
                                shape = VegaShapes.Small,
                                color = if (isCurrent) VegaColors.Accent.copy(alpha = 0.25f) else VegaColors.SurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) VegaColors.Accent else VegaColors.Border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isCurrent) VegaColors.Accent else VegaColors.TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: YAYIN TEŞHİS & AKIŞ DETAYLARI
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 2x3 Grid Teknik Metrik Kartları
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticMetricCard(
                                title = "Çözünürlük",
                                value = diagnostics.resolution,
                                badge = diagnostics.resolutionBadge,
                                badgeColor = VegaColors.Accent,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricCard(
                                title = "Kare hızı",
                                value = diagnostics.frameRate,
                                badge = diagnostics.frameRateBadge,
                                badgeColor = VegaColors.Accent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticMetricCard(
                                title = "Video kodek",
                                value = diagnostics.videoCodec,
                                badge = diagnostics.videoCodecBadge,
                                badgeColor = VegaColors.Accent,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricCard(
                                title = "Ses formatı",
                                value = diagnostics.audioCodec,
                                badge = diagnostics.audioCodecBadge,
                                badgeColor = VegaColors.Accent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticMetricCard(
                                title = "Anlık bitrate",
                                value = diagnostics.bitrate,
                                badge = "AĞ",
                                badgeColor = VegaColors.Gold,
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricCard(
                                title = "Tampon sağlığı",
                                value = diagnostics.bufferHealthSecs,
                                badge = "CANLI",
                                badgeColor = VegaColors.Accent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Canlı Tampon Doluluk Göstergesi
                        val bufferSecs = diagnostics.bufferHealthSecs.replace("s", "").trim().toFloatOrNull() ?: 3f
                        val bufferProgress = (bufferSecs / 10f).coerceIn(0.05f, 1f)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(VegaShapes.Small)
                                .background(VegaColors.SurfaceVariant)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Tampon doluluk oranı", color = VegaColors.TextSecondary, fontSize = 10.5.sp)
                                val bufferText = if (diagnostics.bufferHealthSecs.isNotBlank()) "${diagnostics.bufferHealthSecs} / 10s" else "—"
                                Text(bufferText, color = VegaColors.Accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { bufferProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (bufferSecs >= 3f) VegaColors.Accent else VegaColors.Gold,
                                trackColor = VegaColors.Border
                            )
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
    Surface(
        shape = VegaShapes.Small,
        color = VegaColors.SurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, VegaColors.Border),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = VegaColors.TextSecondary, fontSize = 10.sp)
                if (badge.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(text = badge, color = badgeColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value.ifBlank { "—" },
                color = VegaColors.TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// 6. MODERN PLAYER PILL ACTION BUTTON (FROSTED GLASS ROUNDED PILL)
// ============================================================================

val LocalPlayerInteraction = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun PlayerPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
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

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.91f
            isFocused -> 1.08f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pillScale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isFocused -> VegaColors.Accent
            isActive -> activeColor
            isPressed -> Color.White.copy(alpha = 0.5f)
            else -> VegaColors.Border
        },
        label = "pillBorder"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            isFocused -> VegaColors.Accent.copy(alpha = 0.35f)
            isActive -> activeColor.copy(alpha = 0.25f)
            isPressed -> VegaColors.SurfaceVariant
            else -> VegaColors.OverlayPill
        },
        label = "pillBg"
    )

    val contentColor = if (isFocused || isActive) activeColor else VegaColors.TextPrimary

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            registerInteraction()
            onClick()
        },
        interactionSource = interactionSource,
        shape = VegaShapes.Pill,
        color = animatedBgColor,
        border = androidx.compose.foundation.BorderStroke(if (isFocused) 2.dp else 1.dp, animatedBorderColor),
        modifier = modifier
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    registerInteraction()
                }
            }
            .scale(scale)
            .height(if (isPortrait) 30.dp else 34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isPortrait) 9.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(if (isPortrait) 13.dp else 15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = if (isPortrait) VegaText.Caption else VegaText.Secondary,
                fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
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
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val registerInteraction = LocalPlayerInteraction.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> pressedScale
            isFocused -> 1.12f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "BouncyIconScale"
    )

    val bgAnimated by animateColorAsState(
        targetValue = when {
            isFocused -> VegaColors.Accent.copy(alpha = 0.45f)
            isPressed -> backgroundColor.copy(alpha = (backgroundColor.alpha + 0.25f).coerceAtMost(1f))
            else -> backgroundColor
        },
        label = "BouncyBg"
    )

    Box(
        modifier = modifier
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    registerInteraction()
                }
            }
            .scale(scale)
            .clip(shape)
            .background(bgAnimated)
            .then(
                if (isFocused) Modifier.border(2.dp, VegaColors.Accent, shape)
                else if (border != null) Modifier.border(border, shape)
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = contentColor.copy(alpha = 0.35f)),
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    registerInteraction()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (isFocused) VegaColors.Accent else contentColor) {
            content()
        }
    }
}


