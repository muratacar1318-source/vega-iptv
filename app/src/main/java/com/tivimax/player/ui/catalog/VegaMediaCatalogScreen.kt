@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.catalog

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.*
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaPanel
import com.tivimax.player.ui.main.StreamVaultSectionHeader
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class SeriesShelfData(
    val topRated: List<SeriesStream> = emptyList(),
    val categories: List<Pair<CategoryUiItem, List<SeriesStream>>> = emptyList(),
    val countsByCat: Map<String, Int> = emptyMap(),
    val isReady: Boolean = false
)

@Composable
fun VegaMediaCatalogScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleSeriesCategories.collectAsState()
    val seriesList by viewModel.seriesStreams.collectAsState()
    val selectedCategory by viewModel.selectedSeriesCategory.collectAsState()
    val isLoading by viewModel.seriesLoading.collectAsState()
    val currentSort by viewModel.seriesSortOption.collectAsState()

    val configuration = LocalConfiguration.current
    val isTvDevice = LocalIsTvDevice.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val isMobilePortrait = !isTvDevice && !isWideScreen

    var showCategorySheet by remember { mutableStateOf(false) }
    var mobileSortMenuOpen by remember { mutableStateOf(false) }

    val totalSeriesCount = remember(categories) {
        categories.firstOrNull { it.categoryId == "all" }?.itemCount
            ?: categories.filter { it.categoryId != "all" }.sumOf { it.itemCount }
    }
    val selectedCategoryItem = remember(categories, selectedCategory) {
        if (selectedCategory == "all" || selectedCategory.isEmpty()) null
        else categories.firstOrNull { it.categoryId == selectedCategory }
    }
    val selectedCategoryName = if (selectedCategory == "all" || selectedCategory.isEmpty()) {
        "Tüm Diziler"
    } else {
        selectedCategoryItem?.categoryName ?: "Tüm Diziler"
    }

    val uiCategories = remember(categories) {
        categories
            .filter { it.categoryId != "all" }
            .distinctBy { it.categoryId }
            .map {
                CategoryUiItem(
                    id = it.categoryId,
                    name = it.categoryName,
                    count = it.itemCount,
                    icon = Icons.Default.Folder,
                    iconTint = VegaColors.Accent
                )
            }
    }

    val searchQuery by viewModel.seriesSearchQuery.collectAsState()
    val hasActiveCategory = selectedCategory != "all" && selectedCategory.isNotEmpty()
    val hasSearch = searchQuery.isNotBlank()

    val useShelfMode by viewModel.seriesUseShelfMode.collectAsState()
    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
    val hasAdultSeries by viewModel.hasAdultSeriesCategory.collectAsState()
    var shelfSortMenuOpen by remember { mutableStateOf(false) }
    var showShelfCategoryPicker by remember { mutableStateOf(false) }

    // TV'de arama veya "Tümünü gör" ile açılan kategoriden Geri tuşuyla doğrudan raflara dön
    BackHandler(
        enabled = (isTvDevice && (hasSearch || (useShelfMode && hasActiveCategory))) ||
            (!isTvDevice && (showCategorySheet || hasSearch || hasActiveCategory))
    ) {
        when {
            showCategorySheet -> showCategorySheet = false
            hasSearch -> viewModel.setSeriesSearchQuery("")
            hasActiveCategory -> viewModel.loadSeriesStreams("all")
        }
    }

    if (showCategorySheet) {
        MediaCategoryBottomSheet(
            title = "Dizi Kategorileri",
            categories = uiCategories,
            selectedCategoryId = selectedCategory,
            totalCount = totalSeriesCount,
            onCategorySelected = { viewModel.loadSeriesStreams(it) },
            onDismissRequest = { showCategorySheet = false }
        )
    }

    if (showShelfCategoryPicker) {
        SeriesCategoryPickerDialog(
            title = "Dizi kategorileri",
            allLabel = "Tüm Diziler",
            totalCount = totalSeriesCount,
            categories = uiCategories,
            selectedCategoryId = selectedCategory,
            onSelect = { catId ->
                showShelfCategoryPicker = false
                viewModel.loadSeriesStreams(catId)
            },
            onDismiss = { showShelfCategoryPicker = false }
        )
    }

    if (isMobilePortrait) {
        // MOBİL DİZİLER DENEYİMİ: Kompakt tek satır üst araç çubuğu + 3 sütunlu kenardan kenara ızgara veya temiz editoryal raflar
        val showMobileShelf = useShelfMode && !hasActiveCategory && !hasSearch

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (hasActiveCategory) 4.dp else 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasActiveCategory) {
                        Box(
                            modifier = Modifier
                                .size(VegaSizes.IconButton)
                                .clip(CircleShape)
                                .dpadFocusable(
                                    shape = CircleShape,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    onClick = { viewModel.loadSeriesStreams("all") }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Tüm diziler",
                                tint = VegaColors.TextPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Text(
                        text = if (hasActiveCategory) selectedCategoryName else "Diziler",
                        color = VegaColors.TextPrimary,
                        fontSize = if (hasActiveCategory) 19.sp else VegaText.ScreenTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ParentalLockChip(viewModel, hasAdultSeries)

                    Box(
                        modifier = Modifier
                            .height(VegaSizes.MinTouch)
                            .dpadFocusable(
                                shape = VegaShapes.Pill,
                                focusedBorderColor = VegaColors.FocusBorder,
                                onClick = { showCategorySheet = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = VegaShapes.Pill,
                            color = if (hasActiveCategory) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceVariant.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Kategoriler",
                                    color = if (hasActiveCategory) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = if (hasActiveCategory) VegaColors.Accent else VegaColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .size(VegaSizes.IconButton)
                                .clip(CircleShape)
                                .dpadFocusable(
                                    shape = CircleShape,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    onClick = { mobileSortMenuOpen = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sırala",
                                tint = if (currentSort != SortOption.DEFAULT) VegaColors.Accent else VegaColors.TextSecondary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        SeriesSortDropdownMenu(
                            expanded = mobileSortMenuOpen,
                            currentSort = currentSort,
                            onSelect = {
                                viewModel.setSeriesSort(it)
                                mobileSortMenuOpen = false
                            },
                            onDismiss = { mobileSortMenuOpen = false }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(VegaSizes.IconButton)
                            .clip(CircleShape)
                            .dpadFocusable(
                                shape = CircleShape,
                                focusedBorderColor = VegaColors.FocusBorder,
                                onClick = {
                                    if (showMobileShelf) {
                                        viewModel.setSeriesUseShelfMode(false)
                                    } else {
                                        viewModel.setSeriesUseShelfMode(true)
                                        if (hasActiveCategory) viewModel.loadSeriesStreams("all")
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showMobileShelf) Icons.Default.GridView else Icons.Default.ViewCarousel,
                            contentDescription = if (showMobileShelf) "Izgara görünümü" else "Raf görünümü",
                            tint = VegaColors.TextSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            if (showMobileShelf && !isLoading && seriesList.isNotEmpty()) {
                // MOBİL RAF GÖRÜNÜMÜ (Kutusuz editoryal kategori şeritleri)
                val shelfData by produceState(
                    initialValue = SeriesShelfData(),
                    key1 = seriesList,
                    key2 = uiCategories
                ) {
                    value = withContext(Dispatchers.Default) {
                        val grouped = seriesList.groupBy { it.categoryId }
                        val counts = grouped.mapValues { it.value.size }
                        val top = seriesList
                            .distinctBy { it.seriesId }
                            .sortedByDescending {
                                it.rating?.replace(',', '.')?.toFloatOrNull()
                                    ?: (it.rating5Based?.toFloat()?.times(2f) ?: 0f)
                            }
                            .take(18)
                        val shelves = uiCategories.mapNotNull { cat ->
                            val list = grouped[cat.id]?.distinctBy { it.seriesId }?.take(16)
                            if (!list.isNullOrEmpty()) cat to list else null
                        }
                        SeriesShelfData(
                            topRated = top,
                            categories = shelves,
                            countsByCat = counts,
                            isReady = true
                        )
                    }
                }
                var visibleShelfCats by remember(shelfData.categories) { mutableIntStateOf(10) }
                val visibleShelfCategories = remember(shelfData.categories, visibleShelfCats) {
                    shelfData.categories.take(visibleShelfCats)
                }

                if (!shelfData.isReady) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 2.5.dp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        if (shelfData.topRated.isNotEmpty()) {
                            item(key = "mob_series_shelf_top") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Öne çıkanlar",
                                        color = VegaColors.TextPrimary,
                                        fontSize = VegaText.SectionTitle,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(shelfData.topRated, key = { idx, it -> "mob_top_ser_${it.seriesId}_$idx" }) { _, series ->
                                            Box(modifier = Modifier.width(124.dp)) {
                                                MediaPosterCard(
                                                    imageUrl = series.cover,
                                                    title = series.name,
                                                    subtitle = "",
                                                    rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                                    isFavorite = favoriteSeriesIds.contains(series.seriesId.toString()),
                                                    onClick = { viewModel.loadSeriesDetail(series) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        itemsIndexed(visibleShelfCategories, key = { idx, pair -> "mob_ser_shelf_${pair.first.id}_$idx" }) { _, (cat, catSeries) ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MobileSeriesShelfHeader(
                                    title = cat.name,
                                    onSeeAllClick = { viewModel.loadSeriesStreams(cat.id) }
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(catSeries, key = { idx, it -> "mob_shelf_${cat.id}_${it.seriesId}_$idx" }) { _, series ->
                                        Box(modifier = Modifier.width(116.dp)) {
                                            MediaPosterCard(
                                                imageUrl = series.cover,
                                                title = series.name,
                                                subtitle = "",
                                                rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                                isFavorite = favoriteSeriesIds.contains(series.seriesId.toString()),
                                                onClick = { viewModel.loadSeriesDetail(series) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (visibleShelfCats < shelfData.categories.size) {
                            item(key = "mob_ser_shelf_more_$visibleShelfCats") {
                                LaunchedEffect(visibleShelfCats) {
                                    visibleShelfCats += 10
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = VegaColors.Accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // MOBİL 3 SÜTUNLU KENARDAN KENARA AFİŞ IZGARASI
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    VegaMediaContentGrid(
                        isLoading = isLoading,
                        seriesList = seriesList,
                        favoriteSeriesIds = favoriteSeriesIds,
                        searchQuery = searchQuery,
                        onClearSearch = { viewModel.setSeriesSearchQuery("") },
                        onSearchAllLive = { viewModel.searchAcrossAllLive(searchQuery) },
                        onSearchAllVod = { viewModel.searchAcrossAllVod(searchQuery) },
                        onSearchAllSeries = { viewModel.searchAcrossAllSeries(searchQuery) },
                        currentSort = currentSort,
                        onSortSelected = { sort -> viewModel.setSeriesSort(sort) },
                        onSeriesClick = { series -> viewModel.loadSeriesDetail(series) },
                        isWideScreen = false,
                        isMobileCompact = true,
                        selectedCategory = selectedCategory,
                        positionStore = viewModel.catalogPositions
                    )
                }
            }
        }
    } else if (useShelfMode && !hasActiveCategory && !hasSearch && !isLoading && seriesList.isNotEmpty()) {
        // TV & GENİŞ EKRAN SİNEMA DİZİ RAFLARI GÖRÜNÜMÜ (Default dispatcher'da gruplama)
        val shelfData by produceState(
            initialValue = SeriesShelfData(),
            key1 = seriesList,
            key2 = uiCategories
        ) {
            value = withContext(Dispatchers.Default) {
                val grouped = seriesList.groupBy { it.categoryId }
                val counts = grouped.mapValues { it.value.size }
                val top = seriesList
                    .distinctBy { it.seriesId }
                    .sortedByDescending {
                        it.rating?.replace(',', '.')?.toFloatOrNull()
                            ?: (it.rating5Based?.toFloat()?.times(2f) ?: 0f)
                    }
                    .take(18)
                val shelves = uiCategories.mapNotNull { cat ->
                    val list = grouped[cat.id]?.distinctBy { it.seriesId }?.take(16)
                    if (!list.isNullOrEmpty()) cat to list else null
                }
                SeriesShelfData(
                    topRated = top,
                    categories = shelves,
                    countsByCat = counts,
                    isReady = true
                )
            }
        }
        val topSeries = shelfData.topRated
        var visibleShelfCats by remember(shelfData.categories) { mutableIntStateOf(10) }
        val visibleShelfCategories = remember(shelfData.categories, visibleShelfCats) {
            shelfData.categories.take(visibleShelfCats)
        }
        val shelfPosterWidth = if (isTvDevice) 160.dp else 148.dp
        val topPosterWidth = if (isTvDevice) 164.dp else 152.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTvDevice) 24.dp else 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTvDevice) 26.dp else 22.dp)
        ) {
            item(key = "series_shelf_controls") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Diziler",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.ScreenTitle,
                            fontWeight = FontWeight.Bold
                        )
                        if (totalSeriesCount > 0) {
                            Text(
                                text = "$totalSeriesCount dizi",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ParentalLockChip(viewModel, hasAdultSeries)

                        SeriesTonalPill(
                            label = "Ara",
                            icon = Icons.Default.Search,
                            onClick = { viewModel.navigateTo(Screen.Search) }
                        )

                        Box {
                            val sortLabel = when (currentSort) {
                                SortOption.ADDED_DESC -> "Eklenme"
                                SortOption.RATING_DESC -> "Puan"
                                SortOption.NAME_ASC -> "A - Z"
                                SortOption.NAME_DESC -> "Z - A"
                                SortOption.DEFAULT -> "Sırala"
                            }
                            SeriesTonalPill(
                                label = sortLabel,
                                icon = Icons.AutoMirrored.Filled.Sort,
                                isActive = currentSort != SortOption.DEFAULT,
                                onClick = { shelfSortMenuOpen = true }
                            )
                            SeriesSortDropdownMenu(
                                expanded = shelfSortMenuOpen,
                                currentSort = currentSort,
                                onSelect = {
                                    viewModel.setSeriesSort(it)
                                    shelfSortMenuOpen = false
                                },
                                onDismiss = { shelfSortMenuOpen = false }
                            )
                        }

                        SeriesTonalPill(
                            label = "Kategoriler",
                            icon = Icons.Default.Folder,
                            onClick = { showShelfCategoryPicker = true }
                        )

                        SeriesTonalPill(
                            label = "Klasik liste",
                            icon = Icons.AutoMirrored.Filled.MenuOpen,
                            onClick = { viewModel.setSeriesUseShelfMode(false) }
                        )
                    }
                }
            }

            if (!shelfData.isReady) {
                item(key = "series_shelf_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 2.5.dp)
                    }
                }
            } else {
                if (topSeries.isNotEmpty()) {
                    item(key = "series_shelf_top") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().focusRestorer()
                        ) {
                            itemsIndexed(topSeries, key = { idx, it -> "top_ser_${it.seriesId}_$idx" }) { _, series ->
                                Box(modifier = Modifier.width(topPosterWidth)) {
                                    MediaPosterCard(
                                        imageUrl = series.cover,
                                        title = series.name,
                                        subtitle = "",
                                        rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                        isFavorite = favoriteSeriesIds.contains(series.seriesId.toString()),
                                        onClick = { viewModel.loadSeriesDetail(series) }
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(visibleShelfCategories, key = { idx, pair -> "ser_shelf_${pair.first.id}_$idx" }) { _, (cat, catSeries) ->
                    val catTotalCount = cat.count.takeIf { it > 0 } ?: shelfData.countsByCat[cat.id]
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StreamVaultSectionHeader(
                            title = cat.name,
                            count = catTotalCount,
                            onSeeAllClick = { viewModel.loadSeriesStreams(cat.id) }
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().focusRestorer()
                        ) {
                            itemsIndexed(catSeries, key = { idx, it -> "shelf_${cat.id}_${it.seriesId}_$idx" }) { _, series ->
                                Box(modifier = Modifier.width(shelfPosterWidth)) {
                                    MediaPosterCard(
                                        imageUrl = series.cover,
                                        title = series.name,
                                        subtitle = "",
                                        rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                        isFavorite = favoriteSeriesIds.contains(series.seriesId.toString()),
                                        onClick = { viewModel.loadSeriesDetail(series) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (visibleShelfCats < shelfData.categories.size) {
                    item(key = "shelf_more_$visibleShelfCats") {
                        LaunchedEffect(visibleShelfCats) {
                            visibleShelfCats += 10
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = VegaColors.Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // TV / TABLET KLASİK LİSTE VEYA SEÇİLİ KATEGORİ IZGARASI
        val backToShelvesFocusRequester = remember { FocusRequester() }
        LaunchedEffect(selectedCategory, useShelfMode, hasSearch) {
            if (isTvDevice && isWideScreen && (hasActiveCategory || hasSearch) && useShelfMode && viewModel.catalogPositions.lastOpenedItemKey == null) {
                repeat(3) {
                    withFrameNanos { }
                    delay(70)
                    if (backToShelvesFocusRequester.requestFocusSafely()) return@LaunchedEffect
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!useShelfMode) {
                CategorySidebar(
                    categories = uiCategories,
                    selectedCategoryId = selectedCategory,
                    onCategorySelected = { viewModel.loadSeriesStreams(it) },
                    title = "Kategoriler",
                    totalCount = totalSeriesCount,
                    allLabel = "Tüm Diziler",
                    tvFocusBehavior = isTvDevice,
                    footer = if (isTvDevice) {
                        {
                            ParentalLockRow(viewModel, hasAdultSeries)
                        }
                    } else null
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.Accent.copy(alpha = 0.16f),
                            modifier = Modifier
                                .focusRequester(backToShelvesFocusRequester)
                                .dpadFocusable(
                                    shape = VegaShapes.Pill,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    scale = 1.04f,
                                    onClick = {
                                        viewModel.setSeriesUseShelfMode(true)
                                        if (hasSearch) viewModel.setSeriesSearchQuery("")
                                        if (hasActiveCategory) viewModel.loadSeriesStreams("all")
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(15.dp))
                                Text("Raflara dön", color = VegaColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text(
                            text = selectedCategoryName,
                            color = VegaColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (useShelfMode) {
                        SeriesTonalPill(
                            label = "Kategoriler",
                            icon = Icons.AutoMirrored.Filled.MenuOpen,
                            muted = true,
                            onClick = { viewModel.setSeriesUseShelfMode(false) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    VegaMediaContentGrid(
                        isLoading = isLoading,
                        seriesList = seriesList,
                        favoriteSeriesIds = favoriteSeriesIds,
                        searchQuery = searchQuery,
                        onClearSearch = { viewModel.setSeriesSearchQuery("") },
                        onSearchAllLive = { viewModel.searchAcrossAllLive(searchQuery) },
                        onSearchAllVod = { viewModel.searchAcrossAllVod(searchQuery) },
                        onSearchAllSeries = { viewModel.searchAcrossAllSeries(searchQuery) },
                        currentSort = currentSort,
                        onSortSelected = { sort -> viewModel.setSeriesSort(sort) },
                        onSeriesClick = { series -> viewModel.loadSeriesDetail(series) },
                        isWideScreen = true,
                        isMobileCompact = false,
                        selectedCategory = selectedCategory,
                        positionStore = viewModel.catalogPositions,
                        lockChip = {
                            if (!isTvDevice) {
                                ParentalLockChip(viewModel, hasAdultSeries)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSeriesShelfHeader(
    title: String,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = VegaColors.TextPrimary,
            fontSize = VegaText.SectionTitle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = VegaSizes.MinTouch)
                .clip(VegaShapes.Small)
                .dpadFocusable(
                    shape = VegaShapes.Small,
                    focusedBorderColor = VegaColors.FocusBorder,
                    onClick = onSeeAllClick
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Tümü",
                    color = VegaColors.Accent,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SeriesTonalPill(
    label: String,
    icon: ImageVector,
    isActive: Boolean = false,
    muted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = VegaShapes.Pill,
        color = if (isActive) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier.dpadFocusable(
            shape = VegaShapes.Pill,
            focusedBorderColor = VegaColors.FocusBorder,
            scale = 1.04f,
            onClick = onClick
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (muted) VegaColors.TextSecondary else VegaColors.Accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = when {
                    isActive -> VegaColors.Accent
                    muted -> VegaColors.TextSecondary
                    else -> VegaColors.TextPrimary
                },
                fontSize = 12.sp,
                fontWeight = if (muted) FontWeight.Medium else FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SeriesSortDropdownMenu(
    expanded: Boolean,
    currentSort: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    if (!expanded) return
    val isTv = LocalIsTvDevice.current
    val options = remember {
        listOf(
            Triple(SortOption.ADDED_DESC, "Eklenme tarihi (yeni)", Icons.Default.Schedule),
            Triple(SortOption.RATING_DESC, "Puan sıralaması (en yüksek)", Icons.Default.Star),
            Triple(SortOption.NAME_ASC, "A - Z (isme göre)", Icons.Default.SortByAlpha),
            Triple(SortOption.NAME_DESC, "Z - A (isme göre ters)", Icons.Default.SortByAlpha),
            Triple(SortOption.DEFAULT, "Varsayılan sıra", Icons.Default.Reorder)
        )
    }

    if (isTv) {
        val initialFocusRequester = remember { FocusRequester() }
        val selectedIndex = remember(currentSort) {
            options.indexOfFirst { it.first == currentSort }.coerceAtLeast(0)
        }
        Dialog(onDismissRequest = onDismiss) {
            LaunchedEffect(Unit) {
                repeat(2) {
                    withFrameNanos { }
                    delay(60)
                    if (initialFocusRequester.requestFocusSafely()) return@LaunchedEffect
                }
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .vegaPanel(VegaShapes.Large)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sıralama",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )
                options.forEachIndexed { idx, (opt, label, icon) ->
                    val isSelected = currentSort == opt
                    var isFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Medium,
                        color = when {
                            isFocused -> VegaColors.SurfaceVariant
                            isSelected -> VegaColors.Accent.copy(alpha = 0.16f)
                            else -> VegaColors.SurfaceElevated.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (idx == selectedIndex) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.FocusBorder,
                                scale = 1.02f,
                                onClick = { onSelect(opt) },
                                onFocusStateChange = { isFocused = it }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = when {
                                    opt == SortOption.RATING_DESC -> VegaColors.Gold
                                    isSelected -> VegaColors.Accent
                                    else -> VegaColors.TextSecondary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = label,
                                color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.background(VegaColors.Surface)
        ) {
            options.forEach { (opt, label, icon) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (currentSort == opt) VegaColors.Accent else VegaColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = when {
                                opt == SortOption.RATING_DESC -> VegaColors.Gold
                                opt == SortOption.ADDED_DESC -> VegaColors.Accent
                                else -> VegaColors.TextSecondary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = { onSelect(opt) }
                )
            }
        }
    }
}

@Composable
private fun SeriesCategoryPickerDialog(
    title: String,
    allLabel: String,
    totalCount: Int,
    categories: List<CategoryUiItem>,
    selectedCategoryId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocusRequester = remember { FocusRequester() }
    val allItems = remember(categories, allLabel, totalCount) {
        buildList {
            add(CategoryUiItem(id = "all", name = allLabel, count = totalCount, icon = Icons.Default.GridView, iconTint = VegaColors.Accent))
            addAll(categories.distinctBy { it.id })
        }
    }
    val selectedIndex = remember(allItems, selectedCategoryId) {
        val targetId = selectedCategoryId.ifEmpty { "all" }
        allItems.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
    }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            repeat(2) {
                withFrameNanos { }
                delay(60)
                if (initialFocusRequester.requestFocusSafely()) return@LaunchedEffect
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .vegaPanel(VegaShapes.Large)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${categories.size} kategori",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(allItems, key = { idx, item -> "ser_pick_${item.id}_$idx" }) { idx, cat ->
                    val isSelected = (cat.id == "all" && (selectedCategoryId == "all" || selectedCategoryId.isEmpty())) ||
                        cat.id == selectedCategoryId
                    var isFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Medium,
                        color = when {
                            isFocused -> VegaColors.SurfaceVariant
                            isSelected -> VegaColors.Accent.copy(alpha = 0.16f)
                            else -> Color.Transparent
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (idx == selectedIndex) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.FocusBorder,
                                scale = 1.02f,
                                onClick = { onSelect(cat.id) },
                                onFocusStateChange = { isFocused = it }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cat.name,
                                color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (cat.count > 0) {
                                    Text(
                                        text = "${cat.count}",
                                        color = VegaColors.TextMuted,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = VegaColors.Accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.SurfaceVariant,
                    modifier = Modifier.dpadFocusable(
                        shape = VegaShapes.Medium,
                        focusedBorderColor = VegaColors.FocusBorder,
                        onClick = onDismiss
                    )
                ) {
                    Text(
                        text = "Kapat",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VegaMediaContentGrid(
    isLoading: Boolean,
    seriesList: List<SeriesStream>,
    favoriteSeriesIds: Set<String>,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onSearchAllLive: () -> Unit,
    onSearchAllVod: () -> Unit,
    onSearchAllSeries: () -> Unit,
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onSeriesClick: (SeriesStream) -> Unit,
    isWideScreen: Boolean,
    isMobileCompact: Boolean,
    selectedCategory: String,
    positionStore: com.tivimax.player.ui.vm.CatalogPositionStore,
    lockChip: @Composable () -> Unit = {}
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }

    // G-034: Dizi ızgarası kaydırma konumu ve sonsuz kaydırma sınırı bellekte saklanır; detaydan dönünce aynı yerden başlar
    val positionKey = if (searchQuery.isBlank()) "series:$selectedCategory" else "series:$selectedCategory:q:$searchQuery"
    val savedPosition = remember(positionKey) { positionStore.get(positionKey) }
    var visibleLimit by remember(seriesList) { mutableIntStateOf(maxOf(40, savedPosition?.visibleLimit ?: 0)) }
    val pagedSeries = remember(seriesList, visibleLimit) { seriesList.take(visibleLimit) }
    val gridState = androidx.compose.runtime.saveable.rememberSaveable(
        positionKey,
        saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver
    ) {
        androidx.compose.foundation.lazy.grid.LazyGridState(savedPosition?.firstIndex ?: 0, savedPosition?.offset ?: 0)
    }
    val latestVisibleLimit by rememberUpdatedState(visibleLimit)
    DisposableEffect(positionKey) {
        onDispose {
            positionStore.save(positionKey, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, latestVisibleLimit)
        }
    }
    // G-034: TV'de dönüşte son açılan afişe odak ver
    val isTvForRestore = LocalIsTvDevice.current
    val restoreFocusKey = remember(positionKey) { if (isTvForRestore) positionStore.lastOpenedItemKey else null }
    val restoreFocusRequester = remember { FocusRequester() }
    LaunchedEffect(restoreFocusKey, pagedSeries.isEmpty()) {
        val targetKey = restoreFocusKey ?: return@LaunchedEffect
        if (pagedSeries.isEmpty()) return@LaunchedEffect
        val index = pagedSeries.indexOfFirst { "series_${it.seriesId}_${it.num}" == targetKey }
        if (index >= 0) {
            withFrameNanos { }
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                gridState.scrollToItem(index)
            }
            withFrameNanos { }
            if (!restoreFocusRequester.requestFocusSafely()) {
                withFrameNanos { }
                restoreFocusRequester.requestFocusSafely()
            }
        }
        positionStore.lastOpenedItemKey = null
    }

    val configuration = LocalConfiguration.current
    val gridCells = when {
        isMobileCompact -> GridCells.Fixed(3)
        !isTvForRestore && isWideScreen -> GridCells.Adaptive(minSize = 136.dp)
        else -> GridCells.Fixed((configuration.screenWidthDp / 155).coerceIn(4, 7))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isMobileCompact || searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isMobileCompact) 12.dp else 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchQuery.isNotBlank()) {
                    Surface(
                        shape = VegaShapes.Pill,
                        color = VegaColors.Accent.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                                text = "\"$searchQuery\" (${seriesList.size} dizi)",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .dpadFocusable(
                                        shape = CircleShape,
                                        focusedBorderColor = VegaColors.FocusBorder,
                                        onClick = onClearSearch
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Filtreyi temizle",
                                    tint = VegaColors.TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "${seriesList.size} dizi",
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!isMobileCompact) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lockChip()

                        Box {
                            val sortLabel = when (currentSort) {
                                SortOption.ADDED_DESC -> "Eklenme"
                                SortOption.RATING_DESC -> "Puan"
                                SortOption.NAME_ASC -> "A - Z"
                                SortOption.NAME_DESC -> "Z - A"
                                SortOption.DEFAULT -> "Sırala"
                            }

                            ActionChipButton(
                                text = sortLabel,
                                icon = Icons.AutoMirrored.Filled.Sort,
                                onClick = { isSortMenuOpen = true }
                            )

                            SeriesSortDropdownMenu(
                                expanded = isSortMenuOpen,
                                currentSort = currentSort,
                                onSelect = {
                                    onSortSelected(it)
                                    isSortMenuOpen = false
                                },
                                onDismiss = { isSortMenuOpen = false }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            }
        } else if (seriesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "\"$searchQuery\" için dizi bulunamadı." else "Bu kategoride içerik bulunamadı.",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary
                    )
                    if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        GlobalSearchOptionsCard(
                            query = searchQuery,
                            currentScope = "series",
                            isAllCategory = selectedCategory == "all",
                            onSearchAllLive = onSearchAllLive,
                            onSearchAllVod = onSearchAllVod,
                            onSearchAllSeries = onSearchAllSeries
                        )
                    }
                }
            }
        } else {
            val restoreTargetIndex = remember(pagedSeries, restoreFocusKey) {
                if (restoreFocusKey == null) -1
                else pagedSeries.indexOfFirst { "series_${it.seriesId}_${it.num}" == restoreFocusKey }
            }
            val gridSpacing = if (isMobileCompact) 10.dp else 12.dp
            val gridHorizontalPadding = if (isMobileCompact) 10.dp else 8.dp
            LazyVerticalGrid(
                columns = gridCells,
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(if (isMobileCompact) 10.dp else 14.dp),
                contentPadding = PaddingValues(top = 6.dp, start = gridHorizontalPadding, end = gridHorizontalPadding, bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pagedSeries, key = { idx, it -> "series_grid_${it.seriesId}_${it.num}_$idx" }) { idx, series ->
                    val itemKey = "series_${series.seriesId}_${series.num}"
                    VegaMediaCard(
                        series = series,
                        isFavorite = favoriteSeriesIds.contains(series.seriesId.toString()),
                        onClick = {
                            positionStore.lastOpenedItemKey = itemKey
                            onSeriesClick(series)
                        },
                        modifier = if (idx == restoreTargetIndex) {
                            Modifier.focusRequester(restoreFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }

                if (visibleLimit < seriesList.size) {
                    item(key = "series_more_$visibleLimit", span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(visibleLimit) {
                            visibleLimit += 30
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                if (searchQuery.isNotBlank()) {
                    item(key = "series_global_search", span = { GridItemSpan(maxLineSpan) }) {
                        GlobalSearchOptionsCard(
                            query = searchQuery,
                            currentScope = "series",
                            isAllCategory = selectedCategory == "all",
                            onSearchAllLive = onSearchAllLive,
                            onSearchAllVod = onSearchAllVod,
                            onSearchAllSeries = onSearchAllSeries
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VegaMediaCard(
    series: SeriesStream,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val releaseYear = series.releaseDate?.take(4)?.ifBlank { "" } ?: ""
    val genreText = series.genre?.split(",")?.firstOrNull()?.trim()?.ifBlank { "" } ?: ""
    val subText = listOf(releaseYear, genreText).filter { it.isNotBlank() }.joinToString(" · ")

    MediaPosterCardBox(
        imageUrl = series.cover,
        title = series.name,
        subtitle = subText,
        rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
        isFavorite = isFavorite,
        onClick = onClick,
        modifier = modifier
    )
}
