package com.tivimax.player.ui.player

import com.tivimax.player.MainActivity
import android.app.RemoteAction
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
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

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayerSurface(
    playerManager: TiviPlayerManager,
    isLive: Boolean,
    aspectRatio: AspectRatioMode,
    subtitleColor: String,
    subtitleSize: String,
    subtitleDelayMs: Long = 0L,
    isInPipMode: Boolean = false,
    onPlayerViewCreated: ((PlayerView) -> Unit)? = null,
    onPlayerViewReleased: (() -> Unit)? = null,
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

    var localPlayerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(localPlayerViewRef) {
        val view = localPlayerViewRef ?: return@LaunchedEffect
        // İlk karede ve Crossfade bittikten sonra yüzeyi garantiye almak için yeniden talep
        delay(350L)
        playerManager.attachView(view, isPrimary = true, tag = "VideoPlayerSurface-delay350")
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                localPlayerViewRef = this
                onPlayerViewCreated?.invoke(this)
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
                val (initialResizeMode, initialScaleX, initialScaleY) = if (isInPipMode) {
                    Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                } else {
                    when (aspectRatio) {
                        AspectRatioMode.FIT -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                        AspectRatioMode.FILL -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                        AspectRatioMode.ZOOM -> Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.18f, 1.18f)
                        AspectRatioMode.RATIO_16_9 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                        AspectRatioMode.RATIO_4_3 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0.75f, 1.0f)
                    }
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
            localPlayerViewRef = playerView
            onPlayerViewCreated?.invoke(playerView)
            val currentPlayer = playerManager.player ?: playerManager.initializePlayer(isLive)
            playerManager.attachView(playerView, isPrimary = true, tag = "VideoPlayerSurface-update")
            val (targetResizeMode, targetScaleX, targetScaleY) = if (isInPipMode) {
                Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
            } else {
                when (aspectRatio) {
                    AspectRatioMode.FIT -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                    AspectRatioMode.FILL -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.ZOOM -> Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.18f, 1.18f)
                    AspectRatioMode.RATIO_16_9 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.RATIO_4_3 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0.75f, 1.0f)
                }
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
            if (localPlayerViewRef == playerView) {
                localPlayerViewRef = null
            }
            onPlayerViewReleased?.invoke()
            delayedController.detach()
            playerManager.detachView(playerView, tag = "VideoPlayerSurface-onRelease")
        },
        modifier = modifier
    )
}
