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
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import kotlinx.coroutines.delay

/**
 * 3-BÖLGELİ OTANTİK TIVIMATE ANA REHBER VE CANLI TV DÜZENİ (SADECE ANDROID TV)
 */
@OptIn(UnstableApi::class)
@Composable
fun TvTiviMateScreen(
    viewModel: MainViewModel,
    onFullScreenChange: (Boolean) -> Unit = {}
) {
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

    val zapperNumber by viewModel.zapperNumber.collectAsState()
    val isZapperActive by viewModel.isZapperActive.collectAsState()
    val zapperMatchedChannel by viewModel.zapperMatchedChannel.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isPlayerScreen = currentScreen is Screen.Player

    var isFullScreenPlayer by remember { mutableStateOf(false) }
    var showEpgGrid by remember { mutableStateOf(false) }

    // Tam ekrana veya tam saat çizelgeli EPG rehberine geçişte ana ekran sol menüyü gizler
    LaunchedEffect(isFullScreenPlayer, showEpgGrid) {
        onFullScreenChange(isFullScreenPlayer || showEpgGrid)
    }
    DisposableEffect(Unit) {
        onDispose {
            onFullScreenChange(false)
            // Ana ekranda başka sekmeye geçildiyse önizleme arka planda ses vermesin.
            // (Oynatıcı ekranına geçişte durdurma yapılmaz; orada yeni yayın zaten başlatılmıştır.)
            if (viewModel.currentScreen.value is Screen.Home) {
                viewModel.playerManager.pause()
            }
        }
    }

    val channelListState = rememberLazyListState()
    val defaultCategoryFocus = remember { FocusRequester() }

    // Başka sekmeden Canlı TV'ye dönüldüğünde duraklatılan önizlemeyi canlı noktadan yeniden başlat
    LaunchedEffect(Unit) {
        val previous = activePreviewChannel
        if (previous != null && !viewModel.playerManager.isPlaying.value) {
            viewModel.previewChannel(previous)
        }
    }

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

    if (showEpgGrid) {
        // TİVİMATE TAM SAAT ÇİZELGELİ EPG PROGRAM REHBERİ
        TvEpgGridScreen(
            viewModel = viewModel,
            onBack = { showEpgGrid = false },
            onSelectChannel = { channel ->
                viewModel.playLiveChannel(channel)
                showEpgGrid = false
                isFullScreenPlayer = true
            }
        )
    } else if (isFullScreenPlayer && activePreviewChannel != null) {
        // TAM EKRAN TIVIMATE PLAYER OSD (ZERO REBUFFERING)
        TvPlayerOverlay(
            viewModel = viewModel,
            channel = activePreviewChannel!!,
            onCloseFullScreen = { isFullScreenPlayer = false },
            onOpenEpgGrid = {
                isFullScreenPlayer = false
                showEpgGrid = true
            }
        )
    } else {
        // 3 SÜTUNLU TIVIMATE REHBER EKRANI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        val keyCode = keyEvent.nativeKeyEvent.keyCode
                        if (keyCode in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) {
                            val digit = keyCode - android.view.KeyEvent.KEYCODE_0
                            viewModel.onNumericKeyPressed(digit)
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && isZapperActive) {
                            viewModel.dismissZapper()
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VegaColors.Background)
            ) {
            // ==========================================
            // 1. SOL SÜTUN: COLLAPSIBLE NAV RAIL & KATEGORİLER (230.dp)
            // ==========================================
            Surface(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight(),
                color = VegaColors.Surface,
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
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Canlı TV",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sabit Kategori Filtreleri
                    val isFavSelected = selectedCategory == "favorites"
                    val isRecentSelected = selectedCategory == "recents"
                    val isAllSelected = selectedCategory == "all"

                    // Favoriler
                    Surface(
                        shape = VegaShapes.Small,
                        color = if (isFavSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(defaultCategoryFocus)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.Accent,
                                shape = VegaShapes.Small,
                                onClick = { viewModel.selectedLiveCategory.value = "favorites" }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Favori Kanallar",
                                color = if (isFavSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (favoriteIds.isNotEmpty()) {
                                Text("${favoriteIds.size}", color = VegaColors.TextMuted, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Son İzlenenler
                    Surface(
                        shape = VegaShapes.Small,
                        color = if (isRecentSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.Accent,
                                shape = VegaShapes.Small,
                                onClick = { viewModel.selectedLiveCategory.value = "recents" }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = if (isRecentSelected) VegaColors.Accent else VegaColors.TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Son İzlenenler",
                                color = if (isRecentSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = VegaColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

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
                                shape = VegaShapes.Small,
                                color = if (isSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Small,
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
                                        tint = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = category.categoryName,
                                        color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (category.itemCount > 0) {
                                        Text("${category.itemCount}", color = VegaColors.TextMuted, fontSize = VegaText.Caption)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VerticalDivider(color = VegaColors.Border, thickness = 1.dp)

            // ==========================================
            // 2. ORTA SÜTUN: TEMİZ VE HIZLI KANAL LİSTESİ (260.dp)
            // ==========================================
            Surface(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                color = VegaColors.Background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kanallar (${displayedChannels.size})",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, VegaColors.Accent),
                            modifier = Modifier.dpadFocusable(
                                shape = VegaShapes.Small,
                                focusedBorderColor = VegaColors.Accent,
                                onClick = { showEpgGrid = true }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Rehber",
                                    color = VegaColors.Accent,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = VegaColors.Border, thickness = 1.dp, modifier = Modifier.padding(bottom = 6.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VegaColors.Accent, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        }
                    } else if (displayedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Kanal bulunamadı", color = VegaColors.TextMuted, fontSize = VegaText.Secondary)
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(displayedChannels, key = { "${it.streamId}_${it.num}" }) { channel ->
                                LaunchedEffect(channel.streamId) {
                                    viewModel.loadEpgSummary(channel.streamId)
                                }
                                val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
                                val epg = epgSummaryMap[channel.streamId]
                                val currentProgram = epg?.getDecodedTitle() ?: channel.currentProgram
                                val progress = epg?.getProgressFraction()

                                Surface(
                                    shape = VegaShapes.Small,
                                    color = if (isCurrentPreview) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.Card,
                                    border = if (isCurrentPreview) BorderStroke(1.dp, VegaColors.Accent) else BorderStroke(1.dp, VegaColors.Border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.Accent,
                                            shape = VegaShapes.Small,
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
                                            color = if (isCurrentPreview) VegaColors.Accent else VegaColors.TextSecondary,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(26.dp)
                                        )

                                        AsyncShimmerImage(
                                            model = channel.streamIcon,
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(VegaShapes.Small)
                                                .background(VegaColors.Background),
                                            placeholderIcon = Icons.Default.Tv
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.name,
                                                color = if (isCurrentPreview) VegaColors.Accent else VegaColors.TextPrimary,
                                                fontSize = VegaText.Secondary,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!currentProgram.isNullOrBlank()) {
                                                Text(
                                                    text = currentProgram,
                                                    color = VegaColors.TextSecondary,
                                                    fontSize = VegaText.Caption,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (progress != null) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    LinearProgressIndicator(
                                                        progress = { progress },
                                                        color = VegaColors.Accent,
                                                        trackColor = VegaColors.Border,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(2.dp)
                                                            .clip(VegaShapes.Small)
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

            VerticalDivider(color = VegaColors.Border, thickness = 1.dp)

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
                    shape = VegaShapes.Medium,
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.5.dp, VegaColors.Accent.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            onClick = {
                                if (activePreviewChannel != null) {
                                    isFullScreenPlayer = true
                                }
                            }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isPlayerScreen) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        setKeepContentOnPlayerReset(true)
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        viewModel.playerManager.attachView(this, isPrimary = false, tag = "TvTiviMateScreen-preview")
                                    }
                                },
                                update = { playerView ->
                                    viewModel.playerManager.attachView(playerView, isPrimary = false, tag = "TvTiviMateScreen-preview")
                                },
                                onRelease = { playerView ->
                                    viewModel.playerManager.detachView(playerView, tag = "TvTiviMateScreen-preview")
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

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
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.Live)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Canlı önizleme", color = VegaColors.OnAccent, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "▶ Tam ekran yap (OK)",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // AKTİF KANAL & EPG DETAY KARTI
                activePreviewChannel?.let { channel ->
                    Card(
                        shape = VegaShapes.Medium,
                        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
                        border = BorderStroke(1.dp, VegaColors.Border),
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
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.Background),
                                    placeholderIcon = Icons.Default.Tv
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(channel.name, color = VegaColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = VegaText.BodyProminent)
                                    val currentEpg = epgSummaryMap[channel.streamId]
                                    Text(
                                        text = currentEpg?.getFormattedTime() ?: "Canlı yayın",
                                        color = VegaColors.Accent,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            HorizontalDivider(color = VegaColors.Border, thickness = 1.dp)

                            // Program Detay Özeti
                            val activeEpg = epgSummaryMap[channel.streamId]
                            Text(
                                text = activeEpg?.getDecodedTitle() ?: channel.currentProgram ?: "Bu kanal için program yayın bilgisi iletilmedi.",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
                            )

                            val decodedDesc = activeEpg?.getDecodedDescription()
                            Text(
                                text = if (!decodedDesc.isNullOrBlank()) decodedDesc else "Detaylı akış özet bilgisi sunucu tarafından iletilmedi.",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )

                            // SIRADAKİ PROGRAMLAR (TİVİMATE EPG AKIŞI)
                            if (currentEpgList.size > 1) {
                                HorizontalDivider(color = VegaColors.Border, thickness = 1.dp)
                                Text("Sıradaki programlar", color = VegaColors.Accent, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    currentEpgList.drop(1).take(3).forEach { prog ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = prog.getDecodedTitle(),
                                                color = VegaColors.TextPrimary,
                                                fontSize = VegaText.Caption,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = prog.getFormattedTime(),
                                                color = VegaColors.TextMuted,
                                                fontSize = VegaText.Caption
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

            // NUMERIC ZAPPER (KUMANDA NUMARATÖR) OVERLAY
            if (isZapperActive && zapperNumber.isNotBlank()) {
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.Accent,
                    border = BorderStroke(1.5.dp, VegaColors.OnAccent),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                ) {
                    Text(
                        text = "[ $zapperNumber ] ${zapperMatchedChannel?.name ?: ""}",
                        color = VegaColors.OnAccent,
                        fontSize = VegaText.ScreenTitle,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
