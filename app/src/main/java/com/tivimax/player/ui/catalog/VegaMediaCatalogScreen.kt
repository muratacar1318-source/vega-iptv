package com.tivimax.player.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.SeriesCategory
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable

// ============================================================================
// GLASSMORPHISM TEMA VE RENK PALETİ
// ============================================================================
private val BgGradientStart = Color(0xFF0B0C1A)
private val BgGradientEnd = Color(0xFF15162C)

private val NeonPurple = Color(0xFF7C4DFF)
private val NeonCyan = Color(0xFF00E5FF)
private val AccentGradient = Brush.horizontalGradient(listOf(NeonPurple, NeonCyan))

private val GlassBorder = Color(0x33FFFFFF)
private val GlassPurpleBorder = Color(0x667C4DFF)
private val GlassSurface = Color(0x261B1D36) // ~15% Alpha
private val GlassSurfaceLight = Color(0x4025284A) // ~25% Alpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VegaMediaCatalogScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleSeriesCategories.collectAsState()
    val seriesList by viewModel.seriesStreams.collectAsState()
    val selectedCategory by viewModel.selectedSeriesCategory.collectAsState()
    val searchQuery by viewModel.seriesSearchQuery.collectAsState()
    val isLoading by viewModel.seriesLoading.collectAsState()

    var activeTab by remember { mutableStateOf("Diziler") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgGradientStart, BgGradientEnd)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VegaTopAppBar(
                    appName = "Vega IPTV",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSeriesSearchQuery(it) },
                    onSyncClick = { viewModel.openPlaylistSync() },
                    onSettingsClick = { viewModel.setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.SETTINGS) }
                )
            },
            bottomBar = {
                VegaBottomNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { tab ->
                        activeTab = tab
                        when (tab) {
                            "Ana Sayfa" -> viewModel.setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.DASHBOARD)
                            "Canlı TV" -> viewModel.setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.LIVE_TV)
                            "Filmler" -> viewModel.setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.MOVIES)
                            "Diziler" -> viewModel.setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.SERIES)
                            "Favoriler" -> viewModel.setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.FAVORITES)
                        }
                    }
                )
            }
        ) { innerPadding ->
            // 3. ANA DÜZEN (SPLIT LAYOUT - ROW: %32 Sol Menü, %68 Sağ İçerik)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // SOL KATEGORİ MENÜSÜ (%32 Genişlik)
                Box(modifier = Modifier.weight(0.32f)) {
                    VegaCategorySidebar(
                        categories = categories,
                        selectedCategoryId = selectedCategory,
                        onCategorySelected = { catId -> viewModel.loadSeriesStreams(catId) }
                    )
                }

                // SAĞ İÇERİK ALANI (%68 Genişlik)
                Box(modifier = Modifier.weight(0.68f)) {
                    VegaMediaContentGrid(
                        isLoading = isLoading,
                        seriesList = seriesList,
                        onSeriesClick = { series -> viewModel.loadSeriesDetail(series) }
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. ÜST BAR (TOP APP BAR - GLASSMORPHISM)
// ============================================================================
@Composable
private fun VegaTopAppBar(
    appName: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // SOL: "Vega IPTV" Başlığı (Açık Mavi - Beyaz Parlamalı Font)
            Text(
                text = appName,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )

            // ORTA: Yuvarlatılmış Kapsül Arama Çubuğu
            Surface(
                shape = RoundedCornerShape(50),
                color = GlassSurfaceLight,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Dizi, film veya kanal ara...",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.5.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(NeonCyan),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Temizle",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                }
            }

            // SAĞ: Yenile ve Ayarlar Butonları (Yuvarlatılmış Kare - 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Yenile",
                    onClick = onSyncClick
                )
                GlassIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Ayarlar",
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = GlassSurfaceLight,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier
            .size(38.dp)
            .dpadFocusable(
                focusedBorderColor = NeonCyan,
                shape = RoundedCornerShape(12.dp),
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================================================
// 4. SOL KATEGORİ MENÜSÜ (LAZYCOLUMN - GLASSMORPHISM)
// ============================================================================
@Composable
private fun VegaCategorySidebar(
    categories: List<SeriesCategory>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Üst Başlık: "KATEGORİLER" & Filtre İkonu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "KATEGORİLER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.2.sp
                )
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtre",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories, key = { it.categoryId }) { category ->
                    val isSelected = category.categoryId == selectedCategoryId
                    VegaCategoryItem(
                        category = category,
                        isSelected = isSelected,
                        onClick = { onCategorySelected(category.categoryId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VegaCategoryItem(
    category: SeriesCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    val backgroundBrush = if (isSelected) {
        Brush.horizontalGradient(
            listOf(
                NeonPurple.copy(alpha = 0.35f),
                NeonCyan.copy(alpha = 0.10f)
            )
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    val borderStroke = if (isSelected) {
        BorderStroke(1.5.dp, AccentGradient)
    } else {
        BorderStroke(0.5.dp, GlassBorder)
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .dpadFocusable(
                focusedBorderColor = NeonCyan,
                shape = shape,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol Kenarda Dikey Parlak Çizgi (Seçili ise)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AccentGradient)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Marka / İkon Rozeti (Yuvarlatılmış Kare)
                val iconColor = remember(category.categoryName) {
                    val nameLower = category.categoryName.lowercase()
                    when {
                        nameLower.contains("prime") || nameLower.contains("amazon") -> Color(0xFF00A8E1)
                        nameLower.contains("netflix") -> Color(0xFFE50914)
                        nameLower.contains("apple") -> Color(0xFFE2E8F0)
                        nameLower.contains("anime") -> Color(0xFFFF3366)
                        nameLower.contains("disney") -> Color(0xFF113CCF)
                        else -> NeonPurple
                    }
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(iconColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Kategori Adı
                Text(
                    text = category.categoryName,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // İçerik Sayısı (Örn: "(181)")
                if (category.itemCount > 0) {
                    Text(
                        text = "(${category.itemCount})",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ============================================================================
// 5. SAĞ İÇERİK ALANI (LAZYVERTICALGRID - 3 SÜTUN)
// ============================================================================
@Composable
private fun VegaMediaContentGrid(
    isLoading: Boolean,
    seriesList: List<SeriesStream>,
    onSeriesClick: (SeriesStream) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Üst Sağ: "Filtrele" Butonu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = {},
                shape = RoundedCornerShape(10.dp),
                color = GlassSurfaceLight,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier
                    .height(34.dp)
                    .dpadFocusable(focusedBorderColor = NeonCyan, shape = RoundedCornerShape(10.dp), onClick = {})
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtrele",
                        tint = NeonCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Text("Filtrele", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
            }
        } else if (seriesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Bu kategoride henüz dizi veya film bulunmuyor.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(seriesList.take(60), key = { "series_${it.seriesId}_${it.num}" }) { series ->
                    VegaMediaCard(
                        series = series,
                        onClick = { onSeriesClick(series) }
                    )
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
        // Afiş Görseli (2:3 En-Boy Oranı, 12.dp Köşe Yuvarlatmalı)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clickable { onClick() }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncShimmerImage(
                    model = series.cover,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Afiş Üzerinde Alt Gölgelendirme Gradient'ı
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )

                // Puan Rozeti (Badge) - Afişin Sağ Üst Köşesi
                val ratingText = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) } ?: "7.8"
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Puan",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = ratingText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Başlık (Maksimum 2 satır)
        Text(
            text = series.name,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Alt Bilgi: "Yıl • Tür"
        val releaseYear = series.releaseDate?.take(4)?.ifBlank { "2024" } ?: "2024"
        val genreText = series.genre?.split(",")?.firstOrNull()?.trim()?.ifBlank { "Dram" } ?: "Dram"
        Text(
            text = "$releaseYear • $genreText",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// 6. ALT NAVİGASYON BARI (BOTTOM NAVIGATION - GLASSMORPHISM)
// ============================================================================
@Composable
private fun VegaBottomNavigationBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        color = Color(0xEB0B0C1A),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navTabs = listOf(
                "Ana Sayfa" to Icons.Default.Home,
                "Canlı TV" to Icons.Default.LiveTv,
                "Filmler" to Icons.Default.Movie,
                "Diziler" to Icons.Default.VideoLibrary,
                "Favoriler" to Icons.Default.Star
            )

            navTabs.forEach { (title, icon) ->
                val isSelected = activeTab == title

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(title) },
                    contentAlignment = Alignment.Center
                ) {
                    // Aktif Sekme İçin Yukarıdan Aşağıya Vuran Mor Işık Hüzmesi (Spotlight Effect)
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            NeonPurple.copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(32.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(AccentGradient)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) NeonPurple else Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = title,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeonPurple else Color.White.copy(alpha = 0.45f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
