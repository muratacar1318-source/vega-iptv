package com.tivimax.player.ui.tv

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaPanel
import com.tivimax.player.ui.live.modern.AnimatedEqualizer
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 3-BÖLGELİ STREAMVAULT TARZI CANLI TV EKRANI (ANDROID TV)
 * - Sol kart: Kategoriler + kategori arama + hızlı filtreler (Favoriler / Son İzlenenler)
 * - Orta kart: Seçili kategori başlığı + kanal arama + logo/EPG/ilerleme çubuklu kanal satırları
 * - Sağ kart: Kanal Önizleme (16:9 canlı önizleme, program bilgisi, zaman çubuğu ve OK ipucu)
 */
@OptIn(UnstableApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class, FlowPreview::class)
@Composable
fun TvTiviMateScreen(
    viewModel: MainViewModel,
    onFullScreenChange: (Boolean) -> Unit = {}
) {
    val categories by viewModel.visibleLiveCategories.collectAsState()
    val channels by viewModel.liveStreams.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val isLoading by viewModel.liveLoading.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
    val favoriteStreams by viewModel.favoriteLiveStreams.collectAsState()
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
    var categoryQuery by remember { mutableStateOf("") }
    var channelQuery by remember { mutableStateOf("") }
    var showQuickFilters by remember { mutableStateOf(true) }
    var isCategorySearchEditing by remember { mutableStateOf(false) }
    var isChannelSearchEditing by remember { mutableStateOf(false) }
    val isSearchEditing = isCategorySearchEditing || isChannelSearchEditing
    var lastFocusedChannelId by remember { mutableStateOf<Int?>(null) }
    var quickActionChannel by remember { mutableStateOf<LiveStream?>(null) }
    var hasInitialFocused by remember { mutableStateOf(false) }
    var wasInSubScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullScreenPlayer, showEpgGrid) {
        onFullScreenChange(isFullScreenPlayer || showEpgGrid)
        if (isFullScreenPlayer || showEpgGrid) {
            wasInSubScreen = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onFullScreenChange(false)
            if (viewModel.currentScreen.value !is Screen.Player) {
                viewModel.detachMiniPlayer()
            }
        }
    }

    val channelListState = rememberLazyListState()
    val defaultCategoryFocus = remember { FocusRequester() }
    val channelFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    LaunchedEffect(Unit) {
        val previous = activePreviewChannel
        if (previous != null && !viewModel.playerManager.isPlaying.value) {
            viewModel.previewChannel(previous)
        }
    }

    LaunchedEffect(channels) {
        if (activePreviewChannel == null && channels.isNotEmpty()) {
            viewModel.previewChannel(channels.first())
        }
    }

    val filteredCategories = remember(categories, categoryQuery) {
        val q = categoryQuery.trim()
        if (q.isEmpty()) categories
        else categories.filter { it.categoryName.contains(q, ignoreCase = true) }
    }

    val displayedChannels = remember(channels, selectedCategory, favoriteStreams, recentChannels, channelQuery) {
        val base = when (selectedCategory) {
            "favorites" -> favoriteStreams
            "recents" -> recentChannels
            else -> channels
        }
        val q = channelQuery.trim()
        if (q.isEmpty()) base
        else base.filter { it.name.contains(q, ignoreCase = true) }
    }

    val activeCategoryTitle = remember(selectedCategory, categories) {
        when (selectedCategory) {
            "favorites" -> "Favori Kanallar"
            "recents" -> "Son İzlenenler"
            "all", "" -> "Tüm Kanallar"
            else -> categories.firstOrNull { it.categoryId == selectedCategory }?.categoryName ?: "Kanallar"
        }
    }

    // Görünür kanallar için toplu ve gecikmeli EPG yüklemesi (HTTP fırtınasını önler)
    LaunchedEffect(channelListState, displayedChannels) {
        snapshotFlow {
            channelListState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                displayedChannels.getOrNull(info.index)?.streamId
            }
        }
            .distinctUntilChanged()
            .debounce(150L)
            .collect { visibleIds ->
                if (visibleIds.isNotEmpty()) {
                    viewModel.loadEpgSummariesBatch(visibleIds)
                }
            }
    }

    // Girişte ve tam ekran / rehberden dönüşte doğru kanala odaklan
    LaunchedEffect(isFullScreenPlayer, showEpgGrid, displayedChannels.isNotEmpty()) {
        if (isFullScreenPlayer || showEpgGrid || displayedChannels.isEmpty()) return@LaunchedEffect
        val shouldRestore = !hasInitialFocused || wasInSubScreen
        if (!shouldRestore) return@LaunchedEffect

        val targetId = lastFocusedChannelId
            ?: activePreviewChannel?.streamId
            ?: displayedChannels.first().streamId
        val foundIndex = displayedChannels.indexOfFirst { it.streamId == targetId }
        val targetIndex = if (foundIndex >= 0) foundIndex else 0
        val resolvedId = displayedChannels[targetIndex].streamId

        runCatching {
            val firstVisible = channelListState.firstVisibleItemIndex
            val lastVisible = firstVisible + channelListState.layoutInfo.visibleItemsInfo.size
            if (targetIndex < firstVisible || targetIndex >= lastVisible) {
                channelListState.scrollToItem((targetIndex - 2).coerceAtLeast(0))
            }
        }
        delay(60L)
        val focused = channelFocusRequesters[resolvedId]?.requestFocusSafely() == true
        if (focused) {
            hasInitialFocused = true
            wasInSubScreen = false
        } else if (!hasInitialFocused) {
            defaultCategoryFocus.requestFocusSafely()
            hasInitialFocused = true
            wasInSubScreen = false
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    if (isSearchEditing || quickActionChannel != null) {
                        return@onPreviewKeyEvent false
                    }
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
                            viewModel.zapRecall()?.let { lastFocusedChannelId = it.streamId }
                            return@onPreviewKeyEvent true
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_UP ||
                            keyCode == android.view.KeyEvent.KEYCODE_PAGE_UP) {
                            val currentId = activePreviewChannel?.streamId ?: displayedChannels.firstOrNull()?.streamId
                            if (currentId != null) {
                                viewModel.zapNextChannel(currentId)?.let { lastFocusedChannelId = it.streamId }
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN ||
                            keyCode == android.view.KeyEvent.KEYCODE_PAGE_DOWN) {
                            val currentId = activePreviewChannel?.streamId ?: displayedChannels.firstOrNull()?.streamId
                            if (currentId != null) {
                                viewModel.zapPreviousChannel(currentId)?.let { lastFocusedChannelId = it.streamId }
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
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ==========================================
                // 1. SOL KART: KATEGORİLER (StreamVault LiveTV.png)
                // ==========================================
                Column(
                    modifier = Modifier
                        .width(248.dp)
                        .fillMaxHeight()
                        .vegaPanel(VegaShapes.Large)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Kategoriler",
                        color = VegaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    StreamVaultSearchField(
                        value = categoryQuery,
                        onValueChange = { categoryQuery = it },
                        placeholder = "Kategori ara...",
                        onInputActiveChanged = { isCategorySearchEditing = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hızlı Filtreler Kutusu (Favoriler + Son İzlenenler)
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.SurfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(VegaShapes.Small)
                                    .dpadFocusable(
                                        shape = VegaShapes.Small,
                                        focusedBorderColor = VegaColors.FocusBorder,
                                        scale = 1.02f,
                                        onClick = { showQuickFilters = !showQuickFilters }
                                    )
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Hızlı filtreler",
                                        color = VegaColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (selectedCategory == "favorites") "Favoriler seçili"
                                        else if (selectedCategory == "recents") "Son izlenenler"
                                        else "Tüm kategoriler",
                                        color = VegaColors.TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = if (showQuickFilters) "Gizle" else "Göster",
                                    color = VegaColors.Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (showQuickFilters) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TiviCategoryItem(
                                    title = "Favori Kanallar",
                                    icon = Icons.Default.Star,
                                    iconTint = VegaColors.Gold,
                                    count = favoriteIds.size,
                                    isSelected = selectedCategory == "favorites",
                                    focusRequester = defaultCategoryFocus,
                                    onClick = { viewModel.selectedLiveCategory.value = "favorites" }
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                TiviCategoryItem(
                                    title = "Son İzlenenler",
                                    icon = Icons.Default.History,
                                    iconTint = if (selectedCategory == "recents") VegaColors.Accent else VegaColors.TextSecondary,
                                    count = recentChannels.size,
                                    isSelected = selectedCategory == "recents",
                                    onClick = { viewModel.selectedLiveCategory.value = "recents" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(
                            items = filteredCategories,
                            key = { index, category -> "tv_cat_${category.categoryId}_$index" }
                        ) { _, category ->
                            val isSelected = category.categoryId == selectedCategory
                            TiviCategoryItem(
                                title = category.categoryName,
                                icon = null,
                                iconTint = VegaColors.Accent,
                                count = category.itemCount,
                                isSelected = isSelected,
                                onClick = { viewModel.loadLiveStreams(category.categoryId) }
                            )
                        }
                        // G-052: Listenin en sonunda +18 kilit satırı (yalnız yetişkin kategori varsa görünür)
                        item(key = "parental_lock_row") {
                            val hasAdultLive by viewModel.hasAdultLiveCategory.collectAsState()
                            Spacer(modifier = Modifier.height(6.dp))
                            com.tivimax.player.ui.components.ParentalLockRow(viewModel, hasAdultLive)
                        }
                    }
                }

                // ==========================================
                // 2. ORTA KART: KANAL LİSTESİ (StreamVault LiveTV.png)
                // ==========================================
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                ) {
                    // Başlık ve Tam Rehber Hapı
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeCategoryTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${displayedChannels.size} kanal görüntüleniyor",
                                color = VegaColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.SurfaceElevated,
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier.dpadFocusable(
                                shape = VegaShapes.Small,
                                focusedBorderColor = VegaColors.FocusBorder,
                                onClick = { showEpgGrid = true }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Tam rehber",
                                    color = VegaColors.Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    StreamVaultSearchField(
                        value = channelQuery,
                        onValueChange = { channelQuery = it },
                        placeholder = "Kanal ara...",
                        onInputActiveChanged = { isChannelSearchEditing = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = VegaColors.Accent,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.5.dp
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
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(
                                items = displayedChannels,
                                key = { index, channel -> "ch_${channel.streamId}_${channel.categoryId}_$index" }
                            ) { _, channel ->
                                val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
                                val isFav = favoriteIds.contains(channel.streamId.toString())
                                val epg = epgSummaryMap[channel.streamId]
                                val currentProgram = epg?.getDecodedTitle() ?: channel.currentProgram
                                val progress = epg?.getProgressFraction()
                                val qualityBadges = remember(channel.name) { extractQualityBadges(channel.name) }
                                var isRowFocused by remember { mutableStateOf(false) }
                                val rowFocusRequester = remember(channel.streamId) {
                                    channelFocusRequesters.getOrPut(channel.streamId) { FocusRequester() }
                                }

                                Surface(
                                    shape = VegaShapes.Medium,
                                    color = when {
                                        isRowFocused -> VegaColors.SurfaceVariant
                                        isCurrentPreview -> VegaColors.Accent.copy(alpha = 0.14f)
                                        else -> VegaColors.SurfaceElevated
                                    },
                                    border = if (isRowFocused) null else BorderStroke(
                                        width = if (isCurrentPreview) 1.5.dp else 1.dp,
                                        color = if (isCurrentPreview) VegaColors.Accent.copy(alpha = 0.7f) else VegaColors.Border
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .focusRequester(rowFocusRequester)
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.FocusBorder,
                                            shape = VegaShapes.Medium,
                                            scale = 1.02f,
                                            onClick = {
                                                if (isCurrentPreview) {
                                                    isFullScreenPlayer = true
                                                } else {
                                                    viewModel.previewChannel(channel)
                                                }
                                            },
                                            onLongClick = {
                                                quickActionChannel = channel
                                            },
                                            onFocusStateChange = { focused ->
                                                isRowFocused = focused
                                                if (focused) {
                                                    lastFocusedChannelId = channel.streamId
                                                }
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Logo Kutusu (StreamVault kare logo rozeti)
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(VegaShapes.Small)
                                                .background(VegaColors.SurfaceAccent)
                                                .padding(5.dp),
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

                                        // Kanal Bilgileri
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val numPrefix = if (channel.num > 0) {
                                                    channel.num.toString().padStart(2, '0') + "  "
                                                } else ""
                                                Text(
                                                    text = "$numPrefix${channel.name}",
                                                    color = if (isCurrentPreview || isRowFocused) VegaColors.TextPrimary else VegaColors.TextPrimary.copy(alpha = 0.92f),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                if (isCurrentPreview) {
                                                    AnimatedEqualizer(
                                                        barColor = VegaColors.Accent,
                                                        maxHeight = 10.dp,
                                                        barWidth = 2.dp,
                                                        spacing = 1.5.dp
                                                    )
                                                }

                                                if (isFav) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = VegaColors.Gold,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }

                                                if (qualityBadges.isNotEmpty()) {
                                                    QualityBadge(tag = qualityBadges.first())
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = currentProgram ?: "Program bilgisi yok",
                                                color = if (currentProgram != null) VegaColors.TextSecondary else VegaColors.TextMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (progress != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    color = VegaColors.Accent,
                                                    trackColor = VegaColors.SurfaceAccent,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .clip(VegaShapes.Pill)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. SAĞ KART: KANAL ÖNİZLEME (StreamVault LiveTV.png)
                // ==========================================
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .vegaPanel(VegaShapes.Large)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Kanal Önizleme",
                        color = VegaColors.Accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // 16:9 Canlı Video Önizleme Kutusu
                    var isPreviewFocused by remember { mutableStateOf(false) }
                    Card(
                        shape = VegaShapes.Medium,
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = if (isPreviewFocused) null else BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.FocusBorder,
                                scale = 1.02f,
                                onClick = {
                                    if (activePreviewChannel != null) {
                                        isFullScreenPlayer = true
                                    }
                                },
                                onFocusStateChange = { isPreviewFocused = it }
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

                            previewError?.let { msg ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.78f))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = msg,
                                        color = VegaColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Kanal ve Program Bilgileri (StreamVault LiveTV.png sağ panel düzeni)
                    activePreviewChannel?.let { channel ->
                        val activeEpg = epgSummaryMap[channel.streamId]
                        val progress = activeEpg?.getProgressFraction() ?: 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = channel.name,
                                color = VegaColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = activeEpg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın",
                                color = VegaColors.TextPrimary.copy(alpha = 0.92f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            val timeStr = activeEpg?.getFormattedTime()
                            if (!timeStr.isNullOrBlank()) {
                                Text(
                                    text = timeStr,
                                    color = VegaColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // StreamVault parlayan noktalı ilerleme çubuğu
                            if (activeEpg != null) {
                                StreamVaultProgressBar(
                                    progress = progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                )
                            }

                            val decodedDesc = activeEpg?.getDecodedDescription()
                            if (!decodedDesc.isNullOrBlank()) {
                                Text(
                                    text = decodedDesc,
                                    color = VegaColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (currentEpgList.size > 1) {
                                val nextProg = currentEpgList[1]
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Sonraki: ${nextProg.getFormattedTime()} • ${nextProg.getDecodedTitle()}",
                                    color = VegaColors.TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            var isHintFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = VegaShapes.Small,
                                color = if (isHintFocused) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.Accent.copy(alpha = 0.10f),
                                border = if (isHintFocused) null else BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.30f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        shape = VegaShapes.Small,
                                        focusedBorderColor = VegaColors.FocusBorder,
                                        scale = 1.02f,
                                        onClick = { isFullScreenPlayer = true },
                                        onFocusStateChange = { isHintFocused = it }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = null,
                                        tint = VegaColors.Accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Tam ekran için tekrar OK'a bas",
                                        color = VegaColors.Accent,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // RAKAM İLE KANAL GEÇİŞİ (NUMERIC ZAPPER)
            if (isZapperActive && zapperNumber.isNotBlank()) {
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.Accent,
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 24.dp)
                ) {
                    Text(
                        text = "[ $zapperNumber ] ${zapperMatchedChannel?.name ?: ""}".trim(),
                        color = VegaColors.OnAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // OK UZUN BASMA HIZLI İŞLEM MODALI
            quickActionChannel?.let { targetChannel ->
                val isTargetFav = favoriteIds.contains(targetChannel.streamId.toString())
                val targetEpg = epgSummaryMap[targetChannel.streamId]
                val firstActionFocus = remember(targetChannel.streamId) { FocusRequester() }

                LaunchedEffect(targetChannel.streamId) {
                    delay(40L)
                    firstActionFocus.requestFocusSafely()
                }

                Dialog(onDismissRequest = { quickActionChannel = null }) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 320.dp, max = 420.dp)
                            .vegaPanel(VegaShapes.Large)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.SurfaceAccent)
                                    .padding(5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncShimmerImage(
                                    model = targetChannel.streamIcon,
                                    contentDescription = targetChannel.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholderIcon = Icons.Default.Tv
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = targetChannel.name,
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.SectionTitle,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = targetEpg?.getDecodedTitle()
                                        ?: targetChannel.currentProgram
                                        ?: "Canlı yayın",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)

                        TvChannelQuickActionItem(
                            label = if (isTargetFav) "Favorilerden çıkar" else "Favorilere ekle",
                            icon = if (isTargetFav) Icons.Default.StarBorder else Icons.Default.Star,
                            iconTint = VegaColors.Gold,
                            focusRequester = firstActionFocus,
                            onClick = {
                                viewModel.toggleFavoriteLive(targetChannel.streamId.toString())
                                quickActionChannel = null
                            }
                        )

                        TvChannelQuickActionItem(
                            label = "Program rehberini aç",
                            icon = Icons.Default.Schedule,
                            iconTint = VegaColors.Accent,
                            onClick = {
                                if (activePreviewChannel?.streamId != targetChannel.streamId) {
                                    viewModel.previewChannel(targetChannel)
                                }
                                quickActionChannel = null
                                showEpgGrid = true
                            }
                        )

                        TvChannelQuickActionItem(
                            label = "Tam ekran izle",
                            icon = Icons.Default.PlayArrow,
                            iconTint = VegaColors.Accent,
                            onClick = {
                                if (activePreviewChannel?.streamId != targetChannel.streamId) {
                                    viewModel.previewChannel(targetChannel)
                                }
                                quickActionChannel = null
                                isFullScreenPlayer = true
                            }
                        )

                        TvChannelQuickActionItem(
                            label = "Kapat",
                            icon = Icons.Default.Close,
                            iconTint = VegaColors.TextSecondary,
                            onClick = { quickActionChannel = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvChannelQuickActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Medium,
        color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
        border = if (isFocused) null else BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.FocusBorder,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.Body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun StreamVaultSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onInputActiveChanged: ((Boolean) -> Unit)? = null
) {
    val isTv = LocalIsTvDevice.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val shellFocusRequester = remember { FocusRequester() }
    val inputFocusRequester = remember { FocusRequester() }

    var hasContainerFocus by remember { mutableStateOf(false) }
    var hasInputFocus by remember { mutableStateOf(false) }
    var acceptsInput by remember { mutableStateOf(false) }
    var pendingInputActivation by remember { mutableStateOf(false) }
    val currentOnInputActiveChanged by rememberUpdatedState(onInputActiveChanged)

    val isFocused = hasContainerFocus || hasInputFocus
    val isInputActive = if (isTv) (acceptsInput || hasInputFocus) else hasInputFocus

    LaunchedEffect(isInputActive) {
        currentOnInputActiveChanged?.invoke(isInputActive)
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnInputActiveChanged?.invoke(false)
        }
    }

    fun activateInput() {
        acceptsInput = true
        if (isTv) {
            pendingInputActivation = true
        } else {
            runCatching { inputFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    fun exitInput(moveDirection: FocusDirection? = null) {
        pendingInputActivation = false
        acceptsInput = false
        keyboardController?.hide()
        if (moveDirection != null) {
            val moved = focusManager.moveFocus(moveDirection)
            if (!moved) {
                runCatching { shellFocusRequester.requestFocus() }
            }
        } else {
            runCatching { shellFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(acceptsInput, pendingInputActivation) {
        if (!isTv || !acceptsInput || !pendingInputActivation) return@LaunchedEffect
        runCatching { inputFocusRequester.requestFocus() }
        keyboardController?.show()
        pendingInputActivation = false
    }

    BackHandler(enabled = isTv && acceptsInput) {
        exitInput()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = VegaShapes.Medium,
            color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated.copy(alpha = 0.85f),
            border = if (hasContainerFocus) null else BorderStroke(
                width = if (hasInputFocus) 2.dp else 1.dp,
                color = if (hasInputFocus) VegaColors.FocusBorder else VegaColors.Border
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(shellFocusRequester)
                .dpadFocusable(
                    shape = VegaShapes.Medium,
                    focusedBorderColor = VegaColors.FocusBorder,
                    scale = 1.02f,
                    onClick = { activateInput() },
                    onFocusStateChange = { hasContainerFocus = it }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        readOnly = isTv && !acceptsInput,
                        textStyle = TextStyle(
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(VegaColors.Accent),
                        keyboardOptions = tvKeyboardOptions(KeyboardOptions(imeAction = ImeAction.Search)),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (isTv) {
                                    exitInput(FocusDirection.Down)
                                } else {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocusRequester)
                            .focusProperties {
                                canFocus = if (isTv) acceptsInput else true
                            }
                            .onFocusChanged { state ->
                                val wasInputFocused = hasInputFocus
                                hasInputFocus = state.hasFocus
                                if (wasInputFocused && !state.hasFocus && isTv) {
                                    pendingInputActivation = false
                                    acceptsInput = false
                                    keyboardController?.hide()
                                }
                            }
                            .tvOpenKeyboardOnOk()
                            .onPreviewKeyEvent { keyEvent ->
                                if (!isTv || !acceptsInput) return@onPreviewKeyEvent false
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            exitInput(FocusDirection.Down)
                                            return@onPreviewKeyEvent true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            exitInput(FocusDirection.Up)
                                            return@onPreviewKeyEvent true
                                        }
                                        KeyEvent.KEYCODE_BACK -> {
                                            exitInput()
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                }
                                false
                            }
                    )
                }

                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (value.isNotEmpty()) {
            Surface(
                shape = VegaShapes.Medium,
                color = VegaColors.SurfaceElevated,
                border = BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .size(40.dp)
                    .dpadFocusable(
                        shape = VegaShapes.Medium,
                        focusedBorderColor = VegaColors.FocusBorder,
                        scale = 1.04f,
                        onClick = { onValueChange("") }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Temizle",
                        tint = VegaColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun StreamVaultProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier.height(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(VegaShapes.Pill)
                .background(VegaColors.SurfaceAccent)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(3.dp)
                .clip(VegaShapes.Pill)
                .background(VegaColors.Accent)
        )
        Box(
            modifier = Modifier
                .offset(x = (maxWidth * clamped - 3.dp).coerceIn(0.dp, (maxWidth - 6.dp).coerceAtLeast(0.dp)))
                .size(6.dp)
                .clip(CircleShape)
                .background(VegaColors.FocusBorder)
        )
    }
}

@Composable
private fun TiviCategoryItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    iconTint: Color,
    count: Int,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Small,
        color = when {
            isFocused -> VegaColors.SurfaceVariant
            isSelected -> VegaColors.Accent.copy(alpha = 0.16f)
            else -> Color.Transparent
        },
        border = if (isSelected && !isFocused) BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.35f)) else null,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = title,
                color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$count",
                    color = if (isSelected) VegaColors.Accent else VegaColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
