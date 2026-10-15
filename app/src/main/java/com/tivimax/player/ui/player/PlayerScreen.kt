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
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.text.style.TextAlign
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
import com.tivimax.player.ui.player.components.TiviPlayerHUD
import com.tivimax.player.ui.player.components.PlayerPillButton
import com.tivimax.player.ui.player.components.BouncyIconButton
import com.tivimax.player.ui.player.components.LocalPlayerInteraction
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent

import com.tivimax.player.util.LocalIsTvDevice

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
    val isTvDevice = LocalIsTvDevice.current

    if (isTvDevice && isLive) {
        val activeChan = viewModel.liveStreams.collectAsState().value.firstOrNull { it.streamId == streamId }
        val fallbackChan = LiveStream(streamId = streamId, name = title, categoryId = categoryId)
        com.tivimax.player.ui.tv.TvPlayerOverlay(
            viewModel = viewModel,
            channel = activeChan ?: fallbackChan,
            onCloseFullScreen = onBack
        )
        return
    }
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
    var showPlayerHUD by remember { mutableStateOf(false) }
    var hudInitialTab by remember { mutableIntStateOf(0) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showOverlayGuide by remember { mutableStateOf(false) }
    var showRecentChannelsBar by remember { mutableStateOf(false) }
    var showNumericKeypad by remember { mutableStateOf(false) }
    var showCategoryManagementDialog by remember { mutableStateOf(false) }
    var showBottomInfoCard by remember { mutableStateOf(true) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }

    val playerFocusRequester = remember { FocusRequester() }
    val firstButtonFocusRequester = remember { FocusRequester() }
    val sliderFocusRequester = remember { FocusRequester() }
    val bottomRowFocusRequester = remember { FocusRequester() }
    val lockButtonFocusRequester = remember { FocusRequester() }
    var isSliderFocused by remember { mutableStateOf(false) }
    var showLockBanner by remember { mutableStateOf(false) }
    var lastOkPressTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(showLockBanner) {
        if (showLockBanner) {
            delay(3500L)
            showLockBanner = false
        }
    }

    // TV kumandası için ekrana ilk girişte ana video oynatıcıya odağı ver
    LaunchedEffect(Unit) {
        delay(200)
        try {
            playerFocusRequester.requestFocus()
        } catch (e: Exception) { }
    }

    // HUD açıldığında ilk butona, kapandığında ana oynatıcıya odağı ver
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(120)
            try {
                firstButtonFocusRequester.requestFocus()
            } catch (e: Exception) { }
        } else {
            delay(50)
            try {
                playerFocusRequester.requestFocus()
            } catch (e: Exception) { }
        }
    }

    // Üst Bar için gerçek zamanlı saat (14:35)
    var currentTimeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeText = sdf.format(Date())
            delay(10000L)
        }
    }

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
    var optimisticSeekPosition by remember { mutableStateOf<Long?>(null) }

    // YouTube-style dynamic double-tap ripple
    var doubleTapSide by remember { mutableStateOf<String?>(null) } // "left" or "right"
    var doubleTapSeconds by remember { mutableIntStateOf(0) }
    var lastDoubleTapTime by remember { mutableLongStateOf(0L) }
    var isDoubleTapActive by remember { mutableStateOf(false) }

    // Fullscreen gesture scrub state
    var gestureScrubActive by remember { mutableStateOf(false) }
    var gestureScrubTargetMs by remember { mutableLongStateOf(0L) }
    var gestureScrubDiffMs by remember { mutableLongStateOf(0L) }
    var gestureDragInitialPos by remember { mutableLongStateOf(0L) }
    var dragDirection by remember { mutableStateOf<String?>(null) } // "horizontal", "vertical_left", "vertical_right"

    val effectivePosition = when {
        isScrubbing -> scrubPosition.toLong()
        gestureScrubActive -> gestureScrubTargetMs
        optimisticSeekPosition != null -> optimisticSeekPosition!!
        else -> currentPosition
    }

    LaunchedEffect(doubleTapSide, doubleTapSeconds) {
        if (doubleTapSide != null) {
            isDoubleTapActive = true
            delay(900L)
            doubleTapSide = null
            doubleTapSeconds = 0
            isDoubleTapActive = false
        }
    }

    LaunchedEffect(currentPosition, optimisticSeekPosition) {
        if (optimisticSeekPosition != null) {
            if (kotlin.math.abs(currentPosition - optimisticSeekPosition!!) <= 1500L) {
                optimisticSeekPosition = null
            }
        }
    }

    LaunchedEffect(optimisticSeekPosition) {
        if (optimisticSeekPosition != null) {
            delay(1200L)
            optimisticSeekPosition = null
        }
    }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isUserInteracting = isScrubbing || gestureScrubActive || (doubleTapSide != null) || showChannelDrawer || showTrackSheet || showSleepTimerDialog || showPlayerHUD || showOverlayGuide || showRecentChannelsBar || showNumericKeypad || showCategoryManagementDialog || isZapperActive || showEpisodesDrawer || (dragStartOffset != null)

    val registerInteraction: () -> Unit = remember {
        { lastInteractionTime = System.currentTimeMillis() }
    }

    // 7 Saniyelik Akıllı HUD ve Tam Ekran Zamanlayıcısı (7000ms)
    // Kullanıcı işlem yaptığı sürece ASLA kapanmaz, her dokunuşta veya işlemde süre sıfırlanıp 7 saniye baştan başlar.
    val HUD_AUTO_HIDE_TIMEOUT_MS = 7000L

    // Kanal değiştiğinde alt bilgi paneli 7 saniye görünür ve sayaç başlar
    LaunchedEffect(streamId) {
        if (isLive) {
            showBottomInfoCard = true
            registerInteraction()
        }
    }

    LaunchedEffect(showControls, showBottomInfoCard, isPlaying, lastInteractionTime, isUserInteracting, isControlsLocked) {
        if ((showControls || showBottomInfoCard) && isPlaying && !isUserInteracting) {
            val timeout = if (isControlsLocked) 4000L else if (!showControls && showBottomInfoCard) 4000L else 7000L
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                val remaining = timeout - elapsed
                if (remaining <= 0) {
                    if (!isUserInteracting) {
                        showControls = false
                        showBottomInfoCard = false
                    }
                    break
                }
                delay(remaining.coerceAtLeast(100L))
            }
        }
    }

    // Tam Ekran (Immersive Mode): Oynatıcı açıldığında tam ekran moduna al, çıkışta geri yükle
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Eğer varsa çentik/kamera boşluğunu da kullanmasına izin ver (Tam kapsayıcı medya deneyimi)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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

    // Oynatıcı başlatılmamışsa veya boşsa akışı başlat (ViewModel zaten başlatmışsa mükerrer istek atılmaz)
    LaunchedEffect(streamUrl, streamId) {
        if (playerManager.currentPlayingUrl == null && streamUrl.isNotBlank()) {
            playerManager.play(streamUrl, isLive = isLive)
        }
    }

    // Lifecycle Observer (Arka plana atıldığında PiP açık değilse videoyu duraklat)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isInPipMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (!isInPipMode) {
                    playerManager.pause()
                }
            } else if (event == Lifecycle.Event.ON_START) {
                if (!isControlsLocked) {
                    playerManager.play()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
        val isTv = activity?.let { act ->
            val uiModeManager = act.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
            val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val hasLeanback = act.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
            val hasNoTouch = !act.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
            isTvMode || hasLeanback || hasNoTouch
        } ?: false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            try {
                val hasPip = activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                if (hasPip) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(!isTv && autoPipEnabled && isPlaying)
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
        } else if (showEpisodesDrawer) {
            showEpisodesDrawer = false
        } else if (showPlayerHUD) {
            showPlayerHUD = false
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
            if (!isLive) {
                if (currentPosition > 0) {
                    viewModel.saveWatchProgress(streamId, currentPosition, duration)
                }
                playerManager.release()
            } else {
                val curChan = allLiveStreams.firstOrNull { it.streamId == streamId } ?: lastPlayedLiveChannel
                if (curChan != null) {
                    viewModel.activePreviewChannel.value = curChan
                }
            }
            onBack()
        }
    }

    CompositionLocalProvider(LocalPlayerInteraction provides registerInteraction) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                        return@onPreviewKeyEvent false
                    }
                    val keyCode = keyEvent.nativeKeyEvent.keyCode

                    // EKRAN KİLİDİ AKTİFKEN:
                    if (isControlsLocked) {
                        registerInteraction()
                        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                            val now = System.currentTimeMillis()
                            if (now - lastOkPressTime < 800L) {
                                isControlsLocked = false
                                showLockBanner = false
                                seekRipple = "🔓 Ekran Kilidi Açıldı"
                                return@onPreviewKeyEvent true
                            }
                            lastOkPressTime = now
                        }
                        showLockBanner = true
                        showControls = true
                        return@onPreviewKeyEvent true
                    }

                    // HUD AÇIKKEN:
                    if (showControls) {
                        // Kumandadan basılan her tuş 5 saniyelik HUD kapanma süresini sıfırlar
                        registerInteraction()

                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            if (!isLive && isBuffering) {
                                playerManager.release()
                                onBack()
                                return@onPreviewKeyEvent true
                            }
                            showControls = false
                            showBottomInfoCard = false
                            try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                            return@onPreviewKeyEvent true
                        }

                        // Canlı TV modundayken kumandadan yukarı/aşağı tuşlarına basıldığında
                        // butonlar arasında takılmak yerine anında sonraki/önceki kanala zaplasın:
                        if (isLive) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_CHANNEL_UP -> {
                                    showControls = false
                                    val next = viewModel.zapNextChannel(streamId)
                                    if (next != null) seekRipple = "Sonraki: ${next.name} ⏭"
                                    showBottomInfoCard = true
                                    try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                                    return@onPreviewKeyEvent true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            showControls = false
                            showRecentChannelsBar = true
                            showBottomInfoCard = true
                            try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                            return@onPreviewKeyEvent true
                        }
                            }
                        }

                        // Alt bardaki aksiyon butonları arasında gezinme ve Enter/OK tıklaması
                        return@onPreviewKeyEvent false
                    }

                    // Yarı Saydam Rehber, Son Kanallar veya Çekmeceler açıkken:
                    if (showOverlayGuide || showRecentChannelsBar || showEpisodesDrawer || showPlayerHUD || showChannelDrawer) {
                        registerInteraction()
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            if (showOverlayGuide) showOverlayGuide = false
                            else if (showRecentChannelsBar) showRecentChannelsBar = false
                            else if (showEpisodesDrawer) showEpisodesDrawer = false
                            else if (showPlayerHUD) showPlayerHUD = false
                            else if (showChannelDrawer) showChannelDrawer = false
                            try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }

                    // HUD KAPALIYKEN (Normal İzleme Modu):
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            if (isLive) {
                                val next = viewModel.zapNextChannel(streamId)
                                if (next != null) seekRipple = "Sonraki: ${next.name} ⏭"
                                showBottomInfoCard = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                            false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (isLive) {
                            showRecentChannelsBar = true
                            showBottomInfoCard = true
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isLive) {
                                showOverlayGuide = true
                                showControls = false
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            } else {
                                playerManager.rewind10Seconds()
                                seekRipple = "-10s"
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isLive) {
                                val prev = viewModel.zapRecall()
                                if (prev != null) {
                                    seekRipple = "↩ ${prev.name}"
                                } else {
                                    showRecentChannelsBar = true
                                }
                                showBottomInfoCard = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            } else {
                                playerManager.forward10Seconds()
                                seekRipple = "+10s"
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (isLive) {
                                // Canlı TV'de orta tuşa basıldığında:
                                // Bilgi kartı açıksa kapatır; temiz ekrandayken hızlı kanal listesini açıp kapatır
                                if (showBottomInfoCard) {
                                    showBottomInfoCard = false
                                } else {
                                    showChannelDrawer = !showChannelDrawer
                                }
                                showControls = false
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            } else {
                                showControls = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (!isLive && isBuffering) {
                                playerManager.release()
                                onBack()
                                return@onPreviewKeyEvent true
                            }
                            if (showBottomInfoCard) {
                                showBottomInfoCard = false
                                return@onPreviewKeyEvent true
                            }
                            false
                        }
                        else -> false
                    }
                }
                .pointerInput(isControlsLocked, showControls, showBottomInfoCard, showOverlayGuide, showRecentChannelsBar, showChannelDrawer) {
                    detectTapGestures(
                        onTap = {
                            if (isControlsLocked) {
                                showLockBanner = true
                                showControls = true
                                registerInteraction()
                            } else {
                                if (showOverlayGuide) {
                                    showOverlayGuide = false
                                } else if (showRecentChannelsBar) {
                                    showRecentChannelsBar = false
                                } else if (showChannelDrawer) {
                                    showChannelDrawer = false
                                } else if (showControls || showBottomInfoCard) {
                                    // Arayüz açıkken boş alana dokunursa arayüzü tamamen gizle
                                    showControls = false
                                    showBottomInfoCard = false
                                } else {
                                    // Arayüz kapalıyken dokunursa arayüzü aç ve 7-8 saniye sayacını başlat
                                    showControls = true
                                    if (isLive) {
                                        showBottomInfoCard = true
                                    }
                                    registerInteraction()
                                }
                            }
                        },
                    onDoubleTap = { offset ->
                        registerInteraction()
                        if (isControlsLocked) {
                            isControlsLocked = false
                            showLockBanner = false
                            seekRipple = "🔓 Ekran Kilidi Açıldı"
                        } else {
                            val now = System.currentTimeMillis()
                            val isQuickSuccession = (now - lastDoubleTapTime) < 850L
                            lastDoubleTapTime = now

                            if (!isLive) {
                            if (offset.x < size.width * 0.38f) {
                                // Sol tarafa çift tık: Geri sar
                                val oldSide = doubleTapSide
                                doubleTapSide = "left"
                                doubleTapSeconds = if (isQuickSuccession && oldSide == "left") doubleTapSeconds + 10 else 10
                                playerManager.rewind10Seconds()
                                seekRipple = null
                            } else if (offset.x > size.width * 0.62f) {
                                // Sağ tarafa çift tık: İleri sar
                                val oldSide = doubleTapSide
                                doubleTapSide = "right"
                                doubleTapSeconds = if (isQuickSuccession && oldSide == "right") doubleTapSeconds + 10 else 10
                                playerManager.forward10Seconds()
                                seekRipple = null
                            } else {
                                // Ortaya çift tık: En-Boy Oranı değiştir
                                doubleTapSide = null
                                val newMode = playerManager.cycleAspectRatio()
                                seekRipple = when (newMode) {
                                    AspectRatioMode.FIT -> "En-Boy: Orijinal"
                                    AspectRatioMode.FILL -> "En-Boy: Doldur"
                                    AspectRatioMode.ZOOM -> "En-Boy: Yakınlaştır"
                                    AspectRatioMode.RATIO_16_9 -> "En-Boy: 16:9"
                                    AspectRatioMode.RATIO_4_3 -> "En-Boy: 4:3"
                                }
                            }
                        } else {
                            // Canlı TV'de en-boy oranı döngüsü
                            val newMode = playerManager.cycleAspectRatio()
                            seekRipple = when (newMode) {
                                AspectRatioMode.FIT -> "En-Boy: Orijinal"
                                AspectRatioMode.FILL -> "En-Boy: Doldur"
                                AspectRatioMode.ZOOM -> "En-Boy: Yakınlaştır"
                                AspectRatioMode.RATIO_16_9 -> "En-Boy: 16:9"
                                AspectRatioMode.RATIO_4_3 -> "En-Boy: 4:3"
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
                            dragDirection = null
                            gestureScrubActive = false
                            gestureDragInitialPos = if (optimisticSeekPosition != null) optimisticSeekPosition!! else playerManager.currentPosition.value
                            currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        },
                        onDragEnd = {
                            registerInteraction()
                            if (gestureScrubActive && !isLive && duration > 0) {
                                val target = gestureScrubTargetMs
                                optimisticSeekPosition = target
                                playerManager.seekTo(target)
                            }
                            gestureScrubActive = false
                            dragStartOffset = null
                            dragDirection = null
                        },
                        onDragCancel = {
                            registerInteraction()
                            gestureScrubActive = false
                            dragStartOffset = null
                            dragDirection = null
                        },
                        onDrag = { change, dragAmount ->
                            registerInteraction()
                            change.consume()
                            val start = dragStartOffset ?: return@detectDragGestures

                            if (dragDirection == null) {
                                val dx = kotlin.math.abs(dragAmount.x)
                                val dy = kotlin.math.abs(dragAmount.y)
                                if (dx > 10f || dy > 10f) {
                                    dragDirection = if (dx > dy && !isLive && duration > 0) {
                                        "horizontal"
                                    } else {
                                        "vertical_center"
                                    }
                                }
                            }

                            when (dragDirection) {
                                "horizontal" -> {
                                    if (!isLive && duration > 0 && !isDoubleTapActive) {
                                        gestureScrubActive = true
                                        val totalDragX = change.position.x - start.x
                                        val timeDeltaMs = (totalDragX * 120L).toLong()
                                        val target = (gestureDragInitialPos + timeDeltaMs).coerceIn(0L, duration)
                                        gestureScrubTargetMs = target
                                        gestureScrubDiffMs = target - gestureDragInitialPos
                                    }
                                }
                                "vertical_center" -> {
                                    if (isLive) {
                                        val totalDragY = change.position.y - start.y
                                        if (kotlin.math.abs(totalDragY) > size.height * 0.2f) {
                                            if (totalDragY < 0) {
                                                // Yukarı kaydırma (Sonraki Kanal)
                                                val next = viewModel.zapNextChannel(streamId)
                                                if (next != null) seekRipple = "Sonraki: ${next.name} ⏭"
                                            } else {
                                                // Aşağı kaydırma (Önceki Kanal)
                                                val prev = viewModel.zapPreviousChannel(streamId)
                                                if (prev != null) seekRipple = "⏮ ${prev.name}"
                                            }
                                            dragDirection = "handled" // Tekrar tetiklenmemesi için
                                            showBottomInfoCard = true
                                        }
                                    }
                                }
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

        // YouTube Tarzı Sol Çift Tık (Geri Sarma Dalgası)
        AnimatedVisibility(
            visible = doubleTapSide == "left",
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(if (isPortrait) 0.65f else 0.85f)
                    .width(if (isPortrait) 130.dp else 220.dp)
                    .clip(RoundedCornerShape(topEnd = 160.dp, bottomEnd = 160.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (isPortrait) 36.dp else 48.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "-${doubleTapSeconds} sn",
                        color = Color.White,
                        fontSize = if (isPortrait) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // YouTube Tarzı Sağ Çift Tık (İleri Sarma Dalgası)
        AnimatedVisibility(
            visible = doubleTapSide == "right",
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(if (isPortrait) 0.65f else 0.85f)
                    .width(if (isPortrait) 130.dp else 220.dp)
                    .clip(RoundedCornerShape(topStart = 160.dp, bottomStart = 160.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.22f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (isPortrait) 36.dp else 48.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "+${doubleTapSeconds} sn",
                        color = Color.White,
                        fontSize = if (isPortrait) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // EKRAN KİLİDİ BİLGİLENDİRME BİLDİRİMİ
        AnimatedVisibility(
            visible = showLockBanner,
            enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xE6141923),
                border = BorderStroke(1.2.dp, TiviAccentLive.copy(alpha = 0.85f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TiviAccentLive,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "🔒 Ekran Kilidi Aktif — Kilidi açmak için ekrana çift tıklayın veya kumandadan OK tuşuna 2 kez basın.",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Tam Ekran Yatay Kaydırma (Scrub / Hızlı Arama HUD Kartı)
        AnimatedVisibility(
            visible = gestureScrubActive,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TiviOverlayDark,
                border = BorderStroke(1.5.dp, Color(0xFFFF2A6D).copy(alpha = 0.85f)),
                tonalElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDuration(gestureScrubTargetMs),
                            color = Color(0xFFFF2A6D),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val sign = if (gestureScrubDiffMs >= 0) "+" else "-"
                        val diffAbs = kotlin.math.abs(gestureScrubDiffMs)
                        Text(
                            text = "[$sign${formatDuration(diffAbs)}]",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (duration > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((gestureScrubTargetMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFFF2A6D))
                            )
                        }
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
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // Arayüzün boş alanına dokunulduğunda tüm arayüzü gizle
                                    showControls = false
                                    showBottomInfoCard = false
                                }
                            )
                        }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = if (isPortrait) 0.30f else 0.35f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = if (isPortrait) 0.40f else 0.45f)
                                )
                            )
                        )
                ) {
                // TOP BAR: Geri (Sol Üst), Başlık (Orta - Yalnızca VOD/Dizi), Saat / Uyku / PiP / Kilit (Sağ Üst)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            start = if (isPortrait) 10.dp else 18.dp,
                            end = if (isPortrait) 10.dp else 18.dp,
                            top = if (isPortrait) 22.dp else 10.dp,
                            bottom = if (isPortrait) 6.dp else 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BouncyIconButton(
                        onClick = {
                            if (!isLive) {
                                if (currentPosition > 0) {
                                    viewModel.saveWatchProgress(streamId, currentPosition, duration)
                                }
                                playerManager.release()
                            } else {
                                val curChan = allLiveStreams.firstOrNull { it.streamId == streamId } ?: lastPlayedLiveChannel
                                if (curChan != null) {
                                    viewModel.activePreviewChannel.value = curChan
                                }
                            }
                            onBack()
                        },
                        modifier = Modifier.size(if (isPortrait) 38.dp else 44.dp),
                        backgroundColor = Color.Black.copy(alpha = 0.25f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White,
                            modifier = Modifier.size(if (isPortrait) 22.dp else 26.dp)
                        )
                    }

                    // Orta Kısım: VOD / Dizi için Başlık (Canlı TV'de tekrarlayan rozetler kaldırıldı)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isLive) {
                            val activeEpisode = currentEpisode?.first
                            val displayTitle = if (activeEpisode != null) {
                                val seriesName = currentSeriesInfo?.info?.name ?: title
                                "$seriesName - S${activeEpisode.season} B${activeEpisode.episodeNum}"
                            } else {
                                title
                            }

                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = if (isPortrait) 13.5.sp else 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Sağ Üst: Gerçek zamanlı Saat (14:35), Uyku Zamanlayıcısı, PiP, Ekran Kilidi
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 4.dp else 8.dp)
                    ) {
                        if (currentTimeText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.35f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = currentTimeText,
                                    color = Color.White,
                                    fontSize = if (isPortrait) 12.sp else 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (sleepTimerSeconds != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TiviSecondary.copy(alpha = 0.35f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .clickable { showSleepTimerDialog = true }
                            ) {
                                Text(
                                    text = "🌙 ${sleepTimerSeconds!! / 60}d",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                            BouncyIconButton(
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
                                modifier = Modifier.size(if (isPortrait) 34.dp else 40.dp),
                                backgroundColor = Color.Black.copy(alpha = 0.25f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Küçük Pencere (PiP)",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isPortrait) 20.dp else 23.dp)
                                )
                            }
                        }

                        BouncyIconButton(
                            onClick = {
                                isControlsLocked = !isControlsLocked
                                if (!isControlsLocked) {
                                    showLockBanner = false
                                    seekRipple = "🔓 Ekran Kilidi Açıldı"
                                } else {
                                    showLockBanner = true
                                }
                            },
                            modifier = Modifier
                                .size(if (isPortrait) 34.dp else 40.dp)
                                .focusRequester(lockButtonFocusRequester),
                            backgroundColor = Color.Black.copy(alpha = 0.25f)
                        ) {
                            Icon(
                                imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Ekran Kilidi",
                                tint = if (isControlsLocked) TiviAccentLive else Color.White,
                                modifier = Modifier.size(if (isPortrait) 20.dp else 23.dp)
                            )
                        }
                    }
                }

                // CENTER CONTROLS (TACTILE BOUNCY GLASS BUTTONS) - YALNIZCA FİLM VE DİZİLER (VOD) İÇİN GÖSTERİLİR, CANLI TV'DE ORTA EKRAN TEMİZDİR
                if (!isControlsLocked && !isLive) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 32.dp else 48.dp)
                    ) {
                        BouncyIconButton(
                            onClick = {
                                playerManager.rewind10Seconds()
                                seekRipple = "-10s"
                            },
                            modifier = Modifier.size(if (isPortrait) 48.dp else 56.dp),
                            backgroundColor = Color.Black.copy(alpha = 0.35f),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "10s Geri",
                                tint = Color.White,
                                modifier = Modifier.size(if (isPortrait) 30.dp else 36.dp)
                            )
                        }

                        BouncyIconButton(
                            onClick = { playerManager.togglePlayPause() },
                            modifier = Modifier
                                .size(if (isPortrait) 66.dp else 78.dp)
                                .focusRequester(firstButtonFocusRequester),
                            backgroundColor = Color.Black.copy(alpha = 0.45f),
                            shape = CircleShape,
                            pressedScale = 0.88f
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Oynat/Duraklat",
                                tint = Color.White,
                                modifier = Modifier.size(if (isPortrait) 44.dp else 54.dp)
                            )
                        }

                        BouncyIconButton(
                            onClick = {
                                playerManager.forward10Seconds()
                                seekRipple = "+10s"
                            },
                            modifier = Modifier.size(if (isPortrait) 48.dp else 56.dp),
                            backgroundColor = Color.Black.copy(alpha = 0.35f),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "10s İleri",
                                tint = Color.White,
                                modifier = Modifier.size(if (isPortrait) 30.dp else 36.dp)
                            )
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
                                vertical = if (isPortrait) 4.dp else 8.dp
                            )
                    ) {
                        if (isLive) {
                            val currentChannel = allLiveStreams.find { it.streamId == streamId }
                                ?: LiveStream(streamId = streamId, name = title, categoryId = categoryId)

                            // TIVIMATE BİRLEŞİK ALT BİLGİ PANELİ & AKSIYON BUTONLARI DOCK'U
                            TiviBottomInfoCard(
                                visible = (showControls || showBottomInfoCard) && !showOverlayGuide,
                                channel = currentChannel,
                                epg = epgList.firstOrNull(),
                                isFavorite = isFavorite,
                                diagnostics = streamDiagnostics,
                                onToggleFavorite = { viewModel.toggleFavoriteLive(streamId.toString()) },
                                actions = if (showControls && !showOverlayGuide) {
                                    {
                                        val livePillsContent: @Composable () -> Unit = {
                                            PlayerPillButton(
                                                text = "Rehber",
                                                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                                modifier = Modifier.focusRequester(firstButtonFocusRequester),
                                                onClick = {
                                                    showOverlayGuide = true
                                                    showControls = false
                                                },
                                                isActive = showOverlayGuide,
                                                activeColor = OpusCyan,
                                                isPortrait = isPortrait
                                            )

                                            PlayerPillButton(
                                                text = "Son Kanallar",
                                                icon = Icons.Default.History,
                                                onClick = { showRecentChannelsBar = !showRecentChannelsBar },
                                                isActive = showRecentChannelsBar,
                                                isPortrait = isPortrait
                                            )

                                            if (lastPlayedLiveChannel != null && lastPlayedLiveChannel?.streamId != streamId) {
                                                PlayerPillButton(
                                                    text = "↩ ${lastPlayedLiveChannel?.name?.take(10) ?: "Önceki"}",
                                                    icon = Icons.Default.Replay,
                                                    onClick = {
                                                        val prev = viewModel.zapRecall()
                                                        if (prev != null) seekRipple = "↩ ${prev.name}"
                                                    },
                                                    isActive = true,
                                                    activeColor = TiviGold,
                                                    isPortrait = isPortrait
                                                )
                                            }

                                            PlayerPillButton(
                                                text = "Ses & Altyazı",
                                                icon = Icons.Default.Subtitles,
                                                onClick = { showTrackSheet = true },
                                                isPortrait = isPortrait
                                            )

                                            PlayerPillButton(
                                                text = when (aspectRatio) {
                                                    AspectRatioMode.FIT -> "Orijinal"
                                                    AspectRatioMode.FILL -> "Doldur"
                                                    AspectRatioMode.ZOOM -> "Zoom"
                                                    AspectRatioMode.RATIO_16_9 -> "16:9"
                                                    AspectRatioMode.RATIO_4_3 -> "4:3"
                                                },
                                                icon = Icons.Default.AspectRatio,
                                                onClick = {
                                                    val newMode = playerManager.cycleAspectRatio()
                                                    seekRipple = when (newMode) {
                                                        AspectRatioMode.FIT -> "Orijinal Oran"
                                                        AspectRatioMode.FILL -> "Ekranı Doldur"
                                                        AspectRatioMode.ZOOM -> "Yakınlaştır"
                                                        AspectRatioMode.RATIO_16_9 -> "16:9 Oran"
                                                        AspectRatioMode.RATIO_4_3 -> "4:3 Oran"
                                                    }
                                                },
                                                isPortrait = isPortrait
                                            )

                                            PlayerPillButton(
                                                text = if (isDualViewActive) "Çift Ekran Açık" else "Çift Ekran",
                                                icon = Icons.Default.GridView,
                                                onClick = { showDualViewPicker = true },
                                                isActive = isDualViewActive,
                                                activeColor = TiviPrimary,
                                                isPortrait = isPortrait
                                            )

                                            PlayerPillButton(
                                                text = "123",
                                                icon = Icons.Default.Dialpad,
                                                onClick = { showNumericKeypad = true },
                                                isPortrait = isPortrait
                                            )

                                            PlayerPillButton(
                                                text = "Kategoriler",
                                                icon = Icons.Default.Category,
                                                onClick = { showChannelDrawer = !showChannelDrawer },
                                                isActive = showChannelDrawer,
                                                activeColor = OpusCyan,
                                                isPortrait = isPortrait
                                            )
                                        }

                                        if (isPortrait) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                livePillsContent()
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    livePillsContent()
                                                }
                                            }
                                        }
                                    }
                                } else null
                            )
                        } else {
                            // VOD (FİLM VE DİZİ) KONTROLLERİ
                            if (duration > 0) {
                                AnimatedVisibility(
                                    visible = isScrubbing,
                                    enter = fadeIn(tween(150)) + expandVertically(),
                                    exit = fadeOut(tween(150)) + shrinkVertically()
                                ) {
                                    val scrubMillis = scrubPosition.toLong()
                                    val basePos = if (optimisticSeekPosition != null) optimisticSeekPosition!! else currentPosition
                                    val diffSeconds = (scrubMillis - basePos) / 1000L
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
                                            border = BorderStroke(1.dp, Color(0xFFFF2A6D).copy(alpha = 0.7f)),
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
                                                            color = Color(0xFFFF2A6D),
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

                                val currentPosVal = effectivePosition
                                val remainingMs = (duration - currentPosVal).coerceAtLeast(0L)
                                val remainingText = "-${formatDuration(remainingMs)}"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = if (isPortrait) 4.dp else 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatDuration(currentPosVal),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val currentFraction = if (duration > 0) {
                                        (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    val bufferFraction = if (duration > 0) {
                                        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                    } else 0f

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSliderFocused) 2.dp else 0.dp,
                                                color = if (isSliderFocused) OpusCyan else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(if (isSliderFocused) 4.dp else 2.dp)
                                            .focusRequester(sliderFocusRequester)
                                            .onFocusChanged { isSliderFocused = it.isFocused }
                                            .focusProperties {
                                                down = bottomRowFocusRequester
                                            }
                                            .focusable()
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                        try { bottomRowFocusRequester.requestFocus() } catch (e: Exception) {}
                                                        registerInteraction()
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                        val target = (effectivePosition - 10000L).coerceAtLeast(0L)
                                                        optimisticSeekPosition = target
                                                        playerManager.seekTo(target)
                                                        seekRipple = "-10s"
                                                        registerInteraction()
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                        val target = (effectivePosition + 10000L).coerceAtMost(duration)
                                                        optimisticSeekPosition = target
                                                        playerManager.seekTo(target)
                                                        seekRipple = "+10s"
                                                        registerInteraction()
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                                    KeyEvent.KEYCODE_ENTER,
                                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                                        playerManager.togglePlayPause()
                                                        registerInteraction()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            }
                                            .pointerInput(duration) {
                                                detectTapGestures { offset ->
                                                    if (size.width > 0 && duration > 0) {
                                                        val tapFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                                        val target = (tapFraction * duration).toLong()
                                                        optimisticSeekPosition = target
                                                        playerManager.seekTo(target)
                                                        registerInteraction()
                                                    }
                                                }
                                            }
                                            .pointerInput(duration) {
                                                detectHorizontalDragGestures(
                                                    onDragStart = { offset ->
                                                        if (size.width > 0 && duration > 0) {
                                                            isScrubbing = true
                                                            val startFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                                            scrubPosition = startFraction * duration
                                                            registerInteraction()
                                                        }
                                                    },
                                                    onHorizontalDrag = { change, _ ->
                                                        if (size.width > 0 && duration > 0) {
                                                            val dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                                            scrubPosition = dragFraction * duration
                                                            registerInteraction()
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val target = scrubPosition.toLong().coerceIn(0L, duration)
                                                        optimisticSeekPosition = target
                                                        playerManager.seekTo(target)
                                                        isScrubbing = false
                                                        registerInteraction()
                                                    },
                                                    onDragCancel = {
                                                        isScrubbing = false
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        // Custom Seekbar UI (Track + Buffer + Active + Thumb)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(if (isSliderFocused) 8.dp else 6.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.2f))
                                        ) {
                                            if (bufferFraction > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(bufferFraction)
                                                        .fillMaxHeight()
                                                        .background(Color.White.copy(alpha = 0.35f))
                                                )
                                            }
                                            if (currentFraction > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(currentFraction)
                                                        .fillMaxHeight()
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(
                                                                    Color(0xFFFF2A6D),
                                                                    if (isSliderFocused) OpusCyan else Color(0xFFFF5E8A)
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }

                                        // Thumb
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(18.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(currentFraction.coerceAtLeast(0.001f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (isSliderFocused) 16.dp else 12.dp)
                                                        .align(Alignment.CenterEnd)
                                                        .clip(CircleShape)
                                                        .background(if (isSliderFocused) OpusCyan else Color.White)
                                                        .border(
                                                            width = 2.dp,
                                                            color = Color(0xFFFF2A6D),
                                                            shape = CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = remainingText,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(if (isPortrait) 6.dp else 10.dp))

                            val vodPillsContent: @Composable () -> Unit = {
                                PlayerPillButton(
                                    text = "Baştan Başlat",
                                    icon = Icons.Default.Replay,
                                    onClick = {
                                        playerManager.seekTo(0L)
                                        seekRipple = "Baştan Başlatıldı"
                                    },
                                    isPortrait = isPortrait,
                                    modifier = Modifier
                                        .focusRequester(bottomRowFocusRequester)
                                        .focusProperties { up = sliderFocusRequester }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                try { sliderFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            } else false
                                        }
                                )

                                PlayerPillButton(
                                    text = when (aspectRatio) {
                                        AspectRatioMode.FIT -> "Orijinal"
                                        AspectRatioMode.FILL -> "Doldur"
                                        AspectRatioMode.ZOOM -> "Zoom"
                                        AspectRatioMode.RATIO_16_9 -> "16:9"
                                        AspectRatioMode.RATIO_4_3 -> "4:3"
                                    },
                                    icon = Icons.Default.AspectRatio,
                                    onClick = {
                                        val newMode = playerManager.cycleAspectRatio()
                                        seekRipple = when (newMode) {
                                            AspectRatioMode.FIT -> "Orijinal Oran"
                                            AspectRatioMode.FILL -> "Ekranı Doldur"
                                            AspectRatioMode.ZOOM -> "Yakınlaştır"
                                            AspectRatioMode.RATIO_16_9 -> "16:9 Oran"
                                            AspectRatioMode.RATIO_4_3 -> "4:3 Oran"
                                        }
                                    },
                                    isPortrait = isPortrait,
                                    modifier = Modifier
                                        .focusProperties { up = sliderFocusRequester }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                try { sliderFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            } else false
                                        }
                                )

                                PlayerPillButton(
                                    text = "Ses & Altyazı",
                                    icon = Icons.Default.Subtitles,
                                    onClick = { showTrackSheet = true },
                                    isPortrait = isPortrait,
                                    modifier = Modifier
                                        .focusProperties { up = sliderFocusRequester }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                try { sliderFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            } else false
                                        }
                                )

                                if (currentSeriesInfo != null && !currentSeriesInfo?.episodes.isNullOrEmpty()) {
                                    PlayerPillButton(
                                        text = "Bölümler",
                                        icon = Icons.AutoMirrored.Filled.List,
                                        onClick = { showEpisodesDrawer = true },
                                        isActive = showEpisodesDrawer,
                                        activeColor = Color(0xFFFF2A6D),
                                        isPortrait = isPortrait,
                                        modifier = Modifier
                                            .focusProperties { up = sliderFocusRequester }
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                    try { sliderFocusRequester.requestFocus() } catch (e: Exception) {}
                                                    true
                                                } else false
                                            }
                                    )
                                }

                                if (!isLive && nextEpisode != null) {
                                    PlayerPillButton(
                                        text = "Sonraki Bölüm",
                                        icon = Icons.Default.SkipNext,
                                        onClick = { viewModel.playNextEpisode() },
                                        isActive = true,
                                        activeColor = Color(0xFFFF2A6D),
                                        isPortrait = isPortrait,
                                        modifier = Modifier
                                            .focusProperties { up = sliderFocusRequester }
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                    try { sliderFocusRequester.requestFocus() } catch (e: Exception) {}
                                                    true
                                                } else false
                                            }
                                    )
                                }

                                PlayerPillButton(
                                    text = "${playbackSpeed}x Hız",
                                    icon = Icons.Default.Speed,
                                    onClick = {
                                        val newSpeed = playerManager.cyclePlaybackSpeed()
                                        seekRipple = "Hız: ${newSpeed}x"
                                    },
                                    isPortrait = isPortrait,
                                    modifier = Modifier
                                        .focusProperties { up = sliderFocusRequester }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                try { sliderFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            } else false
                                        }
                                )

                                PlayerPillButton(
                                    text = "Kontrol Paneli",
                                    icon = Icons.Default.Tune,
                                    onClick = {
                                        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        currentBrightness = activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0f) 0.5f else it }
                                        hudInitialTab = 0
                                        showPlayerHUD = !showPlayerHUD
                                    },
                                    isActive = showPlayerHUD,
                                    activeColor = Color(0xFFFF2A6D),
                                    isPortrait = isPortrait,
                                    modifier = Modifier
                                        .focusProperties { up = sliderFocusRequester }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                try { sliderFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            } else false
                                        }
                                )
                            }

                            if (isPortrait) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    vodPillsContent()
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        vodPillsContent()
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

                    // 4. DECODER MODE (HW / SW)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Video Çözücü (HW/SW)", color = TiviPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val currentMode = viewModel.preferences.decoderModeFlow.collectAsState(initial = "HARDWARE").value
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("HARDWARE" to "Donanım (Önerilen)", "SOFTWARE" to "Yazılım (Güvenli)").forEach { (mode, label) ->
                            val isSelected = currentMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TiviPrimary.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) TiviPrimary else TiviCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setDecoderMode(mode) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) TiviPrimary else TiviTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // 5. UYKU ZAMANLAYICISI
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

        // UNIFIED MODERN PLAYER HUD (SES & EKRAN / YAYIN TEŞHİS)
        TiviPlayerHUD(
            visible = showPlayerHUD,
            initialTab = hudInitialTab,
            diagnostics = streamDiagnostics,
            currentBrightness = currentBrightness,
            onBrightnessChange = { newB ->
                currentBrightness = newB
                activity?.window?.let { window ->
                    val lp = window.attributes
                    lp.screenBrightness = newB.coerceIn(0.01f, 1f)
                    window.attributes = lp
                }
            },
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            onVolumeChange = { newV ->
                currentVolume = newV
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
            },
            aspectRatio = aspectRatio,
            onAspectRatioChange = { mode ->
                playerManager.setAspectRatio(mode)
            },
            onClose = { showPlayerHUD = false },
            isPortrait = isPortrait,
            modifier = Modifier
                .align(if (isPortrait) Alignment.BottomCenter else Alignment.TopEnd)
                .statusBarsPadding()
                .padding(if (isPortrait) PaddingValues(start = 12.dp, end = 12.dp, bottom = 65.dp) else PaddingValues(top = 55.dp, end = 18.dp))
        )

        // BÖLÜMLER SEÇİCİ BOTTOM SHEET (DİZİ MODUNDA)
        if (showEpisodesDrawer && currentSeriesInfo != null) {
            val episodesMap = currentSeriesInfo?.episodes ?: emptyMap()
            val seasonKeys = episodesMap.keys.sortedBy { it.toIntOrNull() ?: 0 }
            val activeEpisode = currentEpisode?.first
            var activeSeasonKey by remember(activeEpisode, seasonKeys) {
                mutableStateOf(activeEpisode?.season?.toString() ?: seasonKeys.firstOrNull() ?: "1")
            }

            ModalBottomSheet(
                onDismissRequest = { showEpisodesDrawer = false },
                containerColor = TiviSurface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = currentSeriesInfo?.info?.name ?: title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (seasonKeys.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            items(seasonKeys) { sKey ->
                                val isSel = sKey == activeSeasonKey
                                Surface(
                                    onClick = { activeSeasonKey = sKey },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSel) Color(0xFFFF2A6D).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFFFF2A6D) else Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                        Text(
                                            text = "Sezon $sKey",
                                            color = if (isSel) Color(0xFFFF2A6D) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val seasonEpisodes = episodesMap[activeSeasonKey] ?: emptyList()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(seasonEpisodes) { ep ->
                            val isCurrentEp = activeEpisode?.id == ep.id
                            Surface(
                                onClick = {
                                    viewModel.playSeriesEpisode(ep, currentSeriesInfo?.info?.name ?: title)
                                    showEpisodesDrawer = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrentEp) Color(0xFFFF2A6D).copy(alpha = 0.2f) else Color(0xFF141A26),
                                border = BorderStroke(1.dp, if (isCurrentEp) Color(0xFFFF2A6D) else Color(0xFF1E2638)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrentEp) Color(0xFFFF2A6D) else Color.White.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ep.episodeNum.toString(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ep.title.ifBlank { "Bölüm ${ep.episodeNum}" },
                                            color = if (isCurrentEp) Color(0xFFFF2A6D) else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrentEp) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!ep.info?.plot.isNullOrBlank()) {
                                            Text(
                                                text = ep.info?.plot ?: "",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (isCurrentEp) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Oynatılıyor",
                                            tint = Color(0xFFFF2A6D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
                        AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                    AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
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

@OptIn(UnstableApi::class)
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
                val (initialResizeMode, initialScaleX, initialScaleY) = when (aspectRatio) {
                    AspectRatioMode.FIT -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                    AspectRatioMode.FILL -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.ZOOM -> Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.18f, 1.18f)
                    AspectRatioMode.RATIO_16_9 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.RATIO_4_3 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0.75f, 1.0f)
                }
                resizeMode = initialResizeMode
                scaleX = initialScaleX
                scaleY = initialScaleY
            }
        },
        update = { playerView ->
            val currentPlayer = playerManager.player ?: playerManager.initializePlayer(isLive)
            if (playerView.player != currentPlayer) {
                playerView.player = currentPlayer
            }
            val (targetResizeMode, targetScaleX, targetScaleY) = when (aspectRatio) {
                AspectRatioMode.FIT -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                AspectRatioMode.FILL -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                AspectRatioMode.ZOOM -> Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.18f, 1.18f)
                AspectRatioMode.RATIO_16_9 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                AspectRatioMode.RATIO_4_3 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0.75f, 1.0f)
            }
            var layoutChanged = false
            if (playerView.resizeMode != targetResizeMode) {
                playerView.resizeMode = targetResizeMode
                layoutChanged = true
            }
            if (playerView.scaleX != targetScaleX || playerView.scaleY != targetScaleY) {
                playerView.scaleX = targetScaleX
                playerView.scaleY = targetScaleY
                layoutChanged = true
            }
            if (layoutChanged) {
                playerView.requestLayout()
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
            playerView.player = null
        },
        modifier = modifier
    )
}

