@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.vod

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.ActionChipButton
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.CategorySidebar
import com.tivimax.player.ui.components.CategoryUiItem
import com.tivimax.player.ui.components.GlobalSearchOptionsCard
import com.tivimax.player.ui.components.MediaCategoryBottomSheet
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.MediaPosterCardBox
import com.tivimax.player.ui.components.ParentalLockChip
import com.tivimax.player.ui.components.ParentalLockRow
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.requestFocusSafely
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

    val searchQuery by viewModel.vodSearchQuery.collectAsState()
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var mobileSortMenuOpen by remember { mutableStateOf(false) }

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
        movies.firstOrNull { !it.streamIcon.isNullOrBlank() }
    }

    val configuration = LocalConfiguration.current
    val isTvDevice = LocalIsTvDevice.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val isMobilePortrait = !isTvDevice && !isWideScreen

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

    val uiCategories = remember(categories) {
        categories
            .filter { it.categoryId != "all" }
            .distinctBy { it.categoryId }
            .map {
                CategoryUiItem(
                    id = it.categoryId,
                    name = it.categoryName,
                    count = it.itemCount,
                    icon = Icons.Default.Movie,
                    iconTint = VegaColors.Accent
                )
            }
    }

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
        MediaCategoryBottomSheet(
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

    if (isMobilePortrait) {
        // MOBİL FİLMLER DENEYİMİ: Kompakt tek satır üst araç çubuğu + 3 sütunlu kenardan kenara ızgara veya temiz editoryal raflar
        val showMobileShelf = useShelfMode && !hasActiveCategory && !hasSearch

        Column(modifier = Modifier.fillMaxSize()) {
            // Tek satırlık başparmak dostu üst araç çubuğu
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
                                    onClick = { viewModel.loadVodStreams("all") }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Tüm filmler",
                                tint = VegaColors.TextPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Text(
                        text = if (hasActiveCategory) selectedCategoryName else "Filmler",
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
                    ParentalLockChip(viewModel, hasAdultVod)

                    // Kompakt "Kategoriler" hapı (44.dp başparmak dokunma yüksekliği)
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

                    // Sıralama ikonu (44.dp)
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
                        VodSortDropdownMenu(
                            expanded = mobileSortMenuOpen,
                            currentSort = currentSort,
                            onSelect = {
                                viewModel.setVodSort(it)
                                mobileSortMenuOpen = false
                            },
                            onDismiss = { mobileSortMenuOpen = false }
                        )
                    }

                    // Raf / Izgara görünüm değiştirme ikonu (44.dp)
                    Box(
                        modifier = Modifier
                            .size(VegaSizes.IconButton)
                            .clip(CircleShape)
                            .dpadFocusable(
                                shape = CircleShape,
                                focusedBorderColor = VegaColors.FocusBorder,
                                onClick = {
                                    if (showMobileShelf) {
                                        viewModel.setVodUseShelfMode(false)
                                    } else {
                                        viewModel.setVodUseShelfMode(true)
                                        if (hasActiveCategory) viewModel.loadVodStreams("all")
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

            if (showMobileShelf && !isLoading && movies.isNotEmpty()) {
                // MOBİL RAF GÖRÜNÜMÜ (Kutusuz editoryal kategori şeritleri)
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
                            item(key = "mob_vod_shelf_top") {
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
                                        itemsIndexed(shelfData.topRated, key = { idx, it -> "mob_top_vod_${it.streamId}_$idx" }) { _, movie ->
                                            val prog = watchProgressMap[movie.streamId.toString()]
                                            val progFraction = if (prog != null && !prog.isWatched && prog.durationMs > 0L) {
                                                (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                                            } else 0f
                                            Box(modifier = Modifier.width(124.dp)) {
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
                        }

                        itemsIndexed(visibleShelfCategories, key = { idx, pair -> "mob_vod_shelf_${pair.first.id}_$idx" }) { _, (cat, catMovies) ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MobileVodShelfHeader(
                                    title = cat.name,
                                    onSeeAllClick = { viewModel.loadVodStreams(cat.id) }
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(catMovies, key = { idx, it -> "mob_shelf_${cat.id}_${it.streamId}_$idx" }) { _, movie ->
                                        val prog = watchProgressMap[movie.streamId.toString()]
                                        val progFraction = if (prog != null && !prog.isWatched && prog.durationMs > 0L) {
                                            (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                                        } else 0f
                                        Box(modifier = Modifier.width(116.dp)) {
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
                            item(key = "mob_vod_shelf_more_$visibleShelfCats") {
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
                        isWideScreen = false,
                        isMobileCompact = true,
                        visibleLimit = visibleLimit,
                        onLoadMore = { visibleLimit += 30 }
                    )
                }
            }
        }
    } else if (useShelfMode && !hasActiveCategory && !hasSearch && !isLoading && movies.isNotEmpty()) {
        // TV & GENİŞ EKRAN SİNEMA RAF GÖRÜNÜMÜ (Gruplama ve puan sıralaması Default dispatcher'da)
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
        val topPosterWidth = if (isTvDevice) 164.dp else 152.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTvDevice) 24.dp else 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTvDevice) 26.dp else 22.dp)
        ) {
            // Dingin çerçevesiz üst kontrol şeridi
            item(key = "vod_shelf_controls") {
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
                            text = "Filmler",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.ScreenTitle,
                            fontWeight = FontWeight.Bold
                        )
                        if (totalVodCount > 0) {
                            Text(
                                text = "$totalVodCount film",
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
                        ParentalLockChip(viewModel, hasAdultVod)

                        VodTonalPill(
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
                            VodTonalPill(
                                label = sortLabel,
                                icon = Icons.AutoMirrored.Filled.Sort,
                                isActive = currentSort != SortOption.DEFAULT,
                                onClick = { shelfSortMenuOpen = true }
                            )
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

                        VodTonalPill(
                            label = "Kategoriler",
                            icon = Icons.Default.Folder,
                            onClick = { showShelfCategoryPicker = true }
                        )

                        VodTonalPill(
                            label = "Klasik liste",
                            icon = Icons.AutoMirrored.Filled.MenuOpen,
                            onClick = { viewModel.setVodUseShelfMode(false) }
                        )

                        VodTonalPill(
                            label = "Düzenle",
                            icon = Icons.Default.Tune,
                            muted = true,
                            onClick = { showCategoryDialog = true }
                        )
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
                    onCategorySelected = { viewModel.loadVodStreams(it) },
                    title = "Türler",
                    totalCount = totalVodCount,
                    allLabel = "Tüm Filmler",
                    onFilterClick = { showCategoryDialog = true },
                    tvFocusBehavior = isTvDevice,
                    footer = if (isTvDevice) {
                        {
                            ParentalLockRow(viewModel, hasAdultVod)
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
                        VodTonalPill(
                            label = "Kategoriler",
                            icon = Icons.AutoMirrored.Filled.MenuOpen,
                            muted = true,
                            onClick = { viewModel.setVodUseShelfMode(false) }
                        )
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
                        isWideScreen = true,
                        isMobileCompact = false,
                        visibleLimit = visibleLimit,
                        onLoadMore = { visibleLimit += 30 }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileVodShelfHeader(
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
private fun VodTonalPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(allItems, key = { idx, item -> "vod_pick_${item.id}_$idx" }) { idx, cat ->
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
    isWideScreen: Boolean,
    isMobileCompact: Boolean,
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
    // G-034: TV'de dönüşte son açılan afişe odak ver
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
                try { restoreFocusRequester.requestFocus() } catch (_: Exception) { /* sessiz */ }
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
        // Mobilde üst çubuk zaten sıralama ve kategoriyi içerir; sadece arama aktifse temizleme hapı göster
        if (!isMobileCompact || searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isMobileCompact) 12.dp else 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                                text = "\"$searchQuery\" (${filteredMovies.size} film)",
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
                                        onClick = { viewModel.setVodSearchQuery("") }
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
                        text = "${filteredMovies.size} film",
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
                        if (!isTvForRestore) {
                            val hasAdultVod by viewModel.hasAdultVodCategory.collectAsState()
                            ParentalLockChip(viewModel, hasAdultVod)
                        }

                        Box {
                            ActionChipButton(
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
                            fontSize = VegaText.Secondary
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
                    // Geniş ekranda çerçevesiz sakin öne çıkan şeridi
                    if (isWideScreen && heroMovie != null && searchQuery.isBlank()) {
                        Surface(
                            shape = VegaShapes.Large,
                            color = VegaColors.SurfaceElevated.copy(alpha = 0.72f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(156.dp)
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
                                                    VegaColors.Background.copy(alpha = 0.45f),
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
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        val rating = heroMovie.rating ?: heroMovie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                                        if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(13.dp))
                                                Text(rating, color = VegaColors.Gold, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Text(
                                            text = heroMovie.name,
                                            color = VegaColors.TextPrimary,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

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
                                            Text("İzle", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = VegaText.Secondary)
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
                    val gridSpacing = if (isMobileCompact) 10.dp else 12.dp
                    val gridHorizontalPadding = if (isMobileCompact) 10.dp else 8.dp
                    LazyVerticalGrid(
                        columns = gridCells,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = gridHorizontalPadding, end = gridHorizontalPadding, top = 6.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(if (isMobileCompact) 10.dp else 14.dp)
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
                            item(key = "vod_more_$visibleLimit", span = { GridItemSpan(maxLineSpan) }) {
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
                            item(key = "vod_global_search", span = { GridItemSpan(maxLineSpan) }) {
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
