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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import java.util.Locale
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
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.player.components.NumericKeypadDialog
import com.tivimax.player.ui.player.components.TiviBottomInfoCard
import com.tivimax.player.ui.player.components.TiviNumericZapperBox
import com.tivimax.player.ui.player.components.TiviOverlayGuide
import com.tivimax.player.ui.player.components.TiviRecentChannelsBar
import com.tivimax.player.ui.player.components.PlayerSettingsPanel
import com.tivimax.player.ui.player.components.SettingsTab
import com.tivimax.player.ui.player.components.PlayerPillButton
import com.tivimax.player.ui.player.components.BouncyIconButton
import com.tivimax.player.ui.player.components.LocalPlayerInteraction
import com.tivimax.player.ui.player.components.LivePlayerActionBar
import com.tivimax.player.ui.player.components.LivePlayerMoreSheet
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

import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.LocalDeviceMode
import com.tivimax.player.util.LocalIsTvDevice
import com.tivimax.player.util.LocalOrientationManager

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

    val deviceMode = LocalDeviceMode.current
    val orientationManager = LocalOrientationManager.current

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
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
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
    var showSettingsPanel by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableStateOf(SettingsTab.AUDIO) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showOverlayGuide by remember { mutableStateOf(false) }
    var showRecentChannelsBar by remember { mutableStateOf(false) }
    var showNumericKeypad by remember { mutableStateOf(false) }
    var showCategoryManagementDialog by remember { mutableStateOf(false) }
    var showBottomInfoCard by remember { mutableStateOf(true) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

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
    var infoBannerText by remember { mutableStateOf<String?>(null) }
    var lastInfoBannerTime by remember { mutableLongStateOf(0L) }
    var dragStartOffset by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    // Sarma Göstergesi Durumu (±10sn, birikmeli, 700ms)
    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var seekAccumulatedSeconds by remember { mutableIntStateOf(0) }
    var lastSeekTriggerTime by remember { mutableLongStateOf(0L) }

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

    LaunchedEffect(lastSeekTriggerTime) {
        if (seekDirection != null) {
            delay(700L)
            seekDirection = null
            seekAccumulatedSeconds = 0
        }
    }

    LaunchedEffect(lastInfoBannerTime) {
        if (infoBannerText != null) {
            delay(1200L)
            infoBannerText = null
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
    val isUserInteracting = isScrubbing || gestureScrubActive || (seekDirection != null) || (optimisticSeekPosition != null) || (infoBannerText != null) || showChannelDrawer || showSettingsPanel || showSleepTimerDialog || showOverlayGuide || showRecentChannelsBar || showNumericKeypad || showCategoryManagementDialog || isZapperActive || showEpisodesDrawer || showMoreSheet || (dragStartOffset != null)

    LaunchedEffect(isUserInteracting) {
        lastInteractionTime = System.currentTimeMillis()
    }

    val registerInteraction: () -> Unit = remember {
        { lastInteractionTime = System.currentTimeMillis() }
    }

    val showInfoBanner: (String) -> Unit = remember {
        { message ->
            infoBannerText = message
            lastInfoBannerTime = System.currentTimeMillis()
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    val triggerSeek: (Boolean) -> Unit = { isForward ->
        lastInteractionTime = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val isQuickSuccession = (now - lastSeekTriggerTime) < 700L
        val targetDirection = if (isForward) SeekDirection.FORWARD else SeekDirection.REWIND

        if (seekDirection == targetDirection && isQuickSuccession) {
            seekAccumulatedSeconds += 10
        } else {
            seekDirection = targetDirection
            seekAccumulatedSeconds = 10
        }
        lastSeekTriggerTime = now

        if (isForward) {
            playerManager.forward10Seconds()
        } else {
            playerManager.rewind10Seconds()
        }
    }

    // Akıllı HUD ve Tam Ekran Zamanlayıcısı (Canlı TV: 4000ms, VOD: 7000ms)
    // Kullanıcı işlem yaptığı sürece ASLA kapanmaz, her dokunuşta süre sıfırlanır.
    val HUD_AUTO_HIDE_TIMEOUT_MS = if (isLive) 4000L else 7000L

    // Kanal değiştiğinde alt bilgi paneli 4 saniye görünür ve sayaç başlar
    LaunchedEffect(streamId) {
        if (isLive) {
            showBottomInfoCard = true
            registerInteraction()
        }
    }

    LaunchedEffect(showControls, showBottomInfoCard, isPlaying, lastInteractionTime, isUserInteracting, isControlsLocked) {
        if ((showControls || showBottomInfoCard) && isPlaying && !isUserInteracting) {
            val timeout = if (isControlsLocked) 4000L else if (isLive) 4000L else 7000L
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

    // Tam Ekran (Immersive Mode): Merkezi OrientationManager üzerinden yönet
    DisposableEffect(Unit) {
        orientationManager?.setImmersiveMode(true)
        onDispose {
            // Çıkış sonrası tam ekran bayrağı hâlâ açıksa gösterme
            if (!viewModel.isVideoFullscreen.value && viewModel.deviceMode.value != com.tivimax.player.util.DeviceMode.TV) {
                orientationManager?.setImmersiveMode(false)
            }
            orientationManager?.stopOrientationReleaseListener()
        }
    }

    // Yön değişimi (portrait <-> landscape) tamamlandığında immersive modunu yeniden uygula
    LaunchedEffect(configuration.orientation) {
        if (viewModel.currentScreen.value is Screen.Player || viewModel.isVideoFullscreen.value) {
            orientationManager?.setImmersiveMode(true)
        }
    }

    // Oynatıcı kontrolleri otomatik gizlendiğinde (HUD auto-hide) immersive modunu yeniden uygula
    LaunchedEffect(showControls, showBottomInfoCard) {
        if (!showControls && !showBottomInfoCard) {
            if (viewModel.currentScreen.value is Screen.Player || viewModel.isVideoFullscreen.value) {
                orientationManager?.setImmersiveMode(true)
            }
        }
    }

    // Modal BottomSheet / Dialog'lar kapandığında immersive modunu yeniden uygula
    val anyOverlayOpen = showSettingsPanel || showMoreSheet ||
            showSleepTimerDialog || showNumericKeypad || showChannelDrawer ||
            showOverlayGuide || showEpisodesDrawer || showDualViewPicker ||
            showCategoryManagementDialog
    LaunchedEffect(anyOverlayOpen) {
        if (!anyOverlayOpen) {
            if (viewModel.currentScreen.value is Screen.Player || viewModel.isVideoFullscreen.value) {
                orientationManager?.setImmersiveMode(true)
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
    // Lifecycle Observer (Arka plana atıldığında PiP açık değilse videoyu duraklat)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isInPipMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (!isInPipMode) {
                    playerManager.pause()
                }
            } else if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                if (!isControlsLocked && !isInPipMode) {
                    playerManager.play()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Video izlerken ekranın kapanmasını önle (KEEP_SCREEN_ON) ve çıkışta belleği temizle
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (viewModel.currentScreen.value !is Screen.Player && !viewModel.isMiniPlayerActive.value) {
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
        } else if (showSettingsPanel) {
            showSettingsPanel = false
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
                                showInfoBanner("🔓 Ekran Kilidi Açıldı")
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
                                    if (next != null) showInfoBanner("Sonraki: ${next.name} ⏭")
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
                    if (showOverlayGuide || showRecentChannelsBar || showEpisodesDrawer || showSettingsPanel || showChannelDrawer) {
                        registerInteraction()
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            if (showOverlayGuide) showOverlayGuide = false
                            else if (showRecentChannelsBar) showRecentChannelsBar = false
                            else if (showEpisodesDrawer) showEpisodesDrawer = false
                            else if (showSettingsPanel) showSettingsPanel = false
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
                                if (next != null) showInfoBanner("Sonraki: ${next.name} ⏭")
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
                                triggerSeek(false)
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isLive) {
                                val prev = viewModel.zapRecall()
                                if (prev != null) {
                                    showInfoBanner("↩ ${prev.name}")
                                } else {
                                    showRecentChannelsBar = true
                                }
                                showBottomInfoCard = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            } else {
                                triggerSeek(true)
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
                            showInfoBanner("🔓 Ekran Kilidi Açıldı")
                        } else {
                            if (!isLive) {
                                if (offset.x < size.width * 0.38f) {
                                    // Sol tarafa çift tık: Geri sar
                                    triggerSeek(false)
                                } else if (offset.x > size.width * 0.62f) {
                                    // Sağ tarafa çift tık: İleri sar
                                    triggerSeek(true)
                                } else {
                                    // Ortaya çift tık: En-Boy Oranı değiştir
                                    val newMode = playerManager.cycleAspectRatio()
                                    showInfoBanner(
                                        when (newMode) {
                                            AspectRatioMode.FIT -> "En-Boy: Orijinal"
                                            AspectRatioMode.FILL -> "En-Boy: Doldur"
                                            AspectRatioMode.ZOOM -> "En-Boy: Yakınlaştır"
                                            AspectRatioMode.RATIO_16_9 -> "En-Boy: 16:9"
                                            AspectRatioMode.RATIO_4_3 -> "En-Boy: 4:3"
                                        }
                                    )
                                }
                            } else {
                                // Canlı TV'de en-boy oranı döngüsü
                                val newMode = playerManager.cycleAspectRatio()
                                showInfoBanner(
                                    when (newMode) {
                                        AspectRatioMode.FIT -> "En-Boy: Orijinal"
                                        AspectRatioMode.FILL -> "En-Boy: Doldur"
                                        AspectRatioMode.ZOOM -> "En-Boy: Yakınlaştır"
                                        AspectRatioMode.RATIO_16_9 -> "En-Boy: 16:9"
                                        AspectRatioMode.RATIO_4_3 -> "En-Boy: 4:3"
                                    }
                                )
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
                                    if (!isLive && duration > 0 && seekDirection == null) {
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
                                                if (next != null) showInfoBanner("Sonraki: ${next.name} ⏭")
                                            } else {
                                                // Aşağı kaydırma (Önceki Kanal)
                                                val prev = viewModel.zapPreviousChannel(streamId)
                                                if (prev != null) showInfoBanner("⏮ ${prev.name}")
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
                subtitleDelayMs = subtitleDelayMs,
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
                            colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary, contentColor = VegaColors.OnAccent),
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


        // EKRAN KİLİDİ BİLGİLENDİRME BİLDİRİMİ
        AnimatedVisibility(
            visible = showLockBanner,
            enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = VegaShapes.Medium,
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.2.dp, VegaColors.Accent.copy(alpha = 0.85f)),
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
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Ekran kilidi aktif — Kilidi açmak için ekrana çift dokun veya kumandadan OK tuşuna 2 kez bas.",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Secondary,
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
                shape = VegaShapes.Pill,
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.5.dp, VegaColors.Accent.copy(alpha = 0.85f)),
                tonalElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDuration(gestureScrubTargetMs),
                            color = VegaColors.Accent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val sign = if (gestureScrubDiffMs >= 0) "+" else "-"
                        val diffAbs = kotlin.math.abs(gestureScrubDiffMs)
                        Text(
                            text = "[$sign${formatDuration(diffAbs)}]",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary,
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
                                .background(VegaColors.Border)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((gestureScrubTargetMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(VegaColors.Accent)
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
                .displayCutoutPadding()
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
            visible = showControls && !isInPipMode && !showSettingsPanel,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            if (isControlsLocked) {
                // Dokunma kilidi aktifken ekrana dokunulduğunda yalnızca köşede kilidi aç butonu görünür
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
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
                        .displayCutoutPadding()
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
                        backgroundColor = if (isLive) VegaColors.OverlayPill else Color.Black.copy(alpha = 0.25f),
                        border = if (isLive) BorderStroke(1.dp, VegaColors.Border) else null
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
                    if (isLive) {
                        // CANLI TV İÇİN TEK YARI SAYDAM HAP
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.OverlayPill,
                            border = BorderStroke(1.dp, VegaColors.Border)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 2.dp else 4.dp),
                                modifier = Modifier.padding(horizontal = if (isPortrait) 6.dp else 8.dp, vertical = 2.dp)
                            ) {
                                if (currentTimeText.isNotBlank()) {
                                    Text(
                                        text = currentTimeText,
                                        color = Color.White,
                                        fontSize = if (isPortrait) 12.sp else 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                if (sleepTimerSeconds != null) {
                                    var isTimerFocused by remember { mutableStateOf(false) }
                                    Surface(
                                        onClick = { showSleepTimerDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isTimerFocused) OpusCyan.copy(alpha = 0.35f) else Color.Transparent,
                                        border = if (isTimerFocused) BorderStroke(1.5.dp, OpusCyan) else null,
                                        modifier = Modifier
                                            .height(if (isPortrait) 32.dp else 36.dp)
                                            .onFocusChanged { isTimerFocused = it.isFocused }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        ) {
                                            Text(
                                                text = "🌙 ${sleepTimerSeconds!! / 60}d",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    BouncyIconButton(
                                        onClick = { showSleepTimerDialog = true },
                                        modifier = Modifier.size(if (isPortrait) 32.dp else 36.dp),
                                        backgroundColor = Color.Transparent
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bedtime,
                                            contentDescription = "Uyku Zamanlayıcısı",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
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
                                        modifier = Modifier.size(if (isPortrait) 32.dp else 36.dp),
                                        backgroundColor = Color.Transparent
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureInPictureAlt,
                                            contentDescription = "Küçük Pencere (PiP)",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Telefon modunda tek dokunuşla "Döndür" (yatay <-> dikey)
                                if (deviceMode == DeviceMode.PHONE && orientationManager != null) {
                                    BouncyIconButton(
                                        onClick = {
                                            orientationManager.rotatePhonePlayer(
                                                viewModel.currentScreen.value,
                                                deviceMode
                                            )
                                            registerInteraction()
                                        },
                                        modifier = Modifier.size(if (isPortrait) 32.dp else 36.dp),
                                        backgroundColor = Color.Transparent
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ScreenRotation,
                                            contentDescription = "Döndür",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                BouncyIconButton(
                                    onClick = {
                                        isControlsLocked = !isControlsLocked
                                        if (!isControlsLocked) {
                                            showLockBanner = false
                                            showInfoBanner("🔓 Ekran Kilidi Açıldı")
                                        } else {
                                            showLockBanner = true
                                        }
                                    },
                                    modifier = Modifier
                                        .size(if (isPortrait) 32.dp else 36.dp)
                                        .focusRequester(lockButtonFocusRequester),
                                    backgroundColor = Color.Transparent
                                ) {
                                    Icon(
                                        imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Ekran Kilidi",
                                        tint = if (isControlsLocked) TiviAccentLive else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // VOD (Film / Dizi) için mevcut üst sağ grup aynen korunur
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
                                var isTimerFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = { showSleepTimerDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isTimerFocused) OpusCyan.copy(alpha = 0.45f) else TiviSecondary.copy(alpha = 0.35f),
                                    border = if (isTimerFocused) BorderStroke(2.dp, OpusCyan) else null,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .onFocusChanged { isTimerFocused = it.isFocused }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "🌙 ${sleepTimerSeconds!! / 60}d",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                BouncyIconButton(
                                    onClick = { showSleepTimerDialog = true },
                                    modifier = Modifier.size(40.dp),
                                    backgroundColor = Color.Black.copy(alpha = 0.25f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Uyku Zamanlayıcısı",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
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

                            // Telefon modunda tek dokunuşla "Döndür" (yatay <-> dikey)
                            if (deviceMode == DeviceMode.PHONE && orientationManager != null) {
                                BouncyIconButton(
                                    onClick = {
                                        orientationManager.rotatePhonePlayer(
                                            viewModel.currentScreen.value,
                                            deviceMode
                                        )
                                        registerInteraction()
                                    },
                                    modifier = Modifier.size(if (isPortrait) 34.dp else 40.dp),
                                    backgroundColor = Color.Black.copy(alpha = 0.25f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Döndür",
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
                                        showInfoBanner("🔓 Ekran Kilidi Açıldı")
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
                                triggerSeek(false)
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
                            onClick = {
                                playerManager.togglePlayPause()
                                registerInteraction()
                            },
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
                                triggerSeek(true)
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
                            .displayCutoutPadding()
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
                                upcomingEpg = epgList.drop(1).take(3),
                                onDismiss = {
                                    showControls = false
                                    showBottomInfoCard = false
                                },
                                actions = if (showControls && !showOverlayGuide) {
                                    {
                                        LivePlayerActionBar(
                                            modifier = if (isPortrait) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                                            onOpenGuide = {
                                                showOverlayGuide = true
                                                showControls = false
                                            },
                                            isGuideActive = showOverlayGuide,
                                            onToggleRecentChannels = {
                                                showRecentChannelsBar = !showRecentChannelsBar
                                            },
                                            isRecentActive = showRecentChannelsBar,
                                            hasPreviousChannel = lastPlayedLiveChannel != null && lastPlayedLiveChannel?.streamId != streamId,
                                            previousChannelName = lastPlayedLiveChannel?.name,
                                            onZapRecall = {
                                                val prev = viewModel.zapRecall()
                                                if (prev != null) showInfoBanner("↩ ${prev.name}")
                                            },
                                            onOpenAudioSubtitles = {
                                                settingsInitialTab = SettingsTab.AUDIO
                                                showSettingsPanel = true
                                            },
                                            onOpenMore = {
                                                showMoreSheet = true
                                            },
                                            firstButtonFocusRequester = firstButtonFocusRequester
                                        )
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
                                            shape = VegaShapes.Medium,
                                            color = VegaColors.OverlayPanel,
                                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.7f)),
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
                                                            .clip(VegaShapes.Small)
                                                            .background(Color.Black)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = formatDuration(scrubMillis),
                                                            color = VegaColors.Accent,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "/ ${formatDuration(duration)}",
                                                            color = VegaColors.TextSecondary,
                                                            fontSize = VegaText.Caption
                                                        )
                                                    }
                                                    Text(
                                                        text = "Hedef süre ($diffText)",
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
                                                        triggerSeek(false)
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                        triggerSeek(true)
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
                                                                    VegaColors.Accent.copy(alpha = 0.8f),
                                                                    VegaColors.Accent
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
                                                        .background(if (isSliderFocused) VegaColors.Accent else Color.White)
                                                        .border(
                                                            width = 2.dp,
                                                            color = VegaColors.Accent,
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
                                        showInfoBanner("Baştan Başlatıldı")
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
                                    text = "Ses & Altyazı",
                                    icon = Icons.Default.Subtitles,
                                    onClick = {
                                        settingsInitialTab = SettingsTab.SUBTITLE
                                        showSettingsPanel = true
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
                                    text = when (aspectRatio) {
                                        AspectRatioMode.FIT -> "Görüntü: Orijinal"
                                        AspectRatioMode.FILL -> "Görüntü: Doldur"
                                        AspectRatioMode.ZOOM -> "Görüntü: Zoom"
                                        AspectRatioMode.RATIO_16_9 -> "Görüntü: 16:9"
                                        AspectRatioMode.RATIO_4_3 -> "Görüntü: 4:3"
                                    },
                                    icon = Icons.Default.AspectRatio,
                                    onClick = {
                                        settingsInitialTab = SettingsTab.ASPECT_RATIO
                                        showSettingsPanel = true
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

                                if (currentSeriesInfo != null && !currentSeriesInfo?.episodes.isNullOrEmpty()) {
                                    PlayerPillButton(
                                        text = "Bölümler",
                                        icon = Icons.AutoMirrored.Filled.List,
                                        onClick = { showEpisodesDrawer = true },
                                        isActive = showEpisodesDrawer,
                                        activeColor = VegaColors.Accent,
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
                                        text = "Sonraki bölüm",
                                        icon = Icons.Default.SkipNext,
                                        onClick = { viewModel.playNextEpisode() },
                                        isActive = true,
                                        activeColor = VegaColors.Accent,
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
                                    text = "Hız: ${playbackSpeed}x",
                                    icon = Icons.Default.Speed,
                                    onClick = {
                                        settingsInitialTab = SettingsTab.SPEED
                                        showSettingsPanel = true
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

        // EN ÜST KATMAN: SARMA GÖSTERGESİ (SeekIndicator - Sol 1/3 ve Sağ 1/3, dokunmaları yutmaz)
        AnimatedVisibility(
            visible = seekDirection != null,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val currentDir = seekDirection
                if (currentDir == SeekDirection.REWIND) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.33f)
                            .align(Alignment.CenterStart),
                        contentAlignment = Alignment.Center
                    ) {
                        SeekIndicator(
                            direction = SeekDirection.REWIND,
                            seconds = seekAccumulatedSeconds,
                            isPortrait = isPortrait
                        )
                    }
                } else if (currentDir == SeekDirection.FORWARD) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.33f)
                            .align(Alignment.CenterEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        SeekIndicator(
                            direction = SeekDirection.FORWARD,
                            seconds = seekAccumulatedSeconds,
                            isPortrait = isPortrait
                        )
                    }
                }
            }
        }

        // EN ÜST KATMAN: BİLGİ BALONU (Hız, En-Boy, Kilit vb. bildirimler - Ekranın üst ortasında, dokunmaları yutmaz)
        AnimatedVisibility(
            visible = infoBannerText != null,
            enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .padding(top = if (isPortrait) 64.dp else 48.dp)
        ) {
            infoBannerText?.let { text ->
                Surface(
                    shape = VegaShapes.Pill,
                    color = VegaColors.OverlayPanel,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            text.contains("Hız", ignoreCase = true) -> Icons.Default.Speed
                            text.contains("Baştan", ignoreCase = true) -> Icons.Default.Replay
                            text.contains("En-Boy", ignoreCase = true) -> Icons.Default.AspectRatio
                            text.contains("Kilit", ignoreCase = true) -> Icons.Default.Lock
                            else -> Icons.Default.Info
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = text,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.SemiBold
                        )
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
                colors = CardDefaults.cardColors(containerColor = VegaColors.OverlayPanel),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
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
                            Text("Hemen İzle", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VegaColors.OnAccent)
                        }
                    }
                }
            }
        }

        // DAHA FAZLA (⋯) İKİNCİL SEÇENEKLER BOTTOM SHEET
        if (showMoreSheet) {
            LivePlayerMoreSheet(
                aspectRatio = aspectRatio,
                onCycleAspectRatio = {
                    val newMode = playerManager.cycleAspectRatio()
                    showInfoBanner(
                        when (newMode) {
                            AspectRatioMode.FIT -> "En-Boy: Orijinal"
                            AspectRatioMode.FILL -> "En-Boy: Doldur"
                            AspectRatioMode.ZOOM -> "En-Boy: Yakınlaştır"
                            AspectRatioMode.RATIO_16_9 -> "En-Boy: 16:9"
                            AspectRatioMode.RATIO_4_3 -> "En-Boy: 4:3"
                        }
                    )
                },
                isDualViewActive = isDualViewActive,
                onOpenDualView = {
                    showMoreSheet = false
                    showDualViewPicker = true
                },
                onOpenNumpad = {
                    showMoreSheet = false
                    showNumericKeypad = true
                },
                onOpenChannelDrawer = {
                    showMoreSheet = false
                    showChannelDrawer = true
                },
                onLockControls = {
                    showMoreSheet = false
                    isControlsLocked = true
                    showInfoBanner("🔒 Ekran Kilitlendi")
                },
                diagnostics = streamDiagnostics,
                onDismiss = { showMoreSheet = false }
            )
        }

        // TEK KOMPAKT OYNATICI AYARLARI PANELİ (SES, ALTYAZI, GÖRÜNTÜ, HIZ)
        PlayerSettingsPanel(
            visible = showSettingsPanel,
            initialTab = settingsInitialTab,
            isLive = isLive,
            isPortrait = isPortrait,
            audioTracks = audioTracks,
            onSelectAudioTrack = { track ->
                playerManager.selectTrack(track, androidx.media3.common.C.TRACK_TYPE_AUDIO)
            },
            subtitleTracks = subtitleTracks,
            onSelectSubtitleTrack = { track ->
                playerManager.selectTrack(track, androidx.media3.common.C.TRACK_TYPE_TEXT)
            },
            onDisableSubtitles = {
                playerManager.disableSubtitles()
            },
            subtitleDelayMs = subtitleDelayMs,
            onSubtitleDelayChange = { newDelay ->
                viewModel.setSubtitleDelayMs(newDelay)
            },
            subtitleSize = subtitleSize,
            onSubtitleSizeChange = { newSize ->
                viewModel.setSubtitleSize(newSize)
            },
            subtitleColor = subtitleColor,
            onSubtitleColorChange = { newColor ->
                viewModel.setSubtitleColor(newColor)
            },
            aspectRatio = aspectRatio,
            onAspectRatioChange = { newMode ->
                playerManager.setAspectRatio(newMode)
            },
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = { newSpeed ->
                playerManager.setPlaybackSpeed(newSpeed)
            },
            diagnostics = streamDiagnostics,
            onClose = {
                showSettingsPanel = false
                orientationManager?.setImmersiveMode(true)
                try {
                    playerFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
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
                                    shape = VegaShapes.Pill,
                                    color = if (isSel) VegaColors.Accent.copy(alpha = 0.25f) else VegaColors.SurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) VegaColors.Accent else VegaColors.Border),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                        Text(
                                            text = "Sezon $sKey",
                                            color = if (isSel) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary,
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
                                shape = VegaShapes.Medium,
                                color = if (isCurrentEp) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
                                border = BorderStroke(1.dp, if (isCurrentEp) VegaColors.Accent else VegaColors.Border),
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
                                            .background(if (isCurrentEp) VegaColors.Accent else VegaColors.Border),
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
                                            color = if (isCurrentEp) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Body,
                                            fontWeight = if (isCurrentEp) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!ep.info?.plot.isNullOrBlank()) {
                                            Text(
                                                text = ep.info?.plot ?: "",
                                                color = VegaColors.TextSecondary,
                                                fontSize = VegaText.Caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (isCurrentEp) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Oynatılıyor",
                                            tint = VegaColors.Accent,
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
                    .displayCutoutPadding()
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
                    .displayCutoutPadding()
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
                        contentDescription = "Tek ekrana dön",
                        tint = VegaColors.Danger,
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
                color = if (hasAudio) VegaColors.Accent else VegaColors.Border
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
                    if (isPrimary) {
                        playerManager.initializePlayer()
                        playerManager.attachView(this, isPrimary = true, tag = "DualViewSingleWindow-primary")
                    } else {
                        player = playerManager.secondaryExoPlayer
                    }
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
                if (isPrimary) {
                    playerManager.initializePlayer()
                    playerManager.attachView(playerView, isPrimary = true, tag = "DualViewSingleWindow-primary")
                } else {
                    val targetPlayer = playerManager.secondaryExoPlayer
                    if (playerView.player != targetPlayer) {
                        playerView.player = targetPlayer
                    }
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
                if (isPrimary) {
                    playerManager.detachView(playerView, tag = "DualViewSingleWindow-primary")
                } else {
                    playerView.player = null
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
        containerColor = VegaColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = VegaColors.TextSecondary) }
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
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Çift ekran — İkinci kanal",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.BodyProminent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aynı anda canlı izlemek istediğin kanalı seç",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = VegaColors.TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Arama Çubuğu
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Kanal ara...", color = VegaColors.TextMuted, fontSize = VegaText.Secondary) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = VegaColors.Accent)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Temizle", tint = VegaColors.TextPrimary)
                        }
                    }
                },
                singleLine = true,
                shape = VegaShapes.Medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VegaColors.Accent,
                    unfocusedBorderColor = VegaColors.Border,
                    focusedContainerColor = VegaColors.SurfaceVariant,
                    unfocusedContainerColor = VegaColors.SurfaceVariant,
                    focusedTextColor = VegaColors.TextPrimary,
                    unfocusedTextColor = VegaColors.TextPrimary
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
                        shape = VegaShapes.Medium,
                        color = VegaColors.SurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
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
    subtitleDelayMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val delayedController = remember {
        com.tivimax.player.player.DelayedSubtitleController(context, coroutineScope)
    }

    DisposableEffect(delayedController) {
        onDispose {
            delayedController.detach()
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(playerViewRef) {
        val view = playerViewRef ?: return@LaunchedEffect
        // İlk karede ve Crossfade bittikten sonra yüzeyi garantiye almak için yeniden talep
        delay(350L)
        playerManager.attachView(view, isPrimary = true, tag = "VideoPlayerSurface-delay350")
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                playerViewRef = this
                useController = false
                keepScreenOn = true
                setKeepContentOnPlayerReset(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val initialPlayer = playerManager.initializePlayer(isLive)
                playerManager.attachView(this, isPrimary = true, tag = "VideoPlayerSurface-factory")
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
                delayedController.attach(this, initialPlayer)
                delayedController.updateDelay(subtitleDelayMs)
                delayedController.updateStyle(subtitleColor, subtitleSize)
            }
        },
        update = { playerView ->
            playerViewRef = playerView
            val currentPlayer = playerManager.player ?: playerManager.initializePlayer(isLive)
            playerManager.attachView(playerView, isPrimary = true, tag = "VideoPlayerSurface-update")
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
                delayedController.attach(playerView, currentPlayer)
                delayedController.updateDelay(subtitleDelayMs)
                delayedController.updateStyle(subtitleColor, subtitleSize)
            } catch (e: Throwable) {
                // Ignore
            }
        },
        onRelease = { playerView ->
            if (playerViewRef == playerView) {
                playerViewRef = null
            }
            delayedController.detach()
            playerManager.detachView(playerView, tag = "VideoPlayerSurface-onRelease")
        },
        modifier = modifier
    )
}

enum class SeekDirection { FORWARD, REWIND }

@Composable
private fun SeekIndicator(
    direction: SeekDirection,
    seconds: Int,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = VegaColors.OverlayPanel,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, VegaColors.Border),
        tonalElevation = 6.dp,
        modifier = modifier.size(if (isPortrait) 72.dp else 84.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (direction == SeekDirection.FORWARD) Icons.Default.Forward10 else Icons.Default.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (isPortrait) 28.dp else 34.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (direction == SeekDirection.FORWARD) "+$seconds sn" else "-$seconds sn",
                color = Color.White,
                fontSize = if (isPortrait) VegaText.Caption else VegaText.Secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

