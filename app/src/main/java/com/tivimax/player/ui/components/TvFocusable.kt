package com.tivimax.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import android.view.SoundEffectConstants
import android.view.ViewConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.util.LocalIsTvDevice

private class PendingClickHolder(
    var pending: Boolean = false,
    var longPressConsumed: Boolean = false
)

/**
 * Güvenli odak istemi yardımcısı.
 * Öğe henüz yerleşmediyse çökme yerine sessizce false döner.
 */
fun FocusRequester.requestFocusSafely(): Boolean =
    runCatching { requestFocus(); true }.getOrDefault(false)

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dpadFocusable(
    focusedBorderColor: Color = VegaColors.FocusBorder,
    shape: Shape = RoundedCornerShape(10.dp),
    scale: Float = 1.035f,
    onClick: (() -> Unit)? = null,
    onFocusStateChange: ((Boolean) -> Unit)? = null,
    drawFocusBorder: Boolean = true,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val isTv = LocalIsTvDevice.current

    // Mobil cihazlarda sıfır GPU katmanı, sıfır Animatable, sıfır tuş dinleyici maliyeti
    if (!isTv) {
        return@composed when {
            onLongClick != null -> this.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick
            )
            onClick != null -> this.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            else -> this
        }
    }

    val view = LocalView.current
    var isFocused by remember { mutableStateOf(false) }
    val clickHolder = remember { PendingClickHolder() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnFocusStateChange by rememberUpdatedState(onFocusStateChange)

    val performClick: () -> Unit = {
        runCatching { view.playSoundEffect(SoundEffectConstants.CLICK) }
        currentOnClick?.invoke()
    }

    val performLongClick: () -> Unit = {
        runCatching { view.playSoundEffect(SoundEffectConstants.CLICK) }
        currentOnLongClick?.invoke()
    }

    val hasClickHandler = onClick != null || onLongClick != null

    val focusTransformModifier = if (isFocused) {
        Modifier
            .zIndex(2f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    } else {
        Modifier
    }

    this
        .then(focusTransformModifier)
        .onFocusChanged { state ->
            val nowFocused = state.isFocused
            if (nowFocused && !isFocused) {
                runCatching { view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT) }
            }
            if (!nowFocused) {
                clickHolder.pending = false
                clickHolder.longPressConsumed = false
            }
            isFocused = nowFocused
            currentOnFocusStateChange?.invoke(nowFocused)
        }
        .then(
            if (hasClickHandler) {
                val clickModifier = if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = performClick,
                        onLongClick = performLongClick
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = performClick
                    )
                }
                Modifier
                    .onPreviewKeyEvent { event ->
                        if (!isFocused) return@onPreviewKeyEvent false
                        val native = event.nativeKeyEvent
                        val code = native.keyCode
                        val isConfirmKey = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                            code == AndroidKeyEvent.KEYCODE_ENTER ||
                            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                            code == AndroidKeyEvent.KEYCODE_BUTTON_A ||
                            code == AndroidKeyEvent.KEYCODE_SPACE
                        if (!isConfirmKey) return@onPreviewKeyEvent false
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (native.repeatCount == 0) {
                                    clickHolder.pending = true
                                    clickHolder.longPressConsumed = false
                                } else if (
                                    currentOnLongClick != null &&
                                    !clickHolder.longPressConsumed &&
                                    (native.isLongPress || (native.eventTime - native.downTime) >= ViewConfiguration.getLongPressTimeout())
                                ) {
                                    clickHolder.longPressConsumed = true
                                    clickHolder.pending = false
                                    performLongClick()
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                if (clickHolder.longPressConsumed) {
                                    clickHolder.longPressConsumed = false
                                    clickHolder.pending = false
                                } else if (clickHolder.pending) {
                                    clickHolder.pending = false
                                    performClick()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .then(clickModifier)
            } else {
                Modifier.focusable()
            }
        )
        .then(
            if (isFocused && drawFocusBorder) Modifier.border(2.dp, focusedBorderColor, shape) else Modifier
        )
}

fun extractQualityBadges(channelName: String): List<String> {
    val upper = channelName.uppercase()
    val badges = mutableListOf<String>()
    if (upper.contains("4K") || upper.contains("UHD")) {
        badges.add("4K")
    } else if (upper.contains("FHD") || upper.contains("1080")) {
        badges.add("FHD")
    } else if (upper.contains("HD") || upper.contains("720")) {
        badges.add("HD")
    }

    if (upper.contains("100FPS") || upper.contains("100 FPS")) {
        badges.add("100FPS")
    } else if (upper.contains("50FPS") || upper.contains("50 FPS") || upper.contains("60FPS") || upper.contains("60 FPS")) {
        badges.add("50FPS")
    }

    if (upper.contains("HEVC") || upper.contains("H.265") || upper.contains("H265")) {
        badges.add("HEVC")
    }
    return badges
}

@Composable
fun QualityBadge(tag: String) {
    val (bg, textColor) = when (tag) {
        "4K" -> VegaColors.Gold to Color.Black
        "FHD" -> Color(0xFF6366F1) to Color.White // G-057: karşılığı yok (rozet ayrımı için indigo korundu)
        "HD" -> VegaColors.Success to Color.White
        "100FPS" -> VegaColors.Accent to Color.Black
        "50FPS" -> VegaColors.Accent.copy(alpha = 0.7f) to Color.Black
        else -> VegaColors.SurfaceVariant to Color.White
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = tag,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
