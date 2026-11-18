@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.catalog

import androidx.activity.compose.BackHandler
import com.tivimax.player.util.LocalIsTvDevice
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.*
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.main.StreamVaultSectionHeader
import kotlinx.coroutines.delay

@Composable
fun VegaMediaCatalogScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleSeriesCategories.collectAsState()
    val seriesList by viewModel.seriesStreams.collectAsState()
    val selectedCategory by viewModel.selectedSeriesCategory.collectAsState()
    val isLoading by viewModel.seriesLoading.collectAsState()
    val currentSort by viewModel.seriesSortOption.collectAsState()
    val portraitNavStyle by viewModel.portraitCategoryNavStyle.collectAsState()

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    var showCategorySheet by remember { mutableStateOf(false) }

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
    val selectedCategoryCount = if (selectedCategory == "all" || selectedCategory.isEmpty()) {
        totalSeriesCount
    } else {
        selectedCategoryItem?.itemCount ?: totalSeriesCount
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

    val isTvDevice = LocalIsTvDevice.current
    val searchQuery by viewModel.seriesSearchQuery.collectAsState()
    val hasActiveCategory = selectedCategory != "all" && selectedCategory.isNotEmpty()
    val hasSearch = searchQuery.isNotBlank()

    var useShelfMode by remember { mutableStateOf(true) }
    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
    val hasAdultSeries by viewModel.hasAdultSeriesCategory.collectAsState()
    var shelfSortMenuOpen by remember { mutableStateOf(false) }

    // TV'de "Tümünü gör" ile açılan kategoriden Geri tuşuyla doğrudan raflara dön
    BackHandler(
        enabled = (isTvDevice && useShelfMode && hasActiveCategory) ||
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

    if (isWideScreen && useShelfMode && !hasActiveCategory && !hasSearch && !isLoading && seriesList.isNotEmpty()) {
        // STREAMVAULT MODERN DİZİ RAFLARI GÖRÜNÜMÜ (Movies.png ile birebir görsel dil)
        val seriesByCat = remember(seriesList) { seriesList.groupBy { it.categoryId } }
        val topSeries = remember(seriesList) {
            seriesList
                .sortedByDescending { it.rating?.replace(',', '.')?.toFloatOrNull() ?: (it.rating5Based?.toFloat()?.times(2f) ?: 0f) }
                .take(18)
        }
        val shelfCategories = remember(uiCategories, seriesByCat) {
            uiCategories.mapNotNull { cat ->
                val list = seriesByCat[cat.id]?.take(16)
                if (!list.isNullOrEmpty()) cat to list else null
            }.take(20)
        }
        val shelfPosterWidth = if (isTvDevice) 160.dp else 148.dp
        val topPosterWidth = if (isTvDevice) 160.dp else 152.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTvDevice) 20.dp else 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTvDevice) 24.dp else 20.dp)
        ) {
            // Üst Kontrol Şeridi (Arama, Sıralama, Görünüm Değiştirme, Yetişkin Kilidi)
            item(key = "series_shelf_controls") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Diziler",
                            color = VegaColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.SurfaceVariant,
                            border = BorderStroke(1.dp, VegaColors.Border)
                        ) {
                            Text(
                                text = "$totalSeriesCount dizi",
                                color = VegaColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ParentalLockChip(viewModel, hasAdultSeries)

                        // Arama Hapı
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.SurfaceElevated,
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier.dpadFocusable(
                                shape = VegaShapes.Pill,
                                focusedBorderColor = VegaColors.FocusBorder,
                                scale = 1.04f,
                                onClick = { viewModel.navigateTo(Screen.Search) }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(15.dp))
                                Text("Ara", color = VegaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Sıralama Seçici
                        Box {
                            val sortLabel = when (currentSort) {
                                SortOption.ADDED_DESC -> "Eklenme"
                                SortOption.RATING_DESC -> "Puan"
                                SortOption.NAME_ASC -> "A - Z"
                                SortOption.NAME_DESC -> "Z - A"
                                SortOption.DEFAULT -> "Sırala"
                            }
                            Surface(
                                shape = VegaShapes.Pill,
                                color = if (currentSort != SortOption.DEFAULT) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceElevated,
                                border = BorderStroke(1.dp, if (currentSort != SortOption.DEFAULT) VegaColors.Accent.copy(alpha = 0.4f) else VegaColors.Border),
                                modifier = Modifier.dpadFocusable(
                                    shape = VegaShapes.Pill,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    scale = 1.04f,
                                    onClick = { shelfSortMenuOpen = true }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(15.dp))
                                    Text(sortLabel, color = VegaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
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

                        // Klasik Liste Görünümü
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.SurfaceElevated,
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier.dpadFocusable(
                                shape = VegaShapes.Pill,
                                focusedBorderColor = VegaColors.FocusBorder,
                                scale = 1.04f,
                                onClick = { useShelfMode = false }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(15.dp))
                                Text("Klasik liste", color = VegaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Üst Vitrin Rafı (Büyük 160.dp posterler)
            if (topSeries.isNotEmpty()) {
                item(key = "series_shelf_top") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth().focusRestorer()
                    ) {
                        items(topSeries, key = { "top_ser_${it.seriesId}" }) { series ->
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

            // Kategori Rafları (Öğe sayısı + "Tümünü gör" mavi hapı)
            items(shelfCategories, key = { "ser_shelf_${it.first.id}" }) { (cat, catSeries) ->
                val catTotalCount = cat.count.takeIf { it > 0 } ?: seriesByCat[cat.id]?.size
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
                        items(catSeries, key = { "shelf_${cat.id}_${it.seriesId}" }) { series ->
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
        }
    } else if (isWideScreen || portraitNavStyle == UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR) {
        val backToShelvesFocusRequester = remember { FocusRequester() }
        LaunchedEffect(selectedCategory, useShelfMode) {
            if (isTvDevice && isWideScreen && hasActiveCategory && useShelfMode && viewModel.catalogPositions.lastOpenedItemKey == null) {
                repeat(3) {
                    withFrameNanos { }
                    delay(70)
                    if (runCatching { backToShelvesFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isWideScreen || !useShelfMode) {
                CategorySidebar(
                    categories = uiCategories,
                    selectedCategoryId = selectedCategory,
                    onCategorySelected = { viewModel.loadSeriesStreams(it) },
                    title = "Kategoriler",
                    totalCount = totalSeriesCount,
                    allLabel = "Tüm Diziler",
                    tvFocusBehavior = isTvDevice, // G-045
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
                if (isWideScreen) {
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
                                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .focusRequester(backToShelvesFocusRequester)
                                    .dpadFocusable(
                                        shape = VegaShapes.Pill,
                                        focusedBorderColor = VegaColors.FocusBorder,
                                        scale = 1.04f,
                                        onClick = {
                                            useShelfMode = true
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
                            Surface(
                                shape = VegaShapes.Pill,
                                color = VegaColors.SurfaceElevated,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                modifier = Modifier.dpadFocusable(
                                    shape = VegaShapes.Pill,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    scale = 1.04f,
                                    onClick = { useShelfMode = false }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(14.dp))
                                    Text("Kategoriler", color = VegaColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    VegaMediaContentGrid(
                        isLoading = isLoading,
                        seriesList = seriesList,
                        favoriteSeriesIds = favoriteSeriesIds,
                        currentSort = currentSort,
                        onSortSelected = { sort -> viewModel.setSeriesSort(sort) },
                        onSeriesClick = { series -> viewModel.loadSeriesDetail(series) },
                        portraitNavStyle = portraitNavStyle,
                        onPortraitNavStyleChange = { viewModel.setPortraitCategoryNavStyle(it) },
                        isWideScreen = isWideScreen,
                        isSidebarActive = !isWideScreen || !useShelfMode,
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
    } else {
        // DİKEY MOD: TAM EKRAN POSTER IZGARASI + ÇİPLER VEYA AÇILIR MENÜ NAVİGASYONU
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            if (portraitNavStyle == UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN) {
                MediaCategoryDropdownBar(
                    selectedCategoryName = selectedCategoryName,
                    selectedCategoryCount = selectedCategoryCount,
                    onOpenCategorySheet = { showCategorySheet = true }
                )
            } else {
                MediaCategoryNavigationBar(
                    categories = uiCategories,
                    selectedCategoryId = selectedCategory,
                    onCategorySelected = { viewModel.loadSeriesStreams(it) },
                    totalCount = totalSeriesCount,
                    allLabel = "Tüm Diziler",
                    defaultIcon = Icons.Default.VideoLibrary,
                    onOpenAllCategories = { showCategorySheet = true }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                VegaMediaContentGrid(
                    isLoading = isLoading,
                    seriesList = seriesList,
                    favoriteSeriesIds = favoriteSeriesIds,
                    currentSort = currentSort,
                    onSortSelected = { sort -> viewModel.setSeriesSort(sort) },
                    onSeriesClick = { series -> viewModel.loadSeriesDetail(series) },
                    portraitNavStyle = portraitNavStyle,
                    onPortraitNavStyleChange = { viewModel.setPortraitCategoryNavStyle(it) },
                    isWideScreen = isWideScreen,
                    isSidebarActive = false,
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

@Composable
private fun SeriesSortDropdownMenu(
    expanded: Boolean,
    currentSort: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(VegaColors.Surface)
    ) {
        DropdownMenuItem(
            text = { Text("Eklenme tarihi (yeni)", color = if (currentSort == SortOption.ADDED_DESC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(16.dp)) },
            onClick = { onSelect(SortOption.ADDED_DESC) }
        )
        DropdownMenuItem(
            text = { Text("Puan sıralaması (en yüksek)", color = if (currentSort == SortOption.RATING_DESC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(16.dp)) },
            onClick = { onSelect(SortOption.RATING_DESC) }
        )
        DropdownMenuItem(
            text = { Text("A - Z (isme göre)", color = if (currentSort == SortOption.NAME_ASC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp)) },
            onClick = { onSelect(SortOption.NAME_ASC) }
        )
        DropdownMenuItem(
            text = { Text("Z - A (isme göre ters)", color = if (currentSort == SortOption.NAME_DESC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp)) },
            onClick = { onSelect(SortOption.NAME_DESC) }
        )
        DropdownMenuItem(
            text = { Text("Varsayılan sıra", color = if (currentSort == SortOption.DEFAULT) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp)) },
            onClick = { onSelect(SortOption.DEFAULT) }
        )
    }
}

@Composable
private fun VegaMediaContentGrid(
    isLoading: Boolean,
    seriesList: List<SeriesStream>,
    favoriteSeriesIds: Set<String>,
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onSeriesClick: (SeriesStream) -> Unit,
    portraitNavStyle: String,
    onPortraitNavStyleChange: (String) -> Unit,
    isWideScreen: Boolean,
    isSidebarActive: Boolean,
    selectedCategory: String,
    positionStore: com.tivimax.player.ui.vm.CatalogPositionStore,
    lockChip: @Composable () -> Unit = {}
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }

    // G-034: Dizi ızgarası kaydırma konumu ve sonsuz kaydırma sınırı bellekte saklanır; detaydan dönünce aynı yerden başlar
    val positionKey = "series:$selectedCategory"
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
    // G-034: TV'de dönüşte son açılan afişe odak ver (G-033/G-042/G-045 odak koduna dokunmadan)
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
            try {
                restoreFocusRequester.requestFocus()
            } catch (_: Exception) {
                withFrameNanos { }
                try { restoreFocusRequester.requestFocus() } catch (_: Exception) { /* sessiz: öğe henüz yerleşmedi, odak vazgeçildi */ }
            }
        }
        positionStore.lastOpenedItemKey = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${seriesList.size} dizi",
                color = VegaColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lockChip()
                if (!isWideScreen) {
                    CategoryNavStyleToggleButton(
                        currentStyle = portraitNavStyle,
                        onStyleChange = onPortraitNavStyleChange
                    )
                }

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

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            }
        } else if (seriesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Bu kategoride içerik bulunamadı.",
                    color = VegaColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            val configuration = LocalConfiguration.current
            val gridColumns = if (isWideScreen) {
                (configuration.screenWidthDp / 155).coerceIn(4, 7)
            } else if (isSidebarActive) {
                2
            } else {
                3
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pagedSeries, key = { "series_${it.seriesId}_${it.num}" }) { series ->
                    val itemKey = "series_${series.seriesId}_${series.num}"
                    VegaMediaCard(
                        series = series,
                        isFavorite = favoriteSeriesIds.contains(series.seriesId.toString()),
                        onClick = {
                            positionStore.lastOpenedItemKey = itemKey
                            onSeriesClick(series)
                        },
                        modifier = if (restoreFocusKey != null && itemKey == restoreFocusKey) {
                            Modifier.focusRequester(restoreFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }

                if (visibleLimit < seriesList.size) {
                    item(span = { GridItemSpan(gridColumns) }) {
                        LaunchedEffect(Unit) {
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
    val subText = listOf(releaseYear, genreText).filter { it.isNotBlank() }.joinToString(" • ")

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
