package com.tivimax.player.ui.live

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.ActionChipButton
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.GlobalSearchOptionsCard
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.live.modern.LiveTvModernPortraitLayout
import com.tivimax.player.ui.live.shelf.ShelfLiveLayout
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleLiveCategories.collectAsState()
    val allCategories by viewModel.liveCategories.collectAsState()
    val isParentalUnlocked by viewModel.isParentalUnlocked.collectAsState()
    val liveLayoutMode by viewModel.liveLayoutMode.collectAsState()
    val allLiveStreams by viewModel.allLiveStreams.collectAsState()

    val channels by viewModel.liveStreams.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val isLoading by viewModel.liveLoading.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
    val favoriteStreams by viewModel.favoriteLiveStreams.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val currentSort by viewModel.liveSortOption.collectAsState()

    val isMiniPlayerActive by viewModel.isMiniPlayerActive.collectAsState()
    val activePreviewChannel by viewModel.activePreviewChannel.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    val currentEpgList by viewModel.currentEpgList.collectAsState()

    val globalQuery by viewModel.globalSearchQuery.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var showParentalPinDialog by remember { mutableStateOf(false) }
    var selectedProgramDetail by remember { mutableStateOf<Pair<LiveStream, EpgProgram>?>(null) }

    LaunchedEffect(globalQuery) {
        if (globalQuery.isNotBlank() && searchQuery != globalQuery) {
            searchQuery = globalQuery
            viewModel.setLiveSearchQuery(globalQuery)
        }
    }

    LaunchedEffect(searchQuery) {
        viewModel.setLiveSearchQuery(searchQuery)
    }

    // Raf düzenine geçildiğinde canlı önizleme sesini ve bağlantısını anında durdur
    LaunchedEffect(liveLayoutMode) {
        if (liveLayoutMode == UserPreferences.LIVE_LAYOUT_SHELF) {
            viewModel.detachMiniPlayer()
        }
    }

    val displayedChannels = remember(channels, selectedCategory, favoriteStreams, recentChannels) {
        when (selectedCategory) {
            "favorites" -> favoriteStreams
            "recents" -> recentChannels
            else -> channels
        }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val isTvDevice = LocalIsTvDevice.current
    val hasActiveCategory = selectedCategory != "all" && selectedCategory.isNotEmpty()
    val hasSearch = searchQuery.isNotBlank()

    BackHandler(
        enabled = !isTvDevice && isWideScreen && (hasSearch || hasActiveCategory)
    ) {
        if (hasSearch) {
            searchQuery = ""
            viewModel.setLiveSearchQuery("")
        } else if (hasActiveCategory) {
            viewModel.loadLiveStreams("all")
        }
    }

    // Geniş ekranda ilk açılışta ilk kanalı otomatik önizlemeye al
    LaunchedEffect(isWideScreen, channels, liveLayoutMode) {
        if (liveLayoutMode != UserPreferences.LIVE_LAYOUT_SHELF && isWideScreen && activePreviewChannel == null && channels.isNotEmpty()) {
            if (viewModel.playerManager.player?.isPlaying != true) {
                viewModel.previewChannel(channels.first())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.currentScreen.value !is Screen.Player) {
                viewModel.closeMiniPlayer()
                viewModel.playerManager.stopAndClear()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        if (liveLayoutMode == UserPreferences.LIVE_LAYOUT_SHELF) {
            ShelfLiveLayout(
                allLiveStreams = allLiveStreams,
                visibleCategories = categories,
                recentChannels = recentChannels,
                favoriteIds = favoriteIds,
                epgSummaryMap = epgSummaryMap,
                currentPlayingId = activePreviewChannel?.streamId,
                isLoading = isLoading,
                onChannelClick = { channel ->
                    viewModel.playLiveChannel(channel)
                },
                onFavoriteToggle = { channel ->
                    viewModel.toggleFavoriteLive(channel.streamId.toString())
                },
                onLoadEpg = { streamId ->
                    viewModel.loadEpgSummary(streamId)
                },
                onRefresh = {
                    viewModel.refreshLiveChannels()
                },
                onToggleLayout = {
                    viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_CLASSIC)
                }
            )
        } else if (isWideScreen) {
            LiveTvWideScreenLayout(
                viewModel = viewModel,
                categories = categories,
                allCategories = allCategories,
                selectedCategory = selectedCategory,
                displayedChannels = displayedChannels,
                activePreviewChannel = activePreviewChannel,
                epgSummaryMap = epgSummaryMap,
                currentEpgList = currentEpgList,
                favoriteIds = favoriteIds,
                recentChannels = recentChannels,
                isLoading = isLoading,
                isParentalUnlocked = isParentalUnlocked,
                onOpenCategoryManager = { showCategoryManagerDialog = true },
                onOpenParentalDialog = {
                    showParentalPinDialog = true
                },
                onSelectProgramDetail = { stream, prog -> selectedProgramDetail = Pair(stream, prog) }
            )
        } else {
            LiveTvModernPortraitLayout(
                viewModel = viewModel,
                categories = categories,
                allCategories = allCategories,
                selectedCategory = selectedCategory,
                displayedChannels = displayedChannels,
                activePreviewChannel = activePreviewChannel,
                isMiniPlayerActive = isMiniPlayerActive,
                epgSummaryMap = epgSummaryMap,
                favoriteIds = favoriteIds,
                recentChannels = recentChannels,
                currentSort = currentSort,
                isLoading = isLoading,
                searchQuery = searchQuery,
                isParentalUnlocked = isParentalUnlocked,
                onSearchQueryChange = {
                    searchQuery = it
                    viewModel.setLiveSearchQuery(it)
                },
                onOpenCategoryManager = { showCategoryManagerDialog = true },
                onOpenParentalDialog = {
                    showParentalPinDialog = true
                },
                onSelectProgramDetail = { stream, prog -> selectedProgramDetail = Pair(stream, prog) }
            )
        }

        // --- KATEGORİ YÖNETİMİ DİYALOGU ---
        if (showCategoryManagerDialog) {
            CategoryManagementDialog(
                viewModel = viewModel,
                initialTab = 0,
                onDismiss = { showCategoryManagerDialog = false }
            )
        }

        // --- EBEVEYN KİLİDİ PIN DİYALOGU ---
        if (showParentalPinDialog) {
            com.tivimax.player.ui.components.ParentalUnlockDialog(
                viewModel = viewModel,
                onDismiss = { showParentalPinDialog = false }
            )
        }

        // --- PROGRAM DETAY ALT SAYFASI ---
        if (selectedProgramDetail != null) {
            val (channel, program) = selectedProgramDetail!!
            ModalBottomSheet(
                onDismissRequest = { selectedProgramDetail = null },
                containerColor = VegaColors.Surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = VegaColors.Border) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = channel.name,
                                color = VegaColors.Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = VegaText.Body
                            )
                            Text(
                                text = program.getFormattedTime(),
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = program.getDecodedTitle(),
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )

                    val progress = program.getProgressFraction()
                    if (progress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = VegaColors.Accent,
                            trackColor = VegaColors.SurfaceAccent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(VegaShapes.Pill)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val decodedProgramDesc = program.getDecodedDescription()
                    Text(
                        text = if (!decodedProgramDesc.isNullOrBlank()) {
                            decodedProgramDesc
                        } else {
                            "Bu program için detaylı özet bilgisi bulunmuyor."
                        },
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            selectedProgramDetail = null
                            viewModel.playLiveChannel(channel)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VegaColors.Accent,
                            contentColor = VegaColors.OnAccent
                        ),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bu kanalı tam ekran izle",
                            color = VegaColors.OnAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = VegaText.Body
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// KLASİK DİKEY TELEFON DÜZENİ
// ====================================================================
@OptIn(UnstableApi::class)
@Composable
fun LiveTvPortraitLayout(
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
    onSelectProgramDetail: (LiveStream, EpgProgram) -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isPlayerScreen = currentScreen is Screen.Player
    val playerInstanceVersion by viewModel.playerManager.playerInstanceVersion.collectAsState()
    val isPreviewBuffering by viewModel.playerManager.isBuffering.collectAsState()
    var previewPlayerView by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(previewPlayerView, playerInstanceVersion, activePreviewChannel?.streamId, isPlayerScreen) {
        val view = previewPlayerView ?: return@LaunchedEffect
        if (!isPlayerScreen && activePreviewChannel != null) {
            viewModel.playerManager.attachView(view, isPrimary = false, tag = "LiveTvScreen-preview")
        }
    }

    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isEpgTimelineMode by remember { mutableStateOf(false) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarExpanded) 128.dp else 46.dp,
        animationSpec = tween(220),
        label = "SidebarWidth"
    )

    var visibleLimit by remember(displayedChannels) { mutableIntStateOf(40) }
    val pagedChannels = remember(displayedChannels, visibleLimit) {
        displayedChannels.take(visibleLimit)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        // DİKEY CANLI MİNİ ÖNİZLEME
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
                                val view = LayoutInflater.from(ctx)
                                    .inflate(R.layout.player_texture_view, null, false) as PlayerView
                                view.apply {
                                    useController = false
                                    setKeepContentOnPlayerReset(true)
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                                viewModel.playerManager.detachView(playerView, tag = "LiveTvScreen-preview")
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
                                        .size(44.dp)
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.SurfaceElevated.copy(alpha = 0.85f))
                                        .padding(6.dp),
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
                                CircularProgressIndicator(
                                    color = VegaColors.Accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.expandMiniPlayerToFullscreen() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tam ekran",
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.closeMiniPlayer() },
                                modifier = Modifier.size(34.dp)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // SOL PANEL: DİKEY KATEGORİ LİSTESİ
            Surface(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight(),
                color = VegaColors.Surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isSidebarExpanded) Arrangement.SpaceBetween else Arrangement.Center
                    ) {
                        if (isSidebarExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Kategoriler",
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewCarousel,
                                        contentDescription = "Raf düzenine geç",
                                        tint = VegaColors.Accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onOpenCategoryManager,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Yönetim",
                                        tint = VegaColors.Accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { isSidebarExpanded = !isSidebarExpanded },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isSidebarExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Aç veya kapat",
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        item {
                            val isFavSelected = selectedCategory == "favorites"
                            Surface(
                                onClick = { viewModel.selectedLiveCategory.value = "favorites" },
                                shape = VegaShapes.Small,
                                color = if (isFavSelected) VegaColors.Gold.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isFavSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(VegaShapes.Pill)
                                                .background(VegaColors.Gold)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (isFavSelected) VegaColors.Gold else VegaColors.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Favoriler",
                                            color = if (isFavSelected) VegaColors.Gold else VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = categories,
                            key = { index, category -> "portrait_cat_${category.categoryId}_$index" }
                        ) { _, category ->
                            val isSelected = category.categoryId == selectedCategory
                            val categoryIcon = getCategoryIcon(category.categoryName)

                            Surface(
                                onClick = { viewModel.loadLiveStreams(category.categoryId) },
                                shape = VegaShapes.Small,
                                color = if (isSelected) VegaColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(VegaShapes.Pill)
                                                .background(VegaColors.Accent)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(
                                        imageVector = categoryIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val countSuffix = if (category.itemCount > 0) " (${category.itemCount})" else ""
                                        Text(
                                            text = "${category.categoryName}$countSuffix",
                                            color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = {
                                    if (isParentalUnlocked) viewModel.lockParental()
                                    else onOpenParentalDialog()
                                },
                                shape = VegaShapes.Small,
                                color = if (isParentalUnlocked) VegaColors.Danger.copy(alpha = 0.18f) else VegaColors.SurfaceElevated,
                                border = BorderStroke(1.dp, if (isParentalUnlocked) VegaColors.Danger else VegaColors.Border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isParentalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isParentalUnlocked) VegaColors.Danger else VegaColors.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
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
                    }
                }
            }

            VerticalDivider(color = VegaColors.Border, thickness = 1.dp)

            // SAĞ PANEL: ARAMA VE KANAL LİSTESİ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (searchQuery.isNotBlank()) {
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "\"$searchQuery\" (${displayedChannels.size} kanal)",
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSearchQueryChange("") }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Filtreyi temizle",
                                        tint = VegaColors.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            ActionChipButton(
                                text = when (currentSort) {
                                    SortOption.DEFAULT -> "Varsayılan"
                                    SortOption.NAME_ASC -> "A - Z"
                                    SortOption.NAME_DESC -> "Z - A"
                                    SortOption.ADDED_DESC -> "Eklenme"
                                    SortOption.RATING_DESC -> "Kanal no"
                                },
                                icon = Icons.AutoMirrored.Filled.Sort,
                                onClick = { isSortMenuOpen = true }
                            )

                            DropdownMenu(
                                expanded = isSortMenuOpen,
                                onDismissRequest = { isSortMenuOpen = false },
                                modifier = Modifier.background(VegaColors.SurfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Varsayılan sıra",
                                            color = if (currentSort == SortOption.DEFAULT) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Reorder, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        viewModel.setLiveSort(SortOption.DEFAULT)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "A - Z (isme göre)",
                                            color = if (currentSort == SortOption.NAME_ASC) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        viewModel.setLiveSort(SortOption.NAME_ASC)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Z - A (isme göre ters)",
                                            color = if (currentSort == SortOption.NAME_DESC) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        viewModel.setLiveSort(SortOption.NAME_DESC)
                                        isSortMenuOpen = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        ActionChipButton(
                            text = if (isEpgTimelineMode) "Rehber" else "Liste",
                            icon = if (isEpgTimelineMode) Icons.Default.CalendarViewWeek else Icons.AutoMirrored.Filled.ViewList,
                            isSelected = isEpgTimelineMode,
                            onClick = { isEpgTimelineMode = !isEpgTimelineMode }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        ActionChipButton(
                            text = "Raf",
                            icon = Icons.Default.ViewCarousel,
                            isSelected = false,
                            onClick = { viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF) }
                        )
                    }
                }

                // Son İzlenenler
                if (recentChannels.isNotEmpty() && searchQuery.isEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        itemsIndexed(
                            items = recentChannels,
                            key = { index, channel -> "recent_${channel.streamId}_${channel.categoryId}_$index" }
                        ) { _, channel ->
                            Surface(
                                onClick = { viewModel.previewChannel(channel) },
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceElevated,
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(VegaColors.SurfaceAccent),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = channel.name,
                                        color = VegaColors.TextPrimary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VegaColors.Accent)
                        }
                    } else if (displayedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "\"$searchQuery\" için kanal bulunamadı." else "Bu kategoride yayın yok.",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Secondary
                                )
                                if (searchQuery.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = pagedChannels,
                                key = { index, channel -> "ch_${channel.streamId}_${channel.categoryId}_$index" },
                                contentType = { _, _ -> "live_channel" }
                            ) { _, channel ->
                                val isFavorite = favoriteIds.contains(channel.streamId.toString())
                                val isCurrentPlaying = activePreviewChannel?.streamId == channel.streamId && isMiniPlayerActive

                                if (isEpgTimelineMode) {
                                    EpgTimelineRow(
                                        channel = channel,
                                        epg = epgSummaryMap[channel.streamId],
                                        isFavorite = isFavorite,
                                        isPlaying = isCurrentPlaying,
                                        onLoadEpg = { viewModel.loadEpgSummary(channel.streamId) },
                                        onChannelClick = { viewModel.previewChannel(channel) },
                                        onProgramClick = { onSelectProgramDetail(channel, it) },
                                        onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                                    )
                                } else {
                                    EpgChannelCard(
                                        channel = channel,
                                        epg = epgSummaryMap[channel.streamId],
                                        isFavorite = isFavorite,
                                        isPlaying = isCurrentPlaying,
                                        onLoadEpg = { viewModel.loadEpgSummary(channel.streamId) },
                                        onChannelClick = { viewModel.previewChannel(channel) },
                                        onPlayFullscreen = { viewModel.playLiveChannel(channel) },
                                        onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                                    )
                                }
                            }

                            if (visibleLimit < displayedChannels.size) {
                                item(key = "more_$visibleLimit") {
                                    LaunchedEffect(visibleLimit) { visibleLimit += 30 }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = VegaColors.Accent,
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
}

@Composable
fun EpgChannelCard(
    channel: LiveStream,
    epg: EpgProgram? = null,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onLoadEpg: () -> Unit = {},
    onChannelClick: () -> Unit,
    onPlayFullscreen: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    LaunchedEffect(channel.streamId) {
        delay(250L)
        onLoadEpg()
    }

    val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın"
    val programTime = epg?.getFormattedTime() ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "Canlı yayın")
    val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else null)
    val badges = remember(channel.name) { extractQualityBadges(channel.name) }

    Surface(
        color = if (isPlaying) VegaColors.Accent.copy(alpha = 0.14f) else VegaColors.SurfaceElevated,
        shape = VegaShapes.Medium,
        border = BorderStroke(
            width = if (isPlaying) 1.5.dp else 1.dp,
            color = if (isPlaying) VegaColors.Accent else VegaColors.Border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(VegaShapes.Small)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Small,
                            scale = 1.01f,
                            onClick = onChannelClick
                        )
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.name,
                                color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (badges.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                badges.take(2).forEach { QualityBadge(it) }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = programTitle,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = programTime,
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onPlayFullscreen) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Tam ekran oynat",
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (progress != null) {
                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(VegaShapes.Pill),
                    color = VegaColors.Accent,
                    trackColor = VegaColors.SurfaceAccent
                )
            }
        }
    }
}

@Composable
fun EpgTimelineRow(
    channel: LiveStream,
    epg: EpgProgram?,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onLoadEpg: () -> Unit,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit,
    onFavoriteToggle: () -> Unit
) {
    LaunchedEffect(channel.streamId) {
        delay(250L)
        if (epg == null) onLoadEpg()
    }

    Surface(
        color = if (isPlaying) VegaColors.Accent.copy(alpha = 0.14f) else VegaColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (isPlaying) VegaColors.Accent else VegaColors.Border),
        shape = VegaShapes.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .width(138.dp)
                    .fillMaxHeight()
                    .background(VegaColors.Surface.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(VegaShapes.Small)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Small,
                            scale = 1.02f,
                            onClick = onChannelClick
                        )
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(VegaShapes.Small)
                            .background(VegaColors.SurfaceAccent)
                            .padding(3.dp),
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

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = channel.name,
                        color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            VerticalDivider(color = VegaColors.Border, thickness = 1.dp)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(VegaShapes.Small)
                    .dpadFocusable(
                        focusedBorderColor = VegaColors.FocusBorder,
                        shape = VegaShapes.Small,
                        scale = 1.01f,
                        onClick = {
                            if (epg != null) onProgramClick(epg) else onChannelClick()
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (epg != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.Live)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "Canlı",
                                        color = Color.White,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = epg.getDecodedTitle(),
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.Secondary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = epg.getFormattedTime(),
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        val progFraction = epg.getProgressFraction()
                        if (progFraction != null) {
                            LinearProgressIndicator(
                                progress = { progFraction },
                                color = VegaColors.Accent,
                                trackColor = VegaColors.SurfaceAccent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(VegaShapes.Pill)
                            )
                        }

                        val desc = epg.getDecodedDescription()
                        Text(
                            text = if (desc.isNotBlank()) desc else "Detaylar için dokun...",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Program rehberi yükleniyor...",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption
                        )
                    }
                }
            }
        }
    }
}

fun getCategoryIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("all") || lower.contains("tüm") -> Icons.Default.AllInclusive
        lower.contains("favori") -> Icons.Default.Star
        lower.contains("spor") || lower.contains("sport") -> Icons.Default.SportsSoccer
        lower.contains("sinema") || lower.contains("film") || lower.contains("movie") -> Icons.Default.Movie
        lower.contains("belgesel") || lower.contains("doc") -> Icons.Default.Public
        lower.contains("haber") || lower.contains("news") -> Icons.Default.Newspaper
        lower.contains("çocuk") || lower.contains("kid") || lower.contains("anim") -> Icons.Default.ChildCare
        lower.contains("müzik") || lower.contains("music") -> Icons.Default.MusicNote
        lower.contains("ulusal") || lower.contains("genel") -> Icons.Default.Tv
        else -> Icons.Default.LiveTv
    }
}
