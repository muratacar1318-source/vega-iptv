package com.tivimax.player.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaText

/**
 * Canlı TV oynatıcı "Daha fazla" TiviMate yan çekmece menüsü.
 * Düz 48dp satırlar, 0.5dp ayırıcılar, odaklanınca anlık `SurfaceVariant` vurgusu ve sol 3dp vurgu şeridi.
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
                valueText = if (isDualViewActive) "Açık" else null,
                isActive = isDualViewActive,
                onClick = onOpenDualView
            )
            MoreHairlineDivider()

            // 4. Uyku zamanlayıcısı
            val sleepValue = if (sleepTimerSeconds != null && sleepTimerSeconds > 0) {
                val mins = (sleepTimerSeconds + 59) / 60
                "$mins dk"
            } else null
            MoreActionRow(
                icon = Icons.Default.Bedtime,
                title = "Uyku zamanlayıcısı",
                valueText = sleepValue,
                isActive = sleepValue != null,
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
    valueText: String? = null,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val rowBackground = if (isFocused) VegaColors.SurfaceVariant else Color.Transparent
    val showIndicator = isFocused || isActive
    val iconTint = when {
        isFocused || isActive -> VegaColors.Accent
        else -> VegaColors.TextSecondary
    }

    // Tek odak + tıklama hedefi: düz TiviMate menü satırı (kart/hap/çerçeve yok)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(rowBackground)
            .dpadFocusable(
                shape = RectangleShape,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sol 3dp aktif/odak göstergesi
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (showIndicator) VegaColors.Accent else Color.Transparent)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            color = if (isFocused || isActive) VegaColors.TextPrimary else VegaColors.TextPrimary.copy(alpha = 0.92f),
            fontSize = VegaText.Body,
            fontWeight = if (isFocused || isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (!valueText.isNullOrBlank()) {
            Text(
                text = valueText,
                color = VegaColors.Accent,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(end = 6.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) VegaColors.TextPrimary else VegaColors.TextMuted,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(18.dp)
            )
        }
    }
}
