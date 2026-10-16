package com.tivimax.player.ui.tv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import kotlinx.coroutines.delay

/**
 * 3-BÖLGELİ OTANTİK TIVIMATE ANA REHBER VE CANLI TV DÜZENİ (SADECE ANDROID TV)
 */
@OptIn(UnstableApi::class)
@Composable
fun TvTiviMateScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleLiveCategories.collectAsState()
    val allCategories by viewModel.liveCategories.collectAsState()
    val channels by viewModel.liveStreams.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val isLoading by viewModel.liveLoading.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()

    val activePreviewChannel by viewModel.activePreviewChannel.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    val currentEpgList by viewModel.currentEpgList.collectAsState()

    var isFullScreenPlayer by remember { mutableStateOf(false) }
    var navRailTab by remember { mutableStateOf("LIVE_TV") } // "LIVE_TV", "MOVIES", "SERIES", "FAVORITES", "SETTINGS"

    val channelListState = rememberLazyListState()
    val defaultCategoryFocus = remember { FocusRequester() }

    // İlk açılışta ilk kanalı otomatik sağ üst önizlemeye al
    LaunchedEffect(channels) {
        if (activePreviewChannel == null && channels.isNotEmpty()) {
            viewModel.previewChannel(channels.first())
        }
    }

    val displayedChannels = remember(channels, selectedCategory, favoriteIds) {
        if (selectedCategory == "favorites") {
            channels.filter { favoriteIds.contains(it.streamId.toString()) }
        } else {
            channels
        }
    }

    if (isFullScreenPlayer && activePreviewChannel != null) {
        // TAM EKRAN TIVIMATE PLAYER OSD (ZERO REBUFFERING)
        TvPlayerOverlay(
            viewModel = viewModel,
            channel = activePreviewChannel!!,
            onCloseFullScreen = { isFullScreenPlayer = false }
        )
    } else {
        // 3 SÜTUNLU TIVIMATE REHBER EKRANI
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(TiviBackground)
        ) {
            // ==========================================
            // 1. SOL SÜTUN: COLLAPSIBLE NAV RAIL & KATEGORİLER (230.dp)
            // ==========================================
            Surface(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight(),
                color = TiviSurface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    // Logo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(OpusCyan, TiviPrimary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "TIVIMATE REHBER",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sabit Kategori Filtreleri
                    val isFavSelected = selectedCategory == "favorites"
                    val isRecentSelected = selectedCategory == "recents"
                    val isAllSelected = selectedCategory == "all"

                    // Favoriler
                    Surface(
                        onClick = { viewModel.selectedLiveCategory.value = "favorites" },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isFavSelected) TiviGold.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(defaultCategoryFocus)
                            .dpadFocusable(
                                focusedBorderColor = TiviGold,
                                shape = RoundedCornerShape(8.dp),
                                onClick = { viewModel.selectedLiveCategory.value = "favorites" }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = TiviGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Favori Kanallar",
                                color = if (isFavSelected) TiviGold else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (favoriteIds.isNotEmpty()) {
                                Text("${favoriteIds.size}", color = TiviGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Son İzlenenler
                    Surface(
                        onClick = { viewModel.selectedLiveCategory.value = "recents" },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isRecentSelected) OpusCyan.copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusable(
                                focusedBorderColor = OpusCyan,
                                shape = RoundedCornerShape(8.dp),
                                onClick = { viewModel.selectedLiveCategory.value = "recents" }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = if (isRecentSelected) OpusCyan else TiviTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Son İzlenenler",
                                color = if (isRecentSelected) OpusCyan else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                    // Dinamik Kategoriler
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(categories, key = { it.categoryId }) { category ->
                            val isSelected = category.categoryId == selectedCategory

                            Surface(
                                onClick = { viewModel.loadLiveStreams(category.categoryId) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = { viewModel.loadLiveStreams(category.categoryId) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = if (isSelected) OpusCyan else TiviTextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = category.categoryName,
                                        color = if (isSelected) OpusCyan else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (category.itemCount > 0) {
                                        Text("${category.itemCount}", color = TiviTextMuted, fontSize = 9.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VerticalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

            // ==========================================
            // 2. ORTA SÜTUN: TEMİZ VE HIZLI KANAL LİSTESİ (260.dp)
            // ==========================================
            Surface(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                color = TiviBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "KANALLAR (${displayedChannels.size})",
                        color = TiviTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(bottom = 6.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OpusCyan, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        }
                    } else if (displayedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Kanal bulunamadı", color = TiviTextMuted, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(displayedChannels, key = { "${it.streamId}_${it.num}" }) { channel ->
                                val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
                                val epg = epgSummaryMap[channel.streamId]
                                val currentProgram = epg?.getDecodedTitle() ?: channel.currentProgram
                                val progress = epg?.getProgressFraction() ?: 0f

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrentPreview) OpusCyan.copy(alpha = 0.18f) else TiviCardDark,
                                    border = if (isCurrentPreview) BorderStroke(1.dp, OpusCyan) else BorderStroke(0.5.dp, TiviCardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = OpusCyan,
                                            shape = RoundedCornerShape(8.dp),
                                            onClick = {
                                                if (isCurrentPreview) {
                                                    // 2. OK Tıklaması: Tam Ekrana Genişlet (Zero Rebuffering)
                                                    isFullScreenPlayer = true
                                                } else {
                                                    // 1. OK Tıklaması: Sağ Üst Önizlemede Oynat
                                                    viewModel.previewChannel(channel)
                                                }
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${channel.num}",
                                            color = if (isCurrentPreview) OpusCyan else TiviTextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(26.dp)
                                        )

                                        AsyncShimmerImage(
                                            model = channel.streamIcon,
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(Color.Black),
                                            placeholderIcon = Icons.Default.Tv
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.name,
                                                color = if (isCurrentPreview) OpusCyan else Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!currentProgram.isNullOrBlank()) {
                                                Text(
                                                    text = currentProgram,
                                                    color = TiviTextSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    color = OpusCyan,
                                                    trackColor = Color.White.copy(alpha = 0.15f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .clip(RoundedCornerShape(1.dp))
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

            VerticalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

            // ==========================================
            // 3. SAĞ PANEL: MİNİ PLAYER ÖNİZLEME & EPG DETAYI
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 16:9 CANLI MİNİ PLAYER ÖNİZLEME KUTUSU (SHARED EXOPLAYER INSTANCE)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.5.dp, OpusCyan.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clickable {
                            if (activePreviewChannel != null) {
                                isFullScreenPlayer = true
                            }
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    setKeepContentOnPlayerReset(true)
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
                            modifier = Modifier.fillMaxSize()
                        )

                        // Canlı & Tam Ekran İpucu Rozeti
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TiviAccentLive)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("CANLI ÖNİZLEME", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            Text(
                                text = "▶ Tam Ekran Yap (OK)",
                                color = OpusCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // AKTİF KANAL & EPG DETAY KARTI
                activePreviewChannel?.let { channel ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = TiviCardDark),
                        border = BorderStroke(1.dp, TiviCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncShimmerImage(
                                    model = channel.streamIcon,
                                    contentDescription = channel.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black),
                                    placeholderIcon = Icons.Default.Tv
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    val currentEpg = epgSummaryMap[channel.streamId]
                                    Text(
                                        text = currentEpg?.getFormattedTime() ?: "Canlı Yayın",
                                        color = OpusCyan,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                            // Program Detay Özeti
                            val activeEpg = epgSummaryMap[channel.streamId]
                            Text(
                                text = activeEpg?.getDecodedTitle() ?: channel.currentProgram ?: "Bu kanal için program yayın bilgisi iletilmedi.",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = if (!activeEpg?.description.isNullOrBlank()) activeEpg!!.description else "Detaylı akış özet bilgisi sunucu tarafından iletilmedi.",
                                color = TiviTextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )

                            // SIRADAKİ PROGRAMLAR (TİVİMATE EPG AKIŞI)
                            if (currentEpgList.size > 1) {
                                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                Text("SIRADAKİ PROGRAMLAR", color = OpusCyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    currentEpgList.drop(1).take(3).forEach { prog ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = prog.getDecodedTitle(),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = prog.getFormattedTime(),
                                                color = TiviTextMuted,
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
    }
}
