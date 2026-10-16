package com.tivimax.player.ui.catalog

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
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.*

private val NeonCyan = Color(0xFF00E5FF)
private val GlassBorder = Color(0x33FFFFFF)
private val GlassSurface = Color(0x261B1D36)
private val GlassSurfaceLight = Color(0x4025284A)

@Composable
fun VegaMediaCatalogScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleSeriesCategories.collectAsState()
    val seriesList by viewModel.seriesStreams.collectAsState()
    val selectedCategory by viewModel.selectedSeriesCategory.collectAsState()
    val isLoading by viewModel.seriesLoading.collectAsState()
    val currentSort by viewModel.seriesSortOption.collectAsState()

    val uiCategories = remember(categories) {
        categories.map {
            CategoryUiItem(
                id = it.categoryId,
                name = it.categoryName,
                count = it.itemCount,
                icon = Icons.Default.Folder,
                iconTint = Color(0xFF7C4DFF)
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ortak Sol Kategori Menüsü
        CategorySidebar(
            categories = uiCategories,
            selectedCategoryId = selectedCategory,
            onCategorySelected = { viewModel.loadSeriesStreams(it) },
            title = "KATEGORİLER"
        )

        // SAĞ İÇERİK ALANI (LazyVerticalGrid - 3 Sütun)
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
                onSeriesClick = { series -> viewModel.loadSeriesDetail(series) }
            )
        }
    }
}

@Composable
private fun VegaMediaContentGrid(
    isLoading: Boolean,
    seriesList: List<SeriesStream>,
    currentSort: com.tivimax.player.data.model.SortOption,
    onSortSelected: (com.tivimax.player.data.model.SortOption) -> Unit,
    onSeriesClick: (SeriesStream) -> Unit
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                val sortLabel = when (currentSort) {
                    com.tivimax.player.data.model.SortOption.ADDED_DESC -> "Eklenme"
                    com.tivimax.player.data.model.SortOption.RATING_DESC -> "Puan"
                    com.tivimax.player.data.model.SortOption.NAME_ASC -> "A - Z"
                    com.tivimax.player.data.model.SortOption.NAME_DESC -> "Z - A"
                    com.tivimax.player.data.model.SortOption.DEFAULT -> "Filtrele"
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
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp)) },
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
            // TV / yatay ekranda daha fazla sütun (Filmler ekranıyla aynı ölçü)
            val configuration = LocalConfiguration.current
            val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
            val gridColumns = if (isWideScreen) (configuration.screenWidthDp / 140).coerceIn(5, 7) else 3

            // Sonsuz kaydırma: tüm diziler parça parça yüklenir (önceden ilk 60 ile sınırlıydı)
            var visibleLimit by remember(seriesList) { mutableIntStateOf(40) }
            val pagedSeries = remember(seriesList, visibleLimit) { seriesList.take(visibleLimit) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 90.dp),
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

                val ratingText = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) } ?: "7.8"
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
                            tint = Color(0xFFFFD700),
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
