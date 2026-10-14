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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.TrackInfo
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
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

    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf(false) }
    var showDiagnosticsHUD by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioBrightnessHUD by remember { mutableStateOf(false) }

    // Dokunmatik Hareket Geri Bildirimleri (Parlaklık & Ses)
    var brightnessFeedback by remember { mutableStateOf<Int?>(null) }
    var volumeFeedback by remember { mutableStateOf<Int?>(null) }
    var seekRipple by remember { mutableStateOf<String?>(null) }
    var centerDragAccumulator by remember { mutableFloatStateOf(0f) }

    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()
    val isFavorite = favoriteLiveIds.contains(streamId.toString())
    val epgList by viewModel.currentEpgList.collectAsState()

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

    // Otomatik Kontrol Gizleme Sayacı (Sürükleme yaparken arayüz ASLA kaybolmaz)
    LaunchedEffect(showControls, isPlaying, isScrubbing) {
        if (showControls && isPlaying && !isControlsLocked && !isScrubbing) {
            delay(4500)
            showControls = false
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
    val nextEpisode = remember(currentEpisode, currentPosition) { viewModel.getNextEpisode() }
    var autoNextDismissed by remember(currentEpisode) { mutableStateOf(false) }

    // Bölüm bittiğinde otomatik sonraki bölüme geçiş
    LaunchedEffect(currentPosition, duration, isLive, currentEpisode) {
        if (!isLive && currentEpisode != null && duration > 30_000L && currentPosition >= duration - 1500L && duration > 0L) {
            viewModel.playNextEpisode()
        }
    }

    var resumeNotification by remember { mutableStateOf<String?>(null) }

    // Kaldığı yerden başlama bildirimi
    LaunchedEffect(Unit) {
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

    // Periyodik İlerleme Otomatik Kaydı (Her 5 saniyede bir kaydeder)
    LaunchedEffect(isPlaying, streamId, isLive) {
        if (!isLive) {
            while (true) {
                delay(5000)
                val pos = playerManager.currentPosition.value
                val dur = playerManager.duration.value
                if (playerManager.isPlaying.value && pos > 0) {
                    viewModel.saveWatchProgress(streamId, pos, dur)
                }
            }
        }
    }

    // Ekrandan çıkıldığında veya arkaya atıldığında son konumu kaydet
    DisposableEffect(streamId) {
        onDispose {
            if (!isLive && playerManager.currentPosition.value > 0) {
                viewModel.saveWatchProgress(streamId, playerManager.currentPosition.value, playerManager.duration.value)
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
        if (showChannelDrawer) {
            showChannelDrawer = false
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
                        if (isControlsLocked) {
                            showControls = !showControls
                        } else {
                            showControls = !showControls
                            showChannelDrawer = false
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isControlsLocked) {
                            if (!isLive) {
                                if (offset.x < screenWidth * 0.35f) {
                                    // Sol tarafa çift tık: 10 saniye geri (Sadece Film/Dizi)
                                    playerManager.rewind10Seconds()
                                    seekRipple = "-10s"
                                } else if (offset.x > screenWidth * 0.65f) {
                                    // Sağ tarafa çift tık: 10 saniye ileri (Sadece Film/Dizi)
                                    playerManager.forward10Seconds()
                                    seekRipple = "+10s"
                                } else {
                                    // Ortaya çift tık: En-Boy Oranı değiştir
                                    val newMode = playerManager.cycleAspectRatio()
                                    seekRipple = when (newMode) {
                                        AspectRatioMode.FIT -> "Orijinal Oran"
                                        AspectRatioMode.FILL -> "Ekranı Doldur"
                                        AspectRatioMode.ZOOM -> "Yakınlaştır"
                                        AspectRatioMode.RATIO_16_9 -> "16:9 Oran"
                                    }
                                }
                            } else {
                                // Canlı TV'de +10s / -10s sarmalar tamamen iptal edildi
                                if (offset.x in (screenWidth * 0.35f)..(screenWidth * 0.65f)) {
                                    val newMode = playerManager.cycleAspectRatio()
                                    seekRipple = when (newMode) {
                                        AspectRatioMode.FIT -> "Orijinal Oran"
                                        AspectRatioMode.FILL -> "Ekranı Doldur"
                                        AspectRatioMode.ZOOM -> "Yakınlaştır"
                                        AspectRatioMode.RATIO_16_9 -> "16:9 Oran"
                                    }
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // Media3 ExoPlayer Video Render View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = playerManager.initializePlayer()
                    try {
                        subtitleView?.let { subView ->
                            subView.setStyle(
                                androidx.media3.ui.CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    android.graphics.Color.argb(160, 0, 0, 0),
                                    android.graphics.Color.TRANSPARENT,
                                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null
                                )
                            )
                            subView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                            subView.setBottomPaddingFraction(0.12f)
                        }
                    } catch (e: Throwable) {
                        // Ignore styling errors on non-standard devices
                    }
                    resizeMode = when (aspectRatio) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                }
            },
            update = { playerView ->
                if (playerView.player == null) {
                    playerView.player = playerManager.initializePlayer()
                }
                playerView.resizeMode = when (aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
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

        // Gesture HUD Pill: Parlaklık veya Ses
        brightnessFeedback?.let { pct ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 36.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TiviOverlayDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.BrightnessMedium, contentDescription = null, tint = TiviGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "%$pct", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        volumeFeedback?.let { pct ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TiviOverlayDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = TiviPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "%$pct", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Double-Tap Ripple Feedback
        seekRipple?.let { text ->
            LaunchedEffect(text) {
                delay(1200)
                seekRipple = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TiviOverlayDark)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(text = text, color = TiviPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                        // Canlı yayında Hızlı Kanal Değiştirici Butonu
                        if (isLive) {
                            IconButton(
                                onClick = { showChannelDrawer = !showChannelDrawer },
                                modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatListBulleted,
                                    contentDescription = "Kanal Listesi",
                                    tint = if (showChannelDrawer) TiviPrimary else Color.White,
                                    modifier = Modifier.size(if (isPortrait) 18.dp else 24.dp)
                                )
                            }

                            // Ses & Parlaklık (HUD) Butonu
                            IconButton(
                                onClick = {
                                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                                    showAudioBrightnessHUD = !showAudioBrightnessHUD
                                },
                                modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Ses & Parlaklık (HUD)",
                                    tint = if (showAudioBrightnessHUD) TiviPrimary else Color.White,
                                    modifier = Modifier.size(if (isPortrait) 18.dp else 24.dp)
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

                        // PiP Butonu (Picture-in-Picture)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(
                                onClick = {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                },
                                modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP Modu",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isPortrait) 18.dp else 24.dp)
                                )
                            }
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
                            // TIVIMATE TARZI CANLI TV BİLGİ KARTI (OSD)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TiviOverlayDark),
                                shape = RoundedCornerShape(if (isPortrait) 10.dp else 14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = if (isPortrait) 8.dp else 12.dp,
                                        vertical = if (isPortrait) 6.dp else 10.dp
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            val currentChannel = viewModel.liveStreams.collectAsState().value.find { it.streamId == streamId }
                                            if (!currentChannel?.streamIcon.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = currentChannel?.streamIcon,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .size(if (isPortrait) 26.dp else 40.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(TiviCard)
                                                )
                                                Spacer(modifier = Modifier.width(if (isPortrait) 6.dp else 10.dp))
                                            }
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = title,
                                                        color = Color.White,
                                                        fontSize = if (isPortrait) 13.sp else 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(TiviPrimary.copy(alpha = 0.2f))
                                                            .padding(
                                                                horizontal = if (isPortrait) 4.dp else 6.dp,
                                                                vertical = 1.dp
                                                            )
                                                    ) {
                                                        Text(
                                                            text = streamDiagnostics.resolution.ifEmpty { "1080p FHD" },
                                                            color = TiviPrimary,
                                                            fontSize = if (isPortrait) 9.sp else 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                val currentProgram = epgList.firstOrNull()
                                                if (currentProgram != null && currentProgram.title.isNotBlank()) {
                                                    Text(
                                                        text = "Şu An: ${currentProgram.title}",
                                                        color = TiviGold,
                                                        fontSize = if (isPortrait) 10.sp else 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Canlı Yayın Akışı",
                                                        color = TiviTextSecondary,
                                                        fontSize = if (isPortrait) 10.sp else 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Favori & Canlıya Geç & Hızlı Liste Açma
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 2.dp else 4.dp)
                                        ) {
                                            // Canlıya Geç Butonu (3s Tampon)
                                            Surface(
                                                onClick = {
                                                    val synced = viewModel.syncToLive()
                                                    if (synced) {
                                                        seekRipple = "🔴 Canlıya Geçildi (3s Tampon)"
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = TiviAccentLive.copy(alpha = 0.25f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, TiviAccentLive)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        horizontal = if (isPortrait) 5.dp else 7.dp,
                                                        vertical = if (isPortrait) 3.dp else 4.dp
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(if (isPortrait) 5.dp else 6.dp)
                                                            .clip(CircleShape)
                                                            .background(TiviAccentLive)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "CANLIYA GEÇ",
                                                        color = Color.White,
                                                        fontSize = if (isPortrait) 9.sp else 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleFavoriteLive(streamId.toString()) },
                                                modifier = Modifier.size(if (isPortrait) 30.dp else 40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Favori",
                                                    tint = if (isFavorite) TiviGold else Color.White,
                                                    modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { showChannelDrawer = true },
                                                modifier = Modifier.size(if (isPortrait) 30.dp else 40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FormatListBulleted,
                                                    contentDescription = "Kanal Listesi",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                                                )
                                            }
                                        }
                                    }

                                    // EPG Sonraki Program (Dikeyde yer tasarrufu için sadece yatayda veya kompakt)
                                    if (!isPortrait) {
                                        val nextProgram = epgList.getOrNull(1)
                                        if (nextProgram != null && nextProgram.title.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Sonraki: ${nextProgram.title} (${nextProgram.start ?: ""})",
                                                color = TiviTextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(if (isPortrait) 4.dp else 8.dp))

                                    // Alt Butonlar: Oran, Ses & Parlaklık (HUD), Ses & Altyazı
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
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
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                            contentPadding = PaddingValues(
                                                horizontal = if (isPortrait) 8.dp else 10.dp,
                                                vertical = if (isPortrait) 2.dp else 4.dp
                                            ),
                                            modifier = Modifier.height(if (isPortrait) 28.dp else 34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AspectRatio,
                                                contentDescription = null,
                                                tint = TiviPrimary,
                                                modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = when (aspectRatio) {
                                                    AspectRatioMode.FIT -> "Oran: Fit"
                                                    AspectRatioMode.FILL -> "Oran: Doldur"
                                                    AspectRatioMode.ZOOM -> "Oran: Zoom"
                                                    AspectRatioMode.RATIO_16_9 -> "Oran: 16:9"
                                                },
                                                color = Color.White,
                                                fontSize = if (isPortrait) 10.sp else 11.sp
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 4.dp else 8.dp)
                                        ) {
                                            // Ses & Parlaklık HUD Butonu
                                            Surface(
                                                onClick = {
                                                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                    currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                                                    showAudioBrightnessHUD = !showAudioBrightnessHUD
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (showAudioBrightnessHUD) TiviPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (showAudioBrightnessHUD) TiviPrimary else Color.White.copy(alpha = 0.25f)),
                                                modifier = Modifier.height(if (isPortrait) 28.dp else 34.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = if (isPortrait) 8.dp else 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tune,
                                                        contentDescription = "Ses & Parlaklık",
                                                        tint = if (showAudioBrightnessHUD) TiviPrimary else Color.White,
                                                        modifier = Modifier.size(if (isPortrait) 14.dp else 16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Ses/Parlaklık",
                                                        color = if (showAudioBrightnessHUD) TiviPrimary else Color.White,
                                                        fontSize = if (isPortrait) 10.sp else 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { showTrackSheet = true },
                                                modifier = Modifier.size(if (isPortrait) 28.dp else 34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Subtitles,
                                                    contentDescription = "Ses ve Altyazı",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(if (isPortrait) 18.dp else 20.dp)
                                                )
                                            }
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
                                            value = if (isScrubbing) scrubPosition else currentPosition.toFloat(),
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
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val newMode = playerManager.cycleAspectRatio()
                                            seekRipple = when (newMode) {
                                                AspectRatioMode.FIT -> "Orijinal Oran"
                                                AspectRatioMode.FILL -> "Ekranı Doldur"
                                                AspectRatioMode.ZOOM -> "Yakınlaştır"
                                                AspectRatioMode.RATIO_16_9 -> "16:9 Oran"
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.AspectRatio, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (aspectRatio) {
                                                AspectRatioMode.FIT -> "Fit"
                                                AspectRatioMode.FILL -> "Doldur"
                                                AspectRatioMode.ZOOM -> "Zoom"
                                                AspectRatioMode.RATIO_16_9 -> "16:9"
                                            },
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                    }

                                    // Hız Butonu (0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
                                    OutlinedButton(
                                        onClick = {
                                            val newSpeed = playerManager.cyclePlaybackSpeed()
                                            seekRipple = "Hız: ${newSpeed}x"
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = TiviSecondary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${playbackSpeed}x",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!isLive && nextEpisode != null) {
                                    IconButton(
                                        onClick = { viewModel.playNextEpisode() },
                                        modifier = Modifier.size(if (isPortrait) 32.dp else 40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Sonraki Bölüm",
                                            tint = TiviAccentLive,
                                            modifier = Modifier.size(if (isPortrait) 20.dp else 24.dp)
                                        )
                                    }
                                }

                                IconButton(onClick = { showTrackSheet = true }) {
                                    Icon(imageVector = Icons.Default.Subtitles, contentDescription = "Ses ve Altyazı", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // HIZLI KANAL DEĞİŞTİRİCİ ÇEKMECESİ (Canlı TV İzlerken Sağdan/Soldan Açılır)
        AnimatedVisibility(
            visible = showChannelDrawer && isLive,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .align(Alignment.CenterStart)
        ) {
            val liveList by viewModel.liveStreams.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = TiviOverlayDark),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
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

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(liveList) { channel ->
                            val isCurrent = channel.streamId == streamId
                            Surface(
                                onClick = {
                                    showChannelDrawer = false
                                    viewModel.playLiveChannel(channel)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) TiviPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(TiviCard)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = channel.name,
                                        color = if (isCurrent) TiviPrimary else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                imageVector = if (currentVolume == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
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
