package com.tivimax.player.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.TiviPlayerManager
import com.tivimax.player.player.TrackInfo
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.player.components.NumericKeypadDialog
import com.tivimax.player.ui.player.components.TiviBottomInfoCard
import com.tivimax.player.ui.player.components.TiviNumericZapperBox
import com.tivimax.player.ui.player.components.TiviOverlayGuide
import com.tivimax.player.ui.player.components.TiviRecentChannelsBar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    streamUrl: String = "",
    title: String,
    isLive: Boolean,
    streamId: Int,
    categoryId: String = "",
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val playerManager = viewModel.playerManager

    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val bufferedPosition by playerManager.bufferedPosition.collectAsState()
    val errorMessage by playerManager.errorMessage.collectAsState()
    val aspectRatio by playerManager.aspectRatio.collectAsState()
    val audioTracks by playerManager.audioTracks.collectAsState()
    val subtitleTracks by playerManager.subtitleTracks.collectAsState()

    val streamDiagnostics by playerManager.streamDiagnostics.collectAsState()
    val sleepTimerSeconds by playerManager.sleepTimerSeconds.collectAsState()
    val audioOffsetMs by playerManager.audioOffsetMs.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()

    val isDualViewActive by playerManager.isDualViewActive.collectAsState()
    val isPrimaryAudioActive by playerManager.isPrimaryAudioActive.collectAsState()
    val secondaryChannelTitle by playerManager.secondaryChannelTitle.collectAsState()
    val secondaryIsBuffering by playerManager.secondaryIsBuffering.collectAsState()
    val isScreensSwapped by playerManager.isScreensSwapped.collectAsState()
    var showDualViewPicker by remember { mutableStateOf(false) }

    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf(false) }
    var showDiagnosticsHUD by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioBrightnessHUD by remember { mutableStateOf(false) }
    var showOverlayGuide by remember { mutableStateOf(false) }
    var showRecentChannelsBar by remember { mutableStateOf(false) }
    var showNumericKeypad by remember { mutableStateOf(false) }
    var showCategoryManagementDialog by remember { mutableStateOf(false) }
    var showBottomInfoCard by remember { mutableStateOf(true) }

    val isZapperActive by viewModel.isZapperActive.collectAsState()
    val zapperNumber by viewModel.zapperNumber.collectAsState()
    val zapperMatchedChannel by viewModel.zapperMatchedChannel.collectAsState()
    val recentLiveChannels by viewModel.recentChannels.collectAsState()

    // Dokunmatik Hareket Geri Bildirimleri (Parlaklık & Ses)
    var brightnessFeedback by remember { mutableStateOf<Int?>(null) }
    var volumeFeedback by remember { mutableStateOf<Int?>(null) }
    var seekRipple by remember { mutableStateOf<String?>(null) }
    var dragStartOffset by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    LaunchedEffect(brightnessFeedback) {
        if (brightnessFeedback != null) {
            delay(1500)
            brightnessFeedback = null
        }
    }

    LaunchedEffect(volumeFeedback) {
        if (volumeFeedback != null) {
            delay(1500)
            volumeFeedback = null
        }
    }

    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()
    val isFavorite = favoriteLiveIds.contains(streamId.toString())
    val epgList by viewModel.currentEpgList.collectAsState()
    val allLiveStreams by viewModel.liveStreams.collectAsState()

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var currentBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }) }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isUserInteracting = isScrubbing || showChannelDrawer || showTrackSheet || showSleepTimerDialog || showDiagnosticsHUD || showAudioBrightnessHUD || showOverlayGuide || showRecentChannelsBar || showNumericKeypad || showCategoryManagementDialog || isZapperActive || (dragStartOffset != null)

    val registerInteraction: () -> Unit = remember {
        { lastInteractionTime = System.currentTimeMillis() }
    }

    // TiviMate İmzası: Kanal değiştiğinde alt bilgi paneli 5 saniye görünür ve otomatik kaybolur
    LaunchedEffect(streamId) {
        if (isLive) {
            showBottomInfoCard = true
            delay(5000L)
            if (!isUserInteracting) {
                showBottomInfoCard = false
            }
        }
    }

    LaunchedEffect(showBottomInfoCard, isPlaying, lastInteractionTime, isUserInteracting) {
        if (showBottomInfoCard && isLive && isPlaying && !isUserInteracting && !showControls) {
            delay(5000L)
            if (!isUserInteracting && !showControls) {
                showBottomInfoCard = false
            }
        }
    }

    // Akıllı HUD Zamanlayıcısı (5000ms - Kullanıcı işlem yaparken ASLA kapanmaz)
    LaunchedEffect(showControls, isPlaying, lastInteractionTime, isUserInteracting, isControlsLocked) {
        if (showControls && isPlaying && !isUserInteracting) {
            val timeout = if (isControlsLocked) 3000L else 5000L
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                val remaining = timeout - elapsed
                if (remaining <= 0) {
                    if (!isUserInteracting) {
                        showControls = false
                    }
                    break
                }
                delay(remaining.coerceAtLeast(100L))
            }
        }
    }

    val isInPipMode by viewModel.isInPipMode.collectAsState()
    val autoPipEnabled by viewModel.autoPipEnabled.collectAsState()

    // Altyazı Tercihleri (Boyut & Renk)
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()

    val currentVodDetail by viewModel.currentVodDetail.collectAsState()
    val currentSeriesInfo by viewModel.currentSeriesInfo.collectAsState()

    val thumbnailImage = remember(currentVodDetail, currentSeriesInfo) {
        currentVodDetail?.info?.movieImage
            ?: currentVodDetail?.info?.coverBig
            ?: currentVodDetail?.movieData?.streamIcon
            ?: currentSeriesInfo?.info?.cover
            ?: currentSeriesInfo?.info?.backdropPath?.firstOrNull()
    }

    val currentEpisode by viewModel.currentPlayingEpisode.collectAsState()
    val lastPlayedLiveChannel by viewModel.lastPlayedLiveChannel.collectAsState()
    val nextEpisode = remember(currentEpisode) { viewModel.getNextEpisode() }
    var autoNextDismissed by remember(currentEpisode) { mutableStateOf(false) }

    // Bölüm bittiğinde otomatik sonraki bölüme geçiş (Recomposition döngüsünü önleyen periyodik kontrol)
    LaunchedEffect(streamId, currentEpisode) {
        if (!isLive && currentEpisode != null) {
            while (true) {
                delay(1000L)
                val pos = playerManager.currentPosition.value
                val dur = playerManager.duration.value
                if (dur > 30_000L && pos >= dur - 1500L && dur > 0L) {
                    viewModel.playNextEpisode()
                    break
                }
            }
        }
    }

    var resumeNotification by remember { mutableStateOf<String?>(null) }

    // Kaldığı yerden başlama bildirimi
    LaunchedEffect(streamId) {
        if (!isLive) {
            delay(1000)
            val pos = playerManager.currentPosition.value
            if (pos > 10_000L) {
                val mins = pos / 60000
                val secs = (pos % 60000) / 1000
                resumeNotification = "Kaldığın Yerden Devam Ediliyor (${String.format("%02d:%02d", mins, secs)})"
                delay(3500)
                resumeNotification = null
            }
        }
    }

    // Periyodik İlerleme Otomatik Kaydı (15 saniyede bir arka planda kaydeder - UI State bozmaz)
    LaunchedEffect(streamId, isLive) {
        if (!isLive) {
            while (true) {
                delay(15000L)
                val pos = playerManager.currentPosition.value
                val dur = playerManager.duration.value
                if (playerManager.isPlaying.value && pos > 0L) {
                    viewModel.saveWatchProgress(streamId, pos, dur)
                }
            }
        }
    }

    // Ekrandan çıkıldığında veya arkaya atıldığında son konumu kaydet
    DisposableEffect(streamId) {
        onDispose {
            if (!isLive && playerManager.currentPosition.value > 0L) {
                viewModel.saveWatchProgress(streamId, playerManager.currentPosition.value, playerManager.duration.value)
            }
        }
    }

    // Oynatıcı başlatılmamışsa veya boşsa akışı başlat
    LaunchedEffect(streamUrl, streamId) {
        if (playerManager.player == null || playerManager.player?.currentMediaItem == null) {
            if (streamUrl.isNotBlank()) {
                playerManager.play(streamUrl, isLive = isLive)
            }
        }
    }

    // Video izlerken ekranın kapanmasını önle (KEEP_SCREEN_ON)
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!isLive) {
                playerManager.stopAndClear()
            }
        }
    }

    // Android 12+ (API 31+) için Otomatik PiP Kaydı (Sistem Seviyesi Akıcı Animasyon)
    DisposableEffect(isPlaying, autoPipEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            try {
                val hasPip = activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                if (hasPip) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(autoPipEnabled && isPlaying)
                        .build()
                    activity.setPictureInPictureParams(params)
                }
            } catch (e: Throwable) {
                // Ignore unsupported devices / ROMs
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
                try {
                    val hasPip = activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    if (hasPip) {
                        val params = PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(false)
                            .build()
                        activity.setPictureInPictureParams(params)
                    }
                } catch (e: Throwable) {
                    // Ignore
                }
            }
        }
    }

    // Geri Tuşu Davranışı
    BackHandler {
        if (showOverlayGuide) {
            showOverlayGuide = false
        } else if (showRecentChannelsBar) {
            showRecentChannelsBar = false
        } else if (showNumericKeypad) {
            showNumericKeypad = false
        } else if (showCategoryManagementDialog) {
            showCategoryManagementDialog = false
        } else if (isZapperActive) {
            viewModel.dismissZapper()
        } else if (showDualViewPicker) {
            showDualViewPicker = false
        } else if (showChannelDrawer) {
            showChannelDrawer = false
        } else if (isDualViewActive) {
            playerManager.closeDualView()
        } else {
            if (!isLive && currentPosition > 0) {
                viewModel.saveWatchProgress(streamId, currentPosition, duration)
            }
            playerManager.release()
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isControlsLocked) {
                detectTapGestures(
                    onTap = {
                        registerInteraction()
                        if (isControlsLocked) {
                            showControls = !showControls
                        } else {
                            if (showOverlayGuide) {
                                showOverlayGuide = false
                            } else if (showRecentChannelsBar) {
                                showRecentChannelsBar = false
                            } else {
                                showControls = !showControls
                                showChannelDrawer = false
                                if (isLive) {
                                    showBottomInfoCard = true
                                }
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        registerInteraction()
                        if (!isControlsLocked) {
                            if (!isLive) {
                                if (offset.x < size.width * 0.35f) {
                                    // Sol tarafa çift tık: 10 saniye geri (Sadece Film/Dizi)
                                    playerManager.rewind10Seconds()
                                    seekRipple = "-10 saniye"
                                } else if (offset.x > size.width * 0.65f) {
                                    // Sağ tarafa çift tık: 10 saniye ileri (Sadece Film/Dizi)
                                    playerManager.forward10Seconds()
                                    seekRipple = "+10 saniye"
                                } else {
                                    // Ortaya çift tık: En-Boy Oranı değiştir
                                    val newMode = playerManager.cycleAspectRatio()
                                    seekRipple = when (newMode) {
                                        AspectRatioMode.FIT -> "En-Boy: Orijinal (FIT)"
                                        AspectRatioMode.FILL -> "En-Boy: Doldur (FILL)"
                                        AspectRatioMode.ZOOM -> "En-Boy: Yakınlaştır (ZOOM)"
                                        AspectRatioMode.RATIO_16_9 -> "En-Boy: 16:9"
                                    }
                                }
                            } else {
                                // Canlı TV'de en-boy oranı döngüsü
                                val newMode = playerManager.cycleAspectRatio()
                                seekRipple = when (newMode) {
                                    AspectRatioMode.FIT -> "En-Boy: Orijinal (FIT)"
                                    AspectRatioMode.FILL -> "En-Boy: Doldur (FILL)"
                                    AspectRatioMode.ZOOM -> "En-Boy: Yakınlaştır (ZOOM)"
                                    AspectRatioMode.RATIO_16_9 -> "En-Boy: 16:9"
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(isControlsLocked) {
                if (!isControlsLocked) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            registerInteraction()
                            dragStartOffset = offset
                            currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        },
                        onDragEnd = {
                            registerInteraction()
                            dragStartOffset = null
                        },
                        onDragCancel = {
                            registerInteraction()
                            dragStartOffset = null
                        },
                        onDrag = { change, dragAmount ->
                            registerInteraction()
                            change.consume()
                            val start = dragStartOffset ?: return@detectDragGestures
                            val isLeft = start.x < size.width * 0.45f
                            val isRight = start.x > size.width * 0.55f
                            val delta = -dragAmount.y / size.height

                            if (isLeft) {
                                val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                currentBrightness = newBrightness
                                activity?.window?.attributes = activity?.window?.attributes?.apply {
                                    screenBrightness = newBrightness
                                }
                                brightnessFeedback = (newBrightness * 100).toInt()
                            } else if (isRight) {
                                val volumeDelta = delta * maxVolume * 1.5f
                                val newVolFloat = (currentVolume + volumeDelta).coerceIn(0f, maxVolume.toFloat())
                                val newVol = newVolFloat.toInt()
                                if (newVol != currentVolume) {
                                    currentVolume = newVol
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                }
                                volumeFeedback = ((newVolFloat / maxVolume) * 100).toInt()
                            }
                        }
                    )
                }
            }
    ) {
        if (!isDualViewActive) {
            // Media3 ExoPlayer Video Render View (İzole Edilmiş Yüzey - HUD/Zaman recomposition'larından asla etkilenmez)
            VideoPlayerSurface(
                playerManager = playerManager,
                isLive = isLive,
                aspectRatio = aspectRatio,
                subtitleColor = subtitleColor,
                subtitleSize = subtitleSize,
                modifier = Modifier.fillMaxSize()
            )

            // Buffering Indicator
            if (isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TiviPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        } else {
            DualViewSplitScreen(
                playerManager = playerManager,
                isPortrait = isPortrait,
                primaryTitle = title,
                secondaryTitle = secondaryChannelTitle,
                isPrimaryAudioActive = isPrimaryAudioActive,
                isScreensSwapped = isScreensSwapped,
                isPrimaryBuffering = isBuffering,
                isSecondaryBuffering = secondaryIsBuffering,
                aspectRatio = aspectRatio,
                onSwap = { playerManager.swapDualViewScreens() },
                onClose = { playerManager.closeDualView() },
                onChangeSecondary = { showDualViewPicker = true }
            )
        }

        // Hata Bildirimi Overlay
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = TiviAccentLive,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { playerManager.retryCurrent() },
                            colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary, contentColor = TiviBackground),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tekrar Dene")
                        }
                        OutlinedButton(
                            onClick = { playerManager.retryNextFallback() },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary)
                        ) {
                            Text("Alternatif Akış Dene", color = TiviPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onBack) {
                        Text("Listeye Geri Dön", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }
        }

        // İnce Yarı Saydam Dikey Parlaklık Çubuğu (Sol Kenar)
        AnimatedVisibility(
            visible = brightnessFeedback != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            brightnessFeedback?.let { pct ->
                val fraction = (pct / 100f).coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = TiviOverlayDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .width(42.dp)
                        .height(180.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = null,
                            tint = TiviGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(105.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .background(TiviGold)
                            )
                        }
                        Text(
                            text = "%$pct",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // İnce Yarı Saydam Dikey Ses Seviye Çubuğu (Sağ Kenar)
        AnimatedVisibility(
            visible = volumeFeedback != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            volumeFeedback?.let { pct ->
                val fraction = (pct / 100f).coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = TiviOverlayDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .width(42.dp)
                        .height(180.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (pct == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = TiviPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(105.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .background(TiviPrimary)
                            )
                        }
                        Text(
                            text = "%$pct",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Animasyonlu Bilgi Etiketi (1.5s Pill Badge: +10s, -10s, En-Boy Oranı)
        AnimatedVisibility(
            visible = seekRipple != null,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            seekRipple?.let { text ->
                LaunchedEffect(text) {
                    delay(1500)
                    seekRipple = null
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = TiviOverlayDark,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, TiviPrimary.copy(alpha = 0.7f)),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            text.contains("-10") -> Icons.Default.Replay10
                            text.contains("+10") -> Icons.Default.Forward10
                            else -> Icons.Default.AspectRatio
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = TiviPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Kaldığın Yerden Devam Ediliyor Bildirimi
        AnimatedVisibility(
            visible = resumeNotification != null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            resumeNotification?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = TiviOverlayDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviGold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TiviGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // CONTROLS OVERLAY (Üst Bar, Merkez Butonlar, Alt Bar)
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            if (isControlsLocked) {
                // Dokunma kilidi aktifken ekrana dokunulduğunda yalnızca köşede kilidi aç butonu görünür
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Surface(
                        onClick = { isControlsLocked = false },
                        shape = CircleShape,
                        color = TiviOverlayDark,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, TiviPrimary),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Dokunma Kilidini Aç",
                                tint = TiviPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = if (isPortrait) 0.65f else 0.8f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = if (isPortrait) 0.75f else 0.9f)
                                )
                            )
                        )
                ) {
                // TOP BAR: Geri, Başlık, Saat, Kilit (Kompakt Yükseklik)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            horizontal = if (isPortrait) 10.dp else 16.dp,
                            vertical = if (isPortrait) 2.dp else 6.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (!isLive && currentPosition > 0) {
                                    viewModel.saveWatchProgress(streamId, currentPosition, duration)
                                }
                                playerManager.release()
                                onBack()
                            },
                            modifier = Modifier.size(if (isPortrait) 34.dp else 40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = Color.White,
                                modifier = Modifier.size(if (isPortrait) 20.dp else 24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(if (isPortrait) 4.dp else 6.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TiviAccentLive)
                                            .padding(
                                                horizontal = if (isPortrait) 4.dp else 6.dp,
                                                vertical = if (isPortrait) 1.dp else 2.dp
                                            )
                                    ) {
                                        Text(
                                            text = "CANLI",
                                            color = Color.White,
                                            fontSize = if (isPortrait) 8.sp else 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(if (isPortrait) 6.dp else 8.dp))
                                }
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = if (isPortrait) 13.sp else 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isLive && lastPlayedLiveChannel != null && lastPlayedLiveChannel?.streamId != streamId) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        onClick = { viewModel.zapRecall() },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, TiviGold.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = "Önceki Kanal",
                                                tint = TiviGold,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Son: ${lastPlayedLiveChannel?.name}",
                                                color = TiviGold,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 2.dp else 4.dp)
                    ) {
                        // Canlı yayında TiviMate Rehber Katmanı & Çift Ekran Butonları (Yatayda gösterilir)
                        if (isLive && !isPortrait) {
                            IconButton(
                                onClick = {
                                    showOverlayGuide = !showOverlayGuide
                                    showChannelDrawer = false
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "Rehber / Kanal Listesi",
                                    tint = if (showOverlayGuide) OpusCyan else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Çift Ekran (Dual-View / Multi-Screen)
                            IconButton(
                                onClick = { showDualViewPicker = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Çift Ekran (Dual-View)",
                                    tint = if (isDualViewActive) TiviPrimary else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Ses & Parlaklık (HUD) Butonu
                            IconButton(
                                onClick = {
                                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                                    showAudioBrightnessHUD = !showAudioBrightnessHUD
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Ses & Parlaklık (HUD)",
                                    tint = if (showAudioBrightnessHUD) TiviPrimary else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Uyku Zamanlayıcısı Kalan Süre Rozeti
                        if (sleepTimerSeconds != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TiviSecondary.copy(alpha = 0.4f))
                                    .padding(horizontal = if (isPortrait) 5.dp else 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🌙 ${sleepTimerSeconds!! / 60}:${String.format("%02d", sleepTimerSeconds!! % 60)}",
                                    color = Color.White,
                                    fontSize = if (isPortrait) 9.sp else 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Uyku Zamanlayıcı Butonu
                        IconButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = "Uyku Zamanlayıcı",
                                tint = if (sleepTimerSeconds != null) TiviSecondary else Color.White,
                                modifier = Modifier.size(if (isPortrait) 18.dp else 24.dp)
                            )
                        }

                        // Yayın Bilgi Paneli (Stream Diagnostics HUD)
                        IconButton(
                            onClick = {
                                playerManager.updateDiagnostics()
                                showDiagnosticsHUD = !showDiagnosticsHUD
                            },
                            modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Yayın Bilgisi (HUD)",
                                tint = if (showDiagnosticsHUD) TiviPrimary else Color.White,
                                modifier = Modifier.size(if (isPortrait) 18.dp else 24.dp)
                            )
                        }

                        // Resim İçinde Resim (PiP)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                            IconButton(
                                onClick = {
                                    try {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity.enterPictureInPictureMode(params)
                                    } catch (e: Throwable) {
                                        // Ignore
                                    }
                                },
                                modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Küçük Pencere (PiP)",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isPortrait) 18.dp else 24.dp)
                                )
                            }
                        }

                        // Ekran Kilidi
                        IconButton(
                            onClick = { isControlsLocked = !isControlsLocked },
                            modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                        ) {
                            Icon(
                                imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Ekran Kilidi",
                                tint = if (isControlsLocked) TiviAccentLive else Color.White,
                                modifier = Modifier.size(if (isPortrait) 18.dp else 24.dp)
                            )
                        }


                    }
                }

                // CENTER PLAY/PAUSE/SEEK/ZAP BUTTONS
                if (!isControlsLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(36.dp)
                    ) {
                        if (isLive) {
                            // Önceki Kanal (Zapping)
                            IconButton(
                                onClick = {
                                    val ch = viewModel.zapPreviousChannel(streamId)
                                    seekRipple = "⏮ ${ch?.name ?: "Önceki Kanal"}"
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Önceki Kanal",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else {
                            // 10s Geri
                            IconButton(
                                onClick = {
                                    playerManager.rewind10Seconds()
                                    seekRipple = "-10s"
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "10s Geri",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Oynat / Duraklat
                        IconButton(
                            onClick = { playerManager.togglePlayPause() },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(TiviPrimary)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Oynat/Duraklat",
                                tint = TiviBackground,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        if (isLive) {
                            // Sonraki Kanal (Zapping)
                            IconButton(
                                onClick = {
                                    val ch = viewModel.zapNextChannel(streamId)
                                    seekRipple = "Sonraki: ${ch?.name ?: "Kanal"} ⏭"
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Sonraki Kanal",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        } else {
                            // 10s İleri
                            IconButton(
                                onClick = {
                                    playerManager.forward10Seconds()
                                    seekRipple = "+10s"
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "10s İleri",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Dizi izleniyorsa ve sonraki bölüm varsa: Sonraki Bölüm Butonu
                            if (nextEpisode != null) {
                                IconButton(
                                    onClick = {
                                        viewModel.playNextEpisode()
                                        seekRipple = "Sonraki Bölüm ⏭"
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(TiviAccentLive.copy(alpha = 0.25f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Sonraki Bölüm",
                                        tint = TiviAccentLive,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // BOTTOM CONTROLS & OSD BAR
                if (!isControlsLocked) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                horizontal = if (isPortrait) 8.dp else 16.dp,
                                vertical = if (isPortrait) 4.dp else 6.dp
                            )
                    ) {
                        if (isLive) {
                            val currentChannel = allLiveStreams.find { it.streamId == streamId }
                                ?: LiveStream(streamId = streamId, name = title, categoryId = categoryId)

                            // TIVIMATE 2: GERÇEK TEKNİK ROZETLİ ALT BİLGİ PANELİ (BOTTOM INFO OSD)
                            TiviBottomInfoCard(
                                visible = (showControls || showBottomInfoCard) && !showOverlayGuide,
                                channel = currentChannel,
                                epg = epgList.firstOrNull(),
                                isFavorite = isFavorite,
                                diagnostics = streamDiagnostics,
                                onToggleFavorite = { viewModel.toggleFavoriteLive(streamId.toString()) }
                            )

                            // TIVIMATE AKSIYON ÇUBUĞU (KULLANICI DOKUNDUĞUNDA GÖZÜKÜR)
                            if (showControls && !showOverlayGuide) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Rehber (TiviMate Overlay Guide)
                                    Surface(
                                        onClick = {
                                            showOverlayGuide = true
                                            showControls = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = OpusCyan.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, OpusCyan),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Rehber", tint = OpusCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Rehber", color = OpusCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // 2. Son İzlenen Kanallar Şeridi
                                    Surface(
                                        onClick = { showRecentChannelsBar = !showRecentChannelsBar },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (showRecentChannelsBar) OpusCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, if (showRecentChannelsBar) OpusCyan else Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = "Son Kanallar", tint = if (showRecentChannelsBar) OpusCyan else Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Son Kanallar", color = if (showRecentChannelsBar) OpusCyan else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    // 3. Önceki Kanal (Recall)
                                    if (lastPlayedLiveChannel != null && lastPlayedLiveChannel?.streamId != streamId) {
                                        Surface(
                                            onClick = {
                                                val prev = viewModel.zapRecall()
                                                if (prev != null) seekRipple = "↩ ${prev.name}"
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = TiviGold.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, TiviGold),
                                            modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.History, contentDescription = "Önceki Kanal", tint = TiviGold, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("↩ ${lastPlayedLiveChannel?.name ?: "Önceki"}", color = TiviGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }

                                    // 4. Numaratör (123 Keypad)
                                    Surface(
                                        onClick = { showNumericKeypad = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Dialpad, contentDescription = "Numaratör", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("123", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // 5. Kategorileri Düzenle (Pin & Hide)
                                    Surface(
                                        onClick = { showCategoryManagementDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = "Kategorileri Düzenle", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Kategoriler", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    // 6. Çift Ekran (Dual View)
                                    Surface(
                                        onClick = { showDualViewPicker = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDualViewActive) TiviPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, if (isDualViewActive) TiviPrimary else Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.GridView, contentDescription = "Çift Ekran", tint = if (isDualViewActive) TiviPrimary else Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isDualViewActive) "Çift Ekran Açık" else "Çift Ekran", color = if (isDualViewActive) TiviPrimary else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    // 7. Ekran Oranı (Fit, Fill, Zoom, 16:9)
                                    Surface(
                                        onClick = {
                                            val newMode = playerManager.cycleAspectRatio()
                                            seekRipple = when (newMode) {
                                                AspectRatioMode.FIT -> "Orijinal Oran"
                                                AspectRatioMode.FILL -> "Ekranı Doldur"
                                                AspectRatioMode.ZOOM -> "Yakınlaştır"
                                                AspectRatioMode.RATIO_16_9 -> "16:9 Oran"
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AspectRatio, contentDescription = "Oran", tint = TiviPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = when (aspectRatio) {
                                                    AspectRatioMode.FIT -> "Fit"
                                                    AspectRatioMode.FILL -> "Doldur"
                                                    AspectRatioMode.ZOOM -> "Zoom"
                                                    AspectRatioMode.RATIO_16_9 -> "16:9"
                                                },
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // 8. Ses ve Parlaklık (HUD)
                                    Surface(
                                        onClick = {
                                            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                                            showAudioBrightnessHUD = !showAudioBrightnessHUD
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (showAudioBrightnessHUD) TiviPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, if (showAudioBrightnessHUD) TiviPrimary else Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = "Ses ve Parlaklık", tint = if (showAudioBrightnessHUD) TiviPrimary else Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Ses/Işık", color = if (showAudioBrightnessHUD) TiviPrimary else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    // 9. Ses ve Altyazı Dili
                                    Surface(
                                        onClick = { showTrackSheet = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Subtitles, contentDescription = "Ses ve Altyazı", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Altyazı/Ses", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        } else {
                            // VOD (FİLM VE DİZİ) KONTROLLERİ
                            if (duration > 0) {
                                // Sürüklerken Açılan Kompakt Zaman & Küçük Resim Önizleme Balonu
                                AnimatedVisibility(
                                    visible = isScrubbing,
                                    enter = fadeIn(tween(150)) + expandVertically(),
                                    exit = fadeOut(tween(150)) + shrinkVertically()
                                ) {
                                    val scrubMillis = scrubPosition.toLong()
                                    val diffSeconds = (scrubMillis - currentPosition) / 1000L
                                    val diffText = if (diffSeconds >= 0) "+${formatDuration(diffSeconds * 1000)}" else "-${formatDuration(-diffSeconds * 1000)}"

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = TiviOverlayDark,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.7f)),
                                            tonalElevation = 8.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!thumbnailImage.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = thumbnailImage,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(width = 64.dp, height = 38.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.Black)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = formatDuration(scrubMillis),
                                                            color = TiviPrimary,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "/ ${formatDuration(duration)}",
                                                            color = Color.White.copy(alpha = 0.7f),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = "Hedef Süre ($diffText)",
                                                        color = TiviTextSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatDuration(if (isScrubbing) scrubPosition.toLong() else currentPosition),
                                        color = if (isScrubbing) TiviPrimary else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (duration > 0) {
                                            val bufferFraction = (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color.White.copy(alpha = 0.2f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(bufferFraction)
                                                        .fillMaxHeight()
                                                        .background(TiviSecondary.copy(alpha = 0.85f))
                                                )
                                            }
                                        }

                                        Slider(
                                            value = if (isScrubbing) scrubPosition else currentPosition.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                                            onValueChange = {
                                                showControls = true
                                                isScrubbing = true
                                                scrubPosition = it
                                            },
                                            onValueChangeFinished = {
                                                playerManager.seekTo(scrubPosition.toLong())
                                                isScrubbing = false
                                                showControls = true
                                            },
                                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = TiviPrimary,
                                                activeTrackColor = TiviPrimary,
                                                inactiveTrackColor = Color.Transparent
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Text(
                                        text = formatDuration(duration),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isPortrait) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                                horizontalArrangement = if (isPortrait) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Ekran Oranı
                                Surface(
                                    onClick = {
                                        val newMode = playerManager.cycleAspectRatio()
                                        seekRipple = when (newMode) {
                                            AspectRatioMode.FIT -> "Orijinal Oran"
                                            AspectRatioMode.FILL -> "Ekranı Doldur"
                                            AspectRatioMode.ZOOM -> "Yakınlaştır"
                                            AspectRatioMode.RATIO_16_9 -> "16:9 Oran"
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                    modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = if (isPortrait) 7.dp else 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Oran",
                                            tint = TiviPrimary,
                                            modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (aspectRatio) {
                                                AspectRatioMode.FIT -> "Fit"
                                                AspectRatioMode.FILL -> "Doldur"
                                                AspectRatioMode.ZOOM -> "Zoom"
                                                AspectRatioMode.RATIO_16_9 -> "16:9"
                                            },
                                            color = Color.White,
                                            fontSize = if (isPortrait) 10.sp else 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // 2. Oynatma Hızı
                                Surface(
                                    onClick = {
                                        val newSpeed = playerManager.cyclePlaybackSpeed()
                                        seekRipple = "Hız: ${newSpeed}x"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                    modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = if (isPortrait) 7.dp else 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "Hız",
                                            tint = TiviSecondary,
                                            modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${playbackSpeed}x",
                                            color = Color.White,
                                            fontSize = if (isPortrait) 10.sp else 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // 3. Ses ve Parlaklık (HUD)
                                Surface(
                                    onClick = {
                                        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                                        showAudioBrightnessHUD = !showAudioBrightnessHUD
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (showAudioBrightnessHUD) TiviPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (showAudioBrightnessHUD) TiviPrimary else Color.White.copy(alpha = 0.18f)),
                                    modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = if (isPortrait) 7.dp else 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Ses ve Parlaklık",
                                            tint = if (showAudioBrightnessHUD) TiviPrimary else Color.White,
                                            modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Ses/Işık",
                                            color = if (showAudioBrightnessHUD) TiviPrimary else Color.White,
                                            fontSize = if (isPortrait) 10.sp else 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // 4. Sonraki Bölüm (Dizi modunda)
                                if (!isLive && nextEpisode != null) {
                                    Surface(
                                        onClick = { viewModel.playNextEpisode() },
                                        shape = RoundedCornerShape(8.dp),
                                        color = TiviAccentLive.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, TiviAccentLive),
                                        modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = if (isPortrait) 7.dp else 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = "Sonraki Bölüm",
                                                tint = TiviAccentLive,
                                                modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Sonraki Bölüm",
                                                color = Color.White,
                                                fontSize = if (isPortrait) 10.sp else 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // 5. Ses ve Altyazı Dili
                                Surface(
                                    onClick = { showTrackSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                    modifier = Modifier.height(if (isPortrait) 30.dp else 34.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = if (isPortrait) 7.dp else 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = "Ses ve Altyazı",
                                            tint = Color.White,
                                            modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Altyazı/Ses",
                                            color = Color.White,
                                            fontSize = if (isPortrait) 10.sp else 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // HIZLI KANAL DEĞİŞTİRİCİ ÇEKMECESİ (Canlı TV İzlerken Soldan Açılır, %80 Opak Buzlu Cam)
        AnimatedVisibility(
            visible = showChannelDrawer && isLive,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier
                .fillMaxHeight()
                .width(290.dp)
                .align(Alignment.CenterStart)
        ) {
            val liveList by viewModel.liveStreams.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC0F141C)), // %80 opaklık, buzlu cam
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hızlı Kanal Geçişi",
                            color = TiviPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        IconButton(onClick = { showChannelDrawer = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val categories by viewModel.liveCategories.collectAsState()
                    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories, key = { it.categoryId }) { cat ->
                            val isSel = cat.categoryId == selectedCategory
                            Surface(
                                onClick = { viewModel.loadLiveStreams(cat.categoryId) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) TiviPrimary else TiviCard,
                                contentColor = if (isSel) TiviBackground else TiviTextSecondary
                            ) {
                                Text(
                                    text = cat.categoryName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    var drawerLimit by remember(liveList) { mutableIntStateOf(50) }
                    val pagedDrawerList = remember(liveList, drawerLimit) {
                        liveList.take(drawerLimit)
                    }
                    val drawerListState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val totalItems = drawerListState.layoutInfo.totalItemsCount
                            val lastVisible = drawerListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisible >= totalItems - 5 && drawerLimit < liveList.size
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) {
                            drawerLimit += 50
                        }
                    }

                    LaunchedEffect(showChannelDrawer) {
                        if (showChannelDrawer) {
                            val currentIndex = pagedDrawerList.indexOfFirst { it.streamId == streamId }
                            if (currentIndex >= 0) {
                                drawerListState.scrollToItem((currentIndex - 2).coerceAtLeast(0))
                            }
                        }
                    }

                    LazyColumn(
                        state = drawerListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = pagedDrawerList,
                            key = { it.streamId },
                            contentType = { "drawer_channel" }
                        ) { channel ->
                            val isCurrent = channel.streamId == streamId
                            Surface(
                                onClick = {
                                    showChannelDrawer = false
                                    viewModel.playLiveChannel(channel)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) TiviPrimary.copy(alpha = 0.22f) else Color.Transparent,
                                border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(24.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(TiviPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(TiviCard),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = channel.name,
                                        color = if (isCurrent) TiviPrimary else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(TiviPrimary)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "CANLI",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // OTOMATİK SONRAKİ BÖLÜM GEÇİŞ KARTI (Binge-Watching Popup)
        val timeRemainingMs = if (duration > 0) duration - currentPosition else Long.MAX_VALUE
        val showNextEpisodePrompt = !isLive && currentEpisode != null && nextEpisode != null &&
                !autoNextDismissed && duration > 60_000L && timeRemainingMs in 1000L..25_000L

        AnimatedVisibility(
            visible = showNextEpisodePrompt && !isInPipMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = if (isPortrait) 120.dp else 80.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviOverlayDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.7f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = TiviAccentLive,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Sonraki Bölüm (${(timeRemainingMs / 1000L)}s)",
                            color = TiviAccentLive,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { autoNextDismissed = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = TiviTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "S${nextEpisode?.season} B${nextEpisode?.episodeNum}: ${nextEpisode?.title}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { viewModel.playNextEpisode() },
                            colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Hemen İzle", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // MEDYA VE OYNATMA SEÇENEKLERİ MODAL BOTTOM SHEET
        if (showTrackSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTrackSheet = false },
                containerColor = TiviSurface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Medya ve Oynatma Seçenekleri",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TiviTextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. SES DİLİ (AUDIO TRACKS)
                    Text(text = "Ses Dili", color = TiviPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (audioTracks.isEmpty()) {
                        Text(text = "Ek ses parçası bulunamadı (Varsayılan Ses)", color = TiviTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        audioTracks.forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { playerManager.selectTrack(track, androidx.media3.common.C.TRACK_TYPE_AUDIO) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = track.isSelected,
                                    onClick = { playerManager.selectTrack(track, androidx.media3.common.C.TRACK_TYPE_AUDIO) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = track.name,
                                    color = if (track.isSelected) TiviPrimary else TiviTextPrimary,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. ALTYAZI (SUBTITLES)
                    Text(text = "Altyazı", color = TiviSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    val anySubSelected = subtitleTracks.any { it.isSelected }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { playerManager.disableSubtitles() }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !anySubSelected,
                            onClick = { playerManager.disableSubtitles() }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Altyazı Kapalı",
                            color = if (!anySubSelected) TiviSecondary else TiviTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (!anySubSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    if (subtitleTracks.isNotEmpty()) {
                        subtitleTracks.forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { playerManager.selectTrack(track, androidx.media3.common.C.TRACK_TYPE_TEXT) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = track.isSelected,
                                    onClick = { playerManager.selectTrack(track, androidx.media3.common.C.TRACK_TYPE_TEXT) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = track.name,
                                    color = if (track.isSelected) TiviSecondary else TiviTextPrimary,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // ALTYAZI BOYUTU VE RENGİ ÖZELLEŞTİRME
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Altyazı Boyutu",
                        color = TiviTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val sizes = listOf(
                            "KUCUK" to "Küçük",
                            "ORTA" to "Orta (Varsayılan)",
                            "BUYUK" to "Büyük",
                            "COK_BUYUK" to "Çok Büyük"
                        )
                        items(sizes) { (key, label) ->
                            val isSelected = (subtitleSize == key)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSubtitleSize(key) },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TiviSecondary,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Altyazı Rengi",
                        color = TiviTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val colors = listOf(
                            "BEYAZ" to "⚪ Beyaz",
                            "SARI" to "🟡 Sarı",
                            "YESIL" to "🟢 Yeşil",
                            "MAVI" to "🔵 Mavi"
                        )
                        items(colors) { (key, label) ->
                            val isSelected = (subtitleColor == key)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSubtitleColor(key) },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TiviGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // 3. OYNATMA HIZI (VOD / DİZİ İÇİN)
                    if (!isLive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Oynatma Hızı", color = TiviGold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            items(speeds) { spd ->
                                val isSelected = (playbackSpeed == spd)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { playerManager.setPlaybackSpeed(spd) },
                                    label = {
                                        Text(
                                            text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TiviGold,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    // 4. UYKU ZAMANLAYICISI
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Uyku Zamanlayıcısı", color = Color(0xFF64B5F6), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val timerMinutes = listOf(0, 15, 30, 45, 60, 90)
                        items(timerMinutes) { mins ->
                            val isSelected = if (mins == 0) sleepTimerSeconds == null else (sleepTimerSeconds != null && sleepTimerSeconds!! / 60 in (mins - 1)..mins)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (mins == 0) playerManager.cancelSleepTimer() else playerManager.setSleepTimer(mins)
                                },
                                label = {
                                    Text(
                                        text = if (mins == 0) "Kapalı" else "$mins dk",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF64B5F6),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. SES SENKRONİZASYONU (GECİKME AYARI)
                    Text(text = "Ses Senkronizasyonu (Gecikme)", color = TiviPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${audioOffsetMs}ms", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                        Slider(
                            value = audioOffsetMs.toFloat(),
                            onValueChange = { playerManager.setAudioOffset(it.toLong()) },
                            valueRange = -2000f..2000f,
                            steps = 39,
                            colors = SliderDefaults.colors(thumbColor = TiviPrimary, activeTrackColor = TiviPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { playerManager.setAudioOffset(0L) }) {
                            Text("Sıfırla", fontSize = 12.sp, color = TiviPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // YAYIN BİLGİ PANELİ (STREAM DIAGNOSTICS HUD - TiviMate Tarzı)
        if (showDiagnosticsHUD) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviOverlayDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 60.dp)
                    .width(260.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Yayın Bilgisi (HUD)", color = TiviPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        IconButton(onClick = { showDiagnosticsHUD = false }, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "📐 Çözünürlük: ${streamDiagnostics.resolution}", color = Color.White, fontSize = 12.sp)
                    Text(text = "⚡ Kare Hızı: ${streamDiagnostics.frameRate}", color = Color.White, fontSize = 12.sp)
                    Text(text = "🎬 Video Codec: ${streamDiagnostics.videoCodec}", color = Color.White, fontSize = 12.sp)
                    Text(text = "🔊 Ses Formatı: ${streamDiagnostics.audioCodec}", color = Color.White, fontSize = 12.sp)
                    Text(text = "🚀 Bitrate: ${streamDiagnostics.bitrate}", color = TiviGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "🛡️ Tampon Sağlığı: ${streamDiagnostics.bufferHealthSecs}", color = TiviGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // SES VE PARLAKLIK KONTROL PANELİ (HUD)
        if (showAudioBrightnessHUD) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviOverlayDark),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(if (isPortrait) Alignment.Center else Alignment.TopEnd)
                    .statusBarsPadding()
                    .then(
                        if (isPortrait) Modifier.padding(20.dp)
                        else Modifier.padding(end = 16.dp, top = 60.dp)
                    )
                    .widthIn(max = if (isPortrait) 320.dp else 360.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = TiviPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ses & Parlaklık (HUD)",
                                color = TiviPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = { showAudioBrightnessHUD = false },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // EKRAN PARLAKLIĞI (BRIGHTNESS)
                    val brightnessPct = (currentBrightness * 100).toInt().coerceIn(1, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BrightnessMedium,
                                contentDescription = null,
                                tint = TiviGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Parlaklık", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(text = "%$brightnessPct", color = TiviGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = currentBrightness,
                        onValueChange = { newB ->
                            currentBrightness = newB
                            activity?.window?.let { window ->
                                val lp = window.attributes
                                lp.screenBrightness = newB.coerceIn(0.01f, 1f)
                                window.attributes = lp
                            }
                        },
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = TiviGold,
                            activeTrackColor = TiviGold,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // MEDYA SESİ (VOLUME)
                    val volumePct = if (maxVolume > 0) ((currentVolume.toFloat() / maxVolume) * 100).toInt() else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (currentVolume == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = TiviPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Ses Düzeyi", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(text = "%$volumePct", color = TiviPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = currentVolume.toFloat(),
                        onValueChange = { newV ->
                            val vInt = newV.toInt().coerceIn(0, maxVolume)
                            currentVolume = vInt
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vInt, 0)
                        },
                        valueRange = 0f..maxVolume.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = TiviPrimary,
                            activeTrackColor = TiviPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hızlı Butonlar: Sessiz (Mute) & Standart Parlaklık
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (currentVolume > 0) {
                                    currentVolume = 0
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                } else {
                                    val half = (maxVolume / 2).coerceAtLeast(1)
                                    currentVolume = half
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, half, 0)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            Text(
                                text = if (currentVolume == 0) "Sesi Aç" else "Sessiz",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                currentBrightness = 0.7f
                                activity?.window?.let { window ->
                                    val lp = window.attributes
                                    lp.screenBrightness = 0.7f
                                    window.attributes = lp
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            Text(
                                text = "Varsayılan",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // UYKU ZAMANLAYICISI MODAL DİYALOĞU
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                containerColor = TiviSurface,
                title = { Text("Uyku Zamanlayıcısı", color = TiviTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90).forEach { mins ->
                            Button(
                                onClick = {
                                    playerManager.setSleepTimer(mins)
                                    showSleepTimerDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TiviCard, contentColor = TiviTextPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$mins Dakika Sonra Yayını Kapat")
                            }
                        }
                        if (sleepTimerSeconds != null) {
                            OutlinedButton(
                                onClick = {
                                    playerManager.cancelSleepTimer()
                                    showSleepTimerDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Zamanlayıcıyı İptal Et", color = TiviAccentLive)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Vazgeç", color = TiviTextSecondary)
                    }
                }
            )
        }

        // ÇİFT EKRAN KANAL SEÇİCİ DİYALOĞU
        if (showDualViewPicker && isLive) {
            DualViewChannelPicker(
                liveStreams = allLiveStreams,
                currentStreamId = streamId,
                onChannelSelected = { channel ->
                    viewModel.startDualView(channel)
                    showDualViewPicker = false
                },
                onDismiss = { showDualViewPicker = false }
            )
        }

        // TIVIMATE 1: YARI SAYDAM REHBER KATMANI (OVERLAY GUIDE)
        if (isLive) {
            TiviOverlayGuide(
                visible = showOverlayGuide,
                currentStreamId = streamId,
                viewModel = viewModel,
                onChannelSelected = { channel ->
                    if (channel.streamId == streamId) {
                        showOverlayGuide = false
                    } else {
                        viewModel.playLiveChannel(channel)
                    }
                },
                onClose = { showOverlayGuide = false }
            )
        }

        // TIVIMATE 3: ALTTAN AÇILAN SON İZLENEN KANALLAR ŞERİDİ
        if (isLive) {
            TiviRecentChannelsBar(
                visible = showRecentChannelsBar && !showOverlayGuide,
                recentChannels = recentLiveChannels,
                currentStreamId = streamId,
                onChannelSelected = { channel ->
                    showRecentChannelsBar = false
                    viewModel.playLiveChannel(channel)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (showControls || showBottomInfoCard) 130.dp else 16.dp)
            )
        }

        // TIVIMATE 5: NUMARATÖRLE ANINDA KANAL DEĞİŞTİRME (NUMERIC ZAPPER KUTUSU)
        if (isLive) {
            TiviNumericZapperBox(
                visible = isZapperActive,
                zapperNumber = zapperNumber,
                matchedChannel = zapperMatchedChannel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
            )
        }

        // TIVIMATE 5: DOKUNMATİK NUMARATÖR DİYALOĞU (123 KEYPAD)
        if (showNumericKeypad && isLive) {
            NumericKeypadDialog(
                onDigitClicked = { d -> viewModel.onNumericKeyPressed(d) },
                onEnterClicked = { viewModel.executeZapperSwitch() },
                onDismiss = { showNumericKeypad = false }
            )
        }

        // TIVIMATE 4: GELİŞMİŞ KATEGORİ YÖNETİMİ DİYALOĞU
        if (showCategoryManagementDialog) {
            CategoryManagementDialog(
                viewModel = viewModel,
                initialTab = if (isLive) 0 else 1,
                onDismiss = { showCategoryManagementDialog = false }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DualViewSplitScreen(
    playerManager: com.tivimax.player.player.TiviPlayerManager,
    isPortrait: Boolean,
    primaryTitle: String,
    secondaryTitle: String,
    isPrimaryAudioActive: Boolean,
    isScreensSwapped: Boolean,
    isPrimaryBuffering: Boolean,
    isSecondaryBuffering: Boolean,
    aspectRatio: AspectRatioMode,
    onSwap: () -> Unit,
    onClose: () -> Unit,
    onChangeSecondary: () -> Unit
) {
    val firstIsPrimary = !isScreensSwapped

    Box(modifier = Modifier.fillMaxSize()) {
        if (isPortrait) {
            Column(modifier = Modifier.fillMaxSize()) {
                // First Screen (Top)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    DualViewSingleWindow(
                        playerManager = playerManager,
                        isPrimary = firstIsPrimary,
                        title = if (firstIsPrimary) primaryTitle else secondaryTitle,
                        hasAudio = if (firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                        isBuffering = if (firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                        aspectRatio = aspectRatio,
                        onFocusAudio = {
                            playerManager.switchDualViewAudioFocus(primaryActive = firstIsPrimary)
                        }
                    )
                }

                // Split divider line
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(TiviPrimary.copy(alpha = 0.6f))
                )

                // Second Screen (Bottom)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    DualViewSingleWindow(
                        playerManager = playerManager,
                        isPrimary = !firstIsPrimary,
                        title = if (!firstIsPrimary) primaryTitle else secondaryTitle,
                        hasAudio = if (!firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                        isBuffering = if (!firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                        aspectRatio = aspectRatio,
                        onFocusAudio = {
                            playerManager.switchDualViewAudioFocus(primaryActive = !firstIsPrimary)
                        }
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // First Screen (Left)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    DualViewSingleWindow(
                        playerManager = playerManager,
                        isPrimary = firstIsPrimary,
                        title = if (firstIsPrimary) primaryTitle else secondaryTitle,
                        hasAudio = if (firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                        isBuffering = if (firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                        aspectRatio = aspectRatio,
                        onFocusAudio = {
                            playerManager.switchDualViewAudioFocus(primaryActive = firstIsPrimary)
                        }
                    )
                }

                // Split divider line
                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(TiviPrimary.copy(alpha = 0.6f))
                )

                // Second Screen (Right)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    DualViewSingleWindow(
                        playerManager = playerManager,
                        isPrimary = !firstIsPrimary,
                        title = if (!firstIsPrimary) primaryTitle else secondaryTitle,
                        hasAudio = if (!firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                        isBuffering = if (!firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                        aspectRatio = aspectRatio,
                        onFocusAudio = {
                            playerManager.switchDualViewAudioFocus(primaryActive = !firstIsPrimary)
                        }
                    )
                }
            }
        }

        // Floating Dual-View Action Pill (Top Center)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.4f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TiviAccentLive)
                )

                Text(
                    text = "ÇİFT EKRAN",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Swap Button
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Ekranları Değiştir",
                        tint = TiviPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Change 2nd channel Button
                IconButton(
                    onClick = onChangeSecondary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "2. Kanalı Değiştir",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tek Ekrana Dön",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DualViewSingleWindow(
    playerManager: com.tivimax.player.player.TiviPlayerManager,
    isPrimary: Boolean,
    title: String,
    hasAudio: Boolean,
    isBuffering: Boolean,
    aspectRatio: AspectRatioMode,
    onFocusAudio: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onFocusAudio() }
            .border(
                width = if (hasAudio) 2.dp else 1.dp,
                color = if (hasAudio) TiviPrimary else Color.White.copy(alpha = 0.15f)
            )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = if (isPrimary) playerManager.initializePlayer() else playerManager.secondaryExoPlayer
                    resizeMode = when (aspectRatio) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                }
            },
            update = { playerView ->
                val targetPlayer = if (isPrimary) playerManager.initializePlayer() else playerManager.secondaryExoPlayer
                if (playerView.player != targetPlayer) {
                    playerView.player = targetPlayer
                }
                playerView.resizeMode = when (aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = TiviPrimary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Overlay Badge: Title & Audio Status
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (hasAudio) TiviPrimary else Color.Black.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (hasAudio) TiviPrimary else Color.White.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasAudio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = if (hasAudio) Color.Black else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasAudio) "SES AÇIK" else "SESSİZ",
                        color = if (hasAudio) Color.Black else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualViewChannelPicker(
    liveStreams: List<LiveStream>,
    currentStreamId: Int,
    onChannelSelected: (LiveStream) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredChannels = remember(liveStreams, searchQuery) {
        if (searchQuery.isBlank()) {
            liveStreams.filter { it.streamId != currentStreamId }
        } else {
            liveStreams.filter {
                it.streamId != currentStreamId && it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141923),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = TiviPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Çift Ekran - İkinci Kanal",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aynı anda canlı izlemek istediğiniz kanalı seçin",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Arama Çubuğu
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Kanal ara...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TiviPrimary)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Temizle", tint = Color.White)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TiviPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color(0xFF1A2232),
                    unfocusedContainerColor = Color(0xFF1A2232),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Kanal Listesi
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredChannels, key = { it.streamId }) { channel ->
                    Surface(
                        onClick = { onChannelSelected(channel) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E2638),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!channel.streamIcon.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel.streamIcon,
                                    contentDescription = channel.name,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!channel.currentProgram.isNullOrBlank()) {
                                    Text(
                                        text = "Canlı: ${channel.currentProgram}",
                                        color = TiviTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TiviPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Başlat",
                                    color = TiviPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun VideoPlayerSurface(
    playerManager: TiviPlayerManager,
    isLive: Boolean,
    aspectRatio: AspectRatioMode,
    subtitleColor: String,
    subtitleSize: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = playerManager.initializePlayer(isLive)
                playerManager.registerPlayerView(this)
                resizeMode = when (aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                }
            }
        },
        update = { playerView ->
            val currentPlayer = playerManager.player ?: playerManager.initializePlayer(isLive)
            if (playerView.player != currentPlayer) {
                playerView.player = currentPlayer
                playerManager.registerPlayerView(playerView)
            }
            val targetResizeMode = when (aspectRatio) {
                AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            }
            if (playerView.resizeMode != targetResizeMode) {
                playerView.resizeMode = targetResizeMode
            }
            try {
                playerView.subtitleView?.let { subView ->
                    val subTextColor = when (subtitleColor) {
                        "SARI" -> android.graphics.Color.rgb(255, 215, 0)
                        "YESIL" -> android.graphics.Color.rgb(0, 230, 118)
                        "MAVI" -> android.graphics.Color.rgb(64, 196, 255)
                        else -> android.graphics.Color.WHITE
                    }
                    val subTextSp = when (subtitleSize) {
                        "KUCUK" -> 14f
                        "BUYUK" -> 22f
                        "COK_BUYUK" -> 26f
                        else -> 18f
                    }
                    subView.setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            subTextColor,
                            android.graphics.Color.argb(160, 0, 0, 0),
                            android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    subView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subTextSp)
                    subView.setBottomPaddingFraction(0.12f)
                }
            } catch (e: Throwable) {
                // Ignore
            }
        },
        onRelease = { playerView ->
            playerManager.unregisterPlayerView(playerView)
            playerView.player = null
        },
        modifier = modifier
    )
}

