package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.tv.StreamVaultSearchField

/**
 * Modern ve Kompakt Canlı TV Üst Çubuğu (StreamVault görsel dili).
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
    Surface(
        color = VegaColors.Background.copy(alpha = 0.92f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // TV-güvenli StreamVault Arama Alanı (odak tuzağı olmadan)
            StreamVaultSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Kanal veya program ara...",
                modifier = Modifier.weight(1f)
            )

            // Sağ Taraf Eylemleri: Görünüm Modu, Sıralama, Yenile, Raf Düzeni (Tek odak hedefli)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                    tint = VegaColors.Accent,
                    onClick = onToggleShelfLayout
                )
            }
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

    Surface(
        shape = VegaShapes.Small,
        color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
        border = if (isFocused) null else BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .size(40.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
