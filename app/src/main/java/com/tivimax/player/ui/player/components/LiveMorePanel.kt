package com.tivimax.player.ui.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/**
 * Canlı TV Oynatıcı "⋯ Daha fazla" Yan Paneli (G-023).
 *
 * Kompakt 52dp satırlar:
 * 1. Kanal listesi (onOpenChannelDrawer)
 * 2. Kanal numarası gir (onOpenNumpad)
 * 3. Çift ekran (onOpenDualView, aktifse sağda "Açık" rozeti)
 * 4. Uyku zamanlayıcısı (onOpenSleepTimer, açıksa sağda kalan süre / "30 dk")
 * 5. Yayın bilgisi (DiagnosticsFooter)
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
        firstFocusRequester = firstFocus
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Kanal listesi
            MoreActionRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = "Kanal listesi",
                onClick = onOpenChannelDrawer,
                modifier = Modifier.focusRequester(firstFocus)
            )

            // 2. Kanal numarası gir
            MoreActionRow(
                icon = Icons.Default.Dialpad,
                title = "Kanal numarası gir",
                onClick = onOpenNumpad
            )

            // 3. Çift ekran
            MoreActionRow(
                icon = Icons.Default.GridView,
                title = "Çift ekran",
                badge = if (isDualViewActive) "Açık" else null,
                isHighlighted = isDualViewActive,
                onClick = onOpenDualView
            )

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
private fun MoreActionRow(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = VegaShapes.Small,
        color = if (isHighlighted) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isHighlighted) VegaColors.Accent else VegaColors.Border
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .dpadFocusable(
                shape = VegaShapes.Small,
                focusedBorderColor = VegaColors.Accent,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighlighted) VegaColors.Accent else VegaColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.BodyProminent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.Accent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, VegaColors.Accent)
                ) {
                    Text(
                        text = badge,
                        color = VegaColors.Accent,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VegaColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
