package com.tivimax.player.ui.live.modern

import androidx.activity.compose.BackHandler
import com.tivimax.player.util.LocalIsTvDevice
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.GlobalSearchOptionsCard
import kotlinx.coroutines.flow.collectLatest

/**
 * Modern ve Akıcı Canlı TV Dikey Telefon Düzeni (Netflix / Spotify / TiviMate Deneyimi).
 *
 * Özellikler:
 * 1. Sol dikey kategori sütunu kaldırılmıştır; tam ekran kanal listesine ayrılmıştır.
 * 2. Üstte modern arama, görünüm modu, sıralama ve yenileme çubuğu.
 * 3. Yapışkan (sticky) yatay kategori çipleri ("Tümü", "Favoriler", kategoriler ve "Tümü ▾" açılır listesi).
 * 4. "Tümü" seçiliyken Netflix tarzı üst raflar ("Devam Et", "Favoriler").
 * 5. 3 farklı görünüm modu (Kompakt Liste ~74dp, 3 Sütunlu Izgara, Detaylı Liste).
 * 6. Doğrudan karta dokunarak oynatma (gereksiz play butonu yok).
 * 7. Canlı TV sekmesine tekrar dokunulduğunda en üste kaydırma desteği.
 * 8. Alt navigasyon barı arkasında kalmaması için 96dp bottom padding.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveTvModernPortraitLayout(
    viewModel: MainViewModel,
    categories: List<LiveCategory>,
    allCategories: List<LiveCategory>,
    selectedCategory: String,
    displayedChannels: List<LiveStream>,
    activePreviewChannel: LiveStream?,
    isMiniPlayerActive: Boolean,
    epgSummaryMap: Map<Int, EpgProgram>,
    favoriteIds: Set<String>,
    recentChannels: List<LiveStream>,
    currentSort: SortOption,
    isLoading: Boolean,
    searchQuery: String,
    isParentalUnlocked: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onOpenCategoryManager: () -> Unit,
    onOpenParentalDialog: () -> Unit,
    onSelectProgramDetail: (LiveStream, EpgProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentViewMode by viewModel.liveViewMode.collectAsState()
    val epgProgramsMap by viewModel.epgProgramsMap.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isPlayerScreen = currentScreen is Screen.Player

    var showSortSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }

    val isTvDevice = LocalIsTvDevice.current
    val hasActiveCategory = selectedCategory != "all" && selectedCategory.isNotEmpty()
    val hasSearch = searchQuery.isNotBlank()

    BackHandler(
        enabled = !isTvDevice && (showSortSheet || showCategorySheet || hasSearch || hasActiveCategory)
    ) {
        when {
            showSortSheet -> showSortSheet = false
            showCategorySheet -> showCategorySheet = false
            hasSearch -> onSearchQueryChange("")
            hasActiveCategory -> viewModel.loadLiveStreams("all")
        }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Canlı TV sekmesine tekrar dokunulduğunda en üste yumuşak kaydır
    LaunchedEffect(Unit) {
        viewModel.scrollToTopLiveEvent.collectLatest {
            listState.animateScrollToItem(0)
            gridState.animateScrollToItem(0)
        }
    }

    // Kategori değiştiğinde en tepeye sıfırla
    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    // Arama filtrelemesi (Hem yerel Türkçe uyumlu normalizasyon hem de ViewModel desteği)
    val filteredChannels = remember(displayedChannels, searchQuery) {
        if (searchQuery.isBlank()) {
            displayedChannels
        } else {
            displayedChannels.filter { channel ->
                TurkishStringNormalizer.matches(channel.name, searchQuery) ||
                        TurkishStringNormalizer.matches(channel.currentProgram ?: "", searchQuery)
            }
        }
    }

    // Paging: İlk açılışta ve kaydırdıkça yüklenen kanal sayısı (60fps için)
    var visibleLimit by remember(filteredChannels) { mutableIntStateOf(50) }
    val pagedChannels = remember(filteredChannels, visibleLimit) {
        filteredChannels.take(visibleLimit)
    }

    // Favori kanallar listesi (Üst raf için)
    val favoriteChannels = remember(displayedChannels, favoriteIds) {
        displayedChannels.filter { favoriteIds.contains(it.streamId.toString()) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // 1. DİKEY CANLI MİNİ-PLAYER ÖNİZLEME (Aktifse üstte görünür)
        AnimatedVisibility(
            visible = isMiniPlayerActive && activePreviewChannel != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            activePreviewChannel?.let { channel ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .clickable { viewModel.expandMiniPlayerToFullscreen() }
                ) {
                    if (!isPlayerScreen) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    viewModel.playerManager.attachView(this, isPrimary = false, tag = "LiveTvModernPortrait-preview")
                                }
                            },
                            update = { playerView ->
                                viewModel.playerManager.attachView(playerView, isPrimary = false, tag = "LiveTvModernPortrait-preview")
                            },
                            onRelease = { playerView ->
                                viewModel.playerManager.detachView(playerView, tag = "LiveTvModernPortrait-preview")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TiviAccentLive)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CANLI",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.expandMiniPlayerToFullscreen() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tam Ekran",
                                    tint = OpusCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.closeMiniPlayer() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. ÜST BAR (Arama + Görünüm Modu + Sıralama + Yenile + Raf Düzeni Butonu)
        LiveTvTopBar(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            currentViewMode = currentViewMode,
            onViewModeToggle = {
                val nextMode = when (currentViewMode) {
                    UserPreferences.LIVE_VIEW_COMPACT -> UserPreferences.LIVE_VIEW_GRID
                    UserPreferences.LIVE_VIEW_GRID -> UserPreferences.LIVE_VIEW_DETAILED
                    else -> UserPreferences.LIVE_VIEW_COMPACT
                }
                viewModel.setLiveViewMode(nextMode)
            },
            onOpenSortSheet = { showSortSheet = true },
            onRefresh = { viewModel.refreshLiveChannels() },
            onToggleShelfLayout = { viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF) }
        )

        // 3. YATAY KATEGORİ ÇİP SATIRI ("Tümü", "Favoriler", kategoriler, "Tümü ▾")
        CategoryChipsRow(
            categories = categories,
            selectedCategory = selectedCategory,
            totalChannelsCount = displayedChannels.size,
            favoriteChannelsCount = favoriteIds.size,
            onCategorySelected = { catId ->
                if (catId == "favorites") {
                    viewModel.selectedLiveCategory.value = "favorites"
                } else {
                    viewModel.loadLiveStreams(catId)
                }
            },
            onOpenAllCategories = { showCategorySheet = true }
        )

        // 4. KANAL LİSTESİ VEYA IZGARA ALANI
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading && displayedChannels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OpusCyan)
                }
            } else if (filteredChannels.isEmpty()) {
                // Boş Durum (Empty State)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = TiviTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "\"$searchQuery\" ile eşleşen kanal bulunamadı."
                            } else {
                                "Bu kategoride henüz yayın bulunmuyor."
                            },
                            color = TiviTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            GlobalSearchOptionsCard(
                                query = searchQuery,
                                currentScope = "live",
                                isAllCategory = selectedCategory == "all",
                                onSearchAllLive = { viewModel.searchAcrossAllLive(searchQuery) },
                                onSearchAllVod = { viewModel.searchAcrossAllVod(searchQuery) },
                                onSearchAllSeries = { viewModel.searchAcrossAllSeries(searchQuery) }
                            )
                        }
                    }
                }
            } else {
                when (currentViewMode) {
                    UserPreferences.LIVE_VIEW_GRID -> {
                        // ====================================================================
                        // 2. MOD: 3 SÜTUNLU IZGARA (GRID)
                        // ====================================================================
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = pagedChannels,
                                key = { "grid_${it.streamId}_${it.num}" }
                            ) { channel ->
                                val isFavorite = favoriteIds.contains(channel.streamId.toString())
                                val isCurrentPlaying = activePreviewChannel?.streamId == channel.streamId &&
                                        (isMiniPlayerActive || viewModel.playerManager.player?.isPlaying == true)

                                ChannelGridCard(
                                    channel = channel,
                                    isFavorite = isFavorite,
                                    isPlaying = isCurrentPlaying,
                                    onChannelClick = { viewModel.playLiveChannel(channel) },
                                    onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                                )
                            }

                            // Infinite Scroll / Lazy Chunking
                            if (visibleLimit < filteredChannels.size) {
                                item {
                                    LaunchedEffect(Unit) {
                                        visibleLimit += 30
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = OpusCyan,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    UserPreferences.LIVE_VIEW_DETAILED -> {
                        // ====================================================================
                        // 3. MOD: DETAYLI LİSTE
                        // ====================================================================
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = pagedChannels,
                                key = { "detail_${it.streamId}_${it.num}" }
                            ) { channel ->
                                val isFavorite = favoriteIds.contains(channel.streamId.toString())
                                val isCurrentPlaying = activePreviewChannel?.streamId == channel.streamId &&
                                        (isMiniPlayerActive || viewModel.playerManager.player?.isPlaying == true)
                                val currentEpg = epgSummaryMap[channel.streamId]
                                val channelPrograms = epgProgramsMap[channel.streamId]
                                val nextEpg = remember(channelPrograms, currentEpg) {
                                    if (channelPrograms != null && currentEpg != null) {
                                        val idx = channelPrograms.indexOfFirst { it.id == currentEpg.id }
                                        if (idx >= 0 && idx + 1 < channelPrograms.size) {
                                            channelPrograms[idx + 1]
                                        } else {
                                            val nowSec = System.currentTimeMillis() / 1000L
                                            val threshold = if (currentEpg.stopTimestamp > 0) currentEpg.stopTimestamp else nowSec
                                            channelPrograms.firstOrNull { it.startTimestamp >= threshold }
                                        }
                                    } else {
                                        null
                                    }
                                }

                                ChannelDetailedCard(
                                    channel = channel,
                                    epg = currentEpg,
                                    nextEpg = nextEpg,
                                    isFavorite = isFavorite,
                                    isPlaying = isCurrentPlaying,
                                    onChannelClick = { viewModel.playLiveChannel(channel) },
                                    onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) },
                                    onLoadEpg = { viewModel.loadEpgSummary(channel.streamId) }
                                )
                            }

                            if (visibleLimit < filteredChannels.size) {
                                item {
                                    LaunchedEffect(Unit) {
                                        visibleLimit += 30
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = OpusCyan,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // ====================================================================
                        // 1. MOD: VARSAYILAN KOMPAKT LİSTE (~74dp kartlar, tek ekranda 8-9 kanal)
                        // ====================================================================
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // ÜST RAFLAR (Sadece "Tümü" seçiliyken ve arama yapılmıyorken gösterilir!)
                            if (selectedCategory == "all" && searchQuery.isBlank()) {
                                item(key = "section_shelves") {
                                    LiveShelvesSection(
                                        recentChannels = recentChannels,
                                        favoriteChannels = favoriteChannels,
                                        epgSummaryMap = epgSummaryMap,
                                        currentPlayingId = activePreviewChannel?.streamId,
                                        onChannelClick = { channel ->
                                            viewModel.playLiveChannel(channel)
                                        },
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }
                            }

                            // Kanal Kartları
                            items(
                                items = pagedChannels,
                                key = { "compact_${it.streamId}_${it.num}" }
                            ) { channel ->
                                val isFavorite = favoriteIds.contains(channel.streamId.toString())
                                val isCurrentPlaying = activePreviewChannel?.streamId == channel.streamId &&
                                        (isMiniPlayerActive || viewModel.playerManager.player?.isPlaying == true)

                                ChannelRowCard(
                                    channel = channel,
                                    epg = epgSummaryMap[channel.streamId],
                                    isFavorite = isFavorite,
                                    isPlaying = isCurrentPlaying,
                                    onChannelClick = { viewModel.playLiveChannel(channel) },
                                    onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) },
                                    onLoadEpg = { viewModel.loadEpgSummary(channel.streamId) },
                                    onShowEpgDetail = {
                                        epgSummaryMap[channel.streamId]?.let { prog ->
                                            onSelectProgramDetail(channel, prog)
                                        }
                                    }
                                )
                            }

                            // Infinite Scroll / Chunking
                            if (visibleLimit < filteredChannels.size) {
                                item {
                                    LaunchedEffect(Unit) {
                                        visibleLimit += 30
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = OpusCyan,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
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

    // --- SIRALAMA SEÇENEKLERİ BOTTOM SHEET ---
    if (showSortSheet) {
        LiveSortBottomSheet(
            currentSort = currentSort,
            onSortSelected = { sortOption ->
                viewModel.setLiveSort(sortOption)
            },
            onDismiss = { showSortSheet = false }
        )
    }

    // --- TAM KATEGORİ SEÇİCİ BOTTOM SHEET ("Tümü ▾") ---
    if (showCategorySheet) {
        CategorySelectorBottomSheet(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { categoryId ->
                if (categoryId == "favorites") {
                    viewModel.selectedLiveCategory.value = "favorites"
                } else {
                    viewModel.loadLiveStreams(categoryId)
                }
            },
            onDismiss = { showCategorySheet = false }
        )
    }
}
