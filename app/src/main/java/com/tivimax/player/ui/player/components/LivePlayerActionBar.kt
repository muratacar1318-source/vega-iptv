package com.tivimax.player.ui.player.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText

/**
 * Canlı TV Tam Ekran Oynatıcısı Alt Aksiyon Çubuğu (Kompakt İkon Düzeni).
 *
 * Özellikler:
 * 1. Etiketsiz, yalnızca 22dp ikon ve en az 44x44dp dokunma alanı.
 * 2. Uzun basıldığında Material3 TooltipBox / PlainTooltip ile buton adı gösterilir.
 * 3. Sıralama: Rehber, Önceki kanal, Son kanallar, Ses & Altyazı, Daha Fazla.
 * 4. Dikey modda eşit aralıklı, yatay modda kompakt yan yana yerleşir.
 */
@Composable
fun LivePlayerActionBar(
    onOpenGuide: () -> Unit,
    isGuideActive: Boolean = false,
    onToggleRecentChannels: () -> Unit,
    isRecentActive: Boolean = false,
    hasPreviousChannel: Boolean = false,
    previousChannelName: String? = null,
    onZapRecall: () -> Unit,
    onOpenAudioSubtitles: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
    firstButtonFocusRequester: FocusRequester? = null
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Row(
        modifier = modifier,
        horizontalArrangement = if (isPortrait) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. REHBER (EPG)
        LiveActionButtonItem(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            tooltip = "Rehber",
            contentDescription = "Yayın rehberini aç",
            isActive = isGuideActive,
            activeColor = VegaColors.Accent,
            onClick = onOpenGuide,
            focusRequester = firstButtonFocusRequester
        )

        // 2. ÖNCEKİ KANAL (ZAPPING RECALL)
        val prevTooltip = if (!previousChannelName.isNullOrBlank()) {
            "Önceki: $previousChannelName"
        } else {
            "Önceki kanal"
        }

        LiveActionButtonItem(
            icon = Icons.Default.Replay,
            tooltip = prevTooltip,
            contentDescription = "Önceki kanala geri dön",
            enabled = hasPreviousChannel,
            isActive = hasPreviousChannel,
            activeColor = VegaColors.Gold,
            defaultColor = if (hasPreviousChannel) VegaColors.Gold else VegaColors.TextMuted.copy(alpha = 0.4f),
            onClick = {
                if (hasPreviousChannel) {
                    onZapRecall()
                }
            }
        )

        // 3. SON KANALLAR
        LiveActionButtonItem(
            icon = Icons.Default.History,
            tooltip = "Son kanallar",
            contentDescription = "Son izlenen kanalları göster",
            isActive = isRecentActive,
            activeColor = VegaColors.Accent,
            onClick = onToggleRecentChannels
        )

        // 4. SES & ALTYAZI
        LiveActionButtonItem(
            icon = Icons.Default.Subtitles,
            tooltip = "Ses ve altyazı",
            contentDescription = "Ses ve altyazı parçalarını seç",
            onClick = onOpenAudioSubtitles
        )

        // 5. DAHA FAZLA (⋯)
        LiveActionButtonItem(
            icon = Icons.Default.MoreHoriz,
            tooltip = "Daha fazla",
            contentDescription = "Diğer oynatıcı seçeneklerini aç",
            onClick = onOpenMore
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveActionButtonItem(
    icon: ImageVector,
    tooltip: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    activeColor: Color = VegaColors.Accent,
    defaultColor: Color = VegaColors.TextPrimary,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val registerInteraction = LocalPlayerInteraction.current

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.88f
            isFocused -> 1.1f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btnScale"
    )

    val tintColor by animateColorAsState(
        targetValue = when {
            !enabled -> defaultColor.copy(alpha = 0.35f)
            isFocused -> VegaColors.Accent
            isActive -> activeColor
            else -> defaultColor
        },
        label = "btnColor"
    )

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = tooltip, fontSize = VegaText.Caption)
            }
        },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    registerInteraction()
                    onClick()
                }
            },
            enabled = enabled,
            shape = VegaShapes.Medium,
            color = if (isFocused) VegaColors.Accent.copy(alpha = 0.22f) else Color.Transparent,
            border = if (isFocused) BorderStroke(2.dp, VegaColors.Accent) else null,
            interactionSource = interactionSource,
            modifier = modifier
                .size(VegaSizes.IconButton)
                .scale(scale)
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (state.isFocused) registerInteraction()
                }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tintColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
