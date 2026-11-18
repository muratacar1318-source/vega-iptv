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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.DelayedSubtitleController
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
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
                CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(54.dp))
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
                    border = BorderStroke(1.dp, VegaColors.Border)
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
                        border = BorderStroke(1.dp, VegaColors.Border)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = msg,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
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

        // 2A. SOL ÜST KÖŞE: STREAMVAULT GERİ DÜĞMESİ VE CANLI GÖSTERGESİ (ChannelView.png)
        AnimatedVisibility(
            visible = showInfoCard || showActionTray,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 20.dp, start = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = VegaShapes.Pill,
                    color = VegaColors.OverlayPill,
                    border = BorderStroke(1.dp, VegaColors.Border),
                    modifier = Modifier
                        .size(38.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Pill,
                            focusedBorderColor = VegaColors.FocusBorder,
                            onClick = onCloseFullScreen
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(VegaShapes.Pill)
                            .background(VegaColors.Live)
                    )
                    Text(
                        text = "Canlı",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // KANAL DEĞİŞTİRME BİLDİRİMİ (Üst Orta Hap)
        AnimatedVisibility(
            visible = seekRippleMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        ) {
            seekRippleMessage?.let { rippleMsg ->
                Surface(
                    shape = VegaShapes.Pill,
                    color = VegaColors.OverlayPanel,
                    border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.7f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = rippleMsg,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2B. SAĞ ÜST KÖŞE: SAAT & RAKAM TUŞLAMA OVERLAY
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 24.dp),
            horizontalAlignment = Alignment.End
        ) {
            // SAAT (G-038: sadece bilgi kartı/menü açıkken)
            if (showInfoCard || showActionTray) {
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.OverlayPanel,
                    border = BorderStroke(1.dp, VegaColors.Border)
                ) {
                    Text(
                        text = clockText,
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // RAKAM İLE KANAL GEÇİŞİ (NUMERIC ZAPPER)
            if (isZapperActive && zapperNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.Accent,
                    border = BorderStroke(1.5.dp, Color.White)
                ) {
                    Text(
                        text = "[ $zapperNumber ] ${zapperMatchedChannel?.name ?: ""}".trim(),
                        color = VegaColors.OnAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 3. STREAMVAULT CAM ALT BİLGİ KARTI VE AKSİYON KAROLARI (ChannelView.png)
        AnimatedVisibility(
            visible = showInfoCard,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
        ) {
            val epg = epgSummaryMap[activeChannel.streamId]
            val currentTitle = epg?.getDecodedTitle() ?: activeChannel.currentProgram ?: "Canlı yayın"
            val timeString = epg?.getFormattedTime() ?: if (epg?.start != null && epg.end != null) "${epg.start} - ${epg.end}" else ""
            val progress = epg?.getProgressFraction()
            val nextTitle = activeChannel.nextProgram ?: epg?.getDecodedDescription() ?: ""
            val qualityBadges = remember(activeChannel.name) { extractQualityBadges(activeChannel.name) }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // SOL: Kare Kanal Logosu
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(VegaShapes.Medium)
                                .background(VegaColors.SurfaceAccent)
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

                        // ORTA: [Kanal 8] + Kanal Adı + [1080p · 50fps] + Program + Parlayan İlerleme Çubuğu
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (activeChannel.num > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = VegaColors.SurfaceAccent.copy(alpha = 0.9f),
                                        border = BorderStroke(1.dp, VegaColors.Border)
                                    ) {
                                        Text(
                                            text = "Kanal ${activeChannel.num}",
                                            color = VegaColors.TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = activeChannel.name,
                                    color = VegaColors.TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                // Çözünürlük · FPS Rozeti (ChannelView.png)
                                val techSummary = listOf(
                                    streamDiagnostics.resolutionBadge.ifBlank { qualityBadges.firstOrNull().orEmpty() },
                                    streamDiagnostics.frameRateBadge
                                ).filter { it.isNotBlank() }.joinToString(" · ")

                                if (techSummary.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = VegaColors.SurfaceAccent.copy(alpha = 0.85f),
                                        border = BorderStroke(1.dp, VegaColors.Border)
                                    ) {
                                        Text(
                                            text = techSummary,
                                            color = VegaColors.TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = currentTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val remainingMinutes = remember(epg) {
                                val stop = epg?.stopTimestamp ?: 0L
                                val nowSec = System.currentTimeMillis() / 1000L
                                if (stop > nowSec) ((stop - nowSec + 59L) / 60L).toInt() else null
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (timeString.isNotBlank()) {
                                    Text(
                                        text = timeString,
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (progress != null) {
                                    StreamVaultProgressBar(
                                        progress = progress,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (remainingMinutes != null) {
                                    Text(
                                        text = "• $remainingMinutes dk kaldı",
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (nextTitle.isNotBlank()) {
                                Text(
                                    text = "Sonraki: $nextTitle",
                                    color = VegaColors.TextMuted,
                                    fontSize = 11.sp,
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
                            text = "▲▼ Kanal değiştir   ◀ Kanal listesi   ▶ Kontrol çubuğu",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 1
                        )
                    }

                    // 4. STREAMVAULT İKİ SATIRLI AKSİYON KAROLARI (ChannelView.png — ▶ TUŞU İLE AÇILIR)
                    AnimatedVisibility(
                        visible = showActionTray,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Son İzlenenler Şeridi
                            if (recentChannels.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(recentChannels.take(10), key = { idx, it -> "rec_${it.streamId}_$idx" }) { _, rec ->
                                        Surface(
                                            shape = VegaShapes.Small,
                                            color = VegaColors.SurfaceElevated,
                                            border = BorderStroke(1.dp, VegaColors.Border),
                                            modifier = Modifier.dpadFocusable(
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                shape = VegaShapes.Small,
                                                onClick = { viewModel.playLiveChannel(rec) }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncShimmerImage(
                                                    model = rec.streamIcon,
                                                    contentDescription = rec.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(18.dp).clip(VegaShapes.Small)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(rec.name, color = VegaColors.TextPrimary, fontSize = VegaText.Caption, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }

                            // StreamVault Alt Kontrol Karoları (ChannelView.png)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StreamVaultPlayerTile(
                                    eyebrow = "Oynatma",
                                    value = if (isPlaying) "Duraklat" else "Oynat",
                                    onClick = { playerManager.togglePlayPause() },
                                    modifier = Modifier.focusRequester(actionDockFocusRequester)
                                )
                                StreamVaultPlayerTile(
                                    eyebrow = "Ses ve altyazı",
                                    value = "Parçalar",
                                    onClick = { showTrackSheet = true }
                                )
                                StreamVaultPlayerTile(
                                    eyebrow = "Görüntü",
                                    value = when (aspectRatio) {
                                        AspectRatioMode.FIT -> "Orijinal"
                                        AspectRatioMode.FILL -> "Doldur"
                                        AspectRatioMode.ZOOM -> "Yakınlaştır"
                                        AspectRatioMode.RATIO_16_9 -> "16:9"
                                        else -> "Orijinal"
                                    },
                                    onClick = { playerManager.cycleAspectRatio() }
                                )
                                StreamVaultPlayerTile(
                                    eyebrow = "Favori",
                                    value = if (isFavorite) "Kayıtlı" else "Ekle",
                                    isActive = isFavorite,
                                    onClick = { viewModel.toggleFavoriteLive(activeChannel.streamId.toString()) }
                                )
                                if (onOpenEpgGrid != null) {
                                    StreamVaultPlayerTile(
                                        eyebrow = "Rehber",
                                        value = "Program",
                                        onClick = onOpenEpgGrid
                                    )
                                }
                                StreamVaultPlayerTile(
                                    eyebrow = "Kanallar",
                                    value = "Liste",
                                    onClick = {
                                        guideNavigated = false
                                        showOverlayGuide = true
                                        showInfoCard = false
                                        showActionTray = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. YAYIN ÜSTÜ YARI SAYDAM KANAL LİSTESİ (OVERLAY TV GUIDE - SOL / BACK TUŞU)
        AnimatedVisibility(
            visible = showOverlayGuide,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(420.dp),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Kanal listesi",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.SectionTitle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        state = guideListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(allStreams, key = { index, it -> "guide_${it.streamId}_$index" }) { index, st ->
                            val isSel = st.streamId == activeChannel.streamId
                            var isRowFocused by remember { mutableStateOf(false) }
                            val rowEpg = epgSummaryMap[st.streamId]
                            val rowProgram = rowEpg?.getDecodedTitle() ?: st.currentProgram ?: ""

                            Surface(
                                shape = VegaShapes.Medium,
                                color = when {
                                    isRowFocused -> VegaColors.Accent
                                    isSel -> VegaColors.Accent.copy(alpha = 0.18f)
                                    else -> VegaColors.SurfaceVariant.copy(alpha = 0.7f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isRowFocused -> Color.White
                                        isSel -> VegaColors.Accent
                                        else -> VegaColors.Border.copy(alpha = 0.6f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == guideFocusIndex) Modifier.focusRequester(guideFocusRequester) else Modifier)
                                    .dpadFocusable(
                                        focusedBorderColor = Color.White,
                                        shape = VegaShapes.Medium,
                                        scale = 1.02f,
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
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = if (st.num > 0) "${st.num}" else "•",
                                        color = if (isRowFocused) VegaColors.OnAccent else VegaColors.Accent,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(VegaShapes.Small)
                                            .background(VegaColors.SurfaceAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncShimmerImage(
                                            model = st.streamIcon,
                                            contentDescription = st.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(3.dp),
                                            placeholderIcon = Icons.Default.Tv
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = st.name,
                                            color = if (isRowFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Body,
                                            fontWeight = if (isRowFocused || isSel) FontWeight.Bold else FontWeight.SemiBold,
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
                                    }
                                    if (isSel) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seçili",
                                            tint = if (isRowFocused) VegaColors.OnAccent else VegaColors.Accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. YAYIN ÜSTÜ SES VE ALTYAZI AYARLARI PANELİ (STREAMVAULT CAM PANEL + TEK ODAK)
        AnimatedVisibility(
            visible = showTrackSheet,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Ses ve altyazı",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        var closeFocused by remember { mutableStateOf(false) }
                        Surface(
                            shape = VegaShapes.Pill,
                            color = if (closeFocused) VegaColors.Accent else VegaColors.OverlayPill,
                            border = BorderStroke(1.dp, if (closeFocused) Color.White else VegaColors.Border),
                            modifier = Modifier
                                .size(40.dp)
                                .dpadFocusable(
                                    shape = VegaShapes.Pill,
                                    focusedBorderColor = Color.White,
                                    scale = 1.05f,
                                    onClick = { showTrackSheet = false },
                                    onFocusStateChange = { closeFocused = it }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = if (closeFocused) VegaColors.OnAccent else VegaColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. SES DİLİ
                        item {
                            Text(
                                text = "Ses dili",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (audioTracks.isEmpty()) {
                            item {
                                Text(
                                    text = "Ek ses parçası bulunamadı",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption
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
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = VegaColors.Border)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // 2. ALTYAZI
                        item {
                            Text(
                                text = "Altyazı",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
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
                            TvTrackSelectionRow(
                                title = track.name,
                                icon = Icons.Default.ClosedCaption,
                                selected = track.isSelected,
                                onClick = { playerManager.selectTrack(track, C.TRACK_TYPE_TEXT) }
                            )
                        }

                        // 3. ALTYAZI GECİKMESİ
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = VegaColors.Border)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        item {
                            Text(
                                text = "Altyazı gecikmesi",
                                color = if (anySubSelected) VegaColors.Accent else VegaColors.TextMuted,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!anySubSelected) {
                            item {
                                Text(
                                    text = "Gecikme ayarı için önce bir altyazı seç.",
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption
                                )
                            }
                        } else {
                            item {
                                val delaySeconds = subtitleDelayMs / 1000f
                                val formattedDelay = String.format(Locale.getDefault(), "+%.1f sn", delaySeconds)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TvDelayActionButton(
                                        label = "-100 ms",
                                        onClick = { viewModel.setSubtitleDelayMs(subtitleDelayMs - 100L) },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.Accent.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, VegaColors.Accent),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = formattedDelay,
                                                color = VegaColors.TextPrimary,
                                                fontSize = VegaText.Secondary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
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
        selected -> VegaColors.Accent.copy(alpha = 0.18f)
        else -> VegaColors.SurfaceVariant
    }
    val contentColor = when {
        isFocused -> VegaColors.OnAccent
        selected -> VegaColors.Accent
        else -> VegaColors.TextPrimary
    }

    Surface(
        shape = VegaShapes.Medium,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            when {
                isFocused -> Color.White
                selected -> VegaColors.Accent
                else -> VegaColors.Border
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .dpadFocusable(
                focusedBorderColor = Color.White,
                shape = VegaShapes.Medium,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
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

    Surface(
        shape = VegaShapes.Small,
        color = if (isFocused) VegaColors.Accent else VegaColors.SurfaceVariant,
        border = BorderStroke(1.dp, if (isFocused) Color.White else VegaColors.Border),
        modifier = modifier
            .height(40.dp)
            .dpadFocusable(
                focusedBorderColor = Color.White,
                shape = VegaShapes.Small,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = when {
                    isFocused -> VegaColors.OnAccent
                    isNeutral -> VegaColors.TextPrimary
                    else -> VegaColors.Accent
                },
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StreamVaultPlayerTile(
    eyebrow: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val tileShape = RoundedCornerShape(11.dp)

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isActive -> VegaColors.Accent.copy(alpha = 0.22f)
        else -> VegaColors.SurfaceElevated.copy(alpha = 0.92f)
    }
    val eyebrowColor = when {
        isFocused -> VegaColors.OnAccent.copy(alpha = 0.8f)
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
            .widthIn(min = 96.dp)
            .height(50.dp)
            .dpadFocusable(
                focusedBorderColor = Color.White,
                shape = tileShape,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
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
