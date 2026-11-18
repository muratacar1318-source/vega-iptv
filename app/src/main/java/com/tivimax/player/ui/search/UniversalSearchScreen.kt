@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.search

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.tivimax.player.util.LocalIsTvDevice
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

    val configuration = LocalConfiguration.current
    val isTv = LocalIsTvDevice.current
    val isWideScreen = isTv ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    val onBack: () -> Unit = {
        if (!viewModel.navigateBack()) {
            viewModel.navigateTo(Screen.Home, false)
        }
    }

    BackHandler { onBack() }

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
                    .background(VegaColors.Surface.copy(alpha = 0.96f))
                    .statusBarsPadding()
            ) {
                // Üst Bar: Geri + Tam Genişlik Arama Çubuğu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VegaIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        onClick = onBack
                    )

                    Surface(
                        shape = VegaShapes.Medium,
                        color = if (isSearchFocused) VegaColors.SurfaceElevated else VegaColors.SurfaceVariant.copy(alpha = 0.72f),
                        border = BorderStroke(
                            width = if (isSearchFocused) 1.5.dp else 0.75.dp,
                            color = if (isSearchFocused) VegaColors.FocusBorder else VegaColors.Border.copy(alpha = 0.65f)
                        ),
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
                                tint = if (isSearchFocused) VegaColors.Accent else VegaColors.TextMuted,
                                modifier = Modifier.size(19.dp)
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

                // Minimal Yatay Filtre Hapları (Tümü, Canlı, Filmler, Diziler)
                if (searchQuery.length >= 2) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
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
                                label = "Canlı",
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

                // Yükleme Çubuğu veya İnce Ayırıcı
                if (isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = VegaColors.Accent,
                        trackColor = Color.Transparent
                    )
                } else {
                    HorizontalDivider(
                        color = VegaColors.Border.copy(alpha = 0.55f),
                        thickness = 0.5.dp
                    )
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
                // Durum 1: Arama kutusu boş -> Sakin boş durum + Son aramalar çipleri
                searchQuery.isEmpty() -> {
                    EmptyAndRecentState(
                        recentSearches = recentSearches,
                        onSearchClick = {
                            viewModel.setUniversalSearchQuery(it)
                            viewModel.saveRecentSearch(it)
                        },
                        onRemoveClick = { viewModel.removeRecentSearch(it) },
                        onClearAll = { viewModel.clearRecentSearches() }
                    )
                }

                // Durum 2: 1 karakter
                searchQuery.length == 1 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "En az 2 harf yaz",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Body
                        )
                    }
                }

                // Durum 3: Sonuç yok
                totalCount == 0 && !isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = VegaColors.TextMuted,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = "'$searchQuery' için sonuç bulunamadı",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.BodyProminent,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Durum 4: Sonuçlar
                else -> {
                    SearchResultsContent(
                        activeFilter = activeFilter,
                        isWideScreen = isWideScreen,
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
            else -> VegaColors.SurfaceVariant.copy(alpha = 0.72f)
        },
        modifier = Modifier.dpadFocusable(
            shape = VegaShapes.Pill,
            focusedBorderColor = if (isSelected) Color.White else VegaColors.FocusBorder,
            onClick = onClick,
            onFocusStateChange = { isFocused = it }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                color = if (isSelected) VegaColors.OnAccent else VegaColors.TextPrimary,
                fontSize = VegaText.Secondary,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
            if (count > 0) {
                Text(
                    text = "$count",
                    color = if (isSelected) VegaColors.OnAccent.copy(alpha = 0.78f) else VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyAndRecentState(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (recentSearches.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Son aramalar",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    VegaButton(
                        text = "Temizle",
                        variant = VegaButtonVariant.TEXT,
                        onClick = onClearAll
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(recentSearches, key = { idx, q -> "recent_${q}_$idx" }) { _, query ->
                        RecentSearchChip(
                            query = query,
                            onClick = { onSearchClick(query) },
                            onRemove = { onRemoveClick(query) }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = VegaColors.TextMuted,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = "Kanal, film veya dizi ara",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RecentSearchChip(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var pillFocused by remember { mutableStateOf(false) }
    var removeFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = VegaShapes.Pill,
            color = if (pillFocused) VegaColors.SurfaceElevated else VegaColors.Surface,
            border = if (!pillFocused) BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)) else null,
            modifier = Modifier.dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = VegaColors.FocusBorder,
                onClick = onClick,
                onFocusStateChange = { pillFocused = it }
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = if (pillFocused) VegaColors.Accent else VegaColors.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = query,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            shape = VegaShapes.Pill,
            color = if (removeFocused) VegaColors.SurfaceElevated else Color.Transparent,
            modifier = Modifier
                .size(30.dp)
                .dpadFocusable(
                    shape = VegaShapes.Pill,
                    focusedBorderColor = VegaColors.FocusBorder,
                    onClick = onRemove,
                    onFocusStateChange = { removeFocused = it }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Sil",
                    tint = VegaColors.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    activeFilter: SearchFilter,
    isWideScreen: Boolean,
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
    val gridColumns = if (isWideScreen) GridCells.Adaptive(minSize = 130.dp) else GridCells.Fixed(3)
    val gridSpacing = if (isWideScreen) 12.dp else 10.dp

    when (activeFilter) {
        SearchFilter.ALL -> {
            val firstInLive = liveResults.isNotEmpty()
            val firstInVod = !firstInLive && vodResults.isNotEmpty()
            val firstInSeries = !firstInLive && !firstInVod && seriesResults.isNotEmpty()
            val previewLive = remember(liveResults) { liveResults.take(6) }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (previewLive.isNotEmpty()) {
                    item(key = "search_header_live") {
                        SectionHeader(
                            title = "Canlı",
                            count = liveResults.size,
                            onSeeAllClick = onSeeAllLive
                        )
                    }
                    itemsIndexed(previewLive, key = { idx, item -> "search_live_${item.streamId}_$idx" }) { idx, channel ->
                        UniversalSearchChannelRow(
                            channel = channel,
                            showDivider = idx < previewLive.lastIndex,
                            onClick = { onChannelClick(channel) },
                            modifier = if (firstInLive && idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier
                        )
                    }
                }

                if (vodResults.isNotEmpty()) {
                    item(key = "search_section_vod") {
                        SectionHeader(
                            title = "Filmler",
                            count = vodResults.size,
                            onSeeAllClick = onSeeAllVod
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRestorer()
                        ) {
                            itemsIndexed(vodResults, key = { idx, item -> "search_vod_${item.streamId}_$idx" }) { idx, vod ->
                                Box(
                                    modifier = Modifier
                                        .width(if (isWideScreen) 126.dp else 108.dp)
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
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (seriesResults.isNotEmpty()) {
                    item(key = "search_section_series") {
                        SectionHeader(
                            title = "Diziler",
                            count = seriesResults.size,
                            onSeeAllClick = onSeeAllSeries
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRestorer()
                        ) {
                            itemsIndexed(seriesResults, key = { idx, item -> "search_series_${item.seriesId}_$idx" }) { idx, series ->
                                val releaseYear = series.releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                                Box(
                                    modifier = Modifier
                                        .width(if (isWideScreen) 126.dp else 108.dp)
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
                contentPadding = PaddingValues(vertical = 6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(liveResults, key = { idx, item -> "search_live_tab_${item.streamId}_$idx" }) { idx, channel ->
                    UniversalSearchChannelRow(
                        channel = channel,
                        showDivider = idx < liveResults.lastIndex,
                        onClick = { onChannelClick(channel) },
                        modifier = if (idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier
                    )
                }
            }
        }

        SearchFilter.MOVIES -> {
            LazyVerticalGrid(
                columns = gridColumns,
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
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
                columns = gridColumns,
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
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

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    onSeeAllClick: () -> Unit
) {
    var seeAllFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.BodyProminent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$count",
                color = VegaColors.TextMuted,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Medium
            )
        }

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
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Tümünü gör",
                    color = if (seeAllFocused) VegaColors.Accent else VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = if (seeAllFocused) VegaColors.Accent else VegaColors.TextMuted,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * İnce çizgi ayırıcılı canlı kanal satırı (çerçevesiz, tek odak hedefli).
 */
@Composable
private fun UniversalSearchChannelRow(
    channel: LiveStream,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = VegaShapes.Small,
            color = if (isFocused) VegaColors.SurfaceElevated else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .dpadFocusable(
                    shape = VegaShapes.Small,
                    focusedBorderColor = VegaColors.FocusBorder,
                    onClick = onClick,
                    onFocusStateChange = { isFocused = it }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val program = channel.currentProgram?.trim()?.takeIf { it.isNotBlank() }
                    if (program != null) {
                        Text(
                            text = program,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Oynat",
                    tint = if (isFocused) VegaColors.Accent else VegaColors.TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                color = VegaColors.Border.copy(alpha = 0.45f),
                thickness = 0.5.dp
            )
        }
    }
}
