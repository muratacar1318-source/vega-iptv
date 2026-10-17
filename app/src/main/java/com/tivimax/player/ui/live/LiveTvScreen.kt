package com.tivimax.player.ui.live

import androidx.activity.compose.BackHandler
import com.tivimax.player.util.LocalIsTvDevice
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import com.tivimax.player.ui.components.ActionChipButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.GlobalSearchOptionsCard
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.live.shelf.ShelfLiveLayout
import com.tivimax.player.ui.live.modern.LiveTvModernPortraitLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleLiveCategories.collectAsState()
    val allCategories by viewModel.liveCategories.collectAsState()
    val hiddenCategories by viewModel.hiddenCategories.collectAsState()
    val isParentalUnlocked by viewModel.isParentalUnlocked.collectAsState()
    val liveLayoutMode by viewModel.liveLayoutMode.collectAsState()
    val allLiveStreams by viewModel.allLiveStreams.collectAsState()

    val channels by viewModel.liveStreams.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val isLoading by viewModel.liveLoading.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
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
    var parentalPinInput by remember { mutableStateOf("") }
    var parentalPinError by remember { mutableStateOf(false) }
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

    val displayedChannels = remember(channels, selectedCategory, favoriteIds) {
        if (selectedCategory == "favorites") {
            channels.filter { favoriteIds.contains(it.streamId.toString()) }
        } else {
            channels
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
    LaunchedEffect(isWideScreen, channels) {
        if (isWideScreen && activePreviewChannel == null && channels.isNotEmpty()) {
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
            .background(TiviBackground)
    ) {
        Crossfade(
            targetState = liveLayoutMode,
            animationSpec = tween(200),
            label = "live_layout_mode_crossfade"
        ) { mode ->
            if (mode == UserPreferences.LIVE_LAYOUT_SHELF) {
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
            } else {
                if (isWideScreen) {
                    // ====================================================================
                    // 4 BÖLMELİ PROFESYONEL CANLI TV KONTROL MERKEZİ (TIVIMATE & OPUS IPTV)
                    // ====================================================================
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
                            parentalPinInput = ""
                            parentalPinError = false
                            showParentalPinDialog = true
                        },
                        onSelectProgramDetail = { stream, prog -> selectedProgramDetail = Pair(stream, prog) }
                    )
                } else {
                    // ====================================================================
                    // DİKEY TELEFON DÜZENİ (PORTRAIT MOBILE LAYOUT)
                    // ====================================================================
                    LiveTvModernPortraitLayout(
                        viewModel = viewModel,
                        categories = categories,
                        allCategories = allCategories,
                        selectedCategory = selectedCategory,
                        displayedChannels = displayedChannels,
                        activePreviewChannel = activePreviewChannel,
                        isMiniPlayerActive = false, // HomeScreen seviyesindeki VegaGlobalMiniPlayer tarafından üstte yönetilir
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
                            parentalPinInput = ""
                            parentalPinError = false
                            showParentalPinDialog = true
                        },
                        onSelectProgramDetail = { stream, prog -> selectedProgramDetail = Pair(stream, prog) }
                    )
                }
            }
        }

        // --- KATEGORİ YÖNETİMİ DİYALOGU (GİZLEME, SABİTLEME VE TOPLU İŞLEMLER) ---
        if (showCategoryManagerDialog) {
            CategoryManagementDialog(
                viewModel = viewModel,
                initialTab = 0,
                onDismiss = { showCategoryManagerDialog = false }
            )
        }

        // --- EBEVEYN KİLİDİ PIN DİYALOGU ---
        if (showParentalPinDialog) {
            AlertDialog(
                onDismissRequest = { showParentalPinDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF5350))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ebeveyn Kilidi", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            "Yetişkin (+18) içerikleri açmak için 4 haneli PIN kodunuzu girin (Varsayılan: 0000):",
                            color = TiviTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = parentalPinInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                    parentalPinInput = it
                                    parentalPinError = false
                                }
                            },
                            isError = parentalPinError,
                            singleLine = true,
                            placeholder = { Text("****") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusCyan,
                                errorBorderColor = Color.Red
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (parentalPinError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Hatalı PIN kodu! Tekrar deneyin.", color = Color.Red, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.unlockParental(parentalPinInput)) {
                                showParentalPinDialog = false
                            } else {
                                parentalPinError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan)
                    ) {
                        Text("Kilidi Aç", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showParentalPinDialog = false }) {
                        Text("İptal", color = TiviTextSecondary)
                    }
                },
                containerColor = TiviSurface
            )
        }

        // --- EPG PROGRAM DETAY BOTTOM SHEET ---
        if (selectedProgramDetail != null) {
            val (channel, program) = selectedProgramDetail!!
            ModalBottomSheet(
                onDismissRequest = { selectedProgramDetail = null },
                containerColor = TiviSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TiviCardBorder) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncShimmerImage(
                            model = channel.streamIcon,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            placeholderIcon = Icons.Default.Tv
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(channel.name, color = OpusCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(program.getFormattedTime(), color = TiviTextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = program.getDecodedTitle(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val progress = program.getProgressFraction()
                    LinearProgressIndicator(
                        progress = { progress },
                        color = OpusCyan,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val decodedProgramDesc = program.getDecodedDescription()
                    Text(
                        text = if (!decodedProgramDesc.isNullOrBlank()) decodedProgramDesc else "Bu program için detaylı özet bilgisi sunucu tarafından iletilmedi.",
                        color = TiviTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            selectedProgramDetail = null
                            viewModel.playLiveChannel(channel)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bu Kanalı Tam Ekran İzle", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ====================================================================
// DİKEY TELEFON DÜZENİ (PORTRAIT MOBILE LAYOUT)
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
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isEpgTimelineMode by remember { mutableStateOf(false) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarExpanded) 125.dp else 46.dp,
        animationSpec = tween(220),
        label = "SidebarWidth"
    )

    var visibleLimit by remember(displayedChannels) { mutableStateOf(40) }
    val pagedChannels = remember(displayedChannels, visibleLimit) {
        displayedChannels.take(visibleLimit)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // DİKEY CANLI MİNİ-PLAYER ÖNİZLEME
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TiviAccentLive)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("CANLI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                            IconButton(onClick = { viewModel.expandMiniPlayerToFullscreen() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Tam Ekran", tint = OpusCyan, modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { viewModel.closeMiniPlayer() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // TIVIMATE SOL DİKEY KATEGORİ SÜTUNU & SAĞ KANAL LİSTESİ
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
                color = TiviSurface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isSidebarExpanded) Arrangement.SpaceBetween else Arrangement.Center
                    ) {
                        if (isSidebarExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("KATEGORİLER", color = TiviTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.ViewCarousel, contentDescription = "Raf Düzenine Geç", tint = OpusCyan, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = onOpenCategoryManager, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Tune, contentDescription = "Yönetim", tint = OpusCyan, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        IconButton(onClick = { isSidebarExpanded = !isSidebarExpanded }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isSidebarExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Aç/Kapa",
                                tint = OpusCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Favoriler
                        item {
                            val isFavSelected = selectedCategory == "favorites"
                            Surface(
                                onClick = { viewModel.selectedLiveCategory.value = "favorites" },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFavSelected) TiviGold.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isFavSelected) {
                                        Box(modifier = Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(1.5.dp)).background(TiviGold))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(Icons.Default.Star, contentDescription = null, tint = if (isFavSelected) TiviGold else TiviTextSecondary, modifier = Modifier.size(16.dp))
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Favoriler", color = if (isFavSelected) TiviGold else Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Kategoriler
                        items(categories, key = { it.categoryId }) { category ->
                            val isSelected = category.categoryId == selectedCategory
                            val categoryIcon = getCategoryIcon(category.categoryName)

                            Surface(
                                onClick = { viewModel.loadLiveStreams(category.categoryId) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Box(modifier = Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(1.5.dp)).background(OpusCyan))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(categoryIcon, contentDescription = null, tint = if (isSelected) OpusCyan else TiviTextSecondary, modifier = Modifier.size(16.dp))
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val countSuffix = if (category.itemCount > 0) " (${category.itemCount})" else ""
                                        Text("${category.categoryName}$countSuffix", color = if (isSelected) OpusCyan else Color.White, fontSize = 13.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        // Ebeveyn Kilidi
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = {
                                    if (isParentalUnlocked) viewModel.lockParental()
                                    else onOpenParentalDialog()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isParentalUnlocked) Color(0xFFEF5350).copy(alpha = 0.2f) else TiviCard,
                                border = BorderStroke(1.dp, if (isParentalUnlocked) Color(0xFFEF5350) else TiviCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (isParentalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, tint = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary, modifier = Modifier.size(16.dp))
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isParentalUnlocked) "+18 Açık (Kilitle)" else "+18 Korumalı", color = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VerticalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

            // SAĞ PANEL: ARAMA & KANAL LİSTESİ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Sıralama ve Aktif Arama Filtresi Başlığı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (searchQuery.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = OpusCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = OpusCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "\"$searchQuery\" (${displayedChannels.size} kanal)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSearchQueryChange("") }
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Filtreyi Temizle",
                                        tint = TiviTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Sıralama
                        Box {
                            ActionChipButton(
                                text = when (currentSort) {
                                    SortOption.DEFAULT -> "Varsayılan"
                                    SortOption.NAME_ASC -> "A - Z"
                                    SortOption.NAME_DESC -> "Z - A"
                                    SortOption.ADDED_DESC -> "Eklenme"
                                    SortOption.RATING_DESC -> "Kanal No"
                                },
                                icon = Icons.AutoMirrored.Filled.Sort,
                                onClick = { isSortMenuOpen = true }
                            )

                            DropdownMenu(
                                expanded = isSortMenuOpen,
                                onDismissRequest = { isSortMenuOpen = false },
                                modifier = Modifier.background(TiviSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Varsayılan Sıra", color = if (currentSort == SortOption.DEFAULT) OpusCyan else Color.White, fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setLiveSort(SortOption.DEFAULT)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("A - Z (İsme Göre)", color = if (currentSort == SortOption.NAME_ASC) OpusCyan else Color.White, fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setLiveSort(SortOption.NAME_ASC)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Z - A (İsme Göre Ters)", color = if (currentSort == SortOption.NAME_DESC) OpusCyan else Color.White, fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setLiveSort(SortOption.NAME_DESC)
                                        isSortMenuOpen = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // TV Rehberi / Liste Görünümü
                        ActionChipButton(
                            text = if (isEpgTimelineMode) "Rehber" else "Liste",
                            icon = if (isEpgTimelineMode) Icons.Default.CalendarViewWeek else Icons.AutoMirrored.Filled.ViewList,
                            isSelected = isEpgTimelineMode,
                            onClick = { isEpgTimelineMode = !isEpgTimelineMode }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Raf Düzenine Geçiş Butonu
                        ActionChipButton(
                            text = "Raf",
                            icon = Icons.Default.ViewCarousel,
                            isSelected = false,
                            onClick = { viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF) }
                        )
                    }
                }

                // Son İzlenenler (Recents Bar)
                if (recentChannels.isNotEmpty() && searchQuery.isEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        items(recentChannels, key = { "recent_${it.streamId}" }) { channel ->
                            Surface(
                                onClick = { viewModel.previewChannel(channel) },
                                shape = RoundedCornerShape(8.dp),
                                color = TiviCard,
                                border = BorderStroke(1.dp, TiviCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.Black),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = channel.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OpusCyan)
                        }
                    } else if (displayedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "\"$searchQuery\" için kanal bulunamadı." else "Bu kategoride yayın yok.",
                                    color = TiviTextSecondary,
                                    fontSize = 13.sp
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
                            items(
                                items = pagedChannels,
                                key = { "${it.streamId}_${it.num}" },
                                contentType = { "live_channel" }
                            ) { channel ->
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
                                item {
                                    LaunchedEffect(Unit) { visibleLimit += 30 }
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = OpusCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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

    val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
    val programTime = epg?.getFormattedTime() ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "Canlı Yayın")
    val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else 0.45f)
    val badges = extractQualityBadges(channel.name)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.12f) else TiviCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isPlaying) BorderStroke(1.5.dp, OpusCyan) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChannelClick() }
            .dpadFocusable(focusedBorderColor = OpusCyan)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncShimmerImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(2.dp),
                    placeholderIcon = Icons.Default.Tv
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            color = if (isPlaying) OpusCyan else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (badges.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            badges.take(2).forEach { QualityBadge(it) }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = programTitle,
                        color = TiviTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = programTime,
                        color = TiviTextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPlayFullscreen) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Tam Ekran Oynat", tint = OpusCyan, modifier = Modifier.size(28.dp))
                }

                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) TiviGold else TiviTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = if (isPlaying) OpusCyan else TiviPrimary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(4.dp))
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

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.16f) else TiviCard),
        border = BorderStroke(1.dp, if (isPlaying) OpusCyan else TiviCardBorder.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .dpadFocusable(focusedBorderColor = OpusCyan)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onChannelClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncShimmerImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.04f)),
                    placeholderIcon = Icons.Default.Tv
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = if (isPlaying) OpusCyan else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                }

                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) TiviGold else TiviTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            VerticalDivider(color = TiviCardBorder, thickness = 1.dp)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (epg != null) onProgramClick(epg) else onChannelClick()
                    }
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(TiviAccentLive)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("CANLI", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = epg.getDecodedTitle(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = epg.getFormattedTime(),
                                color = TiviTextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        LinearProgressIndicator(
                            progress = { epg.getProgressFraction() },
                            color = OpusCyan,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                        )

                        val desc = epg.getDecodedDescription()
                        Text(
                            text = if (desc.isNotBlank()) desc else "Detaylar için dokunun...",
                            color = TiviTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Elektronik program rehberi (EPG) alınıyor...", color = TiviTextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
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
