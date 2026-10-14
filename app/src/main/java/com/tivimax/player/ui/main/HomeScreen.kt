package com.tivimax.player.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
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

    // Çıkış Önleme: Kullanıcı hangi sekmede olursa olsun o sekmede kalır, yanlışlıkla çıkmayı önlemek için çift tıklama istenir
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

    Scaffold(
        topBar = {
            Surface(
                color = TiviSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(50.dp) // %22 daha kompakt üst bar (64dp yerine 50dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Logo ve Başlık
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(TiviPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = TiviBackground,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppConfig.APP_NAME,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TiviTextPrimary
                        )
                    }

                    // Sağ Üst Alan: Bitiş Tarihi, Bağlantı Rozeti ve Ayarlar Butonu
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

                        // Ayarlar Butonu (Sağ Üst)
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
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSettingsActive) TiviPrimary.copy(alpha = 0.2f) else TiviCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ayarlar",
                                tint = if (isSettingsActive) TiviPrimary else TiviTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
                        .height(54.dp) // %30 daha kompakt alt bar (80dp yerine 54dp)
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
