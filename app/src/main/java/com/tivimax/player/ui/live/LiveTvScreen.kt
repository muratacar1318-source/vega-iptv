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
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.GlobalSearchOptionsCard

@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(viewModel: MainViewModel) {
    val categories by viewModel.liveCategories.collectAsState()
    val channels by viewModel.liveStreams.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val isLoading by viewModel.liveLoading.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val currentSort by viewModel.liveSortOption.collectAsState()

    val isMiniPlayerActive by viewModel.isMiniPlayerActive.collectAsState()
    val activePreviewChannel by viewModel.activePreviewChannel.collectAsState()

    val globalQuery by viewModel.globalSearchQuery.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSortMenuOpen by remember { mutableStateOf(false) }

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
                            Text(
                                text = "KATEGORİLER",
                                color = TiviTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
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
                            items(pagedChannels, key = { "${it.streamId}_${it.num}" }) { channel ->
                                val isFavorite = favoriteIds.contains(channel.streamId.toString())
                                val isCurrentPlaying = activePreviewChannel?.streamId == channel.streamId && isMiniPlayerActive

                                EpgChannelCard(
                                    channel = channel,
                                    isFavorite = isFavorite,
                                    isPlaying = isCurrentPlaying,
                                    onChannelClick = { viewModel.previewChannel(channel) },
                                    onPlayFullscreen = { viewModel.playLiveChannel(channel) },
                                    onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                                )
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
    }
}

@Composable
fun EpgChannelCard(
    channel: LiveStream,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onChannelClick: () -> Unit,
    onPlayFullscreen: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    // EPG Varsayılan Program Verisi (Xtream EPG olmadığında akıllı tahmin)
    val programTitle = channel.currentProgram ?: "Canlı Yayın Akışı"
    val programTime = if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "13:00 - 15:30"
    val progress = if (channel.programProgress > 0f) channel.programProgress else 0.45f

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
