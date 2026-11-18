package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.main.HomeTab
import com.tivimax.player.util.LocalIsTvDevice

/**
 * Sade ve özgün Vega üst gezinme çubuğu (Android TV ve geniş ekranlar için).
 * Sol: 4 köşeli geometrik Vega yıldız amblemi + VEGA | Orta: Çerçevesiz sekmeler | Sağ: Liste, yenileme ve hat bilgisi.
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

    Column(
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
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // SOL: Özgün 4 köşeli Vega yıldız amblemi + VEGA kelime markası
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(VegaShapes.Small)
                    .then(
                        if (!isTv) Modifier.clickable { onTabSelected(HomeTab.DASHBOARD) } else Modifier
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                VegaStarEmblem(
                    accentColor = VegaColors.Accent,
                    highlightColor = VegaColors.FocusBorder,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VEGA",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ORTA: Çerçevesiz yatay sekmeler + Arama + Ayarlar
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

            // SAĞ: Liste Seçici, Yenileme ve Hat Göstergesi
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aktif Liste Seçici
                Surface(
                    shape = VegaShapes.Pill,
                    color = if (playlistFocused) VegaColors.SurfaceElevated else VegaColors.Surface,
                    border = if (!playlistFocused) BorderStroke(0.75.dp, VegaColors.Border) else null,
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(max = 152.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Pill,
                            focusedBorderColor = VegaColors.FocusBorder,
                            scale = 1.035f,
                            onClick = onPlaylistsClick,
                            onFocusStateChange = { playlistFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = if (playlistFocused) VegaColors.Accent else VegaColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = activePlaylistName,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Caption,
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
                    shape = VegaShapes.Pill,
                    color = when {
                        syncFocused -> VegaColors.SurfaceElevated
                        isSyncing -> VegaColors.Accent.copy(alpha = 0.14f)
                        else -> Color.Transparent
                    },
                    modifier = Modifier
                        .height(34.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Pill,
                            focusedBorderColor = VegaColors.FocusBorder,
                            scale = 1.035f,
                            onClick = onSyncClick,
                            onFocusStateChange = { syncFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 9.dp),
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
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold
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

                // Sakin Hat Sayacı Göstergesi
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(VegaShapes.Pill)
                        .background(VegaColors.Surface)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(VegaColors.Success)
                    )
                    Text(
                        text = "$activeCons/$maxCons",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // İnce yatay ayırıcı çizgi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(0.75.dp)
                .background(VegaColors.Border)
        )
    }
}

/**
 * 4 köşeli geometrik Vega yıldız / prizma amblemi (saf Canvas vektör matematiği).
 */
@Composable
private fun VegaStarEmblem(
    accentColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    val starPath = remember { Path() }
    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.48f
        val inner = r * 0.28f
        starPath.reset()
        starPath.moveTo(cx, cy - r)
        starPath.lineTo(cx + inner, cy - inner)
        starPath.lineTo(cx + r, cy)
        starPath.lineTo(cx + inner, cy + inner)
        starPath.lineTo(cx, cy + r)
        starPath.lineTo(cx - inner, cy + inner)
        starPath.lineTo(cx - r, cy)
        starPath.lineTo(cx - inner, cy - inner)
        starPath.close()
        drawPath(path = starPath, color = accentColor)
        drawCircle(color = highlightColor, radius = r * 0.14f, center = Offset(cx, cy))
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
    val shape = VegaShapes.Pill

    val containerColor = when {
        isFocused -> VegaColors.SurfaceElevated
        selected -> VegaColors.SurfaceElevated.copy(alpha = 0.65f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(containerColor)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = shape,
                scale = 1.035f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    selected || isFocused -> VegaColors.Accent
                    else -> VegaColors.TextMuted
                },
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = if (selected || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }

        if (selected && !isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(18.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(VegaColors.Accent)
            )
        }
    }
}
