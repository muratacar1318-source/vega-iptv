package com.tivimax.player.ui.main

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
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
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
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

import com.tivimax.player.ui.tv.TvNavigationRail
import com.tivimax.player.ui.tv.TvTiviMateScreen
import com.tivimax.player.util.LocalIsTvDevice

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
    val isTvDevice = LocalIsTvDevice.current

    val selectedTab by viewModel.selectedHomeTab.collectAsState()
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var showSyncConfirmationDialog by remember { mutableStateOf(false) }

    // Android TV: sol menü odak durumu ve Canlı TV tam ekran oynatıcı durumu
    val tvRailFocusRequester = remember { FocusRequester() }
    var tvRailHasFocus by remember { mutableStateOf(false) }
    var tvLiveFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isTvDevice) {
        if (isTvDevice) {
            // Kumandanın ilk basışta bir odak noktası bulabilmesi için menüdeki seçili sekmeye odaklan
            delay(150)
            try {
                tvRailFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val playlistSyncState by viewModel.playlistSyncState.collectAsState()
    val updateInfo by viewModel.autoUpdateManager.updateInfo.collectAsState()
    val downloadProgress by viewModel.autoUpdateManager.downloadProgress.collectAsState()

    // Çıkış Önleme: Kullanıcı hangi sekmede olursa olsun yanlışlıkla çıkmayı önlemek için çift tıklama istenir
    BackHandler(enabled = true) {
        if (isTvDevice && !tvRailHasFocus) {
            // TV: Geri tuşu önce içerikten sol menüye döner
            try {
                tvRailFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@BackHandler
        }
        if (selectedTab != HomeTab.DASHBOARD) {
            // TV ve Mobil/Tablet: Seçili sekme Dashboard değilse Dashboard'a dön
            viewModel.setSelectedHomeTab(HomeTab.DASHBOARD)
            return@BackHandler
        }
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

    if (isTvDevice) {
        // ANDROID TV: Mobil arayüzün tüm sekmeleri (Ana Sayfa, Canlı TV, Filmler, Diziler, Favoriler, Ayarlar)
        // kumanda ile gezilebilen sol menü ile gösterilir. Canlı TV sekmesi TiviMate rehber düzenini kullanır.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(VegaColors.Background)
        ) {
            // Canlı TV tam ekran oynatılırken menü gizlenir
            if (!(selectedTab == HomeTab.LIVE_TV && tvLiveFullScreen)) {
                TvNavigationRail(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        if (tab != selectedTab) {
                            tvLiveFullScreen = false
                            viewModel.setVideoFullscreen(false)
                            viewModel.setSelectedHomeTab(tab)
                        }
                    },
                    onSearchClick = { viewModel.navigateTo(Screen.Search) },
                    selectedTabFocusRequester = tvRailFocusRequester,
                    onRailFocusChanged = { tvRailHasFocus = it },
                    activeCons = activeCons,
                    maxCons = maxCons
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (selectedTab == HomeTab.LIVE_TV) {
                    TvTiviMateScreen(
                        viewModel = viewModel,
                        onFullScreenChange = {
                            tvLiveFullScreen = it
                            viewModel.setVideoFullscreen(it)
                        }
                    )
                } else {
                    HomeContentBody(selectedTab = selectedTab, viewModel = viewModel)
                }
            }
        }
    } else Scaffold(
        topBar = {
            HomeTopBar(
                appName = AppConfig.APP_NAME,
                expDateFormatted = expDateFormatted,
                activeCons = activeCons,
                maxCons = maxCons,
                showSettings = true,
                isSyncing = playlistSyncState.isSyncing,
                syncPercentage = playlistSyncState.overallPercentage,
                onLogoClick = { viewModel.setSelectedHomeTab(HomeTab.DASHBOARD) },
                onSettingsClick = {
                    if (selectedTab == HomeTab.SETTINGS) {
                        viewModel.setSelectedHomeTab(HomeTab.DASHBOARD)
                    } else {
                        viewModel.setSelectedHomeTab(HomeTab.SETTINGS)
                    }
                },
                onSyncClick = { showSyncConfirmationDialog = true },
                onSearchClick = { viewModel.navigateTo(Screen.Search) }
            )
        },
        bottomBar = {
            if (!isWideScreen) {
                VegaBottomNavigationBar(
                    activeTab = selectedTab,
                    onTabSelected = { tab ->
                        if (tab == HomeTab.LIVE_TV && selectedTab == HomeTab.LIVE_TV) {
                            viewModel.triggerLiveScrollToTop()
                        } else {
                            viewModel.setSelectedHomeTab(tab)
                        }
                    }
                )
            }
        },
        containerColor = VegaColors.Background
    ) { innerPadding ->
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                HomeNavigationRail(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.setSelectedHomeTab(it) },
                    activeCons = activeCons,
                    maxCons = maxCons
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    HomeContentBody(selectedTab = selectedTab, viewModel = viewModel)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                HomeContentBody(selectedTab = selectedTab, viewModel = viewModel)
            }
        }
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
            onDismissRequest = { viewModel.snoozeUpdate(info, hours = 24) },
            containerColor = VegaColors.Surface,
            shape = VegaShapes.Large,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🚀 Yeni Güncelleme Mevcut (${info.tagName})",
                        color = VegaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Vega IPTV'nin en son sürümü yayınlandı. Şimdi güncelleyebilir veya daha sonra güncellemek üzere erteleyebilirsin:",
                        color = VegaColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = VegaShapes.Small,
                        color = VegaColors.Card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
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
                                color = VegaColors.TextPrimary,
                                fontSize = 13.sp,
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
                                    color = VegaColors.Accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "%$downloadProgress",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (downloadProgress!! / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(VegaShapes.Small),
                                color = VegaColors.Accent,
                                trackColor = VegaColors.Border
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (downloadProgress == null) {
                    Button(
                        onClick = { viewModel.downloadAndInstallUpdate(info) },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .focusRequester(updateFocusRequester)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = Color.White,
                                onClick = { viewModel.downloadAndInstallUpdate(info) }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = VegaColors.OnAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Şimdi İndir ve Kur", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (downloadProgress == null) {
                    OutlinedButton(
                        onClick = { viewModel.snoozeUpdate(info, hours = 24) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = VegaShapes.Medium,
                        modifier = Modifier.dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            onClick = { viewModel.snoozeUpdate(info, hours = 24) }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Daha Sonra", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        )
    }

    // İÇERİK SENKRONİZASYON ONAY DİYALOĞU
    if (showSyncConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showSyncConfirmationDialog = false },
            containerColor = VegaColors.Surface,
            shape = VegaShapes.Large,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = VegaColors.Accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "İçerik Senkronizasyonu", color = VegaColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Tüm canlı kanallar, filmler, diziler ve EPG rehberi sunucudan güncellenecektir. Devam etmek istiyor musun?",
                    color = VegaColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSyncConfirmationDialog = false
                        viewModel.openPlaylistSync()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                    shape = VegaShapes.Medium
                ) {
                    Text(text = "Güncelle", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncConfirmationDialog = false }) {
                    Text(text = "İptal", color = VegaColors.TextSecondary)
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
    onLogoClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onSyncClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    Surface(
        color = VegaColors.Background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sol: Uygulama logosu (32.dp) ve yanında "Vega IPTV" başlığı
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(VegaShapes.Small)
                    .clickable { onLogoClick() }
                    .dpadFocusable(
                        shape = VegaShapes.Small,
                        focusedBorderColor = VegaColors.Accent,
                        onClick = onLogoClick
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(VegaShapes.Small)
                        .background(VegaColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = VegaColors.OnAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = appName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegaColors.TextPrimary,
                    maxLines = 1
                )
            }

            // Orta: Arama Butonu/Çubuğu
            Surface(
                shape = VegaShapes.Pill,
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .padding(horizontal = 10.dp)
                    .dpadFocusable(
                        shape = VegaShapes.Pill,
                        focusedBorderColor = VegaColors.Accent,
                        onClick = onSearchClick
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onSearchClick() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Arama",
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "İçerik ara...",
                        color = VegaColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Sağ: Senkronizasyon (aktifse), Yenile ve Ayarlar butonları
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSyncing) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .height(40.dp)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.Accent,
                                onClick = onSyncClick
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { onSyncClick() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = VegaColors.Accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "%$syncPercentage",
                                color = VegaColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Yenile Butonu
                Surface(
                    shape = VegaShapes.Medium,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, VegaColors.Border),
                    onClick = onSyncClick,
                    modifier = Modifier
                        .size(40.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            onClick = onSyncClick
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Tüm İçeriği Senkronize Et",
                            tint = VegaColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Ayarlar Butonu
                if (showSettings) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, VegaColors.Border),
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.Accent,
                                onClick = onSettingsClick
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ayarlar",
                                tint = VegaColors.TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VegaBottomNavigationBar(
    activeTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit
) {
    val bottomTabs = listOf(
        HomeTab.DASHBOARD,
        HomeTab.LIVE_TV,
        HomeTab.MOVIES,
        HomeTab.SERIES,
        HomeTab.FAVORITES
    )

    Surface(
        color = VegaColors.Background,
        border = BorderStroke(0.5.dp, VegaColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { tab ->
                val isSelected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(tab) }
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            focusedBorderColor = VegaColors.Accent,
                            onClick = { onTabSelected(tab) }
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isSelected) {
                        // Seçili sekmenin üst kenarına çizgi
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(0.65f)
                                .height(2.5.dp)
                                .background(
                                    color = VegaColors.Accent,
                                    shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                                )
                        )
                        // Arkasında yumuşak vurgu parlama efekti
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            VegaColors.Accent.copy(alpha = 0.15f),
                                            VegaColors.Accent.copy(alpha = 0.03f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 5.dp, bottom = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) VegaColors.TextPrimary else VegaColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeNavigationRail(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    activeCons: String,
    maxCons: String
) {
    val tabs = listOf(
        HomeTab.DASHBOARD,
        HomeTab.LIVE_TV,
        HomeTab.MOVIES,
        HomeTab.SERIES,
        HomeTab.FAVORITES
    )

    Surface(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight(),
        color = VegaColors.Background,
        border = BorderStroke(0.5.dp, VegaColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(VegaShapes.Medium)
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(
                                        listOf(
                                            VegaColors.Accent.copy(alpha = 0.20f),
                                            VegaColors.Accent.copy(alpha = 0.05f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                }
                            )
                            .clickable { onTabSelected(tab) }
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.Accent,
                                onClick = { onTabSelected(tab) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(
                                        color = VegaColors.Accent,
                                        shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                                    )
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) VegaColors.TextPrimary else VegaColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Text(
                text = "$activeCons/$maxCons",
                color = VegaColors.Success,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
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
            .background(VegaColors.SurfaceVariant.copy(alpha = 0.4f))
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
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kaldığın Yerden Devam Et",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegaColors.TextPrimary
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
                        .background(VegaColors.Live)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Son İzlenen Canlı Kanallar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegaColors.TextPrimary
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
        shape = VegaShapes.Medium,
        color = VegaColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
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
                        .clip(VegaShapes.Small)
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
                            tint = VegaColors.Accent,
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
                        color = VegaColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val mins = progress.positionMs / 60000
                    Text(
                        text = "$mins. dakikada kaldı",
                        color = VegaColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // İlerleme Çubuğu (Progress Bar)
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = VegaColors.Accent,
                trackColor = VegaColors.Border
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
        shape = VegaShapes.Medium,
        color = VegaColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
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
                    .clip(VegaShapes.Small)
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
                    color = VegaColors.TextPrimary,
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
                            .background(VegaColors.Live)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Canlı",
                        color = VegaColors.Live,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
