package com.tivimax.player.ui.tv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
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

    val zapperNumber by viewModel.zapperNumber.collectAsState()
    val isZapperActive by viewModel.isZapperActive.collectAsState()
    val zapperMatchedChannel by viewModel.zapperMatchedChannel.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isPlayerScreen = currentScreen is Screen.Player

    var isFullScreenPlayer by remember { mutableStateOf(false) }
    var showEpgGrid by remember { mutableStateOf(false) }
    var categoryQuery by remember { mutableStateOf("") }
    var channelQuery by remember { mutableStateOf("") }
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
                // 1. SOL SÜTUN: KATEGORİLER
                // ==========================================
                Column(
                    modifier = Modifier
                        .width(238.dp)
                        .fillMaxHeight()
                        .vegaPanel(VegaShapes.Large)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Kategoriler",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
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

                    // Hızlı erişim satırları (çerçevesiz, sade TiviMate/Infuse tarzı)
                    TiviCategoryItem(
                        title = "Favori Kanallar",
                        icon = Icons.Default.Star,
                        iconTint = VegaColors.Gold,
                        count = favoriteIds.size,
                        isSelected = selectedCategory == "favorites",
                        focusRequester = defaultCategoryFocus,
                        onClick = { viewModel.selectedLiveCategory.value = "favorites" }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    TiviCategoryItem(
                        title = "Son İzlenenler",
                        icon = Icons.Default.History,
                        iconTint = if (selectedCategory == "recents") VegaColors.Accent else VegaColors.TextSecondary,
                        count = recentChannels.size,
                        isSelected = selectedCategory == "recents",
                        onClick = { viewModel.selectedLiveCategory.value = "recents" }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = VegaColors.Border.copy(alpha = 0.45f),
                        thickness = 0.5.dp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                // 2. ORTA SÜTUN: KANAL LİSTESİ (EPG izole bileşen)
                // ==========================================
                TvChannelListPane(
                    viewModel = viewModel,
                    activeCategoryTitle = activeCategoryTitle,
                    displayedChannels = displayedChannels,
                    isLoading = isLoading,
                    activePreviewChannelId = activePreviewChannel?.streamId,
                    favoriteIds = favoriteIds,
                    channelQuery = channelQuery,
                    onChannelQueryChange = { channelQuery = it },
                    onSearchActiveChanged = { isChannelSearchEditing = it },
                    channelListState = channelListState,
                    channelFocusRequesters = channelFocusRequesters,
                    onOpenEpgGrid = { showEpgGrid = true },
                    onChannelClick = { channel, isCurrentPreview ->
                        if (isCurrentPreview) {
                            isFullScreenPlayer = true
                        } else {
                            viewModel.previewChannel(channel)
                        }
                    },
                    onChannelLongClick = { quickActionChannel = it },
                    onChannelFocused = { lastFocusedChannelId = it },
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                )

                // ==========================================
                // 3. SAĞ SÜTUN: 16:9 CANLI ÖNİZLEME + YAYIN BİLGİSİ
                // ==========================================
                val openFullScreenFromPreview = remember { { isFullScreenPlayer = true } }
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .vegaPanel(VegaShapes.Large)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvPreviewPlayerBox(
                        viewModel = viewModel,
                        activePreviewChannel = activePreviewChannel,
                        isPlayerScreen = isPlayerScreen,
                        isFullScreenPlayer = isFullScreenPlayer,
                        showEpgGrid = showEpgGrid,
                        onOpenFullScreen = openFullScreenFromPreview
                    )

                    TvPreviewChannelInfo(
                        viewModel = viewModel,
                        activePreviewChannel = activePreviewChannel,
                        onOpenFullScreen = openFullScreenFromPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
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
                val dialogEpgMap by viewModel.epgSummaryMap.collectAsState()
                val targetEpg = dialogEpgMap[targetChannel.streamId]
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

                        HorizontalDivider(color = VegaColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)

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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvChannelListPane(
    viewModel: MainViewModel,
    activeCategoryTitle: String,
    displayedChannels: List<LiveStream>,
    isLoading: Boolean,
    activePreviewChannelId: Int?,
    favoriteIds: Set<String>,
    channelQuery: String,
    onChannelQueryChange: (String) -> Unit,
    onSearchActiveChanged: (Boolean) -> Unit,
    channelListState: LazyListState,
    channelFocusRequesters: MutableMap<Int, FocusRequester>,
    onOpenEpgGrid: () -> Unit,
    onChannelClick: (LiveStream, Boolean) -> Unit,
    onChannelLongClick: (LiveStream) -> Unit,
    onChannelFocused: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    var isGuideBtnFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
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
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${displayedChannels.size} kanal",
                    color = VegaColors.TextMuted,
                    fontSize = VegaText.Caption
                )
            }

            Surface(
                shape = VegaShapes.Small,
                color = if (isGuideBtnFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
                modifier = Modifier.dpadFocusable(
                    shape = VegaShapes.Small,
                    focusedBorderColor = VegaColors.FocusBorder,
                    scale = 1.02f,
                    onClick = onOpenEpgGrid,
                    onFocusStateChange = { isGuideBtnFocused = it }
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
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        StreamVaultSearchField(
            value = channelQuery,
            onValueChange = onChannelQueryChange,
            placeholder = "Kanal ara...",
            onInputActiveChanged = onSearchActiveChanged
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
                    fontSize = VegaText.Secondary
                )
            }
        } else {
            LazyColumn(
                state = channelListState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(
                    items = displayedChannels,
                    key = { index, channel -> "ch_${channel.streamId}_${channel.categoryId}_$index" }
                ) { _, channel ->
                    val isCurrentPreview = activePreviewChannelId == channel.streamId
                    val isFav = favoriteIds.contains(channel.streamId.toString())
                    val epg = epgSummaryMap[channel.streamId]
                    val rowFocusRequester = remember(channel.streamId) {
                        channelFocusRequesters.getOrPut(channel.streamId) { FocusRequester() }
                    }

                    TvChannelRow(
                        channel = channel,
                        epg = epg,
                        isCurrentPreview = isCurrentPreview,
                        isFav = isFav,
                        focusRequester = rowFocusRequester,
                        onClick = { onChannelClick(channel, isCurrentPreview) },
                        onLongClick = { onChannelLongClick(channel) },
                        onFocused = { onChannelFocused(channel.streamId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvChannelRow(
    channel: LiveStream,
    epg: EpgProgram?,
    isCurrentPreview: Boolean,
    isFav: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit
) {
    val currentProgram = epg?.getDecodedTitle()?.takeIf { it.isNotBlank() }
        ?: channel.currentProgram?.takeIf { it.isNotBlank() }
    val progress = epg?.getProgressFraction()
    val qualityBadges = remember(channel.name) { extractQualityBadges(channel.name) }
    var isRowFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .focusRequester(focusRequester)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                scale = 1.015f,
                onClick = onClick,
                onLongClick = onLongClick,
                onFocusStateChange = { focused ->
                    isRowFocused = focused
                    if (focused) onFocused()
                }
            )
            .clip(VegaShapes.Small)
            .background(
                when {
                    isRowFocused -> VegaColors.SurfaceVariant
                    isCurrentPreview -> VegaColors.SurfaceElevated
                    else -> Color.Transparent
                }
            )
    ) {
        // Aktif önizleme sol vurgu şeridi
        if (isCurrentPreview) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .width(3.dp)
                    .height(34.dp)
                    .clip(VegaShapes.Pill)
                    .background(VegaColors.Accent)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (channel.num > 0) {
                Text(
                    text = channel.num.toString(),
                    color = if (isCurrentPreview || isRowFocused) VegaColors.Accent else VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 24.dp)
                )
            }

            // Logo Kutusu
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(VegaShapes.Small)
                    .background(VegaColors.SurfaceAccent.copy(alpha = 0.7f))
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

            // Kanal ve EPG Bilgisi
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = channel.name,
                        color = if (isCurrentPreview || isRowFocused) VegaColors.TextPrimary else VegaColors.TextPrimary.copy(alpha = 0.92f),
                        fontSize = VegaText.Body,
                        fontWeight = if (isCurrentPreview || isRowFocused) FontWeight.Bold else FontWeight.SemiBold,
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

                if (currentProgram != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentProgram,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
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

        // Odaksız satırlar arası ince ayırıcı çizgi
        if (!isRowFocused && !isCurrentPreview) {
            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp),
                color = VegaColors.Border.copy(alpha = 0.28f),
                thickness = 0.5.dp
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TvPreviewPlayerBox(
    viewModel: MainViewModel,
    activePreviewChannel: LiveStream?,
    isPlayerScreen: Boolean,
    isFullScreenPlayer: Boolean,
    showEpgGrid: Boolean,
    onOpenFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerInstanceVersion by viewModel.playerManager.playerInstanceVersion.collectAsState()
    var previewPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var isPreviewFocused by remember { mutableStateOf(false) }

    LaunchedEffect(
        previewPlayerView,
        playerInstanceVersion,
        activePreviewChannel?.streamId,
        isPlayerScreen,
        isFullScreenPlayer,
        showEpgGrid
    ) {
        val view = previewPlayerView ?: return@LaunchedEffect
        if (!isPlayerScreen && !isFullScreenPlayer && !showEpgGrid) {
            viewModel.playerManager.attachView(view, isPrimary = false, tag = "TvTiviMateScreen-preview")
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.FocusBorder,
                scale = 1.0f,
                onClick = {
                    if (activePreviewChannel != null) {
                        onOpenFullScreen()
                    }
                },
                onFocusStateChange = { isPreviewFocused = it }
            )
            .clip(VegaShapes.Medium)
            .background(Color.Black)
    ) {
        if (!isPlayerScreen) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.player_texture_view, null, false) as PlayerView).apply {
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
                        previewPlayerView = this
                        if (!isFullScreenPlayer && !showEpgGrid) {
                            viewModel.playerManager.attachView(this, isPrimary = false, tag = "TvTiviMateScreen-preview")
                        }
                    }
                },
                update = { /* Yeniden çizimde attachView çağırma; LaunchedEffect yönetir */ },
                onRelease = { playerView ->
                    if (previewPlayerView === playerView) {
                        previewPlayerView = null
                    }
                    viewModel.playerManager.detachView(playerView, tag = "TvTiviMateScreen-preview")
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        TvPreviewStatusOverlay(
            viewModel = viewModel,
            activePreviewChannel = activePreviewChannel,
            isPreviewFocused = isPreviewFocused
        )
    }
}

@Composable
private fun BoxScope.TvPreviewStatusOverlay(
    viewModel: MainViewModel,
    activePreviewChannel: LiveStream?,
    isPreviewFocused: Boolean
) {
    val isPreviewBuffering by viewModel.playerManager.isBuffering.collectAsState()
    val isPreviewPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val previewError by viewModel.playerManager.errorMessage.collectAsState()
    val reconnectStatus by viewModel.playerManager.reconnectStatus.collectAsState()

    if (previewError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = previewError ?: "",
                color = VegaColors.TextPrimary,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else if (activePreviewChannel != null && isPreviewBuffering) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(VegaShapes.Small)
                        .background(VegaColors.SurfaceAccent.copy(alpha = 0.85f))
                        .padding(5.dp),
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

                Text(
                    text = activePreviewChannel.name,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                CircularProgressIndicator(
                    color = VegaColors.Accent,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )

                if (!reconnectStatus.isNullOrBlank()) {
                    Text(
                        text = reconnectStatus ?: "",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else if (activePreviewChannel != null && (isPreviewPlaying || isPreviewFocused)) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(VegaShapes.Small)
                .background(VegaColors.OverlayPill)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(VegaShapes.Pill)
                    .background(VegaColors.Live)
            )
            Text(
                text = if (isPreviewFocused) "Tam ekran (OK)" else "Canlı",
                color = Color.White,
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TvPreviewChannelInfo(
    viewModel: MainViewModel,
    activePreviewChannel: LiveStream?,
    onOpenFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    val currentEpgList by viewModel.currentEpgList.collectAsState()

    val channel = activePreviewChannel ?: return
    val activeEpg = epgSummaryMap[channel.streamId]
    val progress = activeEpg?.getProgressFraction() ?: 0f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Kanal adı + canlı göstergesi
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (channel.num > 0) "${channel.num}. ${channel.name}" else channel.name,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.SectionTitle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(VegaShapes.Pill)
                        .background(VegaColors.Live)
                )
                Text(
                    text = "Canlı",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Aktif program ve saat aralığı
        val programTitle = activeEpg?.getDecodedTitle()?.takeIf { it.isNotBlank() }
            ?: channel.currentProgram?.takeIf { it.isNotBlank() }
        if (programTitle != null) {
            Text(
                text = programTitle,
                color = VegaColors.TextPrimary.copy(alpha = 0.92f),
                fontSize = VegaText.BodyProminent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        val timeStr = activeEpg?.getFormattedTime()
        if (!timeStr.isNullOrBlank()) {
            Text(
                text = timeStr,
                color = VegaColors.TextMuted,
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.Medium
            )
        }

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
                fontSize = VegaText.Secondary,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (currentEpgList.size > 1) {
            val nextProg = currentEpgList[1]
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = VegaColors.Border.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
            Text(
                text = "Sonraki: ${nextProg.getFormattedTime()} • ${nextProg.getDecodedTitle()}",
                color = VegaColors.TextMuted,
                fontSize = VegaText.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        var isHintFocused by remember { mutableStateOf(false) }
        Surface(
            shape = VegaShapes.Small,
            color = if (isHintFocused) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.Accent.copy(alpha = 0.12f),
            modifier = Modifier
                .fillMaxWidth()
                .dpadFocusable(
                    shape = VegaShapes.Small,
                    focusedBorderColor = VegaColors.FocusBorder,
                    scale = 1.02f,
                    onClick = onOpenFullScreen,
                    onFocusStateChange = { isHintFocused = it }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                scale = 1.015f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .clip(VegaShapes.Small)
            .background(
                when {
                    isFocused -> VegaColors.SurfaceVariant
                    isSelected -> VegaColors.SurfaceElevated
                    else -> Color.Transparent
                }
            )
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .width(3.dp)
                    .height(20.dp)
                    .clip(VegaShapes.Pill)
                    .background(VegaColors.Accent)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                color = when {
                    isFocused -> VegaColors.TextPrimary
                    isSelected -> VegaColors.Accent
                    else -> VegaColors.TextPrimary.copy(alpha = 0.88f)
                },
                fontSize = VegaText.Secondary,
                fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$count",
                    color = if (isSelected) VegaColors.Accent else VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
