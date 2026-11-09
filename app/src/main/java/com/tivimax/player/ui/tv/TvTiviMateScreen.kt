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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.focusRestorer
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
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.live.modern.AnimatedEqualizer
import kotlinx.coroutines.delay

/**
 * 3-BÖLGELİ VEGA SİNEMATİK TIVIMATE STÜDYO DÜZENİ (SADECE ANDROID TV)
 * - Sol sütun: Neon aktif göstergeli ve sayaç rozetli camgöbeği kategoriler (220dp)
 * - Orta sütun: Ekolayzer animasyonlu, kalite rozetli (4K/FHD/50FPS) ve canlı ilerleme çubuklu kanal listesi (290dp)
 * - Sağ panel: 16:9 Sinematik mini oynatıcı ve yayın zaman çizelgeli stüdyo EPG kartı (weight 1f)
 */
@OptIn(UnstableApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    val previewError by viewModel.playerManager.errorMessage.collectAsState()
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

    // Kumanda ile zaplama veya rakam tuşlamasında seçilen kanalı listede görünür kıl
    LaunchedEffect(activePreviewChannel?.streamId) {
        val activeId = activePreviewChannel?.streamId ?: return@LaunchedEffect
        val index = displayedChannels.indexOfFirst { it.streamId == activeId }
        if (index >= 0) {
            val firstVisible = channelListState.firstVisibleItemIndex
            val lastVisible = firstVisible + channelListState.layoutInfo.visibleItemsInfo.size
            if (index < firstVisible || index >= lastVisible) {
                try {
                    channelListState.animateScrollToItem((index - 2).coerceAtLeast(0))
                } catch (_: Exception) {}
            }
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
        // TAM EKRAN TIVIMATE PLAYER OSD
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
        // 3 SÜTUNLU VEGA SİNEMATİK TIVIMATE DÜZENİ
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
                        if (keyCode in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9) {
                            val digit = keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0
                            viewModel.onNumericKeyPressed(digit)
                            return@onPreviewKeyEvent true
                        }
                        if (isZapperActive && (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                            viewModel.executeZapperSwitch()
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && isZapperActive) {
                            viewModel.dismissZapper()
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_GUIDE ||
                            keyCode == android.view.KeyEvent.KEYCODE_BUTTON_Y) {
                            showEpgGrid = !showEpgGrid
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_LAST_CHANNEL ||
                            keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                            viewModel.zapRecall()
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_UP ||
                            keyCode == android.view.KeyEvent.KEYCODE_PAGE_UP) {
                            val currentId = activePreviewChannel?.streamId
                            if (currentId != null) {
                                viewModel.zapNextChannel(currentId)
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN ||
                            keyCode == android.view.KeyEvent.KEYCODE_PAGE_DOWN) {
                            val currentId = activePreviewChannel?.streamId
                            if (currentId != null) {
                                viewModel.zapPreviousChannel(currentId)
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                            viewModel.playerManager.togglePlayPause()
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY) {
                            viewModel.playerManager.play()
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE) {
                            viewModel.playerManager.pause()
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_MEDIA_STOP) {
                            viewModel.playerManager.stop()
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
                // 1. SOL SÜTUN: KATEGORİ PANELİ (220.dp)
                // ==========================================
                Surface(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight(),
                    color = VegaColors.Surface,
                    border = BorderStroke(1.dp, VegaColors.Border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        // Başlık
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.Accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = VegaColors.OnAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Canlı TV",
                                color = VegaColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Sabit Kategori Filtreleri
                        val isFavSelected = selectedCategory == "favorites"
                        val isRecentSelected = selectedCategory == "recents"

                        // Favoriler Butonu
                        TiviCategoryItem(
                            title = "Favori Kanallar",
                            icon = Icons.Default.Star,
                            iconTint = VegaColors.Gold,
                            count = favoriteIds.size,
                            isSelected = isFavSelected,
                            focusRequester = defaultCategoryFocus,
                            onClick = { viewModel.selectedLiveCategory.value = "favorites" }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Son İzlenenler Butonu
                        TiviCategoryItem(
                            title = "Son İzlenenler",
                            icon = Icons.Default.History,
                            iconTint = if (isRecentSelected) VegaColors.Accent else VegaColors.TextSecondary,
                            count = recentChannels.size,
                            isSelected = isRecentSelected,
                            onClick = { viewModel.selectedLiveCategory.value = "recents" }
                        )

                        HorizontalDivider(
                            color = VegaColors.Border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Dinamik Kategoriler
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .focusRestorer(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(categories, key = { it.categoryId }) { category ->
                                val isSelected = category.categoryId == selectedCategory
                                TiviCategoryItem(
                                    title = category.categoryName,
                                    icon = Icons.Default.LiveTv,
                                    iconTint = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                    count = category.itemCount,
                                    isSelected = isSelected,
                                    onClick = { viewModel.loadLiveStreams(category.categoryId) }
                                )
                            }
                        }
                    }
                }

                VerticalDivider(color = VegaColors.Border, thickness = 1.dp)

                // ==========================================
                // 2. ORTA SÜTUN: GELİŞMİŞ KANAL LİSTESİ (290.dp)
                // ==========================================
                Surface(
                    modifier = Modifier
                        .width(290.dp)
                        .fillMaxHeight(),
                    color = VegaColors.Background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        // Sütun Başlığı & Tam Rehber Butonu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kanallar (${displayedChannels.size})",
                                color = VegaColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceVariant,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                modifier = Modifier.dpadFocusable(
                                    shape = VegaShapes.Small,
                                    focusedBorderColor = VegaColors.Accent,
                                    onClick = { showEpgGrid = true }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = VegaColors.Accent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tam rehber",
                                        color = VegaColors.Accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = VegaColors.Border,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = VegaColors.Accent,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else if (displayedChannels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Kanal bulunamadı",
                                    color = VegaColors.TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = channelListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRestorer(),
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
                                    val qualityBadges = remember(channel.name) { extractQualityBadges(channel.name) }

                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = if (isCurrentPreview) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.Card,
                                        border = if (isCurrentPreview) BorderStroke(1.5.dp, VegaColors.Accent) else BorderStroke(1.dp, VegaColors.Border),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(58.dp)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.Accent,
                                                shape = VegaShapes.Small,
                                                scale = 1.03f,
                                                onClick = {
                                                    if (isCurrentPreview) {
                                                        // 2. Tıklama: Tam Ekrana Geç
                                                        isFullScreenPlayer = true
                                                    } else {
                                                        // 1. Tıklama: Sağ Önizlemede Başlat
                                                        viewModel.previewChannel(channel)
                                                    }
                                                }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Kanal Numarası & Ekolayzer
                                            Column(
                                                modifier = Modifier.width(26.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "${channel.num}",
                                                    color = if (isCurrentPreview) VegaColors.Accent else VegaColors.TextMuted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isCurrentPreview) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    AnimatedEqualizer(
                                                        barColor = VegaColors.Accent,
                                                        maxHeight = 10.dp,
                                                        barWidth = 2.dp,
                                                        spacing = 1.5.dp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            // Kanal Logosu
                                            AsyncShimmerImage(
                                                model = channel.streamIcon,
                                                contentDescription = channel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(VegaShapes.Small)
                                                    .background(Color.Black),
                                                placeholderIcon = Icons.Default.Tv
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Kanal İsmi, Kalite Rozeti ve Program Akışı
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = channel.name,
                                                        color = if (isCurrentPreview) VegaColors.Accent else VegaColors.TextPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )

                                                    if (qualityBadges.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        QualityBadge(tag = qualityBadges.first())
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Text(
                                                    text = currentProgram ?: "Canlı yayın",
                                                    color = VegaColors.TextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (progress != null) {
                                                    Spacer(modifier = Modifier.height(3.dp))
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

                VerticalDivider(color = VegaColors.Border, thickness = 1.dp)

                // ==========================================
                // 3. SAĞ PANEL: SİNEMATİK MİNİ OYNATICI VE STÜDYO EPG KARTI
                // ==========================================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 16:9 CANLI MİNİ PLAYER ÖNİZLEME ÇERÇEVESİ
                    Card(
                        shape = VegaShapes.Medium,
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = BorderStroke(
                            1.5.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    VegaColors.Accent.copy(alpha = 0.85f),
                                    VegaColors.Accent.copy(alpha = 0.35f)
                                )
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = Color.White,
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

                            // Önizleme Hatası
                            previewError?.let { msg ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = msg,
                                        color = VegaColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            // Üst Canlı Sinyal & Format Rozeti
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                                        )
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(VegaColors.Live)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Canlı yayın",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = VegaShapes.Pill,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = null,
                                            tint = VegaColors.Accent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Tam ekran (OK)",
                                            color = VegaColors.Accent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // STÜDYO EPG VE KANAL DETAY KARTI
                    activePreviewChannel?.let { channel ->
                        val qualityBadges = remember(channel.name) { extractQualityBadges(channel.name) }
                        val currentEpg = epgSummaryMap[channel.streamId]

                        Card(
                            shape = VegaShapes.Large,
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
                                // Kanal Başlığı ve Rozetler
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(VegaShapes.Small)
                                            .background(Color.Black),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = channel.name,
                                                color = VegaColors.TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            qualityBadges.forEach { tag ->
                                                QualityBadge(tag = tag)
                                            }
                                        }

                                        Text(
                                            text = currentEpg?.getFormattedTime() ?: "Canlı yayın akışı",
                                            color = VegaColors.Accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                HorizontalDivider(color = VegaColors.Border, thickness = 1.dp)

                                // Program Başlığı
                                val activeEpg = epgSummaryMap[channel.streamId]
                                Text(
                                    text = activeEpg?.getDecodedTitle() ?: channel.currentProgram ?: "Bu kanal için program yayın bilgisi iletilmedi.",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Program Açıklaması
                                val decodedDesc = activeEpg?.getDecodedDescription()
                                Text(
                                    text = if (!decodedDesc.isNullOrBlank()) decodedDesc else "Detaylı akış özet bilgisi sunucu tarafından iletilmedi.",
                                    color = VegaColors.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp
                                )

                                // SIRADAKİ PROGRAMLAR ZAMAN ÇİZELGESİ
                                if (currentEpgList.size > 1) {
                                    HorizontalDivider(color = VegaColors.Border, thickness = 1.dp)
                                    Text(
                                        text = "Sıradaki programlar",
                                        color = VegaColors.Accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        currentEpgList.drop(1).take(3).forEach { prog ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = VegaShapes.Small,
                                                        color = VegaColors.SurfaceVariant,
                                                        border = BorderStroke(0.5.dp, VegaColors.Border)
                                                    ) {
                                                        Text(
                                                            text = prog.getFormattedTime(),
                                                            color = VegaColors.TextMuted,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = prog.getDecodedTitle(),
                                                        color = VegaColors.TextPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * SOL KATEGORİ PANEL ÖĞESİ
 */
@Composable
private fun TiviCategoryItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    count: Int,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = VegaShapes.Small,
        color = if (isSelected) VegaColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.4f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Small,
                scale = 1.03f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aktif kategori neon sol çizgi göstergesi
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(VegaShapes.Pill)
                        .background(VegaColors.Accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (count > 0) {
                Surface(
                    shape = VegaShapes.Pill,
                    color = VegaColors.SurfaceVariant,
                    border = BorderStroke(0.5.dp, VegaColors.Border)
                ) {
                    Text(
                        text = "$count",
                        color = VegaColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
