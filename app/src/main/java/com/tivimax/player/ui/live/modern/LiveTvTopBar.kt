package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.tv.StreamVaultSearchField

/**
 * Sade Canlı TV üst çubuğu: arama alanı + çerçevesiz hızlı eylem ikonları.
 * Tüm aksiyon butonları tek odak ve tek tıklama hedefine sahiptir.
 */
@Composable
fun LiveTvTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentViewMode: String,
    onViewModeToggle: () -> Unit,
    onOpenSortSheet: () -> Unit,
    onRefresh: () -> Unit,
    onToggleShelfLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VegaColors.Background.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StreamVaultSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Kanal veya program ara…",
            modifier = Modifier.weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val (modeIcon, modeDesc) = when (currentViewMode) {
                UserPreferences.LIVE_VIEW_GRID -> Pair(Icons.Default.GridView, "Izgara görünümü")
                UserPreferences.LIVE_VIEW_DETAILED -> Pair(Icons.Default.TableRows, "Detaylı görünüm")
                else -> Pair(Icons.AutoMirrored.Filled.ViewList, "Kompakt liste")
            }

            TopBarActionIcon(
                icon = modeIcon,
                contentDescription = modeDesc,
                tint = VegaColors.Accent,
                onClick = onViewModeToggle
            )

            TopBarActionIcon(
                icon = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sırala",
                tint = VegaColors.TextPrimary,
                onClick = onOpenSortSheet
            )

            TopBarActionIcon(
                icon = Icons.Default.Refresh,
                contentDescription = "Kanal listesini yenile",
                tint = VegaColors.TextSecondary,
                onClick = onRefresh
            )

            TopBarActionIcon(
                icon = Icons.Default.ViewCarousel,
                contentDescription = "Raf düzenine geç",
                tint = VegaColors.TextSecondary,
                onClick = onToggleShelfLayout
            )
        }
    }
}

@Composable
private fun TopBarActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(VegaShapes.Pill)
            .background(if (isFocused) VegaColors.SurfaceVariant else Color.Transparent)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Pill,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) VegaColors.Accent else tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
