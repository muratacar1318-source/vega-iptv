@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.vod

import androidx.activity.compose.BackHandler
import com.tivimax.player.util.LocalIsTvDevice
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
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.CategoryUiItem
import com.tivimax.player.ui.components.GlobalSearchOptionsCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaPanel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.MediaPosterCardBox
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.main.StreamVaultSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class VodShelfData(
    val topRated: List<VodStream> = emptyList(),
    val categories: List<Pair<CategoryUiItem, List<VodStream>>> = emptyList(),
    val countsByCat: Map<String, Int> = emptyMap(),
    val isReady: Boolean = false
)

@Composable
fun VodScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleVodCategories.collectAsState()
    val movies by viewModel.vodStreams.collectAsState()
    val selectedCategory by viewModel.selectedVodCategory.collectAsState()
    val isLoading by viewModel.vodLoading.collectAsState()
    val currentSort by viewModel.vodSortOption.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val portraitNavStyle by viewModel.portraitCategoryNavStyle.collectAsState()

    val searchQuery by viewModel.vodSearchQuery.collectAsState()
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }

    val filteredMovies = movies

    // G-034: Detaydan dönünce sonsuz kaydırma sınırı da geri gelir (yoksa 200. filme dönülemez)
    val savedPosition = remember(selectedCategory, searchQuery) {
        viewModel.catalogPositions.get(vodPositionKey(selectedCategory, searchQuery))
    }
    var visibleLimit by remember(filteredMovies) { mutableIntStateOf(maxOf(40, savedPosition?.visibleLimit ?: 0)) }
    val pagedMovies = remember(filteredMovies, visibleLimit) {
        filteredMovies.take(visibleLimit)
    }

    var selectedHeroMovie by remember { mutableStateOf<VodStream?>(null) }
    val featuredMovie = remember(movies) {
        movies.firstOrNull { it.streamIcon != null && it.streamIcon.isNotBlank() }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val totalVodCount = remember(categories) {
        categories.firstOrNull { it.categoryId == "all" }?.itemCount
            ?: categories.filter { it.categoryId != "all" }.sumOf { it.itemCount }
    }
    val selectedCategoryItem = remember(categories, selectedCategory) {
        if (selectedCategory == "all" || selectedCategory.isEmpty()) null
        else categories.firstOrNull { it.categoryId == selectedCategory }
    }
    val selectedCategoryName = if (selectedCategory == "all" || selectedCategory.isEmpty()) {
        "Tüm Filmler"
    } else {
        selectedCategoryItem?.categoryName ?: "Tüm Filmler"
    }
    val selectedCategoryCount = if (selectedCategory == "all" || selectedCategory.isEmpty()) {
        totalVodCount
    } else {
        selectedCategoryItem?.itemCount ?: totalVodCount
    }

    val uiCategories = remember(categories) {
        categories
            .filter { it.categoryId != "all" }
            .distinctBy { it.categoryId }
            .map {
                com.tivimax.player.ui.components.CategoryUiItem(
                    id = it.categoryId,
                    name = it.categoryName,
                    count = it.itemCount,
                    icon = Icons.Default.Movie,
                    iconTint = VegaColors.Accent
                )
            }
    }

    val isTvDevice = LocalIsTvDevice.current
    val hasActiveCategory = selectedCategory != "all" && selectedCategory.isNotEmpty()
    val hasSearch = searchQuery.isNotBlank()

    val useShelfMode by viewModel.vodUseShelfMode.collectAsState()
    val favoriteVodIds by viewModel.favoriteVodIds.collectAsState()
    val hasAdultVod by viewModel.hasAdultVodCategory.collectAsState()
    var shelfSortMenuOpen by remember { mutableStateOf(false) }
    var showShelfCategoryPicker by remember { mutableStateOf(false) }

    // TV'de arama veya "Tümünü gör" ile açılan kategoriden Geri tuşuyla doğrudan raflara dön
    BackHandler(
        enabled = (isTvDevice && (hasSearch || (useShelfMode && hasActiveCategory))) ||
            (!isTvDevice && (showCategorySheet || hasSearch || hasActiveCategory))
    ) {
        when {
            showCategorySheet -> showCategorySheet = false
            hasSearch -> viewModel.setVodSearchQuery("")
            hasActiveCategory -> viewModel.loadVodStreams("all")
        }
    }

    if (showCategorySheet) {
        com.tivimax.player.ui.components.MediaCategoryBottomSheet(
            title = "Film Türleri",
            categories = uiCategories,
            selectedCategoryId = selectedCategory,
            totalCount = totalVodCount,
            onCategorySelected = { viewModel.loadVodStreams(it) },
            onDismissRequest = { showCategorySheet = false }
        )
    }

    if (showShelfCategoryPicker) {
        VodCategoryPickerDialog(
            title = "Film kategorileri",
            allLabel = "Tüm Filmler",
            totalCount = totalVodCount,
            categories = uiCategories,
            selectedCategoryId = selectedCategory,
            onSelect = { catId ->
                showShelfCategoryPicker = false
                viewModel.loadVodStreams(catId)
            },
            onDismiss = { showShelfCategoryPicker = false }
        )
    }

    if (showCategoryDialog) {
        CategoryManagementDialog(
            viewModel = viewModel,
            initialTab = 1,
            onDismiss = { showCategoryDialog = false }
        )
    }

    if (isWideScreen && useShelfMode && !hasActiveCategory && !hasSearch && !isLoading && movies.isNotEmpty()) {
        // STREAMVAULT MODERN RAF GÖRÜNÜMÜ (Gruplama ve puan sıralaması Default dispatcher'da)
        val shelfData by produceState(
            initialValue = VodShelfData(),
            key1 = movies,
            key2 = uiCategories
        ) {
            value = withContext(Dispatchers.Default) {
                val grouped = movies.groupBy { it.categoryId }
                val counts = grouped.mapValues { it.value.size }
                val top = movies
                    .distinctBy { it.streamId }
                    .sortedByDescending {
                        it.rating?.replace(',', '.')?.toFloatOrNull()
                            ?: (it.rating5Based?.toFloat()?.times(2f) ?: 0f)
                    }
                    .take(18)
                val shelves = uiCategories.mapNotNull { cat ->
                    val list = grouped[cat.id]?.distinctBy { it.streamId }?.take(16)
                    if (!list.isNullOrEmpty()) cat to list else null
                }
                VodShelfData(
                    topRated = top,
                    categories = shelves,
                    countsByCat = counts,
                    isReady = true
                )
            }
        }
        val topRatedMovies = shelfData.topRated
        var visibleShelfCats by remember(shelfData.categories) { mutableIntStateOf(10) }
        val visibleShelfCategories = remember(shelfData.categories, visibleShelfCats) {
            shelfData.categories.take(visibleShelfCats)
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
            // Üst Kontrol Şeridi (Arama, Sıralama, Kategoriler, Klasik Liste, Düzenle, Yetişkin Kilidi)
            item(key = "vod_shelf_controls") {
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
                            text = "Filmler",
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
                                text = "$totalVodCount film",
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
                        com.tivimax.player.ui.components.ParentalLockChip(viewModel, hasAdultVod)

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
                            VodSortDropdownMenu(
                                expanded = shelfSortMenuOpen,
                                currentSort = currentSort,
                                onSelect = {
                                    viewModel.setVodSort(it)
                                    shelfSortMenuOpen = false
                                },
                                onDismiss = { shelfSortMenuOpen = false }
                            )
                        }

                        // Tüm Kategorilere Doğrudan Erişim (Kategori Seçici)
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.SurfaceElevated,
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier.dpadFocusable(
                                shape = VegaShapes.Pill,
                                focusedBorderColor = VegaColors.FocusBorder,
                                scale = 1.04f,
                                onClick = { showShelfCategoryPicker = true }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(15.dp))
                                Text("Kategoriler", color = VegaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
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
                                onClick = { viewModel.setVodUseShelfMode(false) }
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

                        // Kategori Düzenleme (Gizle / Sabitle)
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.SurfaceElevated,
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier.dpadFocusable(
                                shape = VegaShapes.Pill,
                                focusedBorderColor = VegaColors.FocusBorder,
                                scale = 1.04f,
                                onClick = { showCategoryDialog = true }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Kategorileri düzenle", tint = VegaColors.TextSecondary, modifier = Modifier.size(15.dp))
                                Text("Düzenle", color = VegaColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (!shelfData.isReady) {
                item(key = "vod_shelf_loading") {
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
                // Üst Vitrin Rafı (Movies.png üst şerit — büyük 160.dp posterler)
                if (topRatedMovies.isNotEmpty()) {
                    item(key = "vod_shelf_top") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().focusRestorer()
                        ) {
                            itemsIndexed(topRatedMovies, key = { idx, it -> "top_vod_${it.streamId}_$idx" }) { _, movie ->
                                val prog = watchProgressMap[movie.streamId.toString()]
                                val progFraction = if (prog != null && !prog.isWatched && prog.durationMs > 0L) {
                                    (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                Box(modifier = Modifier.width(topPosterWidth)) {
                                    MediaPosterCard(
                                        imageUrl = movie.streamIcon,
                                        title = movie.name,
                                        subtitle = "",
                                        rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                        watchProgress = progFraction,
                                        isFavorite = favoriteVodIds.contains(movie.streamId.toString()),
                                        onClick = { viewModel.loadVodDetail(movie) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Kategori Rafları (StreamVault Movies.png — öğe sayısı + "Tümünü gör" mavi hapı)
                itemsIndexed(visibleShelfCategories, key = { idx, pair -> "vod_shelf_${pair.first.id}_$idx" }) { _, (cat, catMovies) ->
                    val catTotalCount = cat.count.takeIf { it > 0 } ?: shelfData.countsByCat[cat.id]
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StreamVaultSectionHeader(
                            title = cat.name,
                            count = catTotalCount,
                            onSeeAllClick = { viewModel.loadVodStreams(cat.id) }
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().focusRestorer()
                        ) {
                            itemsIndexed(catMovies, key = { idx, it -> "shelf_${cat.id}_${it.streamId}_$idx" }) { _, movie ->
                                val prog = watchProgressMap[movie.streamId.toString()]
                                val progFraction = if (prog != null && !prog.isWatched && prog.durationMs > 0L) {
                                    (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                Box(modifier = Modifier.width(shelfPosterWidth)) {
                                    MediaPosterCard(
                                        imageUrl = movie.streamIcon,
                                        title = movie.name,
                                        subtitle = "",
                                        rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                        watchProgress = progFraction,
                                        isFavorite = favoriteVodIds.contains(movie.streamId.toString()),
                                        onClick = { viewModel.loadVodDetail(movie) }
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
    } else if (isWideScreen || portraitNavStyle == com.tivimax.player.data.preferences.UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR) {
        val backToShelvesFocusRequester = remember { FocusRequester() }
        LaunchedEffect(selectedCategory, useShelfMode) {
            if (isTvDevice && isWideScreen && hasActiveCategory && useShelfMode && viewModel.catalogPositions.lastOpenedItemKey == null) {
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
            // Klasik modda (veya dikey yan çubukta) ortak sol kategori menüsü;
            // "Tümünü gör" ile açılan geniş ekran ızgarada temiz tam genişlikli StreamVault görünümü
            if (!isWideScreen || !useShelfMode) {
                com.tivimax.player.ui.components.CategorySidebar(
                    categories = uiCategories,
                    selectedCategoryId = selectedCategory,
                    onCategorySelected = { viewModel.loadVodStreams(it) },
                    title = "Türler",
                    totalCount = totalVodCount,
                    allLabel = "Tüm Filmler",
                    onFilterClick = { showCategoryDialog = true },
                    tvFocusBehavior = isTvDevice, // G-045
                    footer = if (isTvDevice) {
                        {
                            com.tivimax.player.ui.components.ParentalLockRow(viewModel, hasAdultVod)
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
                                            viewModel.setVodUseShelfMode(true)
                                            if (hasSearch) viewModel.setVodSearchQuery("")
                                            if (hasActiveCategory) viewModel.loadVodStreams("all")
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
                                    onClick = { viewModel.setVodUseShelfMode(false) }
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
                    VodMediaContentGrid(
                        viewModel = viewModel,
                        filteredMovies = filteredMovies,
                        pagedMovies = pagedMovies,
                        featuredMovie = featuredMovie,
                        selectedHeroMovie = selectedHeroMovie,
                        onSelectHeroMovie = { selectedHeroMovie = it },
                        watchProgressMap = watchProgressMap,
                        favoriteVodIds = favoriteVodIds,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        currentSort = currentSort,
                        isLoading = isLoading,
                        gridColumns = if (isWideScreen) (configuration.screenWidthDp / 155).coerceIn(4, 7) else 2,
                        isWideScreen = isWideScreen,
                        portraitNavStyle = portraitNavStyle,
                        visibleLimit = visibleLimit,
                        onLoadMore = { visibleLimit += 30 }
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            if (portraitNavStyle == com.tivimax.player.data.preferences.UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN) {
                com.tivimax.player.ui.components.MediaCategoryDropdownBar(
                    selectedCategoryName = selectedCategoryName,
                    selectedCategoryCount = selectedCategoryCount,
                    onOpenCategorySheet = { showCategorySheet = true }
                )
            } else {
                com.tivimax.player.ui.components.MediaCategoryNavigationBar(
                    categories = uiCategories,
                    selectedCategoryId = selectedCategory,
                    onCategorySelected = { viewModel.loadVodStreams(it) },
                    totalCount = totalVodCount,
                    allLabel = "Tüm Filmler",
                    defaultIcon = Icons.Default.Movie,
                    onOpenAllCategories = { showCategorySheet = true }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                VodMediaContentGrid(
                    viewModel = viewModel,
                    filteredMovies = filteredMovies,
                    pagedMovies = pagedMovies,
                    featuredMovie = featuredMovie,
                    selectedHeroMovie = selectedHeroMovie,
                    onSelectHeroMovie = { selectedHeroMovie = it },
                    watchProgressMap = watchProgressMap,
                    favoriteVodIds = favoriteVodIds,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    currentSort = currentSort,
                    isLoading = isLoading,
                    gridColumns = 3,
                    isWideScreen = isWideScreen,
                    portraitNavStyle = portraitNavStyle,
                    visibleLimit = visibleLimit,
                    onLoadMore = { visibleLimit += 30 }
                )
            }
        }
    }
}

@Composable
private fun VodSortDropdownMenu(
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            isFocused -> VegaColors.SurfaceAccent
                            isSelected -> VegaColors.Accent.copy(alpha = 0.16f)
                            else -> VegaColors.SurfaceElevated
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) VegaColors.Accent else VegaColors.Border
                        ),
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
private fun VodCategoryPickerDialog(
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(allItems, key = { idx, item -> "vod_pick_${item.id}_$idx" }) { idx, cat ->
                    val isSelected = (cat.id == "all" && (selectedCategoryId == "all" || selectedCategoryId.isEmpty())) ||
                        cat.id == selectedCategoryId
                    var isFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Medium,
                        color = when {
                            isFocused -> VegaColors.SurfaceAccent
                            isSelected -> VegaColors.Accent.copy(alpha = 0.16f)
                            else -> VegaColors.SurfaceElevated
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) VegaColors.Accent else VegaColors.Border
                        ),
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
                                        color = VegaColors.TextSecondary,
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
                    border = BorderStroke(1.dp, VegaColors.Border),
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
private fun VodMediaContentGrid(
    viewModel: MainViewModel,
    filteredMovies: List<VodStream>,
    pagedMovies: List<VodStream>,
    featuredMovie: VodStream?,
    selectedHeroMovie: VodStream?,
    onSelectHeroMovie: (VodStream) -> Unit,
    watchProgressMap: Map<String, WatchProgress>,
    favoriteVodIds: Set<String>,
    searchQuery: String,
    selectedCategory: String,
    currentSort: SortOption,
    isLoading: Boolean,
    gridColumns: Int,
    isWideScreen: Boolean,
    portraitNavStyle: String,
    visibleLimit: Int,
    onLoadMore: () -> Unit
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }

    // G-034: Izgara kaydırma konumu bellekte saklanır; detaydan dönünce aynı yerden başlar
    val positionStore = viewModel.catalogPositions
    val positionKey = vodPositionKey(selectedCategory, searchQuery)
    val savedPosition = remember(positionKey) { positionStore.get(positionKey) }
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
    LaunchedEffect(restoreFocusKey, pagedMovies.isEmpty()) {
        val targetKey = restoreFocusKey ?: return@LaunchedEffect
        if (pagedMovies.isEmpty()) return@LaunchedEffect
        val index = pagedMovies.indexOfFirst { "${it.streamId}_${it.num}" == targetKey }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    shape = VegaShapes.Small,
                    color = VegaColors.Accent.copy(alpha = 0.15f),
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
                            text = "\"$searchQuery\" (${filteredMovies.size} film)",
                            color = VegaColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .dpadFocusable(
                                    shape = CircleShape,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    onClick = { viewModel.setVodSearchQuery("") }
                                )
                                .padding(6.dp)
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
                Text(
                    text = "${filteredMovies.size} film",
                    color = VegaColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // G-052: +18 kilit çipi (TV'de kilit satırı kategori listesinin sonunda)
                if (!LocalIsTvDevice.current) {
                    val hasAdultVod by viewModel.hasAdultVodCategory.collectAsState()
                    com.tivimax.player.ui.components.ParentalLockChip(viewModel, hasAdultVod)
                }
                if (!isWideScreen) {
                    com.tivimax.player.ui.components.CategoryNavStyleToggleButton(
                        currentStyle = portraitNavStyle,
                        onStyleChange = { viewModel.setPortraitCategoryNavStyle(it) }
                    )
                }

                // Sıralama Seçici Butonu
                Box {
                    com.tivimax.player.ui.components.ActionChipButton(
                        text = when (currentSort) {
                            SortOption.ADDED_DESC -> "Eklenme"
                            SortOption.RATING_DESC -> "Puan"
                            SortOption.NAME_ASC -> "A - Z"
                            SortOption.NAME_DESC -> "Z - A"
                            SortOption.DEFAULT -> "Sırala"
                        },
                        icon = Icons.AutoMirrored.Filled.Sort,
                        onClick = { isSortMenuOpen = true }
                    )

                    VodSortDropdownMenu(
                        expanded = isSortMenuOpen,
                        currentSort = currentSort,
                        onSelect = {
                            viewModel.setVodSort(it)
                            isSortMenuOpen = false
                        },
                        onDismiss = { isSortMenuOpen = false }
                    )
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
                    CircularProgressIndicator(color = VegaColors.Accent)
                }
            } else if (filteredMovies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "\"$searchQuery\" için film bulunamadı." else "Bu kategoride film yok.",
                            color = VegaColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        if (searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            GlobalSearchOptionsCard(
                                query = searchQuery,
                                currentScope = "vod",
                                isAllCategory = selectedCategory == "all",
                                onSearchAllLive = { viewModel.searchAcrossAllLive(searchQuery) },
                                onSearchAllVod = { viewModel.searchAcrossAllVod(searchQuery) },
                                onSearchAllSeries = { viewModel.searchAcrossAllSeries(searchQuery) }
                            )
                        }
                    }
                }
            } else {
                val heroMovie = selectedHeroMovie ?: featuredMovie ?: pagedMovies.firstOrNull()

                Column(modifier = Modifier.fillMaxSize()) {
                    // StreamVault Öne Çıkan / Odaklanılan Film Şeridi (Yatay Modda)
                    if (isWideScreen && heroMovie != null && searchQuery.isBlank()) {
                        Card(
                            shape = VegaShapes.Large,
                            colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(172.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncShimmerImage(
                                    model = heroMovie.streamIcon,
                                    contentDescription = heroMovie.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.55f)
                                        .align(Alignment.CenterEnd)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    VegaColors.Background,
                                                    VegaColors.Background.copy(alpha = 0.90f),
                                                    VegaColors.Background.copy(alpha = 0.48f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.65f)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(VegaShapes.Small)
                                                    .background(VegaColors.Accent)
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text("Öne çıkan", color = VegaColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            val rating = heroMovie.rating ?: heroMovie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                                            if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(rating, color = VegaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = heroMovie.name,
                                            color = VegaColors.TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Yüksek kaliteli yayın ve kesintisiz sinema deneyimi.",
                                            color = VegaColors.TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Tek odak + tek tıklama hedefi (Button + dpadFocusable çakışması giderildi)
                                    Surface(
                                        shape = VegaShapes.Medium,
                                        color = VegaColors.Accent,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .dpadFocusable(
                                                focusedBorderColor = Color.White,
                                                shape = VegaShapes.Medium,
                                                scale = 1.04f,
                                                onClick = { viewModel.loadVodDetail(heroMovie) }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(18.dp))
                                            Text("Hemen izle", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val restoreTargetIndex = remember(pagedMovies, restoreFocusKey) {
                        if (restoreFocusKey == null) -1
                        else pagedMovies.indexOfFirst { "${it.streamId}_${it.num}" == restoreFocusKey }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = pagedMovies,
                            key = { idx, it -> "vod_grid_${it.streamId}_${it.num}_$idx" },
                            contentType = { _, _ -> "vod_movie" }
                        ) { idx, movie ->
                            val progress = watchProgressMap[movie.streamId.toString()]
                            val itemKey = "${movie.streamId}_${movie.num}"
                            val progFraction = if (progress != null && !progress.isWatched && progress.durationMs > 0 && progress.positionMs > 10_000L) {
                                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            MediaPosterCardBox(
                                imageUrl = movie.streamIcon,
                                title = movie.name,
                                subtitle = "",
                                rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                watchProgress = progFraction,
                                isFavorite = favoriteVodIds.contains(movie.streamId.toString()),
                                onFocusChange = { if (it) onSelectHeroMovie(movie) },
                                onClick = {
                                    positionStore.lastOpenedItemKey = itemKey
                                    viewModel.loadVodDetail(movie)
                                },
                                modifier = if (idx == restoreTargetIndex) {
                                    Modifier.focusRequester(restoreFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                        }

                        // Sonsuz Kaydırma / Sayfalama Tetikleyici
                        if (visibleLimit < filteredMovies.size) {
                            item(key = "vod_more_$visibleLimit", span = { GridItemSpan(gridColumns) }) {
                                LaunchedEffect(visibleLimit) {
                                    onLoadMore()
                                }
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

                        // Arama yapıldığında ızgaranın en altında alternatif arama seçenekleri
                        if (searchQuery.isNotBlank()) {
                            item(key = "vod_global_search", span = { GridItemSpan(gridColumns) }) {
                                GlobalSearchOptionsCard(
                                    query = searchQuery,
                                    currentScope = "vod",
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

@Composable
fun MoviePosterCard(
    movie: VodStream,
    watchProgress: WatchProgress? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
        (watchProgress.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    MediaPosterCardBox(
        imageUrl = movie.streamIcon,
        title = movie.name,
        subtitle = "",
        rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
        watchProgress = progressFraction,
        isFavorite = false,
        onFocusChange = { if (it) onFocused?.invoke() },
        onClick = onClick,
        modifier = modifier
    )
}

/** G-034: Film ızgarası konum anahtarı: "vod:<kategoriId>" (aramada arama metni de eklenir). */
private fun vodPositionKey(selectedCategory: String, searchQuery: String): String =
    if (searchQuery.isBlank()) "vod:$selectedCategory" else "vod:$selectedCategory:q:$searchQuery"
