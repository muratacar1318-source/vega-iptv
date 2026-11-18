package com.tivimax.player.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/**
 * Canlı TV Oynatıcı "Daha fazla" Yan Paneli (İnce ayırıcılı obsidyen liste, tek odaklı satırlar).
 */
@Composable
fun LiveMorePanel(
    visible: Boolean,
    isPortrait: Boolean,
    isDualViewActive: Boolean,
    onOpenDualView: () -> Unit,
    onOpenNumpad: () -> Unit,
    onOpenChannelDrawer: () -> Unit,
    sleepTimerSeconds: Int? = null,
    onOpenSleepTimer: () -> Unit,
    diagnostics: StreamDiagnostics,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocus = remember { FocusRequester() }

    PlayerSidePanel(
        visible = visible,
        title = "Daha fazla",
        onClose = onClose,
        modifier = modifier,
        isPortrait = isPortrait,
        firstFocusRequester = firstFocus,
        headerIcon = Icons.Default.Tune
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            // 1. Kanal listesi
            MoreActionRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = "Kanal listesi",
                onClick = onOpenChannelDrawer,
                modifier = Modifier.focusRequester(firstFocus)
            )
            MoreHairlineDivider()

            // 2. Kanal numarası gir
            MoreActionRow(
                icon = Icons.Default.Dialpad,
                title = "Kanal numarası gir",
                onClick = onOpenNumpad
            )
            MoreHairlineDivider()

            // 3. Çift ekran
            MoreActionRow(
                icon = Icons.Default.GridView,
                title = "Çift ekran",
                badge = if (isDualViewActive) "Açık" else null,
                isHighlighted = isDualViewActive,
                onClick = onOpenDualView
            )
            MoreHairlineDivider()

            // 4. Uyku zamanlayıcısı
            val sleepBadge = if (sleepTimerSeconds != null && sleepTimerSeconds > 0) {
                val mins = (sleepTimerSeconds + 59) / 60
                "$mins dk"
            } else null
            MoreActionRow(
                icon = Icons.Default.Bedtime,
                title = "Uyku zamanlayıcısı",
                badge = sleepBadge,
                isHighlighted = sleepBadge != null,
                onClick = onOpenSleepTimer
            )

            // 5. Yayın bilgisi
            DiagnosticsFooter(diagnostics = diagnostics)
        }
    }
}

@Composable
private fun MoreHairlineDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = VegaColors.Border.copy(alpha = 0.35f)
    )
}

@Composable
private fun MoreActionRow(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isHighlighted -> VegaColors.Accent.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> VegaColors.OnAccent
        isHighlighted -> VegaColors.Accent
        else -> VegaColors.TextSecondary
    }

    // Tek odak + tıklama hedefi: sadece dpadFocusable(onClick) kullanılır.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(VegaShapes.Small)
            .background(containerColor)
            .dpadFocusable(
                shape = VegaShapes.Small,
                focusedBorderColor = Color.White,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            color = if (isFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
            fontSize = VegaText.Body,
            fontWeight = if (isFocused || isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (!badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(VegaShapes.Small)
                    .background(
                        if (isFocused) VegaColors.OnAccent.copy(alpha = 0.16f)
                        else VegaColors.Accent.copy(alpha = 0.16f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    color = if (isFocused) VegaColors.OnAccent else VegaColors.Accent,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) VegaColors.OnAccent.copy(alpha = 0.75f) else VegaColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
