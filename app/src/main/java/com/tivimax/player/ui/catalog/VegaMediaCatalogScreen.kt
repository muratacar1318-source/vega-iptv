package com.tivimax.player.ui.catalog

import androidx.activity.compose.BackHandler
import com.tivimax.player.util.LocalIsTvDevice
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.*
import com.tivimax.player.ui.design.VegaColors

private val NeonCyan = VegaColors.Accent
private val GlassBorder = VegaColors.Border
private val GlassSurface = VegaColors.Card
private val GlassSurfaceLight = VegaColors.SurfaceVariant

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

    BackHandler(
        enabled = !isTvDevice && (showCategorySheet || hasSearch || hasActiveCategory)
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

    if (isWideScreen || portraitNavStyle == UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR) {
        // İki bölmeli Klasik Düzen (Yatay ekran veya kullanıcının seçtiği sol panel modu)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategorySidebar(
                categories = uiCategories,
                selectedCategoryId = selectedCategory,
                onCategorySelected = { viewModel.loadSeriesStreams(it) },
                title = "KATEGORİLER",
                totalCount = totalSeriesCount,
                allLabel = "Tüm Diziler",
                tvFocusBehavior = com.tivimax.player.util.LocalIsTvDevice.current // G-045
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                VegaMediaContentGrid(
                    isLoading = isLoading,
                    seriesList = seriesList,
                    currentSort = currentSort,
                    onSortSelected = { sort -> viewModel.setSeriesSort(sort) },
                    onSeriesClick = { series -> viewModel.loadSeriesDetail(series) },
                    portraitNavStyle = portraitNavStyle,
                    onPortraitNavStyleChange = { viewModel.setPortraitCategoryNavStyle(it) },
                    isWideScreen = isWideScreen,
                    isSidebarActive = true
                )
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
                    currentSort = currentSort,
                    onSortSelected = { sort -> viewModel.setSeriesSort(sort) },
                    onSeriesClick = { series -> viewModel.loadSeriesDetail(series) },
                    portraitNavStyle = portraitNavStyle,
                    onPortraitNavStyleChange = { viewModel.setPortraitCategoryNavStyle(it) },
                    isWideScreen = isWideScreen,
                    isSidebarActive = false
                )
            }
        }
    }
}

@Composable
private fun VegaMediaContentGrid(
    isLoading: Boolean,
    seriesList: List<SeriesStream>,
    currentSort: com.tivimax.player.data.model.SortOption,
    onSortSelected: (com.tivimax.player.data.model.SortOption) -> Unit,
    onSeriesClick: (SeriesStream) -> Unit,
    portraitNavStyle: String,
    onPortraitNavStyleChange: (String) -> Unit,
    isWideScreen: Boolean,
    isSidebarActive: Boolean
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${seriesList.size} Dizi",
                color = TiviTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isWideScreen) {
                    CategoryNavStyleToggleButton(
                        currentStyle = portraitNavStyle,
                        onStyleChange = onPortraitNavStyleChange
                    )
                }

                Box {
                    val sortLabel = when (currentSort) {
                        com.tivimax.player.data.model.SortOption.ADDED_DESC -> "Eklenme"
                        com.tivimax.player.data.model.SortOption.RATING_DESC -> "Puan"
                        com.tivimax.player.data.model.SortOption.NAME_ASC -> "A - Z"
                        com.tivimax.player.data.model.SortOption.NAME_DESC -> "Z - A"
                        com.tivimax.player.data.model.SortOption.DEFAULT -> "Sırala"
                    }

                    ActionChipButton(
                        text = sortLabel,
                        icon = Icons.Default.FilterList,
                        onClick = { isSortMenuOpen = true }
                    )

                DropdownMenu(
                    expanded = isSortMenuOpen,
                    onDismissRequest = { isSortMenuOpen = false },
                    modifier = Modifier.background(com.tivimax.player.theme.TiviSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Eklenme Tarihi (Yeni)", color = if (currentSort == com.tivimax.player.data.model.SortOption.ADDED_DESC) NeonCyan else Color.White, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            onSortSelected(com.tivimax.player.data.model.SortOption.ADDED_DESC)
                            isSortMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Puan Sıralaması (En Yüksek)", color = if (currentSort == com.tivimax.player.data.model.SortOption.RATING_DESC) NeonCyan else Color.White, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            onSortSelected(com.tivimax.player.data.model.SortOption.RATING_DESC)
                            isSortMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("A - Z (İsme Göre)", color = if (currentSort == com.tivimax.player.data.model.SortOption.NAME_ASC) NeonCyan else Color.White, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            onSortSelected(com.tivimax.player.data.model.SortOption.NAME_ASC)
                            isSortMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Z - A (İsme Göre Ters)", color = if (currentSort == com.tivimax.player.data.model.SortOption.NAME_DESC) NeonCyan else Color.White, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            onSortSelected(com.tivimax.player.data.model.SortOption.NAME_DESC)
                            isSortMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Varsayılan Sıra", color = if (currentSort == com.tivimax.player.data.model.SortOption.DEFAULT) NeonCyan else Color.White, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            onSortSelected(com.tivimax.player.data.model.SortOption.DEFAULT)
                            isSortMenuOpen = false
                        }
                    )
                }
            }
        }
    }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            }
        } else if (seriesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Bu kategoride içerik bulunamadı.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        } else {
            val configuration = LocalConfiguration.current
            val gridColumns = if (isWideScreen) {
                (configuration.screenWidthDp / 140).coerceIn(5, 7)
            } else if (isSidebarActive) {
                2
            } else {
                3
            }

            // Sonsuz kaydırma: tüm diziler parça parça yüklenir (önceden ilk 60 ile sınırlıydı)
            var visibleLimit by remember(seriesList) { mutableIntStateOf(40) }
            val pagedSeries = remember(seriesList, visibleLimit) { seriesList.take(visibleLimit) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pagedSeries, key = { "series_${it.seriesId}_${it.num}" }) { series ->
                    VegaMediaCard(
                        series = series,
                        onClick = { onSeriesClick(series) }
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
                            CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = NeonCyan,
                shape = RoundedCornerShape(12.dp),
                onClick = onClick
            )
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncShimmerImage(
                    model = series.cover,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )

                val ratingText = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                if (!ratingText.isNullOrBlank() && ratingText != "0" && ratingText != "0.0") {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(0.5.dp, GlassBorder),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Puan",
                                tint = VegaColors.Gold,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = ratingText,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = series.name,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        val releaseYear = series.releaseDate?.take(4)?.ifBlank { "" } ?: ""
        val genreText = series.genre?.split(",")?.firstOrNull()?.trim()?.ifBlank { "Dizi" } ?: "Dizi"
        val subText = if (releaseYear.isNotBlank()) "$releaseYear • $genreText" else genreText
        Text(
            text = subText,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
