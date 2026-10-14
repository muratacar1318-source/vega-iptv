package com.tivimax.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.login.LoginScreen
import com.tivimax.player.ui.main.HomeScreen
import com.tivimax.player.ui.player.PlayerScreen
import com.tivimax.player.ui.series.SeriesDetailScreen
import com.tivimax.player.ui.vod.VodDetailScreen

@Composable
fun AppNavigation(viewModel: MainViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Crossfade(targetState = currentScreen, modifier = Modifier.fillMaxSize(), label = "ScreenTransition") { screen ->
        when (screen) {
            is Screen.Splash -> {
                SplashScreen()
            }
            is Screen.Login -> {
                LoginScreen(viewModel = viewModel)
            }
            is Screen.Home -> {
                HomeScreen(viewModel = viewModel)
            }
            is Screen.Player -> {
                PlayerScreen(
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
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground),
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
