@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.SearchFilter
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import com.tivimax.player.ui.design.*
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }

    val onBack: () -> Unit = {
        if (!viewModel.navigateBack()) {
            viewModel.navigateTo(Screen.Home, false)
        }
    }

    // Donanım geri tuşu desteği
    BackHandler { onBack() }

    // Ekran ilk açıldığında arama kutusuna güvenli odaklan
    LaunchedEffect(Unit) {
        delay(200)
        searchFocusRequester.requestFocusSafely()
    }

    val totalCount = liveResults.size + vodResults.size + seriesResults.size

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VegaColors.Surface.copy(alpha = 0.94f))
                    .statusBarsPadding()
            ) {
                // Üst Bar: Tek odaklı Geri Butonu + StreamVault Arama Kutusu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VegaIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        onClick = onBack
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Arama Kutusu (Odak çerçeveli, TV kumanda uyumlu)
                    Surface(
                        shape = VegaShapes.Medium,
                        color = if (isSearchFocused) VegaColors.SurfaceElevated else VegaColors.Card,
                        border = BorderStroke(
                            if (isSearchFocused) 2.dp else 1.dp,
                            if (isSearchFocused) VegaColors.FocusBorder else VegaColors.Border
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Ara",
                                tint = if (isSearchFocused) VegaColors.Accent else VegaColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Kanal, film veya dizi ara…",
                                        color = VegaColors.TextMuted,
                                        fontSize = VegaText.Body,
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setUniversalSearchQuery(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = VegaColors.TextPrimary,
                                        fontSize = VegaText.Body
                                    ),
                                    cursorBrush = SolidColor(VegaColors.Accent),
                                    keyboardOptions = tvKeyboardOptions(KeyboardOptions(imeAction = ImeAction.Search)),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            keyboardController?.hide()
                                            viewModel.saveRecentSearch(searchQuery)
                                            if (totalCount > 0) {
                                                if (!firstResultFocusRequester.requestFocusSafely()) {
                                                    focusManager.moveFocus(FocusDirection.Down)
                                                }
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .tvOpenKeyboardOnOk()
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                        .onFocusChanged { isSearchFocused = it.isFocused }
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .dpadFocusable(
                                            shape = VegaShapes.Pill,
                                            focusedBorderColor = VegaColors.FocusBorder,
                                            onClick = { viewModel.setUniversalSearchQuery("") }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Temizle",
                                        tint = VegaColors.TextSecondary,
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
                    HorizontalDivider(color = VegaColors.Border.copy(alpha = 0.6f), thickness = 0.5.dp)
                }

                // Filtre Çipleri (Sonuç Sayıları ile Birlikte)
                if (searchQuery.length >= 2) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item(key = "filter_all") {
                            SearchFilterChip(
                                label = "Tümü",
                                count = totalCount,
                                isSelected = activeFilter == SearchFilter.ALL,
                                onClick = { viewModel.setUniversalSearchFilter(SearchFilter.ALL) }
                            )
                        }
                        item(key = "filter_live") {
                            SearchFilterChip(
                                label = "Canlı TV",
                                count = liveResults.size,
                                isSelected = activeFilter == SearchFilter.LIVE,
                                onClick = { viewModel.setUniversalSearchFilter(SearchFilter.LIVE) }
                            )
                        }
                        item(key = "filter_movies") {
                            SearchFilterChip(
                                label = "Filmler",
                                count = vodResults.size,
                                isSelected = activeFilter == SearchFilter.MOVIES,
                                onClick = { viewModel.setUniversalSearchFilter(SearchFilter.MOVIES) }
                            )
                        }
                        item(key = "filter_series") {
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
        modifier = modifier
            .fillMaxSize()
            .vegaBackdrop()
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

                // Durum 2: 1 Karakter Girildi -> "En az 2 harf yaz"
                searchQuery.length == 1 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .vegaPanel(VegaShapes.Hero)
                                .padding(horizontal = 24.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "En az 2 harf yaz",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
                        Column(
                            modifier = Modifier
                                .widthIn(max = 440.dp)
                                .fillMaxWidth(0.9f)
                                .vegaPanel(VegaShapes.Hero)
                                .padding(horizontal = 28.dp, vertical = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = VegaColors.SurfaceVariant,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        tint = VegaColors.TextMuted,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "'$searchQuery' için sonuç bulunamadı",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Yazımı kontrol et veya farklı bir kelime dene.",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Secondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Durum 4: Arama Sonuçları Listesi
                else -> {
                    SearchResultsContent(
                        activeFilter = activeFilter,
                        liveResults = liveResults,
                        vodResults = vodResults,
                        seriesResults = seriesResults,
                        firstResultFocusRequester = firstResultFocusRequester,
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
 * Filtre Çipi Bileşeni (Tümü, Canlı TV, Filmler, Diziler) — Tek odak hedefi.
 */
@Composable
private fun SearchFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Pill,
        color = when {
            isSelected -> VegaColors.Accent
            isFocused -> VegaColors.SurfaceElevated
            else -> VegaColors.Card
        },
        border = if (!isFocused) {
            BorderStroke(1.dp, if (isSelected) VegaColors.Accent else VegaColors.Border)
        } else null,
        modifier = Modifier.dpadFocusable(
            shape = VegaShapes.Pill,
            focusedBorderColor = if (isSelected) Color.White else VegaColors.FocusBorder,
            onClick = onClick,
            onFocusStateChange = { isFocused = it }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isSelected) VegaColors.OnAccent else VegaColors.TextPrimary,
                fontSize = VegaText.Secondary,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$count",
                    color = if (isSelected) VegaColors.OnAccent.copy(alpha = 0.78f) else VegaColors.Accent,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Son Aramalar Bölümü (En fazla 8 adet, ayrı odak hedefli arama ve silme düğmesi + Temizle).
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
                fontSize = VegaText.BodyProminent,
                fontWeight = FontWeight.Bold
            )
            VegaButton(
                text = "Temizle",
                variant = VegaButtonVariant.TEXT,
                onClick = onClearAll
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Son Aramalar Listesi (İç içe tıklama yok — arama kartı ve silme düğmesi yan yana ayrı odak)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(recentSearches, key = { idx, q -> "recent_${q}_$idx" }) { _, query ->
                var itemFocused by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = if (itemFocused) VegaColors.SurfaceElevated else VegaColors.Card,
                        border = if (!itemFocused) BorderStroke(1.dp, VegaColors.Border) else null,
                        modifier = Modifier
                            .weight(1f)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.FocusBorder,
                                onClick = { onSearchClick(query) },
                                onFocusStateChange = { itemFocused = it }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (itemFocused) VegaColors.Accent else VegaColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = query,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = if (itemFocused) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    VegaIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Sil",
                        onClick = { onRemoveClick(query) }
                    )
                }
            }
        }
    }
}

/**
 * İlk Boş Durum Bilgilendirme Kartı (StreamVault cam panel tarzı)
 */
@Composable
private fun EmptyInitialState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .vegaPanel(VegaShapes.Hero)
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = VegaColors.Accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.30f)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Kanal, film veya dizi ara",
                color = VegaColors.TextPrimary,
                fontSize = VegaText.SectionTitle,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tüm arşivde anında arama yapmak için en az 2 harf yaz.",
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Arama Sonuçları İçeriği (Tümü veya Özel Filtre Sekmesi)
 */
@Composable
private fun SearchResultsContent(
    activeFilter: SearchFilter,
    liveResults: List<LiveStream>,
    vodResults: List<VodStream>,
    seriesResults: List<SeriesStream>,
    firstResultFocusRequester: FocusRequester,
    onChannelClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (SeriesStream) -> Unit,
    onSeeAllLive: () -> Unit,
    onSeeAllVod: () -> Unit,
    onSeeAllSeries: () -> Unit
) {
    when (activeFilter) {
        SearchFilter.ALL -> {
            val firstInLive = liveResults.isNotEmpty()
            val firstInVod = !firstInLive && vodResults.isNotEmpty()
            val firstInSeries = !firstInLive && !firstInVod && seriesResults.isNotEmpty()

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. CANLI TV BÖLÜMÜ
                if (liveResults.isNotEmpty()) {
                    item(key = "search_header_live") {
                        SectionHeader(
                            title = "Canlı TV",
                            count = liveResults.size,
                            titleColor = VegaColors.Accent,
                            onSeeAllClick = onSeeAllLive
                        )
                    }
                    itemsIndexed(liveResults.take(6), key = { idx, item -> "search_live_${item.streamId}_$idx" }) { idx, channel ->
                        UniversalSearchChannelRow(
                            channel = channel,
                            onClick = { onChannelClick(channel) },
                            modifier = if (firstInLive && idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier
                        )
                    }
                }

                // 2. FİLMLER BÖLÜMÜ
                if (vodResults.isNotEmpty()) {
                    item(key = "search_section_vod") {
                        SectionHeader(
                            title = "Filmler",
                            count = vodResults.size,
                            titleColor = VegaColors.Accent,
                            onSeeAllClick = onSeeAllVod
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().focusRestorer() // G-047
                        ) {
                            itemsIndexed(vodResults, key = { idx, item -> "search_vod_${item.streamId}_$idx" }) { idx, vod ->
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .then(if (firstInVod && idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier)
                                ) {
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
                    item(key = "search_section_series") {
                        SectionHeader(
                            title = "Diziler",
                            count = seriesResults.size,
                            titleColor = VegaColors.Accent,
                            onSeeAllClick = onSeeAllSeries
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().focusRestorer() // G-047
                        ) {
                            itemsIndexed(seriesResults, key = { idx, item -> "search_series_${item.seriesId}_$idx" }) { idx, series ->
                                val releaseYear = series.releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .then(if (firstInSeries && idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier)
                                ) {
                                    MediaPosterCard(
                                        imageUrl = series.cover,
                                        title = series.name,
                                        subtitle = if (releaseYear != null) "$releaseYear • Dizi" else "Dizi",
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
                itemsIndexed(liveResults, key = { idx, item -> "search_live_tab_${item.streamId}_$idx" }) { idx, channel ->
                    UniversalSearchChannelRow(
                        channel = channel,
                        onClick = { onChannelClick(channel) },
                        modifier = if (idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier
                    )
                }
            }
        }

        SearchFilter.MOVIES -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 115.dp),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(vodResults, key = { idx, item -> "search_vod_tab_${item.streamId}_$idx" }) { idx, vod ->
                    Box(modifier = if (idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier) {
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
        }

        SearchFilter.SERIES -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 115.dp),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(seriesResults, key = { idx, item -> "search_series_tab_${item.seriesId}_$idx" }) { idx, series ->
                    val releaseYear = series.releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                    Box(modifier = if (idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier) {
                        MediaPosterCard(
                            imageUrl = series.cover,
                            title = series.name,
                            subtitle = if (releaseYear != null) "$releaseYear • Dizi" else "Dizi",
                            rating = series.rating,
                            onClick = { onSeriesClick(series) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bölüm Başlığı (Örn: Canlı TV · 12   Tümünü gör ›) — Tek odaklı aksiyon.
 */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    titleColor: Color,
    onSeeAllClick: () -> Unit
) {
    var seeAllFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title · $count",
            color = titleColor,
            fontSize = VegaText.Secondary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )

        Surface(
            shape = VegaShapes.Small,
            color = if (seeAllFocused) VegaColors.SurfaceElevated else Color.Transparent,
            modifier = Modifier.dpadFocusable(
                shape = VegaShapes.Small,
                focusedBorderColor = VegaColors.FocusBorder,
                onClick = onSeeAllClick,
                onFocusStateChange = { seeAllFocused = it }
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tümünü gör",
                    color = if (seeAllFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    fontWeight = if (seeAllFocused) FontWeight.SemiBold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = if (seeAllFocused) VegaColors.Accent else VegaColors.TextSecondary,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Kompakt Canlı Kanal Satırı (≤64dp kuralı, tek odak hedefi — çift tıklama yok).
 */
@Composable
private fun UniversalSearchChannelRow(
    channel: LiveStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Medium,
        color = if (isFocused) VegaColors.SurfaceElevated else VegaColors.Card,
        border = if (!isFocused) BorderStroke(1.dp, VegaColors.Border) else null,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.FocusBorder,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
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

            // Kanal Bilgisi (No, İsim, Program)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel.num > 0) {
                        Text(
                            text = "${channel.num}",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = channel.name,
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val programInfo = channel.currentProgram ?: "Program bilgisi yok"
                Text(
                    text = if (channel.currentProgram != null) "Şimdi: $programInfo" else programInfo,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
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
