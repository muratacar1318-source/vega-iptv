package com.tivimax.player.ui.tv

import android.app.Activity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.decodeBase64IfNeeded
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.DelayedSubtitleController
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.player.components.LiveTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * OTANTİK TIVIMATE TAM EKRAN OYNATICI VE KUMANDA (D-PAD) OSD KATMANI (SADECE ANDROID TV)
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayerOverlay(
    viewModel: MainViewModel,
    channel: LiveStream,
    onCloseFullScreen: () -> Unit,
    onOpenEpgGrid: (() -> Unit)? = null
) {
    val playerManager = viewModel.playerManager
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val aspectRatio by playerManager.aspectRatio.collectAsState()
    val streamDiagnostics by playerManager.streamDiagnostics.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()

    val activePreviewChan by viewModel.activePreviewChannel.collectAsState()
    val activeChannel = activePreviewChan ?: channel
    val isFavorite = favoriteIds.contains(activeChannel.streamId.toString())

    val categories by viewModel.visibleLiveCategories.collectAsState()
    val allStreams by viewModel.liveStreams.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    val audioTracks by playerManager.audioTracks.collectAsState()
    val subtitleTracks by playerManager.subtitleTracks.collectAsState()
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val anySubSelected = subtitleTracks.any { it.isSelected }
    val startupDebugInfo by playerManager.startupDebugInfo.collectAsState()
    // G-046: Oynatma hatası / yeniden bağlanma mesajı TV'de de görünsün
    val playerError by playerManager.errorMessage.collectAsState()
    val reconnectStatus by playerManager.reconnectStatus.collectAsState()
    val playerInstanceVersion by playerManager.playerInstanceVersion.collectAsState()
    val useTextureView by playerManager.useTextureView.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val delayedController = remember {
        DelayedSubtitleController(context, coroutineScope)
    }

    DisposableEffect(delayedController) {
        onDispose {
            delayedController.detach()
        }
    }

    // TV'de ekran koruyucusunu önle (FLAG_KEEP_SCREEN_ON) ve çıkışta AFR'yi varsayılana sıfırla
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            playerManager.resetAutoFrameRate(activity)
        }
    }

    // Yayın READY olup kare hızı/boyutu belli olunca otomatik yenileme hızını (AFR) uygula
    LaunchedEffect(isPlaying, streamDiagnostics.frameRate, streamDiagnostics.resolution, activeChannel.streamId, playerInstanceVersion) {
        if (activity != null && isPlaying) {
            playerManager.applyAutoFrameRate(activity)
        }
    }

    var tvPlayerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(tvPlayerViewRef, playerInstanceVersion) {
        val view = tvPlayerViewRef ?: return@LaunchedEffect
        val currentPlayer = playerManager.player ?: playerManager.initializePlayer(isLive = true)
        playerManager.attachView(view, isPrimary = true, tag = "TvPlayerOverlay-bind")
        try {
            delayedController.attach(view, currentPlayer)
            delayedController.updateDelay(subtitleDelayMs)
            delayedController.updateStyle(subtitleColor, subtitleSize)
        } catch (_: Throwable) {}
    }

    LaunchedEffect(tvPlayerViewRef, subtitleDelayMs) {
        if (tvPlayerViewRef != null) {
            try { delayedController.updateDelay(subtitleDelayMs) } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(tvPlayerViewRef, subtitleColor, subtitleSize) {
        if (tvPlayerViewRef != null) {
            try { delayedController.updateStyle(subtitleColor, subtitleSize) } catch (_: Throwable) {}
        }
    }

    var showInfoCard by remember { mutableStateOf(true) }
    var showActionTray by remember { mutableStateOf(false) }
    var showOverlayGuide by remember { mutableStateOf(false) }
    // G-045: Liste açıldıktan sonra kullanıcı ▲▼ ile gezindi mi? (Sadece gezinince kanal kendiliğinden açılır)
    var guideNavigated by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf(false) }

    var seekRippleMessage by remember { mutableStateOf<String?>(null) }
    var seekRippleToken by remember { mutableLongStateOf(0L) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(seekRippleToken) {
        if (seekRippleMessage != null) {
            delay(1600L)
            seekRippleMessage = null
        }
    }

    val zapperNumber by viewModel.zapperNumber.collectAsState()
    val isZapperActive by viewModel.isZapperActive.collectAsState()
    val zapperMatchedChannel by viewModel.zapperMatchedChannel.collectAsState()

    val overlayFocusRequester = remember { FocusRequester() }
    val actionDockFocusRequester = remember { FocusRequester() }
    // G-032: Kanal listesi ve ses/altyazı paneli için odak + kaydırma
    val guideFocusRequester = remember { FocusRequester() }
    val trackSheetFocusRequester = remember { FocusRequester() }
    val guideListState = rememberLazyListState()
    val guideFocusIndex = remember(allStreams, activeChannel.streamId) {
        allStreams.indexOfFirst { it.streamId == activeChannel.streamId }.coerceAtLeast(0)
    }

    // Rakam Tuşlaması & Saat
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clockText = sdf.format(Date())
            delay(10000L)
        }
    }

    LaunchedEffect(Unit) {
        repeat(4) {
            delay(50)
            try {
                overlayFocusRequester.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showInfoCard, showActionTray, showOverlayGuide, showTrackSheet) {
        if (!showActionTray && !showOverlayGuide && !showTrackSheet) {
            repeat(4) {
                delay(40)
                try {
                    overlayFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {}
            }
        }
    }

    // Alt kontrol çubuğu (StreamVault karoları) açılınca odağı ilk karoya taşı
    LaunchedEffect(showActionTray) {
        if (showActionTray && !showTrackSheet && !showOverlayGuide) {
            repeat(5) {
                delay(50)
                try {
                    actionDockFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {}
            }
        }
    }

    // G-032: Kanal listesi açılınca oynayan kanala kaydır ve odakla
    LaunchedEffect(showOverlayGuide) {
        if (showOverlayGuide && allStreams.isNotEmpty()) {
            try { guideListState.scrollToItem(guideFocusIndex) } catch (_: Exception) {}
            repeat(5) {
                delay(60)
                try {
                    guideFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {}
            }
        }
    }

    // G-032: Ses/altyazı paneli açılınca ilk parçaya, kapanınca menüye odak
    LaunchedEffect(showTrackSheet) {
        if (showTrackSheet) {
            repeat(5) {
                delay(60)
                try {
                    trackSheetFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {}
            }
        } else if (showActionTray) {
            repeat(4) {
                delay(50)
                try {
                    actionDockFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {}
            }
        }
    }

    // G-038: Bilgi kartı açıkken teknik bilgileri her saniye güncelle
    LaunchedEffect(showInfoCard) {
        while (showInfoCard) {
            playerManager.updateDiagnostics()
            delay(1000L)
        }
    }

    // 5 Saniyelik Otomatik Gizlenme Zamanlayıcısı
    LaunchedEffect(showInfoCard, showActionTray, lastInteractionTime) {
        if ((showInfoCard || showActionTray) && !showOverlayGuide && !showTrackSheet) {
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            val remaining = 5000L - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            showInfoCard = false
            showActionTray = false
        }
    }

    val registerInteraction = {
        lastInteractionTime = System.currentTimeMillis()
    }

    BackHandler(enabled = true) {
        if (isZapperActive) {
            viewModel.dismissZapper()
        } else if (showTrackSheet) {
            showTrackSheet = false
        } else if (showOverlayGuide) {
            showOverlayGuide = false
        } else if (showActionTray) {
            showActionTray = false
        } else if (showInfoCard) {
            showInfoCard = false
        } else {
            onCloseFullScreen()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(overlayFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                registerInteraction()

                // Rakam Tuşlaması (0-9 ve Numpad 0-9)
                if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                    val digit = keyCode - KeyEvent.KEYCODE_0
                    viewModel.onNumericKeyPressed(digit)
                    return@onPreviewKeyEvent true
                }
                if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
                    val digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0
                    viewModel.onNumericKeyPressed(digit)
                    return@onPreviewKeyEvent true
                }

                // EĞER ZAPPER AKTİF VE ENTER'A BASILDIYSA ANINDA GEÇ
                if (isZapperActive && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    viewModel.executeZapperSwitch()
                    return@onPreviewKeyEvent true
                }

                // EĞER AÇIK SÜTUN VEYA YAN PANEL VARSA (En üstteki paneli kapat)
                if (showOverlayGuide || showTrackSheet) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        if (showTrackSheet) {
                            showTrackSheet = false
                            // showActionTray açıksa LaunchedEffect(showTrackSheet) odağı aksiyon karolarına döndürür
                            if (!showActionTray) {
                                try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        } else if (showOverlayGuide) {
                            showOverlayGuide = false
                            try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        return@onPreviewKeyEvent true
                    }
                    // G-032: Kanal listesi açıkken ▶ listeyi kapatır
                    if (showOverlayGuide && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        showOverlayGuide = false
                        try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
                        return@onPreviewKeyEvent true
                    }
                    // G-045: Listede ▲▼ ile gezinme başladı
                    if (showOverlayGuide && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                        guideNavigated = true
                    }
                    return@onPreviewKeyEvent false
                }

                // G-032: Menü (alt tepsi) açıkken tuşlar menünün içinde çalışsın; Geri veya Menü kapatır
                if (showActionTray) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                            showActionTray = false
                            try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
                            return@onPreviewKeyEvent true
                        }
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
                    }
                    return@onPreviewKeyEvent false
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (keyEvent.nativeKeyEvent.repeatCount > 0) {
                            return@onPreviewKeyEvent true
                        }
                        // G-046: Hata mesajı varken OK kanalı yeniden açar
                        if (playerError != null) {
                            viewModel.retryLiveChannel(activeChannel)
                            return@onPreviewKeyEvent true
                        }
                        // G-038: OK yayını durdurmaz; bilgi kartını açar/kapatır
                        showInfoCard = !showInfoCard
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_CHANNEL_UP,
                    KeyEvent.KEYCODE_PAGE_UP -> {
                        val next = viewModel.zapNextChannel(activeChannel.streamId)
                        if (next != null) {
                            seekRippleMessage = "Sonraki: ${next.name}"
                            seekRippleToken = System.currentTimeMillis()
                        }
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_CHANNEL_DOWN,
                    KeyEvent.KEYCODE_PAGE_DOWN -> {
                        val prev = viewModel.zapPreviousChannel(activeChannel.streamId)
                        if (prev != null) {
                            seekRippleMessage = "Önceki: ${prev.name}"
                            seekRippleToken = System.currentTimeMillis()
                        }
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

                    // G-032: ▶ menüyü açar ve odak LaunchedEffect(showActionTray) ile ilk düğmeye gider
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        showInfoCard = true
                        showActionTray = true
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        guideNavigated = false // G-045
                        showOverlayGuide = true
                        showInfoCard = false
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_MENU -> {
                        showActionTray = !showActionTray
                        if (showActionTray) {
                            showInfoCard = true
                        } else {
                            try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_INFO -> {
                        showInfoCard = !showInfoCard
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BUTTON_Y -> {
                        guideNavigated = false
                        showOverlayGuide = !showOverlayGuide
                        showInfoCard = false
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        val prev = viewModel.zapRecall()
                        if (prev != null) {
                            seekRippleMessage = "Önceki: ${prev.name}"
                            seekRippleToken = System.currentTimeMillis()
                        }
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        val next = viewModel.zapNextChannel(activeChannel.streamId)
                        if (next != null) {
                            seekRippleMessage = "Sonraki: ${next.name}"
                            seekRippleToken = System.currentTimeMillis()
                        }
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

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

                    KeyEvent.KEYCODE_BACK -> {
                        if (isZapperActive) {
                            viewModel.dismissZapper()
                            return@onPreviewKeyEvent true
                        }
                        if (showActionTray) showActionTray = false
                        else if (showInfoCard) showInfoCard = false
                        else onCloseFullScreen()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        // 1. TAM EKRAN VİDEO YÜZEYİ (SINGLETON EXOPLAYER - ZERO REBUFFERING)
        key(useTextureView) {
            AndroidView(
                factory = { ctx ->
                    val layoutRes = if (useTextureView) {
                        R.layout.player_texture_view
                    } else {
                        R.layout.player_surface_view
                    }
                    val view = LayoutInflater.from(ctx).inflate(layoutRes, null, false) as PlayerView
                    view.apply {
                        useController = false
                        keepScreenOn = true
                        setKeepContentOnPlayerReset(true)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val initialPlayer = playerManager.player ?: playerManager.initializePlayer(isLive = true)
                        playerManager.attachView(this, isPrimary = true, tag = "TvPlayerOverlay")
                        try {
                            delayedController.attach(this, initialPlayer)
                            delayedController.updateDelay(subtitleDelayMs)
                            delayedController.updateStyle(subtitleColor, subtitleSize)
                        } catch (_: Throwable) {}
                    }
                    tvPlayerViewRef = view
                    view
                },
                update = { playerView ->
                    // G-042: En-boy oranı (telefon oynatıcısıyla aynı eşleme)
                    val (targetResizeMode, targetScaleX, targetScaleY) = when (aspectRatio) {
                        AspectRatioMode.FIT -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                        AspectRatioMode.FILL -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                        AspectRatioMode.ZOOM -> Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.18f, 1.18f)
                        AspectRatioMode.RATIO_16_9 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                        AspectRatioMode.RATIO_4_3 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0.75f, 1.0f)
                    }
                    if (playerView.resizeMode != targetResizeMode) playerView.resizeMode = targetResizeMode
                    if (playerView.scaleX != targetScaleX || playerView.scaleY != targetScaleY) {
                        playerView.scaleX = targetScaleX
                        playerView.scaleY = targetScaleY
                    }
                },
                onRelease = { playerView ->
                    if (tvPlayerViewRef === playerView) {
                        tvPlayerViewRef = null
                    }
                    delayedController.detach()
                    playerManager.detachView(playerView, tag = "TvPlayerOverlay")
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // BUFFET / YÜKLENİYOR
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = VegaColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // YENİDEN BAĞLANMA HAP BİLDİRİMİ
        reconnectStatus?.let { status ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 56.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = VegaShapes.Pill,
                    color = VegaColors.OverlayPanel,
                    border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = VegaColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = status,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // G-046: HATA / YENİDEN BAĞLANMA MESAJI
        if (reconnectStatus == null) {
            playerError?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.OverlayPanel,
                        border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = msg,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "OK: Tekrar dene   ▲▼: Kanal değiştir",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption
                            )
                        }
                    }
                }
            }
        }

        // ÜST YUMUŞAK GRADYAN KARARTMA (Kutusuz üst köşe okunabilirliği)
        AnimatedVisibility(
            visible = showInfoCard || showActionTray,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.62f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // 2A. SOL ÜST KÖŞE: KUTUSUZ GERİ İPUCU (Sadece OSD açıkken)
        AnimatedVisibility(
            visible = showInfoCard || showActionTray,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 20.dp, start = 28.dp)
        ) {
            var backFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .clip(VegaShapes.Pill)
                    .background(if (backFocused) VegaColors.Accent else Color.Transparent)
                    .dpadFocusable(
                        shape = VegaShapes.Pill,
                        drawFocusBorder = false,
                        scale = 1.0f,
                        onClick = onCloseFullScreen,
                        onFocusStateChange = { backFocused = it }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = if (backFocused) VegaColors.OnAccent else Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Geri",
                    color = if (backFocused) VegaColors.OnAccent else Color.White.copy(alpha = 0.78f),
                    fontSize = VegaText.Secondary,
                    fontWeight = if (backFocused) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }

        // KANAL DEĞİŞTİRME BİLDİRİMİ (Minimal üst yayın bandı)
        AnimatedVisibility(
            visible = seekRippleMessage != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        ) {
            seekRippleMessage?.let { rippleMsg ->
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.OverlayPanel,
                    border = BorderStroke(0.5.dp, VegaColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = rippleMsg,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2B. SAĞ ÜST KÖŞE: MİNİMAL SAAT & YAYIN TİPİ RAKAM ZAPPER
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 28.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Minimal canlı saat (HH:mm — kutusuz, sadece OSD açıkken)
            if (showInfoCard || showActionTray) {
                Text(
                    text = clockText,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.SemiBold,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
            }

            // RAKAM İLE KANAL GEÇİŞİ (TiviMate Broadcast Tabular Zapper)
            if (isZapperActive && zapperNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.OverlayPanel,
                    border = BorderStroke(0.75.dp, VegaColors.Border)
                ) {
                    Column(
                        modifier = Modifier.widthIn(min = 148.dp, max = 260.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = zapperNumber,
                                color = VegaColors.TextPrimary,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                            )
                            val matched = zapperMatchedChannel
                            if (matched != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(VegaColors.SurfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncShimmerImage(
                                            model = matched.streamIcon,
                                            contentDescription = matched.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp),
                                            placeholderIcon = Icons.Default.Tv
                                        )
                                    }
                                    Text(
                                        text = matched.name,
                                        color = VegaColors.TextPrimary,
                                        fontSize = VegaText.Secondary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(VegaColors.Accent)
                        )
                    }
                }
            }
        }

        // 3. TAM GENİŞLİKLİ SABİT TIVIMATE YAYIN ALT OSD PANELİ
        AnimatedVisibility(
            visible = showInfoCard,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            val epg = epgSummaryMap[activeChannel.streamId]
            val currentTitle = epg?.getDecodedTitle()
                ?: activeChannel.currentProgram.decodeBase64IfNeeded().ifBlank { "Canlı yayın" }
            val timeRange = LiveTimeFormatter.formatTimeRange(epg)
            val progress = LiveTimeFormatter.calculateProgress(
                epg,
                if (activeChannel.programProgress > 0f) activeChannel.programProgress else null
            )
            val rawNext = activeChannel.nextProgram ?: epg?.getDecodedDescription()
            val nextTitle = rawNext.decodeBase64IfNeeded().trim()
            val nextStartTime = remember(epg?.stopTimestamp) {
                val stop = epg?.stopTimestamp ?: 0L
                if (stop > 0L) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stop * 1000L))
                } else ""
            }
            val qualityBadges = remember(activeChannel.name) { extractQualityBadges(activeChannel.name) }
            val techSummary = remember(streamDiagnostics.resolutionBadge, streamDiagnostics.frameRateBadge, qualityBadges) {
                listOf(
                    streamDiagnostics.resolutionBadge.ifBlank { qualityBadges.firstOrNull().orEmpty() },
                    streamDiagnostics.frameRateBadge
                ).filter { it.isNotBlank() }.joinToString(" · ")
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Yumuşak üst geçiş gradyanı (Color.Transparent -> OverlayPanel)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    VegaColors.OverlayPanel.copy(alpha = 0.52f)
                                )
                            )
                        )
                )

                // Jilet inceliğinde 0.5dp üst ayırıcı çizgi
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = VegaColors.Border
                )

                // Tam genişlikli kenara sıfır yayın bilgi bloğu
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VegaColors.OverlayPanel)
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // SOL: 56dp kare kanal logosu kutusu
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(VegaColors.SurfaceElevated)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncShimmerImage(
                                model = activeChannel.streamIcon,
                                contentDescription = activeChannel.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                placeholderIcon = Icons.Default.Tv
                            )
                        }

                        // SAĞ: Kanal kimliği + program + 3dp ilerleme + sonraki program
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // 1. Satır: Tabular numara + Kanal adı + 6dp canlı nokta + düz metin teknik bilgi
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (activeChannel.num > 0) {
                                    Text(
                                        text = activeChannel.num.toString(),
                                        color = VegaColors.Accent,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                }

                                Text(
                                    text = activeChannel.name,
                                    color = VegaColors.TextPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(VegaColors.Live)
                                )

                                if (techSummary.isNotBlank()) {
                                    Text(
                                        text = techSummary,
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }

                            // 2. Satır: Güncel program (16sp kalın) + sağa yaslı saat aralığı
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = currentTitle,
                                    color = VegaColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (timeRange.isNotBlank()) {
                                    Text(
                                        text = timeRange,
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Secondary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }

                            // 3. Satır: Jilet keskinliğinde 3dp tam genişlikli ilerleme çubuğu
                            if (progress != null) {
                                val clampedProgress = progress.coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color.White.copy(alpha = 0.16f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(clampedProgress)
                                            .fillMaxHeight()
                                            .background(VegaColors.Accent)
                                    )
                                }
                            }

                            // 4. Satır: Sonraki program (tek satır)
                            if (nextTitle.isNotBlank()) {
                                val nextLine = if (nextStartTime.isNotBlank()) {
                                    "Sonraki · $nextStartTime $nextTitle"
                                } else {
                                    "Sonraki · $nextTitle"
                                }
                                Text(
                                    text = nextLine,
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (com.tivimax.player.BuildConfig.DEBUG && startupDebugInfo.isNotBlank()) {
                        Text(
                            text = "Açılış ölçümü: $startupDebugInfo",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 2
                        )
                    }

                    if (!showActionTray) {
                        Text(
                            text = "▲▼ Kanal değiştir   ◀ Kanal listesi   ▶ Seçenekler",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 1
                        )
                    }

                    // 4. TIVIMATE YUVARLAK İKON + ETİKET AKSİYON ŞERİDİ (▶ TUŞU İLE AÇILIR)
                    AnimatedVisibility(
                        visible = showActionTray,
                        enter = expandVertically(tween(150)) + fadeIn(tween(150)),
                        exit = shrinkVertically(tween(120)) + fadeOut(tween(120))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = VegaColors.Border.copy(alpha = 0.45f)
                            )

                            // Son İzlenenler Şeridi (kompakt, çerçevesiz)
                            if (recentChannels.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(recentChannels.take(10), key = { idx, it -> "rec_${it.streamId}_$idx" }) { _, rec ->
                                        var recFocused by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (recFocused) VegaColors.Accent else Color.White.copy(alpha = 0.07f)
                                                )
                                                .dpadFocusable(
                                                    shape = RoundedCornerShape(6.dp),
                                                    drawFocusBorder = false,
                                                    scale = 1.0f,
                                                    onClick = { viewModel.playLiveChannel(rec) },
                                                    onFocusStateChange = { recFocused = it }
                                                )
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(VegaColors.SurfaceElevated),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncShimmerImage(
                                                    model = rec.streamIcon,
                                                    contentDescription = rec.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(2.dp),
                                                    placeholderIcon = Icons.Default.Tv
                                                )
                                            }
                                            Text(
                                                text = rec.name,
                                                color = if (recFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                                                fontSize = VegaText.Caption,
                                                fontWeight = if (recFocused) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // TiviMate Yuvarlak İkon + Alt Etiket Aksiyon Şeridi
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                TvCircularActionButton(
                                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    label = if (isPlaying) "Duraklat" else "Oynat",
                                    onClick = { playerManager.togglePlayPause() },
                                    modifier = Modifier.focusRequester(actionDockFocusRequester)
                                )
                                TvCircularActionButton(
                                    icon = Icons.Default.Tv,
                                    label = "Kanallar",
                                    onClick = {
                                        guideNavigated = false
                                        showOverlayGuide = true
                                        showInfoCard = false
                                        showActionTray = false
                                    }
                                )
                                if (onOpenEpgGrid != null) {
                                    TvCircularActionButton(
                                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        label = "Rehber",
                                        onClick = onOpenEpgGrid
                                    )
                                }
                                TvCircularActionButton(
                                    icon = Icons.Default.Subtitles,
                                    label = "Ses / Altyazı",
                                    onClick = { showTrackSheet = true }
                                )
                                TvCircularActionButton(
                                    icon = Icons.Default.AspectRatio,
                                    label = "Görüntü",
                                    onClick = {
                                        val nextMode = playerManager.cycleAspectRatio()
                                        val modeLabel = when (nextMode) {
                                            AspectRatioMode.FIT -> "Orijinal"
                                            AspectRatioMode.FILL -> "Doldur"
                                            AspectRatioMode.ZOOM -> "Yakınlaştır"
                                            AspectRatioMode.RATIO_16_9 -> "16:9"
                                            AspectRatioMode.RATIO_4_3 -> "4:3"
                                        }
                                        seekRippleMessage = "Görüntü: $modeLabel"
                                        seekRippleToken = System.currentTimeMillis()
                                    }
                                )
                                TvCircularActionButton(
                                    icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    label = "Favori",
                                    isActive = isFavorite,
                                    activeColor = VegaColors.Gold,
                                    onClick = { viewModel.toggleFavoriteLive(activeChannel.streamId.toString()) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. KENARA SIFIR YARI SAYDAM TIVIMATE KANAL LİSTESİ ÇEKMECESİ (SOL KENAR)
        AnimatedVisibility(
            visible = showOverlayGuide,
            enter = slideInHorizontally(tween(160), initialOffsetX = { -it }) + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(140), targetOffsetX = { -it }) + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(412.dp)
                    .background(VegaColors.OverlayPanel)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Kanallar",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (allStreams.isNotEmpty()) {
                            Text(
                                text = "${allStreams.size}",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Medium,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border)

                    LazyColumn(
                        state = guideListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(allStreams, key = { index, it -> "guide_${it.streamId}_$index" }) { index, st ->
                            val isSel = st.streamId == activeChannel.streamId
                            var isRowFocused by remember { mutableStateOf(false) }
                            val rowEpg = epgSummaryMap[st.streamId]
                            val rowProgram = rowEpg?.getDecodedTitle()
                                ?: st.currentProgram.decodeBase64IfNeeded().trim()
                            val rowProgress = LiveTimeFormatter.calculateProgress(
                                rowEpg,
                                if (st.programProgress > 0f) st.programProgress else null
                            )

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(
                                            when {
                                                isRowFocused -> VegaColors.Accent
                                                isSel -> VegaColors.Accent.copy(alpha = 0.12f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(if (index == guideFocusIndex) Modifier.focusRequester(guideFocusRequester) else Modifier)
                                        .dpadFocusable(
                                            shape = RectangleShape,
                                            drawFocusBorder = false,
                                            scale = 1.0f,
                                            onClick = {
                                                // G-045: Kanal zaten açıldıysa OK sadece listeyi kapatır (yeniden bağlanmaz)
                                                if (st.streamId != activeChannel.streamId) {
                                                    viewModel.playLiveChannel(st)
                                                }
                                                showOverlayGuide = false
                                                showInfoCard = true
                                            },
                                            onFocusStateChange = { focused ->
                                                isRowFocused = focused
                                                // G-045: Listede gezinince kanal OK'a basmadan açılır
                                                if (focused && showOverlayGuide && guideNavigated && st.streamId != activeChannel.streamId) {
                                                    viewModel.tvGuidePreviewChannel(st)
                                                }
                                            }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Aktif kanal 3dp sol vurgu çubuğu
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(
                                                when {
                                                    isSel && isRowFocused -> VegaColors.OnAccent
                                                    isSel -> VegaColors.Accent
                                                    else -> Color.Transparent
                                                }
                                            )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = if (st.num > 0) "${st.num}" else "•",
                                            color = if (isRowFocused) VegaColors.OnAccent else if (isSel) VegaColors.Accent else VegaColors.TextSecondary,
                                            fontSize = VegaText.Secondary,
                                            fontWeight = FontWeight.Bold,
                                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isRowFocused) VegaColors.OnAccent.copy(alpha = 0.14f)
                                                    else VegaColors.SurfaceElevated
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncShimmerImage(
                                                model = st.streamIcon,
                                                contentDescription = st.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(3.dp),
                                                placeholderIcon = Icons.Default.Tv
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = st.name,
                                                color = if (isRowFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                                                fontSize = VegaText.Body,
                                                fontWeight = if (isRowFocused || isSel) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (rowProgram.isNotBlank()) {
                                                Text(
                                                    text = rowProgram,
                                                    color = if (isRowFocused) VegaColors.OnAccent.copy(alpha = 0.78f) else VegaColors.TextSecondary,
                                                    fontSize = VegaText.Caption,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (rowProgress != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .background(
                                                            if (isRowFocused) VegaColors.OnAccent.copy(alpha = 0.24f)
                                                            else Color.White.copy(alpha = 0.14f)
                                                        )
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(rowProgress.coerceIn(0f, 1f))
                                                            .fillMaxHeight()
                                                            .background(if (isRowFocused) VegaColors.OnAccent else VegaColors.Accent)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (index < allStreams.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = VegaColors.Border.copy(alpha = 0.28f)
                                    )
                                }
                            }
                        }
                    }
                }

                // İç kenar 0.5dp jilet çizgi
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(VegaColors.Border)
                )
            }
        }

        // 6. KENARA SIFIR YARI SAYDAM TIVIMATE SES VE ALTYAZI ÇEKMECESİ (SAĞ KENAR)
        AnimatedVisibility(
            visible = showTrackSheet,
            enter = slideInHorizontally(tween(160), initialOffsetX = { it }) + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(140), targetOffsetX = { it }) + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(VegaColors.OverlayPanel)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Ses ve altyazı",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        var closeFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (closeFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f))
                                .dpadFocusable(
                                    shape = CircleShape,
                                    drawFocusBorder = false,
                                    scale = 1.0f,
                                    onClick = { showTrackSheet = false },
                                    onFocusStateChange = { closeFocused = it }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = if (closeFocused) VegaColors.OnAccent else VegaColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // 1. SES DİLİ
                        item {
                            Text(
                                text = "Ses dili",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
                            )
                        }

                        if (audioTracks.isEmpty()) {
                            item {
                                Text(
                                    text = "Ek ses parçası bulunamadı",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Secondary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            itemsIndexed(audioTracks, key = { idx, track -> "audio_${track.groupIndex}_${track.formatIndex}_$idx" }) { idx, track ->
                                TvTrackSelectionRow(
                                    title = track.name,
                                    icon = Icons.Default.Audiotrack,
                                    selected = track.isSelected,
                                    modifier = if (idx == 0) Modifier.focusRequester(trackSheetFocusRequester) else Modifier,
                                    onClick = { playerManager.selectTrack(track, C.TRACK_TYPE_AUDIO) }
                                )
                                if (idx < audioTracks.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = VegaColors.Border.copy(alpha = 0.28f)
                                    )
                                }
                            }
                        }

                        item {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = VegaColors.Border.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // 2. ALTYAZI
                        item {
                            Text(
                                text = "Altyazı",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
                            )
                        }

                        // Altyazı Kapalı Seçeneği
                        item {
                            TvTrackSelectionRow(
                                title = "Kapalı",
                                icon = Icons.Default.SubtitlesOff,
                                selected = !anySubSelected,
                                modifier = if (audioTracks.isEmpty()) Modifier.focusRequester(trackSheetFocusRequester) else Modifier,
                                onClick = { playerManager.disableSubtitles() }
                            )
                        }

                        itemsIndexed(subtitleTracks, key = { idx, track -> "sub_${track.groupIndex}_${track.formatIndex}_$idx" }) { _, track ->
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = VegaColors.Border.copy(alpha = 0.28f)
                            )
                            TvTrackSelectionRow(
                                title = track.name,
                                icon = Icons.Default.ClosedCaption,
                                selected = track.isSelected,
                                onClick = { playerManager.selectTrack(track, C.TRACK_TYPE_TEXT) }
                            )
                        }

                        // 3. ALTYAZI GECİKMESİ
                        item {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = VegaColors.Border.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            Text(
                                text = "Altyazı gecikmesi",
                                color = if (anySubSelected) VegaColors.TextMuted else VegaColors.TextMuted.copy(alpha = 0.6f),
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
                            )
                        }

                        if (!anySubSelected) {
                            item {
                                Text(
                                    text = "Gecikme ayarı için önce bir altyazı seç.",
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            item {
                                val delaySeconds = subtitleDelayMs / 1000f
                                val formattedDelay = String.format(Locale.getDefault(), "+%.1f sn", delaySeconds)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TvDelayActionButton(
                                        label = "-100 ms",
                                        onClick = { viewModel.setSubtitleDelayMs(subtitleDelayMs - 100L) },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(VegaColors.Accent.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = formattedDelay,
                                            color = VegaColors.Accent,
                                            fontSize = VegaText.Secondary,
                                            fontWeight = FontWeight.Bold,
                                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                        )
                                    }

                                    TvDelayActionButton(
                                        label = "+100 ms",
                                        onClick = { viewModel.setSubtitleDelayMs(subtitleDelayMs + 100L) },
                                        modifier = Modifier.weight(1f)
                                    )

                                    TvDelayActionButton(
                                        label = "Sıfırla",
                                        isNeutral = true,
                                        onClick = { viewModel.setSubtitleDelayMs(0L) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // İç kenar 0.5dp jilet çizgi
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(VegaColors.Border)
                )
            }
        }
    }
}

@Composable
private fun TvTrackSelectionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isFocused -> VegaColors.Accent
        selected -> VegaColors.Accent.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> VegaColors.OnAccent
        selected -> VegaColors.TextPrimary
        else -> VegaColors.TextPrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 48.dp)
            .background(containerColor)
            .dpadFocusable(
                shape = RectangleShape,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Seçili parça 3dp sol vurgu çubuğu
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    when {
                        selected && isFocused -> VegaColors.OnAccent
                        selected -> VegaColors.Accent
                        else -> Color.Transparent
                    }
                )
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 17.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) VegaColors.OnAccent else if (selected) VegaColors.Accent else VegaColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = VegaText.Body,
                fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Seçili",
                    tint = if (isFocused) VegaColors.OnAccent else VegaColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TvDelayActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNeutral: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f))
            .dpadFocusable(
                shape = RoundedCornerShape(6.dp),
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                isFocused -> VegaColors.OnAccent
                isNeutral -> VegaColors.TextSecondary
                else -> VegaColors.TextPrimary
            },
            fontSize = VegaText.Caption,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TvCircularActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = VegaColors.Accent
) {
    var isFocused by remember { mutableStateOf(false) }

    val circleBg = when {
        isFocused -> VegaColors.Accent
        isActive -> activeColor.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val iconTint = when {
        isFocused -> VegaColors.OnAccent
        isActive -> activeColor
        else -> VegaColors.TextPrimary
    }
    val labelColor = when {
        isFocused -> VegaColors.TextPrimary
        isActive -> activeColor
        else -> VegaColors.TextSecondary
    }

    Column(
        modifier = modifier
            .widthIn(min = 56.dp)
            .dpadFocusable(
                shape = VegaShapes.Small,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(circleBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            color = labelColor,
            fontSize = VegaText.Caption,
            fontWeight = if (isFocused || isActive) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
