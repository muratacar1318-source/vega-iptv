package com.tivimax.player.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.R
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.DelayedSubtitleController
import com.tivimax.player.player.TiviPlayerManager
import kotlinx.coroutines.delay

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
    val playerInstanceVersion by playerManager.playerInstanceVersion.collectAsState()
    val useTextureView by playerManager.useTextureView.collectAsState()

    val delayedController = remember {
        DelayedSubtitleController(context, coroutineScope)
    }

    DisposableEffect(delayedController) {
        onDispose {
            delayedController.detach()
        }
    }

    var localPlayerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // İlk karede, geçiş animasyonu sonrasında ve ExoPlayer yeniden kurulduğunda yüzeyi ve altyazıyı yeniden bağla
    LaunchedEffect(localPlayerViewRef, playerInstanceVersion, isLive) {
        val view = localPlayerViewRef ?: return@LaunchedEffect
        val currentPlayer = playerManager.player ?: playerManager.initializePlayer(isLive)
        playerManager.attachView(view, isPrimary = true, tag = "VideoPlayerSurface-bind")
        try {
            delayedController.attach(view, currentPlayer)
            delayedController.updateDelay(subtitleDelayMs)
            delayedController.updateStyle(subtitleColor, subtitleSize)
        } catch (_: Throwable) {}
        delay(350L)
        if (localPlayerViewRef === view) {
            playerManager.attachView(view, isPrimary = true, tag = "VideoPlayerSurface-delay350")
        }
    }

    LaunchedEffect(localPlayerViewRef, subtitleDelayMs) {
        if (localPlayerViewRef != null) {
            try {
                delayedController.updateDelay(subtitleDelayMs)
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(localPlayerViewRef, subtitleColor, subtitleSize) {
        if (localPlayerViewRef != null) {
            try {
                delayedController.updateStyle(subtitleColor, subtitleSize)
            } catch (_: Throwable) {}
        }
    }

    val currentOnCreated by rememberUpdatedState(onPlayerViewCreated)
    val currentOnReleased by rememberUpdatedState(onPlayerViewReleased)

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
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    applyVideoSurfaceAspectRatio(this, aspectRatio, isInPipMode)
                    val initialPlayer = playerManager.player ?: playerManager.initializePlayer(isLive)
                    playerManager.attachView(this, isPrimary = true, tag = "VideoPlayerSurface-factory")
                    try {
                        delayedController.attach(this, initialPlayer)
                        delayedController.updateDelay(subtitleDelayMs)
                        delayedController.updateStyle(subtitleColor, subtitleSize)
                    } catch (_: Throwable) {}
                }
                localPlayerViewRef = view
                currentOnCreated?.invoke(view)
                view
            },
            update = { playerView ->
                // Yalnızca en-boy oranı / PiP değişiminde güncelle; ilgisiz recomposition'larda
                // attachView / onPlayerViewCreated / updateStyle çağrılmaz.
                applyVideoSurfaceAspectRatio(playerView, aspectRatio, isInPipMode)
            },
            onRelease = { playerView ->
                if (localPlayerViewRef === playerView) {
                    localPlayerViewRef = null
                }
                currentOnReleased?.invoke()
                delayedController.detach()
                playerManager.detachView(playerView, tag = "VideoPlayerSurface-onRelease")
            },
            modifier = modifier
        )
    }
}

@OptIn(UnstableApi::class)
private fun applyVideoSurfaceAspectRatio(
    playerView: PlayerView,
    aspectRatio: AspectRatioMode,
    isInPipMode: Boolean
) {
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
}
