package com.tivimax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tivimax.player.ui.BackgroundSyncStatus
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/** G-029: Arka plan liste güncellemesi şeridi. HIDDEN iken hiçbir şey çizmez. */
@Composable
fun BackgroundSyncBanner(
    status: BackgroundSyncStatus,
    percentage: Int,
    isTv: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (status == BackgroundSyncStatus.HIDDEN) return

    val text = when (status) {
        BackgroundSyncStatus.RUNNING -> "Liste güncelleniyor… %$percentage"
        BackgroundSyncStatus.DONE -> "Liste güncellendi"
        BackgroundSyncStatus.FAILED ->
            if (isTv) "Liste güncellenemedi. Ayarlar'dan tekrar dene." else "Liste güncellenemedi. Tekrar dene"
        BackgroundSyncStatus.STOPPED ->
            if (isTv) "Güncelleme durduruldu. Ayarlar'dan tekrar dene." else "Güncelleme durduruldu. Tekrar dene"
        else -> ""
    }
    val canRetry = !isTv && (status == BackgroundSyncStatus.FAILED || status == BackgroundSyncStatus.STOPPED)

    Row(
        modifier = modifier
            .background(VegaColors.Card, VegaShapes.Medium)
            .then(if (canRetry) Modifier.clickable { onRetry() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == BackgroundSyncStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VegaColors.Accent
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, fontSize = VegaText.Body, color = VegaColors.TextPrimary)
    }
}
