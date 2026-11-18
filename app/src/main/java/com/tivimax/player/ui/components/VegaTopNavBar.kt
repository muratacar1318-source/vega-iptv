package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.main.HomeTab
import com.tivimax.player.util.LocalIsTvDevice

/**
 * StreamVault imzalı yüzen üst gezinme çubuğu (Android TV ve geniş ekranlar için).
 * Sol: Vega IPTV marka logosu ve adı | Orta: Odaklanabilir sekme hapları | Sağ: Liste, yenileme ve hat bilgisi.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VegaTopNavBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onSearchClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onSyncClick: () -> Unit,
    activePlaylistName: String,
    activeCons: String,
    maxCons: String,
    isSyncing: Boolean = false,
    syncPercentage: Int = 0,
    selectedTabFocusRequester: FocusRequester? = null,
    onBarFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navTabs = listOf(
        HomeTab.DASHBOARD,
        HomeTab.LIVE_TV,
        HomeTab.MOVIES,
        HomeTab.SERIES,
        HomeTab.FAVORITES
    )
    val tabRequesters = remember { mutableMapOf<HomeTab, FocusRequester>() }
    val attachedRequesters = remember { mutableSetOf<FocusRequester>() }
    val scrollState = rememberScrollState()
    val isTv = LocalIsTvDevice.current
    var playlistFocused by remember { mutableStateOf(false) }
    var syncFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Large,
        color = VegaColors.Surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onBarFocusChanged(it.hasFocus) }
            .focusProperties {
                onEnter = {
                    val activeReq = selectedTabFocusRequester ?: tabRequesters[selectedTab]
                    if (activeReq != null && activeReq in attachedRequesters) {
                        activeReq.requestFocusSafely()
                    }
                }
            }
            .focusGroup()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // SOL: Vega IPTV Kimliği (TV'de kumanda odak tuzağı oluşturmaması için sadece dokunmatikte tıklanabilir)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(VegaShapes.Small)
                    .then(
                        if (!isTv) Modifier.clickable { onTabSelected(HomeTab.DASHBOARD) } else Modifier
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(VegaShapes.Small)
                        .background(VegaColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = VegaColors.OnAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppConfig.APP_NAME,
                    color = VegaColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // ORTA: Ana Sekmeler + Arama + Ayarlar (StreamVault düzeni)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navTabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val requester = if (isSelected && selectedTabFocusRequester != null) {
                        selectedTabFocusRequester
                    } else {
                        tabRequesters.getOrPut(tab) { FocusRequester() }
                    }
                    DisposableEffect(requester) {
                        attachedRequesters.add(requester)
                        onDispose { attachedRequesters.remove(requester) }
                    }
                    TopNavPillItem(
                        icon = tab.icon,
                        label = tab.title,
                        selected = isSelected,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.focusRequester(requester)
                    )
                }

                TopNavPillItem(
                    icon = Icons.Default.Search,
                    label = "Ara",
                    selected = false,
                    onClick = onSearchClick
                )

                val isSettingsSelected = selectedTab == HomeTab.SETTINGS
                val settingsRequester = if (isSettingsSelected && selectedTabFocusRequester != null) {
                    selectedTabFocusRequester
                } else {
                    tabRequesters.getOrPut(HomeTab.SETTINGS) { FocusRequester() }
                }
                DisposableEffect(settingsRequester) {
                    attachedRequesters.add(settingsRequester)
                    onDispose { attachedRequesters.remove(settingsRequester) }
                }
                TopNavPillItem(
                    icon = HomeTab.SETTINGS.icon,
                    label = HomeTab.SETTINGS.title,
                    selected = isSettingsSelected,
                    onClick = { onTabSelected(HomeTab.SETTINGS) },
                    modifier = Modifier.focusRequester(settingsRequester)
                )
            }

            // SAĞ: Liste Seçici, Yenileme ve Hat Rozeti
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aktif Liste Hapı
                Surface(
                    shape = VegaShapes.Small,
                    color = if (playlistFocused) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.SurfaceElevated,
                    border = if (!playlistFocused) BorderStroke(1.dp, VegaColors.Border) else null,
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(max = 150.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            focusedBorderColor = VegaColors.FocusBorder,
                            scale = 1.035f,
                            onClick = onPlaylistsClick,
                            onFocusStateChange = { playlistFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = activePlaylistName,
                            color = VegaColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = if (playlistFocused) VegaColors.Accent else VegaColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Yenileme / Senkronizasyon Düğmesi
                Surface(
                    shape = VegaShapes.Small,
                    color = when {
                        syncFocused -> VegaColors.Accent.copy(alpha = 0.22f)
                        isSyncing -> VegaColors.Accent.copy(alpha = 0.16f)
                        else -> Color.Transparent
                    },
                    border = if (isSyncing && !syncFocused) BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.4f)) else null,
                    modifier = Modifier
                        .height(34.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            focusedBorderColor = VegaColors.FocusBorder,
                            scale = 1.035f,
                            onClick = onSyncClick,
                            onFocusStateChange = { syncFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = VegaColors.Accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "%$syncPercentage",
                                color = VegaColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "İçeriği güncelle",
                                tint = if (syncFocused) VegaColors.Accent else VegaColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Bağlantı Sayacı Rozeti
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.Success.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, VegaColors.Success.copy(alpha = 0.30f))
                ) {
                    Text(
                        text = "$activeCons/$maxCons",
                        color = VegaColors.Success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopNavPillItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = VegaShapes.Small

    val containerColor = when {
        isFocused -> VegaColors.Accent.copy(alpha = 0.22f)
        selected -> VegaColors.Accent.copy(alpha = 0.14f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(containerColor)
            .then(
                if (selected && !isFocused) {
                    Modifier.border(1.dp, VegaColors.Accent.copy(alpha = 0.34f), shape)
                } else {
                    Modifier
                }
            )
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = shape,
                scale = 1.035f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected || isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = if (selected || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
