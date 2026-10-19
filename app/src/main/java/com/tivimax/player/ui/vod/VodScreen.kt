package com.tivimax.player.ui.vod

import androidx.activity.compose.BackHandler
import com.tivimax.player.util.LocalIsTvDevice
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.GlobalSearchOptionsCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable

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

    var visibleLimit by remember(filteredMovies) { mutableIntStateOf(40) }
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

    BackHandler(
        enabled = !isTvDevice && (showCategorySheet || hasSearch || hasActiveCategory)
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

    if (showCategoryDialog) {
        CategoryManagementDialog(
            viewModel = viewModel,
            initialTab = 1,
            onDismiss = { showCategoryDialog = false }
        )
    }

    if (isWideScreen || portraitNavStyle == com.tivimax.player.data.preferences.UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ortak Sol Kategori Menüsü
            com.tivimax.player.ui.components.CategorySidebar(
                categories = uiCategories,
                selectedCategoryId = selectedCategory,
                onCategorySelected = { viewModel.loadVodStreams(it) },
                title = "TÜRLER",
                totalCount = totalVodCount,
                allLabel = "Tüm Filmler",
                onFilterClick = { showCategoryDialog = true },
                tvFocusBehavior = isTvDevice // G-045
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                VodMediaContentGrid(
                    viewModel = viewModel,
                    filteredMovies = filteredMovies,
                    pagedMovies = pagedMovies,
                    featuredMovie = featuredMovie,
                    selectedHeroMovie = selectedHeroMovie,
                    onSelectHeroMovie = { selectedHeroMovie = it },
                    watchProgressMap = watchProgressMap,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    currentSort = currentSort,
                    isLoading = isLoading,
                    gridColumns = if (isWideScreen) (configuration.screenWidthDp / 140).coerceIn(5, 7) else 2,
                    isWideScreen = isWideScreen,
                    portraitNavStyle = portraitNavStyle,
                    visibleLimit = visibleLimit,
                    onLoadMore = { visibleLimit += 30 }
                )
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
private fun VodMediaContentGrid(
    viewModel: MainViewModel,
    filteredMovies: List<VodStream>,
    pagedMovies: List<VodStream>,
    featuredMovie: VodStream?,
    selectedHeroMovie: VodStream?,
    onSelectHeroMovie: (VodStream) -> Unit,
    watchProgressMap: Map<String, WatchProgress>,
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
                                        .clickable { viewModel.setVodSearchQuery("") }
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Filtreyi Temizle",
                                        tint = VegaColors.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "${filteredMovies.size} Film",
                            color = VegaColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

                            DropdownMenu(
                                expanded = isSortMenuOpen,
                                onDismissRequest = { isSortMenuOpen = false },
                                modifier = Modifier.background(VegaColors.Surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Eklenme tarihi (yeni)", color = if (currentSort == SortOption.ADDED_DESC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setVodSort(SortOption.ADDED_DESC)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Puan sıralaması (en yüksek)", color = if (currentSort == SortOption.RATING_DESC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setVodSort(SortOption.RATING_DESC)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("A - Z (İsme göre)", color = if (currentSort == SortOption.NAME_ASC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setVodSort(SortOption.NAME_ASC)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Z - A (İsme göre ters)", color = if (currentSort == SortOption.NAME_DESC) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setVodSort(SortOption.NAME_DESC)
                                        isSortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Varsayılan sıra", color = if (currentSort == SortOption.DEFAULT) VegaColors.Accent else VegaColors.TextPrimary, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null, tint = VegaColors.TextSecondary, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        viewModel.setVodSort(SortOption.DEFAULT)
                                        isSortMenuOpen = false
                                    }
                                )
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
                            // OPUS IPTV HERO BANNER (Yatay Modda Öne Çıkan / Odaklanılan Film)
                            if (isWideScreen && heroMovie != null && searchQuery.isBlank()) {
                                Card(
                                    shape = VegaShapes.Medium,
                                    colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
                                    border = BorderStroke(1.dp, VegaColors.Border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
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
                                                        Color.Black,
                                                        Color.Black.copy(alpha = 0.9f),
                                                        Color.Black.copy(alpha = 0.5f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(0.65f)
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(VegaShapes.Small)
                                                            .background(VegaColors.Accent)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Öne çıkan", color = VegaColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    val rating = heroMovie.rating ?: heroMovie.rating5Based?.let { String.format("%.1f", it * 2) } ?: "8.2"
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(rating, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(heroMovie.containerExtension?.uppercase() ?: "MP4", color = VegaColors.TextSecondary, fontSize = 11.sp)
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Text(
                                                    text = heroMovie.name,
                                                    color = VegaColors.TextPrimary,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Ultra HD yüksek kaliteli yayın ve kesintisiz sinema deneyimi.",
                                                    color = VegaColors.TextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Button(
                                                onClick = { viewModel.loadVodDetail(heroMovie) },
                                                colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                                                shape = VegaShapes.Medium,
                                                modifier = Modifier
                                                    .height(38.dp)
                                                    .dpadFocusable(
                                                        focusedBorderColor = Color.White,
                                                        shape = VegaShapes.Medium,
                                                        onClick = { viewModel.loadVodDetail(heroMovie) }
                                                    )
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Hemen izle", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 96.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = pagedMovies,
                                    key = { "${it.streamId}_${it.num}" },
                                    contentType = { "vod_movie" }
                                ) { movie ->
                                    val progress = watchProgressMap[movie.streamId.toString()]
                                    MoviePosterCard(
                                        movie = movie,
                                        watchProgress = progress,
                                        onFocused = { onSelectHeroMovie(movie) },
                                        onClick = { viewModel.loadVodDetail(movie) }
                                    )
                                }

                                // Sonsuz Kaydırma / Sayfalama Tetikleyici
                                if (visibleLimit < filteredMovies.size) {
                                    item(span = { GridItemSpan(gridColumns) }) {
                                        LaunchedEffect(Unit) {
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
                                    item(span = { GridItemSpan(gridColumns) }) {
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
    watchProgress: com.tivimax.player.data.preferences.WatchProgress? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onClick,
                onFocusStateChange = { if (it) onFocused?.invoke() }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(VegaShapes.Medium)
                .background(VegaColors.Card)
        ) {
            AsyncShimmerImage(
                model = movie.streamIcon,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholderIcon = Icons.Default.Movie
            )

            // Üst Karartma & IMDb Puan Rozeti
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // IMDb / Puan Rozeti
            val rating = movie.rating ?: movie.rating5Based?.let { String.format("%.1f", it * 2) }
            if (!rating.isNullOrEmpty() && rating != "0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(VegaShapes.Small)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = VegaColors.Gold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = rating,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Kaldığı Yer İlerleme Çubuğu
            if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
                val progressFraction = (watchProgress.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(VegaColors.Gold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = movie.name,
            color = VegaColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        val extText = movie.containerExtension.uppercase().ifBlank { "Film" }
        Text(
            text = extText,
            color = VegaColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
