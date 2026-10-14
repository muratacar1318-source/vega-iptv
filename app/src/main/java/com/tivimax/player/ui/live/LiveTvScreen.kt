package com.tivimax.player.ui.live

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.GlobalSearchOptionsCard

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleLiveCategories.collectAsState()
    val allCategories by viewModel.liveCategories.collectAsState()
    val hiddenCategories by viewModel.hiddenCategories.collectAsState()
    val isParentalUnlocked by viewModel.isParentalUnlocked.collectAsState()

    val channels by viewModel.liveStreams.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val isLoading by viewModel.liveLoading.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val currentSort by viewModel.liveSortOption.collectAsState()

    val isMiniPlayerActive by viewModel.isMiniPlayerActive.collectAsState()
    val activePreviewChannel by viewModel.activePreviewChannel.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()

    val globalQuery by viewModel.globalSearchQuery.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSortMenuOpen by remember { mutableStateOf(false) }

    var isEpgTimelineMode by remember { mutableStateOf(false) }
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var showParentalPinDialog by remember { mutableStateOf(false) }
    var parentalPinInput by remember { mutableStateOf("") }
    var parentalPinError by remember { mutableStateOf(false) }
    var selectedProgramDetail by remember { mutableStateOf<Pair<LiveStream, EpgProgram>?>(null) }

    LaunchedEffect(globalQuery) {
        if (globalQuery.isNotBlank() && searchQuery != globalQuery) {
            searchQuery = globalQuery
        }
    }

    val filteredChannels = remember(channels, searchQuery, currentSort) {
        try {
            val list = if (searchQuery.isBlank()) {
                channels
            } else {
                val q = searchQuery.trim().lowercase()
                channels.filter { (it.name ?: "").lowercase().contains(q) }
            }

            val sorted = when (currentSort) {
                SortOption.DEFAULT -> list
                SortOption.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
                SortOption.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
                SortOption.ADDED_DESC -> list.sortedByDescending { it.added?.toLongOrNull() ?: it.streamId.toLong() }
                SortOption.RATING_DESC -> list.sortedBy { it.num }
            }
            sorted.distinctBy { it.streamId }
        } catch (e: Exception) {
            channels
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // --- 1. DİKEY CANLI MİNİ-PLAYER ÖNİZLEME (TiviMate Tarzı) ---
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
                                player = viewModel.playerManager.initializePlayer()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Üst Bar: Kanal İsmi ve Butonlar
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
                                Text(
                                    text = "CANLI",
                                    color = Color.White,
                                    fontSize = 9.sp,
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
                            // Tam Ekran Butonu
                            IconButton(
                                onClick = { viewModel.expandMiniPlayerToFullscreen() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tam Ekran",
                                    tint = TiviPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // Kapat Butonu
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

        // --- 2. TIVIMATE SOL DİKEY KATEGORİ SÜTUNU & SAĞ KANAL LİSTESİ ---
        var isSidebarExpanded by remember { mutableStateOf(true) }
        val sidebarWidth by animateDpAsState(
            targetValue = if (isSidebarExpanded) 125.dp else 46.dp,
            animationSpec = tween(220),
            label = "SidebarWidth"
        )

        val displayedChannels = remember(filteredChannels, selectedCategory, favoriteIds) {
            if (selectedCategory == "favorites") {
                filteredChannels.filter { favoriteIds.contains(it.streamId.toString()) }
            } else {
                filteredChannels
            }
        }

        var visibleLimit by remember(displayedChannels) { mutableStateOf(40) }
        val pagedChannels = remember(displayedChannels, visibleLimit) {
            displayedChannels.take(visibleLimit)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // SOL PANEL: DİKEY KATEGORİ LİSTESİ (TiviMate Sidebar)
            Surface(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight(),
                color = TiviSurface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Sidebar Üst Başlık & Aç/Kapa Düğmesi
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSidebarExpanded = !isSidebarExpanded }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isSidebarExpanded) Arrangement.SpaceBetween else Arrangement.Center
                    ) {
                        if (isSidebarExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "KATEGORİLER",
                                    color = TiviTextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { showCategoryManagerDialog = true },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Kategori Yönetimi",
                                        tint = TiviPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = if (isSidebarExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Kategorileri Aç/Kapa",
                            tint = TiviPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // 1. Favoriler Kısayol Kategorisi
                        item {
                            val isFavSelected = selectedCategory == "favorites"
                            Surface(
                                onClick = { viewModel.selectedLiveCategory.value = "favorites" },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFavSelected) TiviGold.copy(alpha = 0.18f) else Color.Transparent,
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
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(TiviGold)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (isFavSelected) TiviGold else TiviTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Favoriler",
                                            color = if (isFavSelected) TiviGold else TiviTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isFavSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Sunucudan Gelen Kategoriler
                        items(categories, key = { it.categoryId }) { category ->
                            val isSelected = category.categoryId == selectedCategory
                            val categoryIcon = getCategoryIcon(category.categoryName)

                            Surface(
                                onClick = { viewModel.loadLiveStreams(category.categoryId) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TiviPrimary.copy(alpha = 0.15f) else Color.Transparent,
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
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(TiviPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    Icon(
                                        imageVector = categoryIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) TiviPrimary else TiviTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )

                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = category.categoryName,
                                            color = if (isSelected) TiviPrimary else TiviTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Ebeveyn Kilidi (+18 Koruması)
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = {
                                    if (isParentalUnlocked) {
                                        viewModel.lockParental()
                                    } else {
                                        parentalPinInput = ""
                                        parentalPinError = false
                                        showParentalPinDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isParentalUnlocked) Color(0xFFEF5350).copy(alpha = 0.2f) else TiviCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isParentalUnlocked) Color(0xFFEF5350) else TiviCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isParentalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isParentalUnlocked) "+18 Açık (Kilitle)" else "+18 Korumalı",
                                            color = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary,
                                            fontSize = 11.sp,
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
            }

            // Dikey Ayırıcı Çizgi
            VerticalDivider(
                color = TiviCardBorder.copy(alpha = 0.6f),
                thickness = 1.dp
            )

            // SAĞ PANEL: ARAMA ÇUBUĞU, SON İZLENENLER & KANAL LİSTESİ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Arama ve Sıralama Çubuğu (Kompakt ve Şık)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Kanal veya program ara...", fontSize = 12.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Temizle", tint = TiviTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TiviPrimary,
                            unfocusedBorderColor = TiviCardBorder,
                            focusedContainerColor = TiviCard,
                            unfocusedContainerColor = TiviCard,
                            focusedTextColor = TiviTextPrimary,
                            unfocusedTextColor = TiviTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sıralama Seçici Butonu
                    Box {
                        Surface(
                            onClick = { isSortMenuOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            color = TiviCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sırala",
                                    tint = TiviPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (currentSort) {
                                        SortOption.DEFAULT -> "Varsayılan"
                                        SortOption.NAME_ASC -> "A - Z"
                                        SortOption.NAME_DESC -> "Z - A"
                                        SortOption.ADDED_DESC -> "Eklenme"
                                        SortOption.RATING_DESC -> "Kanal No"
                                    },
                                    color = TiviTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isSortMenuOpen,
                            onDismissRequest = { isSortMenuOpen = false },
                            modifier = Modifier.background(TiviSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Varsayılan Sıra (Önerilen)", color = if (currentSort == SortOption.DEFAULT) TiviPrimary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setLiveSort(SortOption.DEFAULT)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("A - Z (İsme Göre)", color = if (currentSort == SortOption.NAME_ASC) TiviPrimary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setLiveSort(SortOption.NAME_ASC)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Z - A (İsme Göre Ters)", color = if (currentSort == SortOption.NAME_DESC) TiviPrimary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setLiveSort(SortOption.NAME_DESC)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eklenme Tarihi (Yeni)", color = if (currentSort == SortOption.ADDED_DESC) TiviPrimary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setLiveSort(SortOption.ADDED_DESC)
                                    isSortMenuOpen = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Görünüm Seçici Butonu: Liste / TV Rehberi (EPG)
                    Surface(
                        onClick = { isEpgTimelineMode = !isEpgTimelineMode },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isEpgTimelineMode) TiviPrimary.copy(alpha = 0.2f) else TiviCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isEpgTimelineMode) TiviPrimary else TiviCardBorder),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isEpgTimelineMode) Icons.Default.CalendarViewWeek else Icons.Default.ViewList,
                                contentDescription = "Görünüm",
                                tint = if (isEpgTimelineMode) TiviPrimary else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEpgTimelineMode) "TV Rehberi" else "Liste",
                                color = if (isEpgTimelineMode) TiviPrimary else TiviTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                                border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = channel.name,
                                        color = TiviTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TiviPrimary)
                        }
                    } else if (displayedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isEpgTimelineMode) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TiviCard.copy(alpha = 0.6f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "KANAL",
                                            color = TiviTextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(135.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "ŞU AN YAYINDA (CANLI PROGRAM)",
                                            color = TiviPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            items(pagedChannels, key = { "${it.streamId}_${it.num}" }) { channel ->
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
                                        onProgramClick = { selectedProgramDetail = Pair(channel, it) },
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

                            // Sonsuz Kaydırma / Sayfalama Tetikleyici
                            if (visibleLimit < displayedChannels.size) {
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
                                            color = TiviPrimary,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }

                            // Arama yapıldığında listenin en altında alternatif arama seçenekleri
                            if (searchQuery.isNotBlank()) {
                                item {
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
                    }
                }
            }
        }

        // --- 4. KATEGORİ YÖNETİMİ DİYALOGU ---
        if (showCategoryManagerDialog) {
            var catSearch by remember { mutableStateOf("") }
            val filteredAllCats = remember(allCategories, catSearch) {
                if (catSearch.isBlank()) allCategories.filter { it.categoryId != "all" }
                else allCategories.filter { it.categoryId != "all" && it.categoryName.lowercase().contains(catSearch.lowercase()) }
            }

            AlertDialog(
                onDismissRequest = { showCategoryManagerDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = TiviPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kategori Yönetimi", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        Text(
                            "İzlemediğiniz yabancı veya gereksiz kategorileri gizleyerek listenizi sadeleştirin.",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = catSearch,
                            onValueChange = { catSearch = it },
                            placeholder = { Text("Kategori ara...", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TiviPrimary,
                                unfocusedBorderColor = TiviCardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredAllCats, key = { it.categoryId }) { cat ->
                                val isHidden = hiddenCategories.contains(cat.categoryId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleCategoryHidden(cat.categoryId) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cat.categoryName,
                                        color = if (isHidden) TiviTextSecondary.copy(alpha = 0.5f) else Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isHidden) "Gizli" else "Görünür",
                                        tint = if (isHidden) Color(0xFFEF5350) else TiviPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCategoryManagerDialog = false }) {
                        Text("Tamam", color = TiviPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = TiviSurface
            )
        }

        // --- 5. EBEVEYN KİLİDİ PIN DİYALOGU ---
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
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TiviPrimary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary)
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

        // --- 6. EPG PROGRAM DETAY BOTTOM SHEET ---
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
                        AsyncImage(
                            model = channel.streamIcon,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(channel.name, color = TiviPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        color = TiviPrimary,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (!program.description.isNullOrBlank()) program.description else "Bu program için detaylı özet bilgisi sunucu tarafından iletilmedi.",
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
                        colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary),
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
        onLoadEpg()
    }

    // Gerçek Xtream EPG Verisi (Mevcut program, saat ve ilerleme çubuğu)
    val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
    val programTime = epg?.getFormattedTime() ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "Canlı Yayın")
    val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else 0.45f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) TiviPrimary.copy(alpha = 0.12f) else TiviCard
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, TiviPrimary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChannelClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kanal Logosu
                AsyncImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F141F))
                        .padding(4.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Kanal Bilgisi & EPG Program Adı
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            color = if (isPlaying) TiviPrimary else TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.tvArchive == 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            // Catch-up / Geçmiş Yayın İkonu
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Arşiv / Geri Sar",
                                tint = TiviGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // EPG Program Başlığı
                    Text(
                        text = programTitle,
                        color = TiviTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = programTime,
                        color = TiviTextMuted,
                        fontSize = 11.sp
                    )
                }

                // Tam Ekran Oynat Butonu
                IconButton(onClick = onPlayFullscreen) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Tam Ekran Oynat",
                        tint = TiviPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Favori Yıldız Butonu
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) TiviGold else TiviTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // EPG Canlı İlerleme Çubuğu (% kaçı tamamlandı)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isPlaying) TiviPrimary else TiviSecondary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
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
        if (epg == null) {
            onLoadEpg()
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) TiviPrimary.copy(alpha = 0.16f) else TiviCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPlaying) TiviPrimary else TiviCardBorder.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sol: Kanal Bilgi Hücresi (Tıklanınca kanalı oynatır)
            Row(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onChannelClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.streamIcon.isNullOrEmpty()) {
                        AsyncImage(
                            model = channel.streamIcon,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = TiviTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = if (isPlaying) TiviPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) TiviGold else TiviTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            VerticalDivider(color = TiviCardBorder, thickness = 1.dp)

            // Sağ: Canlı EPG Zaman Çizelgesi Kartı
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
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
                                fontWeight = FontWeight.Medium
                            )
                        }

                        val progress = epg.getProgressFraction()
                        LinearProgressIndicator(
                            progress = { progress },
                            color = TiviPrimary,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                        )

                        Text(
                            text = if (!epg.description.isNullOrBlank()) epg.description else "Detaylar için dokunun...",
                            color = TiviTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Elektronik program rehberi (EPG) alınıyor...",
                            color = TiviTextSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
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
