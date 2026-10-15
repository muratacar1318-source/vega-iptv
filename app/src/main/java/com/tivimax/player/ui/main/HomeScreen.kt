package com.tivimax.player.ui.main

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.favorites.FavoritesScreen
import com.tivimax.player.ui.live.LiveTvScreen
import com.tivimax.player.ui.settings.SettingsScreen
import com.tivimax.player.ui.series.SeriesScreen
import com.tivimax.player.ui.vod.VodScreen

enum class HomeTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LIVE_TV("Canlı TV", Icons.Default.LiveTv),
    MOVIES("Filmler", Icons.Default.Movie),
    SERIES("Diziler", Icons.Default.VideoLibrary),
    FAVORITES("Favoriler", Icons.Default.Star),
    SETTINGS("Ayarlar", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedHomeTab.collectAsState()
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Çıkış Önleme: Kullanıcı hangi sekmede olursa olsun yanlışlıkla çıkmayı önlemek için çift tıklama istenir
    BackHandler(enabled = true) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, "Çıkmak için tekrar geri basın", Toast.LENGTH_SHORT).show()
        }
    }

    val userInfo by viewModel.userInfo.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp
    val isWideScreen = isLandscape || screenWidthDp >= 600

    val expDateFormatted = remember(userInfo?.expDate) {
        val exp = userInfo?.expDate
        if (!exp.isNullOrEmpty() && exp != "null") {
            val timestamp = exp.toLongOrNull()
            if (timestamp != null && timestamp > 0) {
                val sdf = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp * 1000L))
            } else {
                exp.take(8)
            }
        } else {
            "Süresiz"
        }
    }

    val activeCons = userInfo?.activeCons?.ifEmpty { "1" } ?: "1"
    val maxCons = userInfo?.maxConnections?.ifEmpty { "1" } ?: "1"

    if (isWideScreen) {
        // --- TABLET & YATAY MOD: SOLDA NAVIGATION RAIL, SAĞDA İÇERİK ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(TiviBackground)
        ) {
            // SOL DİKEY NAVIGATION RAIL
            Surface(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight(),
                color = TiviSurface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Üst Logo
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TiviPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Dikey Menü Sekmeleri
                        val tabs = listOf(HomeTab.LIVE_TV, HomeTab.MOVIES, HomeTab.SERIES, HomeTab.FAVORITES)
                        tabs.forEach { tab ->
                            val isSelected = selectedTab == tab
                            IconButton(
                                onClick = { viewModel.setSelectedHomeTab(tab) },
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) TiviPrimary.copy(alpha = 0.22f) else Color.Transparent)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) TiviPrimary else TiviTextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Alt Alan: Ayarlar Butonu & Bağlantı Rozeti
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isSettingsActive = selectedTab == HomeTab.SETTINGS
                        IconButton(
                            onClick = {
                                if (isSettingsActive) {
                                    viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                                } else {
                                    viewModel.setSelectedHomeTab(HomeTab.SETTINGS)
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSettingsActive) TiviPrimary.copy(alpha = 0.22f) else TiviCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ayarlar",
                                tint = if (isSettingsActive) TiviPrimary else TiviTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // İçerikleri Güncelle / Senkronizasyon Butonu
                        IconButton(
                            onClick = { viewModel.openPlaylistSync() },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TiviCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "İçerikleri Güncelle",
                                tint = TiviPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Bağlantı Sayısı Mini Rozet
                        Text(
                            text = "$activeCons/$maxCons",
                            color = TiviGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // SAĞ ALAN: ÜST BİLGİ BARI & TAB İÇERİĞİ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Kompakt Üst Bar
                HomeTopBar(
                    appName = AppConfig.APP_NAME,
                    expDateFormatted = expDateFormatted,
                    activeCons = activeCons,
                    maxCons = maxCons,
                    showSettings = false, // Zaten rail üzerinde var
                    onSettingsClick = {},
                    onSyncClick = { viewModel.openPlaylistSync() }
                )

                // Tab İçeriği
                HomeContentBody(selectedTab = selectedTab, viewModel = viewModel, isWideScreen = true)
            }
        }
    } else {
        // --- TELEFON DİKEY MOD: ÜSTTE BAR, ALTA BOTTOM NAVIGATION BAR ---
        Scaffold(
            topBar = {
                HomeTopBar(
                    appName = AppConfig.APP_NAME,
                    expDateFormatted = expDateFormatted,
                    activeCons = activeCons,
                    maxCons = maxCons,
                    showSettings = true,
                    onSettingsClick = {
                        if (selectedTab == HomeTab.SETTINGS) {
                            viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                        } else {
                            viewModel.setSelectedHomeTab(HomeTab.SETTINGS)
                        }
                    },
                    onSyncClick = { viewModel.openPlaylistSync() }
                )
            },
            bottomBar = {
                Surface(
                    color = TiviSurface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bottomTabs = listOf(HomeTab.LIVE_TV, HomeTab.MOVIES, HomeTab.SERIES, HomeTab.FAVORITES)
                        bottomTabs.forEach { tab ->
                            val isSelected = selectedTab == tab
                            Surface(
                                onClick = { viewModel.setSelectedHomeTab(tab) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) TiviPrimary.copy(alpha = 0.16f) else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) TiviPrimary else TiviTextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.title,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TiviPrimary else TiviTextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = TiviBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                HomeContentBody(selectedTab = selectedTab, viewModel = viewModel, isWideScreen = false)
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    appName: String,
    expDateFormatted: String,
    activeCons: String,
    maxCons: String,
    showSettings: Boolean,
    onSettingsClick: () -> Unit,
    onSyncClick: () -> Unit = {}
) {
    Surface(
        color = TiviSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo ve Başlık
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TiviPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TiviTextPrimary
                )
            }

            // Sağ Üst Alan: Bitiş Tarihi, Bağlantı Rozeti
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TiviCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Bitiş Tarihi
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Bitiş Tarihi",
                                tint = TiviPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = expDateFormatted,
                                color = TiviTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(text = "•", color = TiviTextMuted, fontSize = 10.sp)

                        // Anlık Bağlantı Limiti
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = "Bağlantı Limiti",
                                tint = TiviGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$activeCons/$maxCons",
                                color = TiviGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onSyncClick,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TiviCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "İçerikleri Güncelle",
                        tint = TiviPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                if (showSettings) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TiviCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = TiviTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContentBody(
    selectedTab: HomeTab,
    viewModel: MainViewModel,
    isWideScreen: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Eğer Canlı TV sekmesindeyse, dikey modda en üstte yatay kaydırılabilir "Kaldığın Yerden Devam Et" ve "Son İzlenen Canlı Kanallar" şeritleri gösterilir.
        // Yatay / TV modunda Canlı TV açıkken ekranın tam yüksekliğini kullanabilmesi için gizlenir.
        if (selectedTab == HomeTab.LIVE_TV && !isWideScreen) {
            ResumeAndRecentDashboard(viewModel = viewModel)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                HomeTab.LIVE_TV -> LiveTvScreen(viewModel = viewModel)
                HomeTab.MOVIES -> VodScreen(viewModel = viewModel)
                HomeTab.SERIES -> SeriesScreen(viewModel = viewModel)
                HomeTab.FAVORITES -> FavoritesScreen(viewModel = viewModel)
                HomeTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ResumeAndRecentDashboard(viewModel: MainViewModel) {
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val vodMovies by viewModel.vodStreams.collectAsState()

    val activeProgressList = remember(watchProgressMap) {
        watchProgressMap.values
            .filter { it.positionMs > 10_000L && !it.isWatched }
            .sortedByDescending { it.timestamp }
            .take(10)
    }

    if (activeProgressList.isEmpty() && recentChannels.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TiviSurfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 6.dp)
    ) {
        // 1. ŞERİT: KALDIĞIN YERDEN DEVAM ET
        if (activeProgressList.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TiviPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kaldığın Yerden Devam Et",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TiviTextPrimary
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeProgressList, key = { "progress_${it.streamId}" }) { progress ->
                    val movie = remember(vodMovies, progress.streamId) {
                        vodMovies.firstOrNull { it.streamId == progress.streamId }
                    }

                    ResumeWatchingCard(
                        progress = progress,
                        movie = movie,
                        onClick = {
                            if (movie != null) {
                                viewModel.playVod(movie, resume = true)
                            }
                        }
                    )
                }
            }
        }

        // 2. ŞERİT: SON İZLENEN CANLI KANALLAR
        if (recentChannels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TiviAccentLive)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Son İzlenen Canlı Kanallar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TiviTextPrimary
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentChannels, key = { "recent_${it.streamId}" }) { channel ->
                    RecentChannelCard(
                        channel = channel,
                        onClick = { viewModel.playLiveChannel(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun ResumeWatchingCard(
    progress: WatchProgress,
    movie: VodStream?,
    onClick: () -> Unit
) {
    val progressFraction = remember(progress) {
        if (progress.durationMs > 0) {
            (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = TiviCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
        modifier = Modifier
            .width(170.dp)
            .height(64.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (movie?.streamIcon != null) {
                        AsyncShimmerImage(
                            model = movie.streamIcon,
                            contentDescription = movie.name,
                            contentScale = ContentScale.Crop,
                            placeholderIcon = Icons.Default.Movie,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = TiviPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play overlay
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie?.name ?: "İçerik #${progress.streamId}",
                        color = TiviTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val mins = progress.positionMs / 60000
                    Text(
                        text = "$mins. dakikada kaldı",
                        color = TiviTextSecondary,
                        fontSize = 9.sp
                    )
                }
            }

            // İlerleme Çubuğu (Progress Bar)
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = TiviPrimary,
                trackColor = TiviCardBorder
            )
        }
    }
}

@Composable
fun RecentChannelCard(
    channel: LiveStream,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = TiviCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
        modifier = Modifier
            .width(140.dp)
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncShimmerImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    placeholderIcon = Icons.Default.Tv,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = TiviTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(TiviAccentLive)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CANLI",
                        color = TiviAccentLive,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
