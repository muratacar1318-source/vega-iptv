package com.tivimax.player.ui.player

import com.tivimax.player.MainActivity
import android.app.RemoteAction
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.tivimax.player.ui.design.VegaChoiceChip
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
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
import com.tivimax.player.ui.player.components.LiveMorePanel
import com.tivimax.player.ui.player.components.PlayerSidePanel
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
    val duration by playerManager.duration.collectAsState()
    val errorMessage by playerManager.errorMessage.collectAsState()
    val reconnectStatus by playerManager.reconnectStatus.collectAsState()
    val aspectRatio by playerManager.aspectRatio.collectAsState()
    val audioTracks by playerManager.audioTracks.collectAsState()
    val subtitleTracks by playerManager.subtitleTracks.collectAsState()
    val playerInstanceVersion by playerManager.playerInstanceVersion.collectAsState()

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

    // TV kumandası için ekrana ilk girişte ana video oynatıcıya odağı ver (düşük donanımlı cihazlar için çoklu deneme)
    LaunchedEffect(Unit) {
        repeat(4) {
            delay(60)
            try {
                playerFocusRequester.requestFocus()
                return@LaunchedEffect
            } catch (e: Exception) { }
        }
    }

    // HUD açıldığında odağı Oynat/Duraklat (veya Canlı ilk aksiyon) düğmesine, kapandığında ana oynatıcıya ver
    LaunchedEffect(showControls, showSettingsPanel, showEpisodesDrawer, showMoreSheet, showSleepTimerDialog) {
        if (showSettingsPanel || showEpisodesDrawer || showMoreSheet || showSleepTimerDialog) {
            return@LaunchedEffect
        }
        if (showControls) {
            repeat(5) {
                delay(50)
                try {
                    // TV'de film/dizide odak doğrudan Oynat/Duraklat düğmesine gelir: OK 1 kez basınca anında duraklat/devam et!
                    firstButtonFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    try {
                        sliderFocusRequester.requestFocus()
                        return@LaunchedEffect
                    } catch (_: Exception) { }
                }
            }
        } else {
            repeat(4) {
                delay(40)
                try {
                    playerFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) { }
            }
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

    // Bilgi Balonu ve Dokunmatik Sürükleme Durumu
    var infoBannerText by remember { mutableStateOf<String?>(null) }
    var lastInfoBannerTime by remember { mutableLongStateOf(0L) }
    var dragStartOffset by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    // Sarma Göstergesi Durumu (±10sn, birikmeli, 700ms)
    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var seekAccumulatedSeconds by remember { mutableIntStateOf(0) }
    var lastSeekTriggerTime by remember { mutableLongStateOf(0L) }

    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()
    val isFavorite = favoriteLiveIds.contains(streamId.toString())
    val epgList by viewModel.currentEpgList.collectAsState()
    val allLiveStreams by viewModel.liveStreams.collectAsState()

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var optimisticSeekPosition by remember { mutableStateOf<Long?>(null) }

    // Fullscreen gesture scrub state
    var gestureScrubActive by remember { mutableStateOf(false) }
    var gestureScrubTargetMs by remember { mutableLongStateOf(0L) }
    var gestureScrubDiffMs by remember { mutableLongStateOf(0L) }
    var gestureDragInitialPos by remember { mutableLongStateOf(0L) }
    var dragDirection by remember { mutableStateOf<String?>(null) } // "horizontal", "vertical_center"

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

    LaunchedEffect(optimisticSeekPosition) {
        val target = optimisticSeekPosition ?: return@LaunchedEffect
        kotlinx.coroutines.withTimeoutOrNull(1200L) {
            playerManager.currentPosition.collect { pos ->
                if (kotlin.math.abs(pos - target) <= 1500L) {
                    optimisticSeekPosition = null
                }
            }
        }
        if (optimisticSeekPosition == target) {
            optimisticSeekPosition = null
        }
    }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isUserInteracting = isScrubbing || gestureScrubActive || (seekDirection != null) || (optimisticSeekPosition != null) || (infoBannerText != null) || showChannelDrawer || showSettingsPanel || showSleepTimerDialog || showOverlayGuide || showRecentChannelsBar || showNumericKeypad || showCategoryManagementDialog || isZapperActive || showEpisodesDrawer || showMoreSheet || showDualViewPicker || (dragStartOffset != null)

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
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

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
    val previousEpisode = remember(currentEpisode) { viewModel.getPreviousEpisode() }
    val nextEpisode = remember(currentEpisode) { viewModel.getNextEpisode() }
    var autoNextDismissed by remember(currentEpisode) { mutableStateOf(false) }

    // Bölüm bittiğinde (STATE_ENDED veya son 1.5 sn) otomatik sonraki bölüme geçiş
    val isCurrentlyScrubbing by rememberUpdatedState(isScrubbing || gestureScrubActive)
    val latestNextEpisode by rememberUpdatedState(nextEpisode)
    var autoNextTriggered by remember(streamId, currentEpisode) { mutableStateOf(false) }

    DisposableEffect(streamId, currentEpisode, playerInstanceVersion, isLive) {
        val player = if (!isLive && currentEpisode != null) playerManager.player else null
        val listener = if (player != null) {
            object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED &&
                        !autoNextTriggered &&
                        !isCurrentlyScrubbing &&
                        latestNextEpisode != null
                    ) {
                        autoNextTriggered = true
                        viewModel.playNextEpisode()
                    }
                }
            }
        } else null
        if (player != null && listener != null) {
            player.addListener(listener)
        }
        onDispose {
            if (player != null && listener != null) {
                player.removeListener(listener)
            }
        }
    }

    LaunchedEffect(streamId, currentEpisode, isLive) {
        if (!isLive && currentEpisode != null) {
            while (!autoNextTriggered) {
                delay(1000L)
                if (autoNextTriggered || isCurrentlyScrubbing || !playerManager.isPlaying.value) continue
                if (latestNextEpisode == null) continue
                val pos = playerManager.currentPosition.value
                val dur = playerManager.duration.value
                if (dur > 30_000L && pos >= dur - 1500L) {
                    autoNextTriggered = true
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
                resumeNotification = "Kaldığın yerden devam ediliyor (${String.format("%02d:%02d", mins, secs)})"
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

    // Lifecycle Observer (Arka plana atıldığında PiP veya çoklu pencere açık değilse videoyu duraklat)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isInPipMode) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = (activity?.let {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && it.isInPictureInPictureMode
            } ?: false) || isInPipMode
            val inMultiWindow = activity?.let {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && it.isInMultiWindowMode
            } ?: false

            if (event == Lifecycle.Event.ON_STOP) {
                if (!inPip && !inMultiWindow) {
                    playerManager.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // PiP moduna geçildiğinde tüm panelleri kapat ve oynatmayı sürdür
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            showControls = false
            showBottomInfoCard = false
            showSettingsPanel = false
            showMoreSheet = false
            showEpisodesDrawer = false
            showDualViewPicker = false
            showChannelDrawer = false
            showOverlayGuide = false
            showRecentChannelsBar = false
            showNumericKeypad = false
            showSleepTimerDialog = false
            showCategoryManagementDialog = false
            showLockBanner = false
            if (!playerManager.isPlaying.value) {
                playerManager.play()
            }
        }
    }

    // Video READY olup kare hızı/boyutu belli olduğunda ekran yenileme hızını eşle (AFR)
    LaunchedEffect(isPlaying, streamDiagnostics.frameRate, streamDiagnostics.resolution, streamId, playerInstanceVersion) {
        if (activity != null && isPlaying) {
            playerManager.applyAutoFrameRate(activity)
        }
    }

    // Video izlerken ekranın kapanmasını önle (KEEP_SCREEN_ON) ve çıkışta AFR + belleği temizle
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            playerManager.resetAutoFrameRate(activity)
            if (viewModel.currentScreen.value !is Screen.Player && !viewModel.isMiniPlayerActive.value) {
                playerManager.stopAndClear()
            }
        }
    }

    // Android 12+ (API 31+) için Otomatik PiP Kaydı (Sistem Seviyesi Akıcı Animasyon)
    DisposableEffect(isPlaying, autoPipEnabled, isLive, playerViewRef) {
        val isTv = activity?.let { act ->
            val uiModeManager = act.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
            val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val hasLeanback = act.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
            val hasNoTouch = !act.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
            isTvMode || hasLeanback || hasNoTouch
        } ?: false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                val hasPip = activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                if (hasPip) {
                    val rational = MainActivity.calculatePipAspectRatio(playerManager.player)
                    val builder = PictureInPictureParams.Builder()
                        .setAspectRatio(rational)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setAutoEnterEnabled(!isTv && autoPipEnabled && isPlaying && isLive)
                        playerViewRef?.let { view ->
                            val rect = android.graphics.Rect()
                            view.getGlobalVisibleRect(rect)
                            if (!rect.isEmpty) {
                                builder.setSourceRectHint(rect)
                            }
                        }
                    }

                    val action = MainActivity.createPipPlayPauseAction(activity, isPlaying)
                    if (action != null) {
                        builder.setActions(listOf(action))
                    }

                    activity.setPictureInPictureParams(builder.build())
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
        } else if (showMoreSheet) {
            showMoreSheet = false
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
                val pos = playerManager.currentPosition.value
                if (pos > 0) {
                    viewModel.saveWatchProgress(streamId, pos, playerManager.duration.value)
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
                                showInfoBanner("Ekran kilidi açıldı")
                                return@onPreviewKeyEvent true
                            }
                            lastOkPressTime = now
                        }
                        showLockBanner = true
                        showControls = true
                        return@onPreviewKeyEvent true
                    }

                    // AÇIK YAN PANEL, REHBER VEYA ÇEKMECE VARSA ÖNCE O İŞLENİR (showControls açık olsa bile BACK önce paneli kapatır!)
                    if (showOverlayGuide || showRecentChannelsBar || showEpisodesDrawer || showSettingsPanel || showMoreSheet || showChannelDrawer || showDualViewPicker) {
                        registerInteraction()
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            if (showOverlayGuide) showOverlayGuide = false
                            else if (showRecentChannelsBar) showRecentChannelsBar = false
                            else if (showEpisodesDrawer) showEpisodesDrawer = false
                            else if (showSettingsPanel) showSettingsPanel = false
                            else if (showMoreSheet) showMoreSheet = false
                            else if (showChannelDrawer) showChannelDrawer = false
                            else if (showDualViewPicker) showDualViewPicker = false
                            if (!showControls) {
                                try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
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

                        // Donanım medya tuşları HUD açıkken de çalışsın
                        when (keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                playerManager.togglePlayPause()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                playerManager.play()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                playerManager.pause()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                playerManager.stop()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                triggerSeek(true)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                triggerSeek(false)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_INFO -> {
                                showBottomInfoCard = !showBottomInfoCard
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MENU -> {
                                showControls = false
                                showSettingsPanel = !showSettingsPanel
                                try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_UP -> {
                                if (isLive) {
                                    showControls = false
                                    val next = viewModel.zapNextChannel(streamId)
                                    if (next != null) showInfoBanner("Sonraki: ${next.name} ⏭")
                                    showBottomInfoCard = true
                                    try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                                    return@onPreviewKeyEvent true
                                } else if (currentEpisode != null) {
                                    showControls = false
                                    viewModel.playNextEpisode()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_LAST_CHANNEL -> {
                                if (isLive) {
                                    showControls = false
                                    val prev = viewModel.zapRecall()
                                    if (prev != null) showInfoBanner("↩ ${prev.name}")
                                    showBottomInfoCard = true
                                    try { playerFocusRequester.requestFocus() } catch (e: Exception) {}
                                    return@onPreviewKeyEvent true
                                } else if (currentEpisode != null) {
                                    showControls = false
                                    viewModel.playPreviousEpisode()
                                    return@onPreviewKeyEvent true
                                }
                            }
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

                    // HUD KAPALIYKEN (Normal İzleme Modu):
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_PAGE_UP -> {
                            if (isLive) {
                                val next = viewModel.zapNextChannel(streamId)
                                if (next != null) showInfoBanner("Sonraki: ${next.name} ⏭")
                                showBottomInfoCard = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                            if (currentEpisode != null && keyCode != KeyEvent.KEYCODE_DPAD_UP) {
                                if (viewModel.playNextEpisode()) {
                                    registerInteraction()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            if (isTvDevice) {
                                showControls = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                            false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (isLive) {
                                showRecentChannelsBar = true
                                showBottomInfoCard = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                            if (currentEpisode != null && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                                if (viewModel.playPreviousEpisode()) {
                                    registerInteraction()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            if (isTvDevice) {
                                showControls = true
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
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            playerManager.togglePlayPause()
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            playerManager.play()
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            playerManager.pause()
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            playerManager.stop()
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            triggerSeek(true)
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            triggerSeek(false)
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            if (isLive) {
                                val next = viewModel.zapNextChannel(streamId)
                                if (next != null) showInfoBanner("Sonraki: ${next.name} ⏭")
                                showBottomInfoCard = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            } else if (currentEpisode != null) {
                                if (viewModel.playNextEpisode()) {
                                    registerInteraction()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_LAST_CHANNEL -> {
                            if (isLive) {
                                val prev = viewModel.zapRecall()
                                if (prev != null) showInfoBanner("↩ ${prev.name}")
                                showBottomInfoCard = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            } else if (currentEpisode != null) {
                                if (viewModel.playPreviousEpisode()) {
                                    registerInteraction()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        }
                        KeyEvent.KEYCODE_INFO -> {
                            showBottomInfoCard = !showBottomInfoCard
                            registerInteraction()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            if (isLive) {
                                showChannelDrawer = !showChannelDrawer
                            } else {
                                showSettingsPanel = !showSettingsPanel
                            }
                            registerInteraction()
                            return@onPreviewKeyEvent true
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
                            showInfoBanner("Ekran kilidi açıldı")
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
        if (!isDualViewActive || isInPipMode) {
            // Media3 ExoPlayer Video Render View (İzole Edilmiş Yüzey - HUD/Zaman recomposition'larından asla etkilenmez)
            VideoPlayerSurface(
                playerManager = playerManager,
                isLive = isLive,
                aspectRatio = aspectRatio,
                subtitleColor = subtitleColor,
                subtitleSize = subtitleSize,
                subtitleDelayMs = subtitleDelayMs,
                isInPipMode = isInPipMode,
                onPlayerViewCreated = { playerViewRef = it },
                onPlayerViewReleased = { playerViewRef = null },
                modifier = Modifier.fillMaxSize()
            )

            // Buffering Indicator
            if (isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = VegaColors.Accent,
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

        // Yeniden Bağlanma Bildirimi (Tam ekranı karartmadan, zarif hap bildirim)
        if (reconnectStatus != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isPortrait) 72.dp else 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = VegaShapes.Pill,
                    color = Color.Black.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, VegaColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = VegaColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = reconnectStatus!!,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Hata Bildirimi Overlay (Yalnızca tüm otomatik denemeler tükendiğinde ve PiP dışında)
        if (errorMessage != null && reconnectStatus == null && !isInPipMode) {
            errorMessage?.let { error ->
                PlayerErrorOverlay(
                    error = error,
                    onRetry = { playerManager.retryCurrent() },
                    onRetryFallback = { playerManager.retryNextFallback() },
                    onBack = onBack
                )
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
                    color = VegaColors.OverlayPanel,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = VegaColors.Gold,
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
            visible = showControls && !isInPipMode && !showSettingsPanel && !showMoreSheet && !showEpisodesDrawer && !showDualViewPicker,
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
                    var isUnlockFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = CircleShape,
                        color = if (isUnlockFocused) VegaColors.Accent else VegaColors.OverlayPanel,
                        border = BorderStroke(1.5.dp, if (isUnlockFocused) Color.White else VegaColors.Accent),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                            .dpadFocusable(
                                shape = CircleShape,
                                focusedBorderColor = Color.White,
                                scale = 1.05f,
                                onClick = { isControlsLocked = false },
                                onFocusStateChange = { isUnlockFocused = it }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Dokunma kilidini aç",
                                tint = if (isUnlockFocused) VegaColors.OnAccent else VegaColors.Accent,
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
                                val pos = playerManager.currentPosition.value
                                if (pos > 0) {
                                    viewModel.saveWatchProgress(streamId, pos, playerManager.duration.value)
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
                        backgroundColor = VegaColors.OverlayPill,
                        border = BorderStroke(1.dp, VegaColors.Border)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White,
                            modifier = Modifier.size(if (isPortrait) 20.dp else 22.dp)
                        )
                    }

                    // Orta Kısım: VOD / Dizi için Başlık (StreamVault üst şerit uyumlu)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (!isLive) {
                            val activeEpisode = currentEpisode?.first
                            val qualityBadgeText = streamDiagnostics.resolutionBadge.trim()
                            if (activeEpisode != null) {
                                val seriesName = currentSeriesInfo?.info?.name ?: title
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.OverlayPill,
                                        border = BorderStroke(1.dp, VegaColors.Border)
                                    ) {
                                        Text(
                                            text = "Dizi",
                                            color = VegaColors.Accent,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = seriesName,
                                        color = Color.White,
                                        fontSize = if (isPortrait) VegaText.Body else VegaText.SectionTitle,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = VegaShapes.Pill,
                                        color = VegaColors.OverlayPill,
                                        border = BorderStroke(1.dp, VegaColors.Border)
                                    ) {
                                        Text(
                                            text = "S${activeEpisode.season} B${activeEpisode.episodeNum}",
                                            color = VegaColors.TextSecondary,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (qualityBadgeText.isNotBlank()) {
                                        Surface(
                                            shape = VegaShapes.Small,
                                            color = VegaColors.OverlayPill,
                                            border = BorderStroke(1.dp, VegaColors.Border)
                                        ) {
                                            Text(
                                                text = qualityBadgeText,
                                                color = VegaColors.TextSecondary,
                                                fontSize = VegaText.Caption,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (activeEpisode.title.isNotBlank()) {
                                    Text(
                                        text = activeEpisode.title,
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.OverlayPill,
                                        border = BorderStroke(1.dp, VegaColors.Border)
                                    ) {
                                        Text(
                                            text = "Film",
                                            color = VegaColors.Accent,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        fontSize = if (isPortrait) VegaText.Body else VegaText.SectionTitle,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (qualityBadgeText.isNotBlank()) {
                                        Surface(
                                            shape = VegaShapes.Small,
                                            color = VegaColors.OverlayPill,
                                            border = BorderStroke(1.dp, VegaColors.Border)
                                        ) {
                                            Text(
                                                text = qualityBadgeText,
                                                color = VegaColors.TextSecondary,
                                                fontSize = VegaText.Caption,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sağ Üst: Birleşik StreamVault Yarı Saydam Hap (Saat, Uyku, Küçük pencere, Döndür, Kilit)
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
                                    fontSize = if (isPortrait) VegaText.Caption else VegaText.Secondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                            }

                            if (sleepTimerSeconds != null) {
                                var isTimerFocused by remember { mutableStateOf(false) }
                                Surface(
                                    shape = VegaShapes.Medium,
                                    color = if (isTimerFocused) VegaColors.Accent else Color.Transparent,
                                    border = if (isTimerFocused) BorderStroke(1.5.dp, Color.White) else null,
                                    modifier = Modifier
                                        .height(if (isPortrait) 32.dp else 36.dp)
                                        .dpadFocusable(
                                            shape = VegaShapes.Medium,
                                            focusedBorderColor = Color.White,
                                            scale = 1.04f,
                                            onClick = { showSleepTimerDialog = true },
                                            onFocusStateChange = { isTimerFocused = it }
                                        )
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "🌙 ${(sleepTimerSeconds!! + 59) / 60} dk",
                                            color = if (isTimerFocused) VegaColors.OnAccent else Color.White,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                BouncyIconButton(
                                    onClick = { showSleepTimerDialog = true },
                                    modifier = Modifier.size(if (isPortrait) 34.dp else 38.dp),
                                    backgroundColor = Color.Transparent
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Uyku zamanlayıcısı",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null && !isTvDevice) {
                                BouncyIconButton(
                                    onClick = {
                                        try {
                                            val rational = MainActivity.calculatePipAspectRatio(playerManager.player)
                                            val builder = PictureInPictureParams.Builder()
                                                .setAspectRatio(rational)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                playerViewRef?.let { view ->
                                                    val rect = android.graphics.Rect()
                                                    view.getGlobalVisibleRect(rect)
                                                    if (!rect.isEmpty) {
                                                        builder.setSourceRectHint(rect)
                                                    }
                                                }
                                            }
                                            val action = MainActivity.createPipPlayPauseAction(activity, isPlaying)
                                            if (action != null) {
                                                builder.setActions(listOf(action))
                                            }
                                            activity.enterPictureInPictureMode(builder.build())
                                        } catch (e: Throwable) {
                                            // Ignore
                                        }
                                    },
                                    modifier = Modifier.size(if (isPortrait) 34.dp else 38.dp),
                                    backgroundColor = Color.Transparent
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Küçük pencere",
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
                                    modifier = Modifier.size(if (isPortrait) 34.dp else 38.dp),
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
                                        showInfoBanner("Ekran kilidi açıldı")
                                    } else {
                                        showLockBanner = true
                                    }
                                },
                                modifier = Modifier
                                    .size(if (isPortrait) 34.dp else 38.dp)
                                    .focusRequester(lockButtonFocusRequester),
                                backgroundColor = Color.Transparent
                            ) {
                                Icon(
                                    imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Ekran kilidi",
                                    tint = if (isControlsLocked) VegaColors.Live else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // BOTTOM CONTROLS & OSD BAR
                if (!isControlsLocked) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .padding(
                                horizontal = if (isPortrait) 8.dp else 20.dp,
                                vertical = if (isPortrait) 6.dp else 14.dp
                            )
                    ) {
                        if (isLive) {
                            val currentChannel = allLiveStreams.find { it.streamId == streamId }
                                ?: LiveStream(streamId = streamId, name = title, categoryId = categoryId)

                            // BİRLEŞİK ALT BİLGİ PANELİ & AKSİYON DOCK'U
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
                                                if (prev != null) showInfoBanner("Önceki: ${prev.name}")
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
                            // ====================================================================
                            // FİLM / DİZİ: STREAMVAULT BUZLU GECE CAM ALT KONTROL PANELİ
                            // ====================================================================
                            if (duration > 0) {
                                VodScrubPreviewPopup(
                                    visible = isScrubbing,
                                    scrubPosition = scrubPosition,
                                    duration = duration,
                                    optimisticSeekPosition = optimisticSeekPosition,
                                    playerManager = playerManager,
                                    thumbnailImage = thumbnailImage
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = VegaColors.OverlayPanel,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                shadowElevation = 14.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = if (isPortrait) 12.dp else 18.dp,
                                        vertical = if (isPortrait) 10.dp else 14.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(if (isPortrait) 8.dp else 12.dp)
                                ) {
                                    // 1. STREAMVAULT İLERLEME / SARMA ÇUBUĞU (4 Hz recomposition bu alt bileşende izole)
                                    if (duration > 0) {
                                        VodSeekBarSection(
                                            playerManager = playerManager,
                                            duration = duration,
                                            isScrubbing = isScrubbing,
                                            scrubPosition = scrubPosition,
                                            onScrubbingChange = { isScrubbing = it },
                                            onScrubPositionChange = { scrubPosition = it },
                                            optimisticSeekPosition = optimisticSeekPosition,
                                            onOptimisticSeekPositionChange = { optimisticSeekPosition = it },
                                            gestureScrubActive = gestureScrubActive,
                                            gestureScrubTargetMs = gestureScrubTargetMs,
                                            isSliderFocused = isSliderFocused,
                                            onSliderFocusChange = { isSliderFocused = it },
                                            sliderFocusRequester = sliderFocusRequester,
                                            firstButtonFocusRequester = firstButtonFocusRequester,
                                            triggerSeek = triggerSeek,
                                            registerInteraction = registerInteraction
                                        )
                                    }

                                    // 2. BÜYÜK VE ODAKLANABİLİR ULAŞIM DÜĞMELERİ (Otomatik odak: Oynat / Duraklat)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 10.dp else 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (previousEpisode != null) {
                                                VodTransportIconButton(
                                                    icon = Icons.Default.SkipPrevious,
                                                    contentDescription = "Önceki bölüm",
                                                    size = if (isPortrait) 42.dp else 46.dp,
                                                    onClick = { viewModel.playPreviousEpisode() },
                                                    modifier = Modifier.focusProperties {
                                                        if (duration > 0) up = sliderFocusRequester
                                                        down = bottomRowFocusRequester
                                                    }
                                                )
                                            }

                                            VodTransportIconButton(
                                                icon = Icons.Default.Replay10,
                                                contentDescription = "10 sn geri",
                                                size = if (isPortrait) 44.dp else 48.dp,
                                                onClick = { triggerSeek(false) },
                                                modifier = Modifier.focusProperties {
                                                    if (duration > 0) up = sliderFocusRequester
                                                    down = bottomRowFocusRequester
                                                }
                                            )

                                            VodTransportIconButton(
                                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                                isPrimary = true,
                                                size = if (isPortrait) 52.dp else 58.dp,
                                                onClick = {
                                                    playerManager.togglePlayPause()
                                                    registerInteraction()
                                                },
                                                modifier = Modifier
                                                    .focusRequester(firstButtonFocusRequester)
                                                    .focusProperties {
                                                        if (duration > 0) up = sliderFocusRequester
                                                        down = bottomRowFocusRequester
                                                    }
                                            )

                                            VodTransportIconButton(
                                                icon = Icons.Default.Forward10,
                                                contentDescription = "10 sn ileri",
                                                size = if (isPortrait) 44.dp else 48.dp,
                                                onClick = { triggerSeek(true) },
                                                modifier = Modifier.focusProperties {
                                                    if (duration > 0) up = sliderFocusRequester
                                                    down = bottomRowFocusRequester
                                                }
                                            )

                                            if (nextEpisode != null) {
                                                VodTransportIconButton(
                                                    icon = Icons.Default.SkipNext,
                                                    contentDescription = "Sonraki bölüm",
                                                    size = if (isPortrait) 42.dp else 46.dp,
                                                    onClick = { viewModel.playNextEpisode() },
                                                    modifier = Modifier.focusProperties {
                                                        if (duration > 0) up = sliderFocusRequester
                                                        down = bottomRowFocusRequester
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // 3. STREAMVAULT İKİ SATIRLI HIZLI AKSİYON KAROLARI
                                    val selectedAudioLabel = remember(audioTracks) {
                                        audioTracks.firstOrNull { it.isSelected }?.name?.take(14) ?: "Varsayılan"
                                    }
                                    val selectedSubLabel = remember(subtitleTracks) {
                                        subtitleTracks.firstOrNull { it.isSelected }?.name?.take(14) ?: "Kapalı"
                                    }
                                    val aspectLabel = when (aspectRatio) {
                                        AspectRatioMode.FIT -> "Orijinal"
                                        AspectRatioMode.FILL -> "Doldur"
                                        AspectRatioMode.ZOOM -> "Yakınlaştır"
                                        AspectRatioMode.RATIO_16_9 -> "16:9"
                                        AspectRatioMode.RATIO_4_3 -> "4:3"
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        VodStreamVaultTile(
                                            eyebrow = "Ses",
                                            value = selectedAudioLabel,
                                            isCompact = isPortrait,
                                            onClick = {
                                                settingsInitialTab = SettingsTab.AUDIO
                                                showSettingsPanel = true
                                            },
                                            modifier = Modifier
                                                .focusRequester(bottomRowFocusRequester)
                                                .focusProperties { up = firstButtonFocusRequester }
                                        )

                                        VodStreamVaultTile(
                                            eyebrow = "Altyazı",
                                            value = selectedSubLabel,
                                            isActive = subtitleTracks.any { it.isSelected },
                                            isCompact = isPortrait,
                                            onClick = {
                                                settingsInitialTab = SettingsTab.SUBTITLE
                                                showSettingsPanel = true
                                            },
                                            modifier = Modifier.focusProperties { up = firstButtonFocusRequester }
                                        )

                                        VodStreamVaultTile(
                                            eyebrow = "Oran",
                                            value = aspectLabel,
                                            isCompact = isPortrait,
                                            onClick = {
                                                settingsInitialTab = SettingsTab.ASPECT_RATIO
                                                showSettingsPanel = true
                                            },
                                            modifier = Modifier.focusProperties { up = firstButtonFocusRequester }
                                        )

                                        VodStreamVaultTile(
                                            eyebrow = "Hız",
                                            value = "${playbackSpeed}x",
                                            isActive = playbackSpeed != 1.0f,
                                            isCompact = isPortrait,
                                            onClick = {
                                                settingsInitialTab = SettingsTab.SPEED
                                                showSettingsPanel = true
                                            },
                                            modifier = Modifier.focusProperties { up = firstButtonFocusRequester }
                                        )

                                        if (currentSeriesInfo != null && !currentSeriesInfo?.episodes.isNullOrEmpty()) {
                                            VodStreamVaultTile(
                                                eyebrow = "Bölümler",
                                                value = "Liste",
                                                isActive = showEpisodesDrawer,
                                                isCompact = isPortrait,
                                                onClick = { showEpisodesDrawer = true },
                                                modifier = Modifier.focusProperties { up = firstButtonFocusRequester }
                                            )
                                        }

                                        VodStreamVaultTile(
                                            eyebrow = "Baştan",
                                            value = "00:00",
                                            isCompact = isPortrait,
                                            onClick = {
                                                playerManager.seekTo(0L)
                                                showInfoBanner("Baştan başlatıldı")
                                            },
                                            modifier = Modifier.focusProperties { up = firstButtonFocusRequester }
                                        )

                                        VodStreamVaultTile(
                                            eyebrow = "Ayarlar",
                                            value = "Tümü",
                                            isCompact = isPortrait,
                                            onClick = {
                                                settingsInitialTab = SettingsTab.AUDIO
                                                showSettingsPanel = true
                                            },
                                            modifier = Modifier.focusProperties { up = firstButtonFocusRequester }
                                        )
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
                            text = "Hızlı kanal geçişi",
                            color = VegaColors.Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = VegaText.BodyProminent
                        )
                        var drawerCloseFocused by remember { mutableStateOf(false) }
                        Surface(
                            shape = VegaShapes.Pill,
                            color = if (drawerCloseFocused) VegaColors.Accent else VegaColors.OverlayPill,
                            border = BorderStroke(1.dp, if (drawerCloseFocused) Color.White else VegaColors.Border),
                            modifier = Modifier
                                .size(36.dp)
                                .dpadFocusable(
                                    shape = VegaShapes.Pill,
                                    focusedBorderColor = Color.White,
                                    scale = 1.05f,
                                    onClick = { showChannelDrawer = false },
                                    onFocusStateChange = { drawerCloseFocused = it }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = if (drawerCloseFocused) VegaColors.OnAccent else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                        itemsIndexed(categories, key = { idx, cat -> "cat_${cat.categoryId}_$idx" }) { _, cat ->
                            val isSel = cat.categoryId == selectedCategory
                            var isCatFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = VegaShapes.Small,
                                color = when {
                                    isCatFocused -> VegaColors.Accent
                                    isSel -> VegaColors.Accent.copy(alpha = 0.20f)
                                    else -> VegaColors.Card
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isCatFocused) Color.White else if (isSel) VegaColors.Accent else VegaColors.Border
                                ),
                                modifier = Modifier.dpadFocusable(
                                    shape = VegaShapes.Small,
                                    focusedBorderColor = Color.White,
                                    scale = 1.03f,
                                    onClick = { viewModel.loadLiveStreams(cat.categoryId) },
                                    onFocusStateChange = { isCatFocused = it }
                                )
                            ) {
                                Text(
                                    text = cat.categoryName,
                                    color = when {
                                        isCatFocused -> VegaColors.OnAccent
                                        isSel -> VegaColors.Accent
                                        else -> VegaColors.TextSecondary
                                    },
                                    fontSize = VegaText.Caption,
                                    fontWeight = if (isSel || isCatFocused) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                        itemsIndexed(
                            items = pagedDrawerList,
                            key = { idx, ch -> "drawer_${ch.streamId}_$idx" },
                            contentType = { _, _ -> "drawer_channel" }
                        ) { _, channel ->
                            val isCurrent = channel.streamId == streamId
                            var isChannelFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = VegaShapes.Medium,
                                color = when {
                                    isChannelFocused -> VegaColors.Accent
                                    isCurrent -> VegaColors.Accent.copy(alpha = 0.18f)
                                    else -> Color.Transparent
                                },
                                border = when {
                                    isChannelFocused -> BorderStroke(1.dp, Color.White)
                                    isCurrent -> BorderStroke(1.dp, VegaColors.Accent)
                                    else -> null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        shape = VegaShapes.Medium,
                                        focusedBorderColor = Color.White,
                                        scale = 1.02f,
                                        onClick = {
                                            showChannelDrawer = false
                                            viewModel.playLiveChannel(channel)
                                        },
                                        onFocusStateChange = { isChannelFocused = it }
                                    )
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
                                                .background(if (isChannelFocused) VegaColors.OnAccent else VegaColors.Accent)
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
                                            .background(VegaColors.Card),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = channel.name,
                                        color = when {
                                            isChannelFocused -> VegaColors.OnAccent
                                            isCurrent -> VegaColors.Accent
                                            else -> Color.White
                                        },
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (isCurrent || isChannelFocused) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isChannelFocused) VegaColors.OnAccent.copy(alpha = 0.2f) else VegaColors.Accent)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Canlı",
                                                color = if (isChannelFocused) VegaColors.OnAccent else VegaColors.OnAccent,
                                                fontSize = VegaText.Caption,
                                                fontWeight = FontWeight.Bold
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

        // OTOMATİK SONRAKİ BÖLÜM GEÇİŞ KARTI (Binge-Watching Popup — 4 Hz recomposition izole)
        if (!isLive && currentEpisode != null && nextEpisode != null && !autoNextDismissed && duration > 60_000L && !isInPipMode) {
            NextEpisodeCountdownOverlay(
                playerManager = playerManager,
                duration = duration,
                nextEpisode = nextEpisode,
                isPortrait = isPortrait,
                onDismiss = { autoNextDismissed = true },
                onWatchNow = { viewModel.playNextEpisode() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (isPortrait) 120.dp else 80.dp)
            )
        }

        // DAHA FAZLA (⋯) İKİNCİL SEÇENEKLER YAN PANELİ (G-023)
        LiveMorePanel(
            visible = showMoreSheet,
            isPortrait = isPortrait,
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
            sleepTimerSeconds = sleepTimerSeconds,
            onOpenSleepTimer = {
                showMoreSheet = false
                showSleepTimerDialog = true
            },
            diagnostics = streamDiagnostics,
            onClose = {
                showMoreSheet = false
                orientationManager?.setImmersiveMode(true)
                try {
                    playerFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        )

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

        // BÖLÜMLER SEÇİCİ YAN PANELİ (DİZİ MODUNDA - TEK ODAKLI STREAMVAULT PANEL)
        if (currentSeriesInfo != null) {
            val episodesMap = currentSeriesInfo?.episodes ?: emptyMap()
            val seasonKeys = remember(episodesMap) {
                episodesMap.keys.sortedBy { it.toIntOrNull() ?: 0 }
            }
            val activeEpisode = currentEpisode?.first
            var activeSeasonKey by remember(activeEpisode, seasonKeys) {
                mutableStateOf(activeEpisode?.season?.toString() ?: seasonKeys.firstOrNull() ?: "1")
            }
            val episodesListState = rememberLazyListState()
            val episodesFirstFocus = remember { FocusRequester() }

            val seasonEpisodes = episodesMap[activeSeasonKey] ?: emptyList()
            val activeEpisodeIndex = remember(seasonEpisodes, activeEpisode) {
                seasonEpisodes.indexOfFirst { it.id == activeEpisode?.id }.coerceAtLeast(0)
            }

            // Açılınca oynayan bölüme kaydır
            LaunchedEffect(showEpisodesDrawer, activeSeasonKey) {
                if (showEpisodesDrawer && seasonEpisodes.isNotEmpty()) {
                    try {
                        episodesListState.scrollToItem(activeEpisodeIndex)
                    } catch (_: Exception) {}
                }
            }

            PlayerSidePanel(
                visible = showEpisodesDrawer,
                title = currentSeriesInfo?.info?.name ?: title,
                onClose = {
                    showEpisodesDrawer = false
                    orientationManager?.setImmersiveMode(true)
                    if (!showControls) {
                        try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                },
                isPortrait = isPortrait,
                firstFocusRequester = if (seasonEpisodes.isNotEmpty()) episodesFirstFocus else null,
                headerIcon = Icons.AutoMirrored.Filled.List
            ) {
                if (seasonKeys.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        items(seasonKeys) { sKey ->
                            val isSel = sKey == activeSeasonKey
                            var isSeasonFocused by remember { mutableStateOf(false) }
                            // Tek odak + tıklama hedefi (çift odak yok)
                            Surface(
                                shape = VegaShapes.Small,
                                color = when {
                                    isSeasonFocused -> VegaColors.Accent
                                    isSel -> VegaColors.Accent.copy(alpha = 0.18f)
                                    else -> VegaColors.SurfaceVariant
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSeasonFocused) Color.White else if (isSel) VegaColors.Accent else VegaColors.Border
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .dpadFocusable(
                                        shape = VegaShapes.Small,
                                        focusedBorderColor = Color.White,
                                        scale = 1.03f,
                                        onClick = { activeSeasonKey = sKey },
                                        onFocusStateChange = { isSeasonFocused = it }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Sezon $sKey",
                                        color = when {
                                            isSeasonFocused -> VegaColors.OnAccent
                                            isSel -> VegaColors.Accent
                                            else -> VegaColors.TextSecondary
                                        },
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (isSel || isSeasonFocused) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = episodesListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(seasonEpisodes.size, key = { "${seasonEpisodes[it].id}_$it" }) { idx ->
                        val ep = seasonEpisodes[idx]
                        val isCurrentEp = activeEpisode?.id == ep.id
                        var isItemFocused by remember { mutableStateOf(false) }

                        // Tek odak + tıklama hedefi: Surface(onClick) KALDIRILDI, yalnızca dpadFocusable(onClick) kullanılır!
                        Surface(
                            shape = VegaShapes.Medium,
                            color = when {
                                isItemFocused -> VegaColors.Accent
                                isCurrentEp -> VegaColors.Accent.copy(alpha = 0.18f)
                                else -> VegaColors.SurfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isItemFocused -> Color.White
                                    isCurrentEp -> VegaColors.Accent
                                    else -> VegaColors.Border
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .then(if (idx == activeEpisodeIndex) Modifier.focusRequester(episodesFirstFocus) else Modifier)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium,
                                    scale = 1.02f,
                                    onClick = {
                                        viewModel.playSeriesEpisode(ep, currentSeriesInfo?.info?.name ?: title)
                                        showEpisodesDrawer = false
                                    },
                                    onFocusStateChange = { isItemFocused = it }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(VegaShapes.Small)
                                        .background(
                                            when {
                                                isItemFocused -> VegaColors.OnAccent.copy(alpha = 0.2f)
                                                isCurrentEp -> VegaColors.Accent
                                                else -> VegaColors.Card
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isItemFocused) VegaColors.OnAccent.copy(alpha = 0.4f)
                                            else if (isCurrentEp) VegaColors.Accent
                                            else VegaColors.Border,
                                            VegaShapes.Small
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ep.episodeNum.toString(),
                                        color = if (isItemFocused || isCurrentEp) VegaColors.OnAccent else Color.White,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ep.title.ifBlank { "Bölüm ${ep.episodeNum}" },
                                        color = when {
                                            isItemFocused -> VegaColors.OnAccent
                                            isCurrentEp -> VegaColors.Accent
                                            else -> VegaColors.TextPrimary
                                        },
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (isCurrentEp || isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    ep.info?.duration?.let { dur ->
                                        Text(
                                            text = dur,
                                            color = if (isItemFocused) VegaColors.OnAccent.copy(alpha = 0.78f) else VegaColors.TextMuted,
                                            fontSize = VegaText.Caption
                                        )
                                    }
                                }

                                if (isCurrentEp) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Oynatılıyor",
                                        tint = if (isItemFocused) VegaColors.OnAccent else VegaColors.Accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        // UYKU ZAMANLAYICISI MODAL DİYALOĞU
        if (showSleepTimerDialog) {
            SleepTimerDialog(
                sleepTimerSeconds = sleepTimerSeconds,
                onSetTimer = { playerManager.setSleepTimer(it) },
                onCancelTimer = { playerManager.cancelSleepTimer() },
                onDismiss = { showSleepTimerDialog = false }
            )
        }

        // ÇİFT EKRAN KANAL SEÇİCİ DİYALOĞU (G-023)
        if (isLive) {
            DualViewChannelPicker(
                visible = showDualViewPicker,
                isPortrait = isPortrait,
                liveStreams = allLiveStreams,
                currentStreamId = streamId,
                onChannelSelected = { channel ->
                    viewModel.startDualView(channel)
                    showDualViewPicker = false
                },
                onDismiss = {
                    showDualViewPicker = false
                    orientationManager?.setImmersiveMode(true)
                    try {
                        playerFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
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

/**
 * Film/Dizi alt cam paneli için tek odaklı ulaşım düğmesi (-10 sn, Oynat/Duraklat, +10 sn, Önceki/Sonraki Bölüm).
 */
@Composable
private fun VodTransportIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val registerInteraction = LocalPlayerInteraction.current
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isPrimary -> VegaColors.Accent.copy(alpha = 0.26f)
        else -> VegaColors.SurfaceElevated.copy(alpha = 0.92f)
    }
    val tintColor = when {
        isFocused -> VegaColors.OnAccent
        else -> Color.White
    }

    Surface(
        shape = VegaShapes.Pill,
        color = containerColor,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> Color.White
                isPrimary -> VegaColors.Accent
                else -> VegaColors.Border
            }
        ),
        modifier = modifier
            .size(size)
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = Color.White,
                scale = if (isPrimary) 1.06f else 1.05f,
                onClick = {
                    registerInteraction()
                    onClick()
                },
                onFocusStateChange = { focused ->
                    isFocused = focused
                    if (focused) registerInteraction()
                }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tintColor,
                modifier = Modifier.size(if (isPrimary) size * 0.54f else size * 0.50f)
            )
        }
    }
}

/**
 * Film/Dizi alt cam paneli için StreamVault iki satırlı hızlı aksiyon karosu (tek odak + tıklama hedefi).
 */
@Composable
private fun VodStreamVaultTile(
    eyebrow: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isCompact: Boolean = false
) {
    val registerInteraction = LocalPlayerInteraction.current
    var isFocused by remember { mutableStateOf(false) }
    val tileShape = RoundedCornerShape(11.dp)

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isActive -> VegaColors.Accent.copy(alpha = 0.22f)
        else -> VegaColors.SurfaceElevated.copy(alpha = 0.92f)
    }
    val eyebrowColor = when {
        isFocused -> VegaColors.OnAccent.copy(alpha = 0.80f)
        isActive -> VegaColors.Gold
        else -> VegaColors.Accent
    }
    val valueColor = when {
        isFocused -> VegaColors.OnAccent
        else -> VegaColors.TextPrimary
    }

    Surface(
        shape = tileShape,
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isFocused) Color.White else VegaColors.Border
        ),
        modifier = modifier
            .widthIn(min = if (isCompact) 82.dp else 96.dp)
            .height(if (isCompact) 44.dp else 50.dp)
            .dpadFocusable(
                focusedBorderColor = Color.White,
                shape = tileShape,
                scale = 1.04f,
                onClick = {
                    registerInteraction()
                    onClick()
                },
                onFocusStateChange = { focused ->
                    isFocused = focused
                    if (focused) registerInteraction()
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (isCompact) 10.dp else 13.dp,
                vertical = if (isCompact) 5.dp else 7.dp
            ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = eyebrow,
                color = eyebrowColor,
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Film/Dizi sarma önizleme balonu — currentPosition okumasını kök PlayerScreen'den izole eder.
 */
@Composable
private fun VodScrubPreviewPopup(
    visible: Boolean,
    scrubPosition: Float,
    duration: Long,
    optimisticSeekPosition: Long?,
    playerManager: TiviPlayerManager,
    thumbnailImage: String?
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + expandVertically(),
        exit = fadeOut(tween(150)) + shrinkVertically()
    ) {
        val currentPosition by playerManager.currentPosition.collectAsState()
        val scrubMillis = scrubPosition.toLong()
        val basePos = optimisticSeekPosition ?: currentPosition
        val diffSeconds = (scrubMillis - basePos) / 1000L
        val diffText = if (diffSeconds >= 0) "+${formatDuration(diffSeconds * 1000)}" else "-${formatDuration(-diffSeconds * 1000)}"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = VegaShapes.Medium,
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.7f)),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                                fontSize = VegaText.BodyProminent,
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
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption
                        )
                    }
                }
            }
        }
    }
}

/**
 * Film/Dizi ilerleme ve sarma çubuğu — 4 Hz currentPosition/bufferedPosition akışını
 * yalnızca bu küçük alt bileşende toplar; kök PlayerScreen ve VideoPlayerSurface etkilenmez.
 */
@Composable
private fun VodSeekBarSection(
    playerManager: TiviPlayerManager,
    duration: Long,
    isScrubbing: Boolean,
    scrubPosition: Float,
    onScrubbingChange: (Boolean) -> Unit,
    onScrubPositionChange: (Float) -> Unit,
    optimisticSeekPosition: Long?,
    onOptimisticSeekPositionChange: (Long?) -> Unit,
    gestureScrubActive: Boolean,
    gestureScrubTargetMs: Long,
    isSliderFocused: Boolean,
    onSliderFocusChange: (Boolean) -> Unit,
    sliderFocusRequester: FocusRequester,
    firstButtonFocusRequester: FocusRequester,
    triggerSeek: (Boolean) -> Unit,
    registerInteraction: () -> Unit
) {
    val currentPosition by playerManager.currentPosition.collectAsState()
    val bufferedPosition by playerManager.bufferedPosition.collectAsState()

    val effectivePosition = when {
        isScrubbing -> scrubPosition.toLong()
        gestureScrubActive -> gestureScrubTargetMs
        optimisticSeekPosition != null -> optimisticSeekPosition
        else -> currentPosition
    }

    val remainingMs = (duration - effectivePosition).coerceAtLeast(0L)
    val remainingText = "-${formatDuration(remainingMs)}"
    val currentFraction = (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val bufferFraction = (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(effectivePosition),
                color = VegaColors.TextPrimary,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${formatDuration(duration)}  ($remainingText)",
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(VegaShapes.Small)
                .border(
                    width = if (isSliderFocused) 2.dp else 0.dp,
                    color = if (isSliderFocused) VegaColors.Accent else Color.Transparent,
                    shape = VegaShapes.Small
                )
                .padding(horizontal = if (isSliderFocused) 6.dp else 2.dp, vertical = 4.dp)
                .focusRequester(sliderFocusRequester)
                .onFocusChanged {
                    onSliderFocusChange(it.isFocused)
                    if (it.isFocused) registerInteraction()
                }
                .focusProperties {
                    down = firstButtonFocusRequester
                }
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            try { firstButtonFocusRequester.requestFocus() } catch (_: Exception) {}
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
                            onOptimisticSeekPositionChange(target)
                            playerManager.seekTo(target)
                            registerInteraction()
                        }
                    }
                }
                .pointerInput(duration) {
                    var currentDragPos = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0 && duration > 0) {
                                onScrubbingChange(true)
                                val startFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                currentDragPos = startFraction * duration
                                onScrubPositionChange(currentDragPos)
                                registerInteraction()
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            if (size.width > 0 && duration > 0) {
                                val dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                currentDragPos = dragFraction * duration
                                onScrubPositionChange(currentDragPos)
                                registerInteraction()
                            }
                        },
                        onDragEnd = {
                            val target = currentDragPos.toLong().coerceIn(0L, duration)
                            onOptimisticSeekPositionChange(target)
                            playerManager.seekTo(target)
                            onScrubbingChange(false)
                            registerInteraction()
                        },
                        onDragCancel = {
                            onScrubbingChange(false)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // StreamVault Parlak Mavi İlerleme Çubuğu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isSliderFocused) 7.dp else 5.dp)
                    .clip(VegaShapes.Pill)
                    .background(VegaColors.SurfaceAccent)
            ) {
                if (bufferFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferFraction)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.25f))
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
                                        VegaColors.Accent.copy(alpha = 0.85f),
                                        VegaColors.Accent
                                    )
                                )
                            )
                    )
                }
            }

            // Parlak Tutamaç (Thumb)
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
                            .background(if (isSliderFocused) Color.White else VegaColors.Accent)
                            .border(
                                width = 2.dp,
                                color = if (isSliderFocused) VegaColors.Accent else Color.White,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

/**
 * Otomatik sonraki bölüm geri sayım kartı — 4 Hz currentPosition akışını kendi içinde izole eder.
 */
@Composable
private fun NextEpisodeCountdownOverlay(
    playerManager: TiviPlayerManager,
    duration: Long,
    nextEpisode: com.tivimax.player.data.model.SeriesEpisode,
    isPortrait: Boolean,
    onDismiss: () -> Unit,
    onWatchNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPosition by playerManager.currentPosition.collectAsState()
    val timeRemainingMs = if (duration > 0) duration - currentPosition else Long.MAX_VALUE
    val showNextEpisodePrompt = timeRemainingMs in 1000L..25_000L

    AnimatedVisibility(
        visible = showNextEpisodePrompt,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VegaColors.OverlayPanel),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.widthIn(max = if (isPortrait) 280.dp else 300.dp)
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
                        tint = VegaColors.Danger,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Sonraki bölüm (${(timeRemainingMs / 1000L)} sn)",
                        color = VegaColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = VegaText.Secondary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    var dismissNextFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Pill,
                        color = if (dismissNextFocused) VegaColors.Accent else VegaColors.OverlayPill,
                        border = BorderStroke(1.dp, if (dismissNextFocused) Color.White else VegaColors.Border),
                        modifier = Modifier
                            .size(30.dp)
                            .dpadFocusable(
                                shape = VegaShapes.Pill,
                                focusedBorderColor = Color.White,
                                scale = 1.05f,
                                onClick = onDismiss,
                                onFocusStateChange = { dismissNextFocused = it }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = if (dismissNextFocused) VegaColors.OnAccent else VegaColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "S${nextEpisode.season} B${nextEpisode.episodeNum}: ${nextEpisode.title}",
                    color = Color.White,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    var watchNowFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Small,
                        color = VegaColors.Accent,
                        border = if (watchNowFocused) BorderStroke(2.dp, Color.White) else null,
                        modifier = Modifier
                            .height(38.dp)
                            .dpadFocusable(
                                shape = VegaShapes.Small,
                                focusedBorderColor = Color.White,
                                scale = 1.04f,
                                onClick = onWatchNow,
                                onFocusStateChange = { watchNowFocused = it }
                            )
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Hemen izle",
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold,
                                color = VegaColors.OnAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
