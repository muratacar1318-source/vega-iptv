package com.tivimax.player.ui.player.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/**
 * Canlı TV oynatıcı alt aksiyon çubuğu (TiviMate çerçevesiz yuvarlak ikon düğmeleri, anlık tepki).
 *
 * Sıralama: Rehber, Önceki kanal, Son kanallar, Ses ve altyazı, Daha fazla.
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
        horizontalArrangement = if (isPortrait) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. REHBER (EPG)
        LiveActionButtonItem(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            tooltip = "Rehber",
            contentDescription = "Yayın rehberini aç",
            isActive = isGuideActive,
            onClick = onOpenGuide,
            focusRequester = firstButtonFocusRequester
        )

        // 2. ÖNCEKİ KANAL
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
            defaultColor = if (hasPreviousChannel) VegaColors.Gold else VegaColors.TextMuted.copy(alpha = 0.40f),
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
            onClick = onToggleRecentChannels
        )

        // 4. SES VE ALTYAZI
        LiveActionButtonItem(
            icon = Icons.Default.Subtitles,
            tooltip = "Ses ve altyazı",
            contentDescription = "Ses ve altyazı parçalarını seç",
            onClick = onOpenAudioSubtitles
        )

        // 5. DAHA FAZLA
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
    defaultColor: Color = Color.White,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val registerInteraction = LocalPlayerInteraction.current

    val containerColor = when {
        !enabled -> Color.Transparent
        isFocused -> VegaColors.Accent
        isActive -> VegaColors.Accent.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val tintColor = when {
        !enabled -> defaultColor.copy(alpha = 0.35f)
        isFocused -> VegaColors.OnAccent
        isActive -> VegaColors.Accent
        else -> defaultColor.copy(alpha = 0.92f)
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = tooltip, fontSize = VegaText.Caption)
            }
        },
        state = rememberTooltipState()
    ) {
        // Tek odak + tıklama hedefi: çerçevesiz 40dp TiviMate yuvarlak ikon düğmesi
        Surface(
            onClick = {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    registerInteraction()
                    onClick()
                }
            },
            enabled = enabled,
            shape = VegaShapes.Pill,
            color = containerColor,
            modifier = modifier
                .size(40.dp)
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
