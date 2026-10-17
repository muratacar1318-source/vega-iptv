package com.tivimax.player.player

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pozisyona dayalı (position-based) gecikmeli altyazı denetleyicisi.
 *
 * subtitleDelayMs == 0 iken: PlayerView'in kendi SubtitleView'i aktif kalır.
 * subtitleDelayMs > 0 iken: PlayerView'in dahili SubtitleView'i gizlenir,
 * üzerine aynı stil ve boyutta özel bir SubtitleView yerleştirilir ve
 * cue grupları player.currentPosition >= (cue pozisyonu + gecikme) kuralına
 * göre ~40ms aralıklarla oynatıcı pozisyonuna göre gecikmeli gösterilir.
 */
@OptIn(UnstableApi::class)
class DelayedSubtitleController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private data class CueEntry(
        val positionMs: Long,
        val cueGroup: CueGroup
    )

    private var targetPlayer: Player? = null
    private var targetPlayerView: PlayerView? = null
    private var customSubtitleView: SubtitleView? = null

    private var subtitleDelayMs: Long = 0L
    private var subtitleColor: String = "BEYAZ"
    private var subtitleSize: String = "ORTA"

    private val cueQueue = ArrayList<CueEntry>()
    private var lastRenderedCueGroup: CueGroup? = null
    private var tickerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            if (subtitleDelayMs <= 0L) return
            val player = targetPlayer ?: return
            val currentPos = player.currentPosition
            synchronized(cueQueue) {
                cueQueue.add(CueEntry(currentPos, cueGroup))
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            clearQueueAndCues()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            clearQueueAndCues()
        }

        override fun onTracksChanged(tracks: Tracks) {
            var hasSelectedSub = false
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT && group.isSelected) {
                    hasSelectedSub = true
                    break
                }
            }
            if (!hasSelectedSub) {
                clearQueueAndCues()
            }
        }
    }

    fun attach(playerView: PlayerView, player: Player) {
        if (targetPlayer == player && targetPlayerView == playerView) {
            applyStyling()
            return
        }

        detach()

        targetPlayer = player
        targetPlayerView = playerView

        var subView = playerView.findViewWithTag<SubtitleView>(TAG_CUSTOM_SUBTITLE)
        if (subView == null) {
            subView = SubtitleView(context).apply {
                tag = TAG_CUSTOM_SUBTITLE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                visibility = if (subtitleDelayMs > 0L) View.VISIBLE else View.GONE
            }
            playerView.addView(subView)
        }
        customSubtitleView = subView

        applyStyling()
        updateVisibilityState()

        player.addListener(playerListener)
        startTicker()
    }

    fun updateDelay(delayMs: Long) {
        val clamped = delayMs.coerceIn(0L, 5000L)
        if (subtitleDelayMs == clamped) return
        val wasZero = subtitleDelayMs == 0L
        subtitleDelayMs = clamped

        if (clamped == 0L) {
            clearQueueAndCues()
            updateVisibilityState()
        } else {
            updateVisibilityState()
            if (wasZero) {
                // Gecikme açıldığında mevcut pozisyonu sıfırdan takip et
                clearQueueAndCues()
            }
        }
    }

    fun updateStyle(color: String, size: String) {
        subtitleColor = color
        subtitleSize = size
        applyStyling()
    }

    fun clearQueueAndCues() {
        synchronized(cueQueue) {
            cueQueue.clear()
        }
        lastRenderedCueGroup = null
        customSubtitleView?.setCues(emptyList())
    }

    fun detach() {
        tickerJob?.cancel()
        tickerJob = null

        targetPlayer?.removeListener(playerListener)
        targetPlayer = null

        clearQueueAndCues()

        targetPlayerView?.subtitleView?.visibility = View.VISIBLE
        customSubtitleView?.let {
            it.visibility = View.GONE
            (it.parent as? ViewGroup)?.removeView(it)
        }
        customSubtitleView = null
        targetPlayerView = null
    }

    private fun updateVisibilityState() {
        val delayActive = subtitleDelayMs > 0L
        targetPlayerView?.subtitleView?.visibility = if (delayActive) View.GONE else View.VISIBLE
        customSubtitleView?.visibility = if (delayActive) View.VISIBLE else View.GONE
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                delay(40L)
                updateDelayedCues()
            }
        }
    }

    private fun updateDelayedCues() {
        val player = targetPlayer ?: return
        val subView = customSubtitleView ?: return
        val delayMs = subtitleDelayMs

        if (delayMs <= 0L) {
            if (subView.visibility != View.GONE) {
                subView.visibility = View.GONE
                subView.setCues(emptyList())
            }
            if (targetPlayerView?.subtitleView?.visibility != View.VISIBLE) {
                targetPlayerView?.subtitleView?.visibility = View.VISIBLE
            }
            return
        }

        if (subView.visibility != View.VISIBLE) {
            subView.visibility = View.VISIBLE
        }
        if (targetPlayerView?.subtitleView?.visibility != View.GONE) {
            targetPlayerView?.subtitleView?.visibility = View.GONE
        }

        val currentPos = player.currentPosition

        synchronized(cueQueue) {
            if (cueQueue.isEmpty()) return

            // Geriye doğru atlama veya seek tespit edildiğinde kuyruğu sıfırla
            if (currentPos < cueQueue.first().positionMs - 500L) {
                cueQueue.clear()
                lastRenderedCueGroup = null
                subView.setCues(emptyList())
                return
            }

            var latestEligibleIndex = -1
            for (i in 0 until cueQueue.size) {
                val entry = cueQueue[i]
                if (currentPos >= entry.positionMs + delayMs) {
                    latestEligibleIndex = i
                } else {
                    break
                }
            }

            if (latestEligibleIndex >= 0) {
                val eligibleEntry = cueQueue[latestEligibleIndex]
                if (lastRenderedCueGroup !== eligibleEntry.cueGroup) {
                    subView.setCues(eligibleEntry.cueGroup.cues)
                    lastRenderedCueGroup = eligibleEntry.cueGroup
                }

                if (latestEligibleIndex > 0) {
                    cueQueue.subList(0, latestEligibleIndex).clear()
                }
            }
        }
    }

    private fun applyStyling() {
        val subTextColor = when (subtitleColor) {
            "SARI" -> android.graphics.Color.rgb(255, 215, 0)
            "YESIL" -> android.graphics.Color.rgb(0, 230, 118)
            "MAVI" -> android.graphics.Color.rgb(64, 196, 255)
            else -> android.graphics.Color.WHITE
        }
        val subTextSp = when (subtitleSize) {
            "KUCUK" -> 14f
            "ORTA" -> 18f
            "BUYUK" -> 22f
            "COK_BUYUK" -> 26f
            else -> subtitleSize.toFloatOrNull() ?: 18f
        }
        val style = CaptionStyleCompat(
            subTextColor,
            android.graphics.Color.argb(160, 0, 0, 0),
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            null
        )

        targetPlayerView?.subtitleView?.let {
            it.setStyle(style)
            it.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subTextSp)
            it.setBottomPaddingFraction(0.12f)
        }

        customSubtitleView?.let {
            it.setStyle(style)
            it.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subTextSp)
            it.setBottomPaddingFraction(0.12f)
        }
    }

    companion object {
        private const val TAG_CUSTOM_SUBTITLE = "delayed_subtitle_view"
    }
}
