package com.tivimax.player.ui.live

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.CategoryItem
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaPanel
import com.tivimax.player.ui.tv.StreamVaultProgressBar
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun LiveTvWideScreenLayout(
    viewModel: MainViewModel,
    categories: List<LiveCategory>,
    allCategories: List<LiveCategory>,
    selectedCategory: String,
    displayedChannels: List<LiveStream>,
    activePreviewChannel: LiveStream?,
    epgSummaryMap: Map<Int, EpgProgram>,
    currentEpgList: List<EpgProgram>,
    favoriteIds: Set<String>,
    recentChannels: List<LiveStream>,
    isLoading: Boolean,
    isParentalUnlocked: Boolean,
    onOpenCategoryManager: () -> Unit,
    onOpenParentalDialog: () -> Unit,
    onSelectProgramDetail: (LiveStream, EpgProgram) -> Unit
) {
    val liveSearchQuery by viewModel.liveSearchQuery.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isPlayerScreen = currentScreen is Screen.Player
    val playerInstanceVersion by viewModel.playerManager.playerInstanceVersion.collectAsState()
    val isPreviewBuffering by viewModel.playerManager.isBuffering.collectAsState()
    var previewPlayerView by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(previewPlayerView, playerInstanceVersion, activePreviewChannel?.streamId, isPlayerScreen) {
        val view = previewPlayerView ?: return@LaunchedEffect
        if (!isPlayerScreen && activePreviewChannel != null) {
            viewModel.playerManager.attachView(view, isPrimary = false, tag = "LiveTvWideScreen-preview")
        }
    }

    val isTv = LocalIsTvDevice.current
    val finalChannels = displayedChannels
    val filteredCategories = categories

    var visibleLimit by remember(finalChannels) { mutableIntStateOf(50) }
    val pagedChannels = remember(finalChannels, visibleLimit) {
        finalChannels.take(visibleLimit)
    }

    val channelListState = rememberLazyListState()
    val categoryFocusRequester = remember { FocusRequester() }

    // Canlı TV açıldığında aktif kategoriye otomatik odaklan
    LaunchedEffect(Unit) {
        delay(150)
        categoryFocusRequester.requestFocusSafely()
    }

    // Tam ekrandan dönüldüğünde veya önizleme kanalı değiştiğinde kanal listesini o kanala kaydır
    LaunchedEffect(activePreviewChannel?.streamId, finalChannels) {
        val targetId = activePreviewChannel?.streamId ?: return@LaunchedEffect
        val idx = finalChannels.indexOfFirst { it.streamId == targetId }
        if (idx >= 0) {
            if (idx >= visibleLimit) {
                visibleLimit = (idx + 20).coerceAtMost(finalChannels.size)
                delay(30L)
            }
            runCatching { channelListState.animateScrollToItem((idx - 2).coerceAtLeast(0)) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ====================================================================
        // 1. SÜTUN (SOL - 232.dp - KATEGORİLER, STREAMVAULT PANEL)
        // ====================================================================
        Box(
            modifier = Modifier
                .width(232.dp)
                .fillMaxHeight()
                .vegaPanel(VegaShapes.Large)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                // Üst Başlık ve Düzen/Kategori Butonları (Tek odak hedefli)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Canlı TV",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.BodyProminent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WideHeaderIconAction(
                            icon = Icons.Default.ViewCarousel,
                            contentDescription = "Raf düzenine geç",
                            tint = VegaColors.Accent,
                            onClick = { viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF) }
                        )
                        WideHeaderIconAction(
                            icon = Icons.Default.Tune,
                            contentDescription = "Kategorileri düzenle",
                            tint = VegaColors.Accent,
                            onClick = onOpenCategoryManager
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Sabit Hızlı Kategoriler: Favoriler, Son İzlenenler, Tüm Kanallar
                val isFavSelected = selectedCategory == "favorites"
                val isRecentSelected = selectedCategory == "recents"
                val isAllSelected = selectedCategory == "all"
                val totalChannelsCount = remember(allCategories, displayedChannels) {
                    allCategories.firstOrNull { it.categoryId == "all" }?.itemCount ?: displayedChannels.size
                }

                WideQuickCategoryRow(
                    title = "Favoriler",
                    icon = Icons.Default.Star,
                    count = favoriteIds.size,
                    isSelected = isFavSelected,
                    accentTint = VegaColors.Gold,
                    focusRequester = if (isFavSelected) categoryFocusRequester else null,
                    onClick = { viewModel.selectedLiveCategory.value = "favorites" }
                )

                Spacer(modifier = Modifier.height(3.dp))

                WideQuickCategoryRow(
                    title = "Son İzlenenler",
                    icon = Icons.Default.History,
                    count = recentChannels.size,
                    isSelected = isRecentSelected,
                    accentTint = VegaColors.Accent,
                    focusRequester = if (isRecentSelected) categoryFocusRequester else null,
                    onClick = { viewModel.selectedLiveCategory.value = "recents" }
                )

                Spacer(modifier = Modifier.height(3.dp))

                WideQuickCategoryRow(
                    title = "Tüm Kanallar",
                    icon = Icons.Default.AllInclusive,
                    count = totalChannelsCount,
                    isSelected = isAllSelected,
                    accentTint = VegaColors.Accent,
                    focusRequester = if (isAllSelected) categoryFocusRequester else null,
                    onClick = { viewModel.loadLiveStreams("all") }
                )

                HorizontalDivider(
                    color = VegaColors.Border,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                // Kategori Listesi
                val distinctCategories = remember(filteredCategories) {
                    filteredCategories
                        .filter { it.categoryId != "all" }
                        .distinctBy { it.categoryId }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = distinctCategories,
                        key = { index, category -> "wide_cat_${category.categoryId}_$index" }
                    ) { _, category ->
                        val isSelected = category.categoryId == selectedCategory
                        CategoryItem(
                            name = category.categoryName,
                            count = category.itemCount,
                            isSelected = isSelected,
                            onClick = { viewModel.loadLiveStreams(category.categoryId) }
                        )
                    }
                }

                // Alt Ebeveyn Kilidi Butonu (Tek odak hedefli)
                Spacer(modifier = Modifier.height(6.dp))
                var isLockFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = VegaShapes.Small,
                    color = when {
                        isLockFocused -> VegaColors.SurfaceVariant
                        isParentalUnlocked -> VegaColors.Danger.copy(alpha = 0.18f)
                        else -> VegaColors.SurfaceElevated
                    },
                    border = if (isLockFocused) null else BorderStroke(
                        1.dp,
                        if (isParentalUnlocked) VegaColors.Danger else VegaColors.Border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Small,
                            scale = 1.02f,
                            onClick = {
                                if (isParentalUnlocked) viewModel.lockParental()
                                else onOpenParentalDialog()
                            },
                            onFocusStateChange = { isLockFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isParentalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isParentalUnlocked) VegaColors.Danger else VegaColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isParentalUnlocked) "+18 açık (kilitle)" else "+18 korumalı",
                            color = if (isParentalUnlocked) VegaColors.Danger else VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ====================================================================
        // 2. SÜTUN (ORTA - 300.dp - STREAMVAULT KANAL LİSTESİ)
        // ====================================================================
        Box(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .vegaPanel(VegaShapes.Large)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                // Kanal Listesi Üst Çubuğu (Kategori Başlığı ve Aktif Arama)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val activeCategoryName = remember(selectedCategory, categories) {
                        when (selectedCategory) {
                            "favorites" -> "Favoriler"
                            "recents" -> "Son İzlenenler"
                            "all" -> "Tüm Kanallar"
                            else -> categories.firstOrNull { it.categoryId == selectedCategory }?.categoryName ?: "Kanallar"
                        }
                    }

                    if (liveSearchQuery.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.Accent.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = VegaColors.Accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "\"$liveSearchQuery\" (${finalChannels.size})",
                                        color = VegaColors.TextPrimary,
                                        fontSize = VegaText.Secondary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            WideHeaderIconAction(
                                icon = Icons.Default.Close,
                                contentDescription = "Filtreyi temizle",
                                tint = VegaColors.TextSecondary,
                                onClick = { viewModel.setLiveSearchQuery("") }
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = activeCategoryName,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.BodyProminent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${finalChannels.size} kanal",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = VegaColors.Border,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = VegaColors.Accent,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else if (finalChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Bu kategoride kanal bulunamadı",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Secondary
                        )
                    }
                } else {
                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        itemsIndexed(
                            items = pagedChannels,
                            key = { index, channel -> "ch_${channel.streamId}_${channel.categoryId}_$index" }
                        ) { _, channel ->
                            val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
                            val isFavorite = favoriteIds.contains(channel.streamId.toString())
                            val epg = epgSummaryMap[channel.streamId]
                            val currentProgram = epg?.getDecodedTitle() ?: channel.currentProgram
                            val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else null)
                            val badges = remember(channel.name) { extractQualityBadges(channel.name) }

                            val onChannelAction = {
                                if (isCurrentPreview) {
                                    viewModel.expandMiniPlayerToFullscreen()
                                } else {
                                    viewModel.previewChannel(channel)
                                }
                            }

                            var isRowFocused by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = VegaShapes.Medium,
                                    color = when {
                                        isRowFocused -> VegaColors.SurfaceVariant
                                        isCurrentPreview -> VegaColors.Accent.copy(alpha = 0.16f)
                                        else -> VegaColors.SurfaceElevated
                                    },
                                    border = if (isRowFocused) null else BorderStroke(
                                        width = if (isCurrentPreview) 1.5.dp else 1.dp,
                                        color = if (isCurrentPreview) VegaColors.Accent.copy(alpha = 0.7f) else VegaColors.Border
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.FocusBorder,
                                            shape = VegaShapes.Medium,
                                            scale = 1.02f,
                                            onClick = onChannelAction,
                                            onFocusStateChange = { isRowFocused = it }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // StreamVault kare logo kutusu
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(VegaShapes.Small)
                                                .background(VegaColors.SurfaceAccent)
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncShimmerImage(
                                                model = channel.streamIcon,
                                                contentDescription = channel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize(),
                                                placeholderIcon = Icons.Default.Tv
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                val numPrefix = if (channel.num > 0) "${channel.num} " else ""
                                                Text(
                                                    text = "$numPrefix${channel.name}",
                                                    color = if (isCurrentPreview) VegaColors.Accent else VegaColors.TextPrimary,
                                                    fontSize = VegaText.Secondary,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .weight(1f, fill = false)
                                                        .then(
                                                            if (isCurrentPreview) {
                                                                Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 35.dp)
                                                            } else {
                                                                Modifier
                                                            }
                                                        )
                                                )

                                                if (badges.isNotEmpty()) {
                                                    QualityBadge(badges.first())
                                                }

                                                if (isTv && isFavorite) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = VegaColors.Gold,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }

                                            if (!currentProgram.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = currentProgram,
                                                    color = if (isCurrentPreview) VegaColors.TextPrimary.copy(alpha = 0.85f) else VegaColors.TextSecondary,
                                                    fontSize = VegaText.Caption,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (progress != null && progress > 0f) {
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    LinearProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(2.dp)
                                                            .clip(VegaShapes.Pill),
                                                        color = VegaColors.Accent,
                                                        trackColor = VegaColors.SurfaceAccent
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = "Program bilgisi yok",
                                                    color = VegaColors.TextMuted,
                                                    fontSize = VegaText.Caption,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // Dokunmatik tabletlerde iç içe çakışma olmadan ayrı kardeş favori butonu
                                if (!isTv) {
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.SurfaceElevated,
                                        border = BorderStroke(1.dp, VegaColors.Border),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                shape = VegaShapes.Small,
                                                scale = 1.04f,
                                                onClick = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                                            )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Favori",
                                                tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Sonsuz Kaydırma Sayfalama Tetikleyici
                        if (visibleLimit < finalChannels.size) {
                            item(key = "more_$visibleLimit") {
                                LaunchedEffect(visibleLimit) {
                                    visibleLimit += 50
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = VegaColors.Accent,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 3. SÜTUN (SAĞ - KALAN GENİŞLİK - STREAMVAULT KANAL ÖNİZLEME PANELİ)
        // ====================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .vegaPanel(VegaShapes.Large)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Kanal önizleme",
                color = VegaColors.Accent,
                fontSize = VegaText.Body,
                fontWeight = FontWeight.SemiBold
            )

            // --- ÜST KISIM: 16:9 CANLI ÖNİZLEME PENCERESİ (TEK ODAK HEDEFİ) ---
            var isPreviewFocused by remember { mutableStateOf(false) }
            Card(
                shape = VegaShapes.Medium,
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = if (isPreviewFocused) null else BorderStroke(
                    width = 1.dp,
                    color = if (activePreviewChannel != null) VegaColors.Accent.copy(alpha = 0.55f) else VegaColors.Border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .then(
                        if (activePreviewChannel != null) {
                            Modifier.dpadFocusable(
                                focusedBorderColor = VegaColors.FocusBorder,
                                shape = VegaShapes.Medium,
                                scale = 1.02f,
                                onClick = { viewModel.expandMiniPlayerToFullscreen() },
                                onFocusStateChange = { isPreviewFocused = it }
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (activePreviewChannel != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isPlayerScreen) {
                            AndroidView(
                                factory = { ctx ->
                                    val view = LayoutInflater.from(ctx)
                                        .inflate(R.layout.player_texture_view, null, false) as PlayerView
                                    view.apply {
                                        useController = false
                                        setKeepContentOnPlayerReset(true)
                                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                    previewPlayerView = view
                                    view
                                },
                                update = {},
                                onRelease = { playerView ->
                                    if (previewPlayerView === playerView) {
                                        previewPlayerView = null
                                    }
                                    viewModel.playerManager.detachView(playerView, tag = "LiveTvWideScreen-preview")
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (isPreviewBuffering) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(VegaShapes.Small)
                                            .background(VegaColors.SurfaceElevated.copy(alpha = 0.85f))
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncShimmerImage(
                                            model = activePreviewChannel.streamIcon,
                                            contentDescription = activePreviewChannel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize(),
                                            placeholderIcon = Icons.Default.Tv
                                        )
                                    }
                                    CircularProgressIndicator(
                                        color = VegaColors.Accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Üst Bilgi Rozeti
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.Live)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Canlı",
                                    color = Color.White,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = activePreviewChannel.name,
                                color = Color.White,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Sağ Alt: Görsel "Tam ekran" ipucu rozeti (iç içe tıklama yok, tüm kart tek hedef)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(VegaShapes.Small)
                                .background(VegaColors.OverlayPill)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = null,
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Tam ekran",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VegaColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = VegaColors.TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Canlı önizleme",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "İzlemek için listeden bir kanal seç",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption
                            )
                        }
                    }
                }
            }

            // --- ALT KISIM: SEÇİLİ KANAL PROGRAM AKIŞI ---
            Surface(
                shape = VegaShapes.Medium,
                color = VegaColors.SurfaceElevated,
                border = BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activePreviewChannel != null) {
                    val channel = activePreviewChannel
                    val epg = epgSummaryMap[channel.streamId]
                    val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın"
                    val programTime = epg?.getFormattedTime() ?: "Canlı yayın"
                    val progress = epg?.getProgressFraction()
                    val description = epg?.getDecodedDescription()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = programTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = programTime,
                                color = VegaColors.Accent,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (progress != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            StreamVaultProgressBar(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = description,
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Secondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        HorizontalDivider(
                            color = VegaColors.Border,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Text(
                            text = "Bugün sırada",
                            color = VegaColors.Gold,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val upcomingPrograms = remember(currentEpgList) {
                            if (currentEpgList.size > 1) currentEpgList.drop(1).take(6)
                            else emptyList()
                        }

                        if (upcomingPrograms.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                upcomingPrograms.forEach { prog ->
                                    val timeOnly = prog.getFormattedTime().split("-").firstOrNull()?.trim() ?: "Sırada"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(VegaShapes.Small)
                                            .dpadFocusable(
                                                shape = VegaShapes.Small,
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                scale = 1.01f,
                                                onClick = { onSelectProgramDetail(channel, prog) }
                                            )
                                            .padding(horizontal = 4.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(VegaShapes.Small)
                                                .background(VegaColors.SurfaceAccent)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = timeOnly,
                                                color = VegaColors.Accent,
                                                fontSize = VegaText.Caption,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = prog.getDecodedTitle(),
                                            color = VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Bu kanal için sonraki program akışı yükleniyor...",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Yayın akışını görmek için bir kanal seç",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WideHeaderIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Small,
        color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
        border = if (isFocused) null else BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .size(34.dp)
            .dpadFocusable(
                shape = VegaShapes.Small,
                focusedBorderColor = VegaColors.FocusBorder,
                scale = 1.05f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WideQuickCategoryRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    isSelected: Boolean,
    accentTint: Color,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Small,
        color = when {
            isFocused -> VegaColors.SurfaceVariant
            isSelected -> accentTint.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        border = if (isSelected && !isFocused) BorderStroke(1.dp, accentTint.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected || accentTint == VegaColors.Gold) accentTint else VegaColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = title,
                color = if (isSelected) accentTint else VegaColors.TextPrimary,
                fontSize = VegaText.Secondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(VegaShapes.Small)
                        .background(if (isSelected) accentTint.copy(alpha = 0.24f) else VegaColors.SurfaceAccent)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$count",
                        color = if (isSelected) accentTint else VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
