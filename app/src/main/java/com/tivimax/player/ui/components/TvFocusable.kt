package com.tivimax.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/**
 * StreamVault'tan uyarlanan güvenli odak istemi yardımcısı.
 * Öğe henüz yerleşmediyse çökme yerine sessizce false döner.
 */
fun FocusRequester.requestFocusSafely(): Boolean =
    runCatching { requestFocus(); true }.getOrDefault(false)

fun Modifier.dpadFocusable(
    focusedBorderColor: Color = VegaColors.FocusBorder,
    shape: Shape = RoundedCornerShape(10.dp),
    scale: Float = 1.035f,
    onClick: (() -> Unit)? = null,
    onFocusStateChange: ((Boolean) -> Unit)? = null
): Modifier = composed {
    val isTv = LocalIsTvDevice.current
    val view = LocalView.current
    var isFocused by remember { mutableStateOf(false) }
    var pendingKeyClick by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnFocusStateChange by rememberUpdatedState(onFocusStateChange)

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) scale else 1.0f,
        animationSpec = tween(durationMillis = 130),
        label = "dpadFocusScale"
    )

    val performClick: () -> Unit = {
        if (isTv) {
            runCatching { view.playSoundEffect(SoundEffectConstants.CLICK) }
        }
        currentOnClick?.invoke()
    }

    this
        .zIndex(if (isFocused) 2f else 0f)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .onFocusChanged { state ->
            val nowFocused = state.isFocused
            if (nowFocused && !isFocused && isTv) {
                runCatching { view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT) }
            }
            if (!nowFocused) {
                pendingKeyClick = false
            }
            isFocused = nowFocused
            currentOnFocusStateChange?.invoke(nowFocused)
        }
        .then(
            if (onClick != null) {
                Modifier
                    .onPreviewKeyEvent { event ->
                        if (!isFocused) return@onPreviewKeyEvent false
                        val code = event.nativeKeyEvent.keyCode
                        val isConfirmKey = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                            code == AndroidKeyEvent.KEYCODE_ENTER ||
                            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                            code == AndroidKeyEvent.KEYCODE_BUTTON_A
                        if (!isConfirmKey) return@onPreviewKeyEvent false
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.repeatCount == 0) {
                                    pendingKeyClick = true
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                if (pendingKeyClick) {
                                    pendingKeyClick = false
                                    performClick()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = performClick
                    )
            } else {
                Modifier.focusable()
            }
        )
        .then(
            if (isFocused) Modifier.border(2.dp, focusedBorderColor, shape) else Modifier
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
