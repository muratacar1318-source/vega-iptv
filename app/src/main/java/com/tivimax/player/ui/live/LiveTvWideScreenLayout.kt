package com.tivimax.player.ui.live

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.CategoryItem
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
        try {
            categoryFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    // Tam ekrandan dönüldüğünde veya önizleme kanalı değiştiğinde kanal listesini o kanala kaydır
    LaunchedEffect(activePreviewChannel?.streamId) {
        val targetId = activePreviewChannel?.streamId ?: return@LaunchedEffect
        val idx = pagedChannels.indexOfFirst { it.streamId == targetId }
        if (idx >= 0) {
            channelListState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // ====================================================================
        // 1. SÜTUN (SOL - 220.dp - KATEGORİLER)
        // ====================================================================
        Surface(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight(),
            color = TiviSurface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // Üst Başlık & Kategori Düzenleme Butonu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(OpusCyan, TiviPrimary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CANLI TV",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = onOpenCategoryManager,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Kategorileri Düzenle",
                            tint = OpusCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Sabit Hızlı Kategoriler: Favoriler, Son İzlenenler, Tüm Kanallar
                val isFavSelected = selectedCategory == "favorites"
                val isRecentSelected = selectedCategory == "recents"
                val isAllSelected = selectedCategory == "all"
                val totalChannelsCount = remember(allCategories, displayedChannels) {
                    allCategories.firstOrNull { it.categoryId == "all" }?.itemCount ?: displayedChannels.size
                }

                // 1) Favoriler
                Surface(
                    onClick = { viewModel.selectedLiveCategory.value = "favorites" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFavSelected) TiviGold.copy(alpha = 0.2f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFavSelected) Modifier.focusRequester(categoryFocusRequester) else Modifier)
                        .dpadFocusable(
                            focusedBorderColor = TiviGold,
                            shape = RoundedCornerShape(8.dp),
                            onClick = { viewModel.selectedLiveCategory.value = "favorites" }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = TiviGold, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Favoriler",
                            color = if (isFavSelected) TiviGold else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isFavSelected) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (favoriteIds.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isFavSelected) TiviGold.copy(alpha = 0.3f) else TiviCardDark)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("${favoriteIds.size}", color = if (isFavSelected) TiviGold else TiviTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 2) Son İzlenenler
                Surface(
                    onClick = { viewModel.selectedLiveCategory.value = "recents" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isRecentSelected) OpusCyan.copy(alpha = 0.16f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isRecentSelected) Modifier.focusRequester(categoryFocusRequester) else Modifier)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(8.dp),
                            onClick = { viewModel.selectedLiveCategory.value = "recents" }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = if (isRecentSelected) OpusCyan else TiviTextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Son İzlenenler",
                            color = if (isRecentSelected) OpusCyan else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isRecentSelected) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (recentChannels.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isRecentSelected) OpusCyan.copy(alpha = 0.3f) else TiviCardDark)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("${recentChannels.size}", color = if (isRecentSelected) OpusCyan else TiviTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 3) Tüm Kanallar
                Surface(
                    onClick = { viewModel.loadLiveStreams("all") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllSelected) OpusCyan.copy(alpha = 0.16f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isAllSelected) Modifier.focusRequester(categoryFocusRequester) else Modifier)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(8.dp),
                            onClick = { viewModel.loadLiveStreams("all") }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AllInclusive, contentDescription = null, tint = if (isAllSelected) OpusCyan else TiviTextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tüm Kanallar",
                            color = if (isAllSelected) OpusCyan else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAllSelected) OpusCyan.copy(alpha = 0.25f) else TiviCardDark)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("$totalChannelsCount", color = if (isAllSelected) OpusCyan else TiviTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                // Kategori Listesi
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredCategories.filter { it.categoryId != "all" }, key = { it.categoryId }) { category ->
                        val isSelected = category.categoryId == selectedCategory
                        CategoryItem(
                            name = category.categoryName,
                            count = category.itemCount,
                            isSelected = isSelected,
                            onClick = { viewModel.loadLiveStreams(category.categoryId) }
                        )
                    }
                }

                // Alt Ebeveyn Kilidi Butonu
                Surface(
                    onClick = {
                        if (isParentalUnlocked) viewModel.lockParental()
                        else onOpenParentalDialog()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isParentalUnlocked) Color(0xFFEF5350).copy(alpha = 0.2f) else TiviCardDark,
                    border = BorderStroke(1.dp, if (isParentalUnlocked) Color(0xFFEF5350) else TiviCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .dpadFocusable(
                            focusedBorderColor = Color(0xFFEF5350),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                if (isParentalUnlocked) viewModel.lockParental()
                                else onOpenParentalDialog()
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isParentalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isParentalUnlocked) "+18 Açık" else "+18 Kilitli",
                            color = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        VerticalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

        // ====================================================================
        // 2. SÜTUN (ORTA - 248.dp - TEMİZ VE HIZLI KANAL LİSTESİ)
        // ====================================================================
        Surface(
            modifier = Modifier
                .width(248.dp)
                .fillMaxHeight(),
            color = TiviBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                // Kanal Listesi Üst Çubuğu (Kategori Başlığı ve Arama)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = OpusCyan.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "\"$liveSearchQuery\" (${finalChannels.size})",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { viewModel.setLiveSearchQuery("") }
                                            .padding(6.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Filtreyi Temizle", tint = TiviTextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$activeCategoryName (${finalChannels.size})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(bottom = 4.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OpusCyan, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                } else if (finalChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Bu kategoride kanal bulunamadı", color = TiviTextMuted, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(pagedChannels, key = { "${it.streamId}_${it.num}" }) { channel ->
                            val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
                            val isFavorite = favoriteIds.contains(channel.streamId.toString())
                            val epg = epgSummaryMap[channel.streamId]
                            val currentProgram = epg?.getDecodedTitle() ?: channel.currentProgram
                            val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else 0f)
                            val badges = extractQualityBadges(channel.name)

                            // TİVİMATE ÇİFT TIKLAMA DAVRANIŞI:
                            // 1. Tıklama -> Önizlemede oynat (previewChannel)
                            // 2. Tıklama -> Zaten önizlemede çalan kanaldayken anında tam ekrana geç (expandMiniPlayerToFullscreen)
                            val onChannelAction = {
                                if (isCurrentPreview) {
                                    viewModel.expandMiniPlayerToFullscreen()
                                } else {
                                    viewModel.previewChannel(channel)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentPreview) OpusCyan.copy(alpha = 0.16f) else Color.Transparent,
                                border = if (isCurrentPreview) BorderStroke(1.dp, OpusCyan.copy(alpha = 0.7f)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = onChannelAction
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Kanal Numarası
                                    Text(
                                        text = "${channel.num}",
                                        color = if (isCurrentPreview) OpusCyan else TiviTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    // Kanal Logosu
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF0F141F))
                                            .padding(1.dp),
                                        placeholderIcon = Icons.Default.Tv
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Kanal Adı ve Anlık Program Bilgisi
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = channel.name,
                                                color = if (isCurrentPreview) OpusCyan else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .then(if (isCurrentPreview) Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 35.dp) else Modifier)
                                            )

                                            // Önizleme İpucu Rozeti
                                            if (isCurrentPreview) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "▶ Tam Ekran (OK)",
                                                    color = OpusCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }

                                        if (!currentProgram.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = currentProgram,
                                                color = if (isCurrentPreview) Color.White.copy(alpha = 0.85f) else TiviTextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (progress > 0f) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .clip(RoundedCornerShape(1.dp)),
                                                    color = OpusCyan,
                                                    trackColor = Color.White.copy(alpha = 0.1f)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Kalite Rozeti
                                    if (badges.isNotEmpty()) {
                                        QualityBadge(badges.first())
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    // Favori Yıldızı
                                    IconButton(
                                        onClick = { viewModel.toggleFavoriteLive(channel.streamId.toString()) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Favori",
                                            tint = if (isFavorite) TiviGold else TiviTextMuted.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Sonsuz Kaydırma Sayfalama Tetikleyici
                        if (visibleLimit < finalChannels.size) {
                            item {
                                LaunchedEffect(Unit) {
                                    visibleLimit += 40
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = OpusCyan,
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

        VerticalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

        // ====================================================================
        // 3. SÜTUN (SAĞ - KALAN GENİŞLİK - CANLI ÖNİZLEME VE PROGRAM REHBERİ)
        // ====================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- ÜST KISIM: 16:9 CANLI ÖNİZLEME PENCERESİ ---
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.5.dp, if (activePreviewChannel != null) OpusCyan.copy(alpha = 0.6f) else TiviCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                if (activePreviewChannel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewModel.expandMiniPlayerToFullscreen() }
                            .dpadFocusable(
                                focusedBorderColor = OpusCyan,
                                shape = RoundedCornerShape(12.dp),
                                onClick = { viewModel.expandMiniPlayerToFullscreen() }
                            )
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    player = viewModel.playerManager.player ?: viewModel.playerManager.initializePlayer()
                                }
                            },
                            update = { playerView ->
                                val p = viewModel.playerManager.player ?: viewModel.playerManager.initializePlayer()
                                if (playerView.player != p) {
                                    playerView.player = p
                                }
                            },
                            onRelease = { playerView ->
                                playerView.player = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Üst Bilgi Rozeti
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TiviAccentLive)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("CANLI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activePreviewChannel.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Sağ Alt: "Tam Ekran (OK)" Rozeti
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(1.dp, OpusCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .clickable { viewModel.expandMiniPlayerToFullscreen() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tam Ekran",
                                    tint = OpusCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tam Ekran (OK)",
                                    color = OpusCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TiviCardDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = TiviTextMuted, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Canlı Önizleme", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Kanal seçmek için listeden OK tuşuna basın", color = TiviTextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }

            // --- ALT KISIM: SEÇİLİ KANAL EPG VE YAYIN AKIŞI ---
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TiviCardDark),
                border = BorderStroke(1.dp, TiviCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activePreviewChannel != null) {
                    val channel = activePreviewChannel
                    val epg = epgSummaryMap[channel.streamId]
                    val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Akış Programı"
                    val programTime = epg?.getFormattedTime() ?: "Canlı Yayın"
                    val progress = epg?.getProgressFraction() ?: 0.5f
                    val description = epg?.getDecodedDescription()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = programTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = programTime,
                                color = OpusCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = OpusCyan,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        if (!description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = description,
                                color = TiviTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        HorizontalDivider(
                            color = TiviCardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // BUGÜN SIRADA
                        Text(
                            text = "BUGÜN SIRADA",
                            color = TiviGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val upcomingPrograms = remember(currentEpgList) {
                            if (currentEpgList.size > 1) currentEpgList.drop(1).take(6)
                            else emptyList()
                        }

                        if (upcomingPrograms.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                upcomingPrograms.forEach { prog ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val timeOnly = prog.getFormattedTime().split("-").firstOrNull()?.trim() ?: "Sırada"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(TiviCard)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = timeOnly,
                                                color = OpusCyan,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = prog.getDecodedTitle(),
                                            color = TiviTextPrimary,
                                            fontSize = 11.sp,
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
                                color = TiviTextMuted,
                                fontSize = 10.sp,
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
                            text = "Yayın akışını görmek için bir kanal seçin",
                            color = TiviTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
