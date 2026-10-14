package com.tivimax.player.ui.vod

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel

@Composable
fun VodScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleVodCategories.collectAsState()
    val movies by viewModel.vodStreams.collectAsState()
    val selectedCategory by viewModel.selectedVodCategory.collectAsState()
    val isLoading by viewModel.vodLoading.collectAsState()
    val currentSort by viewModel.vodSortOption.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    val globalQuery by viewModel.globalSearchQuery.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(globalQuery) {
        if (globalQuery.isNotBlank() && searchQuery != globalQuery) {
            searchQuery = globalQuery
            viewModel.setVodSearchQuery(globalQuery)
        }
    }

    val filteredMovies = remember(movies, searchQuery, currentSort) {
        try {
            val list = if (searchQuery.isBlank()) {
                movies
            } else {
                val q = searchQuery.trim().lowercase()
                movies.filter { (it.name ?: "").lowercase().contains(q) }
            }

            val sorted = when (currentSort) {
                SortOption.DEFAULT -> list
                SortOption.ADDED_DESC -> list.sortedByDescending { it.added?.toLongOrNull() ?: it.streamId.toLong() }
                SortOption.RATING_DESC -> list.sortedByDescending { it.rating5Based ?: it.rating?.toDoubleOrNull() ?: 0.0 }
                SortOption.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
                SortOption.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
            }
            sorted.distinctBy { it.streamId }
        } catch (e: Exception) {
            movies
        }
    }

    var visibleLimit by remember(filteredMovies) { mutableStateOf(40) }
    val pagedMovies = remember(filteredMovies, visibleLimit) {
        filteredMovies.take(visibleLimit)
    }

    val featuredMovie = remember(movies) {
        movies.firstOrNull { it.streamIcon != null && it.streamIcon.isNotBlank() }
    }

    // --- TIVIMATE SOL DİKEY KATEGORİ SÜTUNU & SAĞ FİLM ALANI ---
    var isSidebarExpanded by remember { mutableStateOf(true) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarExpanded) 125.dp else 46.dp,
        animationSpec = tween(220),
        label = "VodSidebarWidth"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
            // SOL PANEL: DİKEY FİLM KATEGORİLERİ
            Surface(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight(),
                color = TiviSurface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSidebarExpanded = !isSidebarExpanded }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isSidebarExpanded) Arrangement.SpaceBetween else Arrangement.Center
                    ) {
                        if (isSidebarExpanded) {
                            Text(
                                text = "TÜRLER",
                                color = TiviTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Icon(
                            imageVector = if (isSidebarExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Türleri Aç/Kapa",
                            tint = TiviSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(categories, key = { it.categoryId }) { category ->
                            val isSelected = category.categoryId == selectedCategory

                            Surface(
                                onClick = { viewModel.loadVodStreams(category.categoryId) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TiviSecondary.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(TiviSecondary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = if (isSelected) TiviSecondary else TiviTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )

                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = category.categoryName,
                                            color = if (isSelected) TiviSecondary else TiviTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VerticalDivider(
                color = TiviCardBorder.copy(alpha = 0.6f),
                thickness = 1.dp
            )

            // SAĞ PANEL: ARAMA ÇUBUĞU & FİLMLER IZGARASI
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Arama ve Sıralama Çubuğu (Kompakt ve Şık)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.setVodSearchQuery(it)
                        },
                        placeholder = { Text("Film ara...", fontSize = 12.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TiviSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.setVodSearchQuery("")
                                }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Temizle", tint = TiviTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TiviSecondary,
                            unfocusedBorderColor = TiviCardBorder,
                            focusedContainerColor = TiviCard,
                            unfocusedContainerColor = TiviCard,
                            focusedTextColor = TiviTextPrimary,
                            unfocusedTextColor = TiviTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sıralama Seçici Butonu
                    Box {
                        Surface(
                            onClick = { isSortMenuOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            color = TiviCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sırala",
                                    tint = TiviSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (currentSort) {
                                        SortOption.ADDED_DESC -> "Eklenme"
                                        SortOption.RATING_DESC -> "Puan"
                                        SortOption.NAME_ASC -> "A - Z"
                                        SortOption.NAME_DESC -> "Z - A"
                                        SortOption.DEFAULT -> "Varsayılan"
                                    },
                                    color = TiviTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isSortMenuOpen,
                            onDismissRequest = { isSortMenuOpen = false },
                            modifier = Modifier.background(TiviSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Eklenme Tarihi (Yeni)", color = if (currentSort == SortOption.ADDED_DESC) TiviSecondary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = TiviSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setVodSort(SortOption.ADDED_DESC)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Puan Sıralaması (En Yüksek)", color = if (currentSort == SortOption.RATING_DESC) TiviSecondary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = TiviGold, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setVodSort(SortOption.RATING_DESC)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("A - Z (İsme Göre)", color = if (currentSort == SortOption.NAME_ASC) TiviSecondary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setVodSort(SortOption.NAME_ASC)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Z - A (İsme Göre Ters)", color = if (currentSort == SortOption.NAME_DESC) TiviSecondary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setVodSort(SortOption.NAME_DESC)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Varsayılan Sıra", color = if (currentSort == SortOption.DEFAULT) TiviSecondary else TiviTextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setVodSort(SortOption.DEFAULT)
                                    isSortMenuOpen = false
                                }
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
                            CircularProgressIndicator(color = TiviSecondary)
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
                                    color = TiviTextSecondary,
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
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
                                    onClick = { viewModel.loadVodDetail(movie) }
                                )
                            }

                            // Sonsuz Kaydırma / Sayfalama Tetikleyici
                            if (visibleLimit < filteredMovies.size) {
                                item(span = { GridItemSpan(2) }) {
                                    LaunchedEffect(Unit) {
                                        visibleLimit += 30
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = TiviSecondary,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }

                            // Arama yapıldığında ızgaranın en altında alternatif arama seçenekleri
                            if (searchQuery.isNotBlank()) {
                                item(span = { GridItemSpan(2) }) {
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(12.dp))
                .background(TiviCard)
        ) {
            AsyncImage(
                model = movie.streamIcon,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = TiviGold,
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

            // Kaldığı Yer İlerleme Çubuğu (Netflix/TiviMate Tarzı Kırmızı/Altın İlerleme Çizgisi)
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
                            .background(TiviGold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = movie.name,
            color = TiviTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
