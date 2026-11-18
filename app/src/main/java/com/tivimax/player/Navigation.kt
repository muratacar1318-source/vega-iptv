package com.tivimax.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.devicemode.DeviceModeSetupScreen
import com.tivimax.player.ui.login.LoginScreen
import com.tivimax.player.ui.main.HomeScreen
import com.tivimax.player.ui.player.PlayerScreen
import com.tivimax.player.ui.search.UniversalSearchScreen
import com.tivimax.player.ui.series.SeriesDetailScreen
import com.tivimax.player.ui.sync.PlaylistSyncScreen
import com.tivimax.player.ui.vod.VodDetailScreen

@Composable
fun AppNavigation(viewModel: MainViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(currentScreen) {
        if (currentScreen is Screen.Login || currentScreen is Screen.PlaylistSync) {
            stateHolder.removeState("home")
        }
    }

    val screenKey = when (val screen = currentScreen) {
        is Screen.Splash -> "splash"
        is Screen.DeviceModeSetup -> "device_mode_setup"
        is Screen.Login -> "login"
        is Screen.PlaylistSync -> "playlist_sync"
        is Screen.Home -> "home"
        is Screen.Player -> "player_${screen.streamId}_${screen.isLive}"
        is Screen.VodDetail -> "vod_detail_${screen.vodStream.streamId}"
        is Screen.SeriesDetail -> "series_detail_${screen.seriesStream.seriesId}"
        is Screen.Search -> "search"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        stateHolder.SaveableStateProvider(key = screenKey) {
            when (val screen = currentScreen) {
                is Screen.Splash -> {
                    SplashScreen()
                }
                is Screen.DeviceModeSetup -> {
                    DeviceModeSetupScreen(viewModel = viewModel)
                }
                is Screen.Login -> {
                    LoginScreen(viewModel = viewModel)
                }
                is Screen.PlaylistSync -> {
                    PlaylistSyncScreen(viewModel = viewModel)
                }
                is Screen.Home -> {
                    HomeScreen(viewModel = viewModel)
                }
                is Screen.Player -> {
                    PlayerScreen(
                        streamUrl = screen.streamUrl,
                        title = screen.title,
                        isLive = screen.isLive,
                        streamId = screen.streamId,
                        categoryId = screen.categoryId,
                        viewModel = viewModel,
                        onBack = {
                            if (!viewModel.navigateBack()) {
                                viewModel.navigateTo(Screen.Home, false)
                            }
                        }
                    )
                }
                is Screen.VodDetail -> {
                    VodDetailScreen(
                        movie = screen.vodStream,
                        viewModel = viewModel,
                        onBack = {
                            if (!viewModel.navigateBack()) {
                                viewModel.navigateTo(Screen.Home, false)
                            }
                        }
                    )
                }
                is Screen.SeriesDetail -> {
                    SeriesDetailScreen(
                        series = screen.seriesStream,
                        viewModel = viewModel,
                        onBack = {
                            if (!viewModel.navigateBack()) {
                                viewModel.navigateTo(Screen.Home, false)
                            }
                        }
                    )
                }
                is Screen.Search -> {
                    UniversalSearchScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(TiviPrimary, TiviSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Logo",
                    tint = TiviBackground,
                    modifier = Modifier.size(50.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = AppConfig.APP_NAME,
                color = TiviTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = AppConfig.APP_TAGLINE,
                color = TiviTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = TiviPrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
