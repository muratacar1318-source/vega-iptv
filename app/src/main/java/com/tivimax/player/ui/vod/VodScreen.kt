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
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
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

    val globalQuery by viewModel.globalSearchQuery.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }

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

    var selectedHeroMovie by remember { mutableStateOf<VodStream?>(null) }
    val featuredMovie = remember(movies) {
        movies.firstOrNull { it.streamIcon != null && it.streamIcon.isNotBlank() }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val gridColumns = if (isWideScreen) (configuration.screenWidthDp / 140).coerceIn(5, 7) else 3

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
                            .padding(horizontal = 8.dp, vertical = 7.dp),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showCategoryDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Kategorileri Düzenle",
                                        tint = TiviSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { isSidebarExpanded = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                                        contentDescription = "Türleri Kapat",
                                        tint = TiviTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { isSidebarExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Türleri Aç",
                                    tint = TiviSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
                                        val countSuffix = if (category.itemCount > 0) " (${category.itemCount})" else ""
                                        Text(
                                            text = "${category.categoryName}$countSuffix",
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
                        val heroMovie = selectedHeroMovie ?: featuredMovie ?: pagedMovies.firstOrNull()

                        Column(modifier = Modifier.fillMaxSize()) {
                            // OPUS IPTV HERO BANNER (Yatay Modda Öne Çıkan / Odaklanılan Film)
                            if (isWideScreen && heroMovie != null && searchQuery.isBlank()) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = TiviCardDark),
                                    border = BorderStroke(1.dp, TiviCardBorder),
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
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(OpusCyan)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("ÖNE ÇIKAN", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    val rating = heroMovie.rating ?: heroMovie.rating5Based?.let { String.format("%.1f", it * 2) } ?: "8.2"
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = TiviGold, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(rating, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(heroMovie.containerExtension?.uppercase() ?: "MP4", color = TiviTextSecondary, fontSize = 11.sp)
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Text(
                                                    text = heroMovie.name,
                                                    color = Color.White,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Ultra HD yüksek kaliteli yayın ve kesintisiz sinema deneyimi.",
                                                    color = TiviTextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Button(
                                                onClick = { viewModel.loadVodDetail(heroMovie) },
                                                colors = ButtonDefaults.buttonColors(containerColor = OpusCyan),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .height(38.dp)
                                                    .dpadFocusable(focusedBorderColor = Color.White)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Hemen İzle", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
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
                                        onFocused = { selectedHeroMovie = movie },
                                        onClick = { viewModel.loadVodDetail(movie) }
                                    )
                                }

                                // Sonsuz Kaydırma / Sayfalama Tetikleyici
                                if (visibleLimit < filteredMovies.size) {
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
                                            CircularProgressIndicator(
                                                color = OpusCyan,
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

        if (showCategoryDialog) {
            CategoryManagementDialog(
                viewModel = viewModel,
                initialTab = 1,
                onDismiss = { showCategoryDialog = false }
            )
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
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(12.dp),
                onFocusStateChange = { if (it) onFocused?.invoke() }
            )
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(12.dp))
                .background(TiviCard)
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
