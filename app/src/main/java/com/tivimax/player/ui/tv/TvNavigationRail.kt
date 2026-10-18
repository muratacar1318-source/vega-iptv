package com.tivimax.player.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.main.HomeTab

/**
 * ANDROID TV SOL MENÜ (10-FOOT UI)
 * Mobildeki alt menünün kumandaya uyarlanmış hali: Ana Sayfa, Canlı TV, Filmler, Diziler,
 * Favoriler + Arama ve Ayarlar. Sekme OK tuşuyla açılır; menüye geri dönüldüğünde odak
 * en son seçilen sekmeye geri gelir (focusRestorer).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvNavigationRail(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onSearchClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    selectedTabFocusRequester: FocusRequester,
    onRailFocusChanged: (Boolean) -> Unit,
    activeCons: String,
    maxCons: String,
    modifier: Modifier = Modifier
) {
    val mainTabs = listOf(
        HomeTab.DASHBOARD,
        HomeTab.LIVE_TV,
        HomeTab.MOVIES,
        HomeTab.SERIES,
        HomeTab.FAVORITES
    )

    Surface(
        modifier = modifier
            .width(92.dp)
            .fillMaxHeight(),
        color = VegaColors.Surface,
        border = BorderStroke(1.dp, VegaColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { onRailFocusChanged(it.hasFocus) }
                .focusRestorer()
                .focusGroup()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(VegaShapes.Medium)
                    .background(VegaColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                mainTabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    TvRailItem(
                        icon = tab.icon,
                        label = tab.title,
                        isSelected = isSelected,
                        onClick = { onTabSelected(tab) },
                        modifier = if (isSelected) Modifier.focusRequester(selectedTabFocusRequester) else Modifier
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TvRailItem(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    label = "Listeler",
                    isSelected = false,
                    onClick = onPlaylistsClick
                )
                TvRailItem(
                    icon = Icons.Default.Search,
                    label = "Ara",
                    isSelected = false,
                    onClick = onSearchClick
                )
                val settingsSelected = selectedTab == HomeTab.SETTINGS
                TvRailItem(
                    icon = HomeTab.SETTINGS.icon,
                    label = HomeTab.SETTINGS.title,
                    isSelected = settingsSelected,
                    onClick = { onTabSelected(HomeTab.SETTINGS) },
                    modifier = if (settingsSelected) Modifier.focusRequester(selectedTabFocusRequester) else Modifier
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$activeCons/$maxCons",
                    color = VegaColors.Success,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TvRailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = VegaShapes.Medium

    val background = when {
        isFocused -> VegaColors.Accent.copy(alpha = 0.25f)
        isSelected -> VegaColors.Accent.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(width = 76.dp, height = 52.dp)
            .graphicsLayer {
                val s = if (isFocused) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(background)
            .then(if (isFocused) Modifier.border(2.dp, VegaColors.Accent, shape) else Modifier)
            // Tek odak hedefi: kumanda ile tek basışta seçilir
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(30.dp)
                    .background(
                        color = VegaColors.Accent,
                        shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                    )
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    isFocused -> VegaColors.TextPrimary
                    isSelected -> VegaColors.Accent
                    else -> VegaColors.TextSecondary
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = VegaText.Caption,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
