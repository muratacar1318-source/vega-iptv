package com.tivimax.player.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.SearchFilter
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import kotlinx.coroutines.delay

@Composable
fun UniversalSearchScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.universalSearchQuery.collectAsState()
    val activeFilter by viewModel.universalSearchFilter.collectAsState()
    val isSearching by viewModel.isUniversalSearching.collectAsState()
    val liveResults by viewModel.universalLiveResults.collectAsState()
    val vodResults by viewModel.universalVodResults.collectAsState()
    val seriesResults by viewModel.universalSeriesResults.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    val onBack: () -> Unit = {
        if (!viewModel.navigateBack()) {
            viewModel.navigateTo(Screen.Home, false)
        }
    }

    // Donanım geri tuşu desteği
    BackHandler { onBack() }

    // Ekran ilk açıldığında arama kutusuna otomatik odaklan ve klavyeyi aç
    LaunchedEffect(Unit) {
        delay(200)
        try {
            searchFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val totalCount = liveResults.size + vodResults.size + seriesResults.size

    Scaffold(
        containerColor = VegaColors.Background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VegaColors.Background)
                    .statusBarsPadding()
            ) {
                // Üst Bar: Geri Butonu + Arama Kutusu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.Accent,
                                onClick = onBack
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = VegaColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Arama Kutusu (BasicTextField tabanlı, kesintisiz ve şık)
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Card,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Ara",
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Kanal, film veya dizi ara…",
                                        color = VegaColors.TextMuted,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setUniversalSearchQuery(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = VegaColors.TextPrimary,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(VegaColors.Accent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            focusManager.clearFocus()
                                            viewModel.saveRecentSearch(searchQuery)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setUniversalSearchQuery("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Temizle",
                                        tint = VegaColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Arama Yükleniyor Göstergesi
                if (isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = VegaColors.Accent,
                        trackColor = Color.Transparent
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.5.dp))
                }

                // Filtre Çipleri (Sonuç Sayıları ile Birlikte)
                if (searchQuery.length >= 2) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            SearchFilterChip(
                                label = "Tümü",
                                count = totalCount,
                                isSelected = activeFilter == SearchFilter.ALL,
                                onClick = { viewModel.setUniversalSearchFilter(SearchFilter.ALL) }
                            )
                        }
                        item {
                            SearchFilterChip(
                                label = "Canlı TV",
                                count = liveResults.size,
                                isSelected = activeFilter == SearchFilter.LIVE,
                                onClick = { viewModel.setUniversalSearchFilter(SearchFilter.LIVE) }
                            )
                        }
                        item {
                            SearchFilterChip(
                                label = "Filmler",
                                count = vodResults.size,
                                isSelected = activeFilter == SearchFilter.MOVIES,
                                onClick = { viewModel.setUniversalSearchFilter(SearchFilter.MOVIES) }
                            )
                        }
                        item {
                            SearchFilterChip(
                                label = "Diziler",
                                count = seriesResults.size,
                                isSelected = activeFilter == SearchFilter.SERIES,
                                onClick = { viewModel.setUniversalSearchFilter(SearchFilter.SERIES) }
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // Durum 1: Arama çubuğu boşken -> Son Aramalar veya Karşılama İpucu
                searchQuery.isEmpty() -> {
                    if (recentSearches.isNotEmpty()) {
                        RecentSearchesSection(
                            recentSearches = recentSearches,
                            onSearchClick = {
                                viewModel.setUniversalSearchQuery(it)
                                viewModel.saveRecentSearch(it)
                            },
                            onRemoveClick = { viewModel.removeRecentSearch(it) },
                            onClearAll = { viewModel.clearRecentSearches() }
                        )
                    } else {
                        EmptyInitialState()
                    }
                }

                // Durum 2: 1 Karakter Girildi -> "En az 2 harf yazın"
                searchQuery.length == 1 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "En az 2 harf yazın",
                            color = VegaColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                // Durum 3: Sonuç Bulunamadı
                totalCount == 0 && !isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = VegaColors.TextMuted,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "'$searchQuery' için sonuç bulunamadı",
                                color = VegaColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Yazımı kontrol edebilir veya farklı bir arama terimi deneyebilirsiniz.",
                                color = VegaColors.TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Durum 4: Arama Sonuçları Listesi
                else -> {
                    SearchResultsContent(
                        query = searchQuery,
                        activeFilter = activeFilter,
                        liveResults = liveResults,
                        vodResults = vodResults,
                        seriesResults = seriesResults,
                        onChannelClick = { channel ->
                            viewModel.saveRecentSearch(searchQuery)
                            viewModel.playLiveChannel(channel)
                        },
                        onVodClick = { vod ->
                            viewModel.saveRecentSearch(searchQuery)
                            viewModel.loadVodDetail(vod)
                        },
                        onSeriesClick = { series ->
                            viewModel.saveRecentSearch(searchQuery)
                            viewModel.loadSeriesDetail(series)
                        },
                        onSeeAllLive = {
                            viewModel.saveRecentSearch(searchQuery)
                            viewModel.searchAcrossAllLive(searchQuery)
                            viewModel.navigateTo(Screen.Home, false)
                        },
                        onSeeAllVod = {
                            viewModel.saveRecentSearch(searchQuery)
                            viewModel.searchAcrossAllVod(searchQuery)
                            viewModel.navigateTo(Screen.Home, false)
                        },
                        onSeeAllSeries = {
                            viewModel.saveRecentSearch(searchQuery)
                            viewModel.searchAcrossAllSeries(searchQuery)
                            viewModel.navigateTo(Screen.Home, false)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Filtre Çipi Bileşeni (Tümü, Canlı TV, Filmler, Diziler)
 */
@Composable
private fun SearchFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = VegaShapes.Pill,
        color = if (isSelected) VegaColors.Accent else VegaColors.Card,
        border = BorderStroke(1.dp, if (isSelected) VegaColors.Accent else VegaColors.Border),
        modifier = Modifier
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = if (isSelected) Color.White else VegaColors.Accent,
                onClick = onClick
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isSelected) VegaColors.OnAccent else VegaColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$count",
                    color = if (isSelected) VegaColors.OnAccent.copy(alpha = 0.75f) else VegaColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Son Aramalar Bölümü (En fazla 8 adet, tek tek silinebilir + Tümünü Temizle)
 */
@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Son aramalar",
                color = VegaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onClearAll) {
                Text(
                    text = "Temizle",
                    color = VegaColors.Accent,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Son Aramalar Listesi
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(recentSearches, key = { it }) { query ->
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.Card,
                    border = BorderStroke(1.dp, VegaColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            onClick = { onSearchClick(query) }
                        )
                        .clickable { onSearchClick(query) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = VegaColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = query,
                                color = VegaColors.TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onRemoveClick(query) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sil",
                                tint = VegaColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * İlk Boş Durum Bilgilendirme Görseli
 */
@Composable
private fun EmptyInitialState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = VegaColors.TextMuted.copy(alpha = 0.35f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Kanal, film veya dizi ara",
                color = VegaColors.TextMuted,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Arama Sonuçları İçeriği (Tümü veya Özel Filtre Sekmesi)
 */
@Composable
private fun SearchResultsContent(
    query: String,
    activeFilter: SearchFilter,
    liveResults: List<LiveStream>,
    vodResults: List<VodStream>,
    seriesResults: List<SeriesStream>,
    onChannelClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (SeriesStream) -> Unit,
    onSeeAllLive: () -> Unit,
    onSeeAllVod: () -> Unit,
    onSeeAllSeries: () -> Unit
) {
    when (activeFilter) {
        SearchFilter.ALL -> {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. CANLI TV BÖLÜMÜ
                if (liveResults.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Canlı TV",
                            count = liveResults.size,
                            titleColor = VegaColors.Accent,
                            onSeeAllClick = onSeeAllLive
                        )
                    }
                    items(liveResults.take(6), key = { "live_${it.streamId}" }) { channel ->
                        UniversalSearchChannelRow(
                            channel = channel,
                            onClick = { onChannelClick(channel) }
                        )
                    }
                }

                // 2. FİLMLER BÖLÜMÜ
                if (vodResults.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Filmler",
                            count = vodResults.size,
                            titleColor = VegaColors.Accent,
                            onSeeAllClick = onSeeAllVod
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(vodResults, key = { "vod_${it.streamId}" }) { vod ->
                                Box(modifier = Modifier.width(115.dp)) {
                                    MediaPosterCard(
                                        imageUrl = vod.streamIcon,
                                        title = vod.name,
                                        subtitle = "Film",
                                        rating = vod.rating,
                                        onClick = { onVodClick(vod) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // 3. DİZİLER BÖLÜMÜ
                if (seriesResults.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Diziler",
                            count = seriesResults.size,
                            titleColor = VegaColors.Accent,
                            onSeeAllClick = onSeeAllSeries
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(seriesResults, key = { "series_${it.seriesId}" }) { series ->
                                Box(modifier = Modifier.width(115.dp)) {
                                    MediaPosterCard(
                                        imageUrl = series.cover,
                                        title = series.name,
                                        subtitle = series.releaseDate?.take(4)?.let { "$it • Dizi" } ?: "Dizi",
                                        rating = series.rating,
                                        onClick = { onSeriesClick(series) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SearchFilter.LIVE -> {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(liveResults, key = { "live_tab_${it.streamId}" }) { channel ->
                    UniversalSearchChannelRow(
                        channel = channel,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }

        SearchFilter.MOVIES -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(vodResults, key = { "vod_tab_${it.streamId}" }) { vod ->
                    MediaPosterCard(
                        imageUrl = vod.streamIcon,
                        title = vod.name,
                        subtitle = "Film",
                        rating = vod.rating,
                        onClick = { onVodClick(vod) }
                    )
                }
            }
        }

        SearchFilter.SERIES -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(seriesResults, key = { "series_tab_${it.seriesId}" }) { series ->
                    MediaPosterCard(
                        imageUrl = series.cover,
                        title = series.name,
                        subtitle = series.releaseDate?.take(4)?.let { "$it • Dizi" } ?: "Dizi",
                        rating = series.rating,
                        onClick = { onSeriesClick(series) }
                    )
                }
            }
        }
    }
}

/**
 * Bölüm Başlığı (Örn: Canlı TV · 12   Tümünü gör ›)
 */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    titleColor: Color,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$title · $count",
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        TextButton(
            onClick = onSeeAllClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tümünü gör",
                    color = VegaColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = VegaColors.TextSecondary,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Kompakt Canlı Kanal Satırı (≤64dp kuralı)
 */
@Composable
private fun UniversalSearchChannelRow(
    channel: LiveStream,
    onClick: () -> Unit
) {
    Surface(
        shape = VegaShapes.Medium,
        color = VegaColors.Card,
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.Accent,
                onClick = onClick
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kanal Logosu
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(VegaShapes.Small)
                    .background(VegaColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncShimmerImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    placeholderIcon = Icons.Default.Tv,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Kanal Bilgisi (No, İsim, EPG)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel.num > 0) {
                        Text(
                            text = "${channel.num}",
                            color = VegaColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = channel.name,
                        color = VegaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val programInfo = channel.currentProgram ?: "Program bilgisi yok"
                Text(
                    text = if (channel.currentProgram != null) "Şimdi: $programInfo" else programInfo,
                    color = VegaColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Oynat İkonu
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Oynat",
                tint = VegaColors.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
