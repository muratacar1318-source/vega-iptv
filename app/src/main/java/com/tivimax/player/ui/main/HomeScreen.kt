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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.favorites.FavoritesScreen
import com.tivimax.player.ui.live.LiveTvScreen
import com.tivimax.player.ui.settings.SettingsScreen
import com.tivimax.player.ui.series.SeriesScreen
import com.tivimax.player.ui.vod.VodScreen
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

enum class HomeTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Ana Sayfa", Icons.Default.Home),
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
    var showSearchDialog by remember { mutableStateOf(false) }

    val playlistSyncState by viewModel.playlistSyncState.collectAsState()
    val updateInfo by viewModel.autoUpdateManager.updateInfo.collectAsState()
    val downloadProgress by viewModel.autoUpdateManager.downloadProgress.collectAsState()

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
                        val tabs = listOf(HomeTab.DASHBOARD, HomeTab.LIVE_TV, HomeTab.MOVIES, HomeTab.SERIES, HomeTab.FAVORITES)
                        tabs.forEach { tab ->
                            val isSelected = selectedTab == tab
                            Surface(
                                onClick = { viewModel.setSelectedHomeTab(tab) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) TiviPrimary.copy(alpha = 0.22f) else Color.Transparent,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .size(46.dp)
                                    .dpadFocusable(
                                        shape = RoundedCornerShape(12.dp),
                                        focusedBorderColor = TiviPrimary,
                                        onClick = { viewModel.setSelectedHomeTab(tab) }
                                    )
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSettingsActive) TiviPrimary.copy(alpha = 0.22f) else TiviCard,
                            onClick = {
                                if (isSettingsActive) {
                                    viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                                } else {
                                    viewModel.setSelectedHomeTab(HomeTab.SETTINGS)
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .dpadFocusable(
                                    shape = RoundedCornerShape(12.dp),
                                    focusedBorderColor = TiviPrimary,
                                    onClick = {
                                        if (isSettingsActive) {
                                            viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                                        } else {
                                            viewModel.setSelectedHomeTab(HomeTab.SETTINGS)
                                        }
                                    }
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Ayarlar",
                                    tint = if (isSettingsActive) TiviPrimary else TiviTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // İçerikleri Güncelle / Senkronizasyon Butonu
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TiviCard,
                            onClick = { viewModel.openPlaylistSync() },
                            modifier = Modifier
                                .size(42.dp)
                                .dpadFocusable(
                                    shape = RoundedCornerShape(12.dp),
                                    focusedBorderColor = TiviPrimary,
                                    onClick = { viewModel.openPlaylistSync() }
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "İçerikleri Güncelle",
                                    tint = TiviPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                    isSyncing = playlistSyncState.isSyncing,
                    syncPercentage = playlistSyncState.overallPercentage,
                    onSettingsClick = {},
                    onSyncClick = { viewModel.openPlaylistSync() },
                    onSearchClick = { showSearchDialog = true }
                )

                // Tab İçeriği
                HomeContentBody(selectedTab = selectedTab, viewModel = viewModel)
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
                    isSyncing = playlistSyncState.isSyncing,
                    syncPercentage = playlistSyncState.overallPercentage,
                    onSettingsClick = {
                        if (selectedTab == HomeTab.SETTINGS) {
                            viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                        } else {
                            viewModel.setSelectedHomeTab(HomeTab.SETTINGS)
                        }
                    },
                    onSyncClick = { viewModel.openPlaylistSync() },
                    onSearchClick = { showSearchDialog = true }
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
                        val bottomTabs = listOf(HomeTab.LIVE_TV, HomeTab.MOVIES, HomeTab.SERIES, HomeTab.FAVORITES, HomeTab.SETTINGS)
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
                HomeContentBody(selectedTab = selectedTab, viewModel = viewModel)
            }
        }
    }

    // Evrensel Arama Dialogu
    if (showSearchDialog) {
        var queryText by remember { mutableStateOf("") }
        val searchFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            delay(150)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            containerColor = TiviSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = OpusCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Evrensel Arama",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = {
                            Text(
                                text = "Kanal, film veya dizi ara...",
                                color = TiviTextMuted,
                                fontSize = 15.sp
                            )
                        },
                        textStyle = TextStyle(fontSize = 15.sp, color = Color.White),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OpusCyan,
                            unfocusedBorderColor = TiviCardBorder,
                            focusedContainerColor = TiviCard,
                            unfocusedContainerColor = TiviCard,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .focusRequester(searchFocusRequester)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Aramak istediğiniz bölümü seçin:",
                        color = TiviTextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Canlı TV Butonu
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TiviCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .dpadFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = OpusCyan,
                                    onClick = {
                                        viewModel.setLiveSearchQuery(queryText)
                                        viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                                        showSearchDialog = false
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LiveTv,
                                    contentDescription = null,
                                    tint = TiviPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Canlı TV",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Filmler Butonu
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TiviCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TiviSecondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .dpadFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = OpusCyan,
                                    onClick = {
                                        viewModel.setVodSearchQuery(queryText)
                                        viewModel.setSelectedHomeTab(HomeTab.MOVIES)
                                        showSearchDialog = false
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = TiviSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Filmler",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Diziler Butonu
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TiviCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, OpusCyan),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .dpadFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = OpusCyan,
                                    onClick = {
                                        viewModel.setSeriesSearchQuery(queryText)
                                        viewModel.setSelectedHomeTab(HomeTab.SERIES)
                                        showSearchDialog = false
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = OpusCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Diziler",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Kapat", color = TiviTextSecondary)
                }
            }
        )
    }

    // ==========================================
    // GITHUB OTOMATİK GÜNCELLEME DİYALOĞU (MADDE 9)
    // ==========================================
    updateInfo?.let { info ->
        val updateFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            delay(150)
            try {
                updateFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }

        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            containerColor = TiviSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = OpusCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🚀 Yeni Güncelleme Mevcut (${info.tagName})",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Vega IPTV'nin en son sürümü yayınlandı:",
                        color = TiviTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TiviCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = info.releaseNotes.ifBlank { "Performans iyileştirmeleri ve hata düzeltmeleri." },
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (downloadProgress != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (downloadProgress == 100) "İndirme Tamamlandı, Kuruluyor..." else "APK İndiriliyor...",
                                    color = OpusCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "%$downloadProgress",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (downloadProgress!! / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = OpusCyan,
                                trackColor = Color(0xFF1E2638)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (downloadProgress == null) {
                    Button(
                        onClick = { viewModel.downloadAndInstallUpdate(info) },
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .focusRequester(updateFocusRequester)
                            .dpadFocusable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = Color.White,
                                onClick = { viewModel.downloadAndInstallUpdate(info) }
                            )
                    ) {
                        Text(text = "Şimdi İndir ve Kur", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (downloadProgress == null) {
                    TextButton(
                        onClick = { viewModel.dismissUpdate() },
                        modifier = Modifier.dpadFocusable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = OpusCyan,
                            onClick = { viewModel.dismissUpdate() }
                        )
                    ) {
                        Text(text = "Daha Sonra", color = TiviTextSecondary)
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeTopBar(
    appName: String,
    expDateFormatted: String,
    activeCons: String,
    maxCons: String,
    showSettings: Boolean,
    isSyncing: Boolean = false,
    syncPercentage: Int = 0,
    onSettingsClick: () -> Unit,
    onSyncClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
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

            // Sağ Üst Alan: Arşiv Yükleme Rozeti, Arama, Bitiş Tarihi, Bağlantı Rozeti, Senkronizasyon, Ayarlar
            val configuration = LocalConfiguration.current
            val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Arka Planda Arşiv Güncelleniyor Rozeti (Madde 6)
                if (isSyncing) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TiviPrimary.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary),
                        modifier = Modifier
                            .dpadFocusable(
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = OpusCyan,
                                onClick = onSyncClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = TiviPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Arşiv güncelleniyor: %$syncPercentage",
                                color = TiviPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Kumandayla Odaklanılabilir "🔍 Arama" Butonu (%20 Büyütüldü)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TiviCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    modifier = Modifier
                        .height(44.dp)
                        .dpadFocusable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = OpusCyan,
                            onClick = onSearchClick
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Arama",
                            tint = OpusCyan,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = "Arama",
                            color = TiviTextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Geniş ekranda (TV / Yanal Mod) Bitiş Tarihi ve Cihaz Limiti gösterilir (Dar mobil ekranda taşmayı önler)
                if (isWideScreen) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TiviCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Bitiş Tarihi
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = "Bitiş Tarihi",
                                    tint = TiviPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = expDateFormatted,
                                    color = TiviTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(text = "•", color = TiviTextMuted, fontSize = 11.sp)

                            // Anlık Bağlantı Limiti
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = "Bağlantı Limiti",
                                    tint = TiviGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$activeCons/$maxCons",
                                    color = TiviGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Senkronizasyon Butonu (%20 Büyütüldü)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TiviCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    onClick = onSyncClick,
                    modifier = Modifier
                        .size(44.dp)
                        .dpadFocusable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = OpusCyan,
                            onClick = onSyncClick
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "İçerikleri Güncelle",
                            tint = TiviPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Ayarlar Butonu (%20 Büyütüldü)
                if (showSettings) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TiviCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(44.dp)
                            .dpadFocusable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = OpusCyan,
                                onClick = onSettingsClick
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ayarlar",
                                tint = TiviTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContentBody(
    selectedTab: HomeTab,
    viewModel: MainViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
            HomeTab.DASHBOARD -> OpusDashboardScreen(viewModel = viewModel)
            HomeTab.LIVE_TV -> LiveTvScreen(viewModel = viewModel)
            HomeTab.MOVIES -> VodScreen(viewModel = viewModel)
            HomeTab.SERIES -> SeriesScreen(viewModel = viewModel)
            HomeTab.FAVORITES -> FavoritesScreen(viewModel = viewModel)
            HomeTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
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
