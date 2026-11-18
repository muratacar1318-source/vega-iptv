package com.tivimax.player.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.XtreamUserInfo
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.VegaButtonVariant
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

@Composable
fun SettingsAccountSection(
    userInfo: XtreamUserInfo?,
    lastSync: String,
    contentCounts: Triple<Int, Int, Int>?,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    activePlaylistName: String = "Ana liste"
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        val cancelFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (cancelFocusRequester.requestFocusSafely()) break
            }
        }

        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = VegaColors.SurfaceElevated,
            shape = VegaShapes.Large,
            title = {
                Text(
                    text = "Bu liste kaldırılsın mı?",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Bu listenin kanalları ve favorileri bu cihazdan silinir. Diğer listelerin etkilenmez.",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    lineHeight = VegaText.SectionTitle
                )
            },
            confirmButton = {
                SettingsActionButton(
                    text = "Bu listeyi kaldır",
                    variant = VegaButtonVariant.DANGER,
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    }
                )
            },
            dismissButton = {
                SettingsActionButton(
                    text = "Vazgeç",
                    variant = VegaButtonVariant.SECONDARY,
                    onClick = { showLogoutDialog = false },
                    modifier = Modifier.focusRequester(cancelFocusRequester)
                )
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isTwoColumn = maxWidth >= 720.dp

        if (isTwoColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProfileCard(
                        userInfo = userInfo,
                        activePlaylistName = activePlaylistName,
                        isRefreshing = isRefreshing,
                        onRefreshClick = onRefreshClick
                    )
                    SubscriptionCard(userInfo = userInfo)
                    ConnectionCard(userInfo = userInfo)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ContentSummaryCard(contentCounts = contentCounts)
                    SyncCard(lastSync = lastSync, onSyncClick = onSyncClick)
                    LogoutCard(onLogoutClick = { showLogoutDialog = true })
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ProfileCard(
                    userInfo = userInfo,
                    activePlaylistName = activePlaylistName,
                    isRefreshing = isRefreshing,
                    onRefreshClick = onRefreshClick
                )
                SubscriptionCard(userInfo = userInfo)
                ConnectionCard(userInfo = userInfo)
                ContentSummaryCard(contentCounts = contentCounts)
                SyncCard(lastSync = lastSync, onSyncClick = onSyncClick)
                LogoutCard(onLogoutClick = { showLogoutDialog = true })
            }
        }
    }
}

@Composable
private fun ProfileCard(
    userInfo: XtreamUserInfo?,
    activePlaylistName: String,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit
) {
    Surface(
        shape = VegaShapes.Large,
        color = VegaColors.Surface,
        border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarLetter = AccountInfoFormatter.getUserAvatarLetter(userInfo?.username)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(VegaColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarLetter,
                    color = VegaColors.Accent,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = userInfo?.username?.ifBlank { "Kullanıcı" } ?: "Kullanıcı",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val statusResult = AccountInfoFormatter.accountStatus(userInfo)
                val statusColor = when (statusResult.colorType) {
                    AccountBadgeColor.GREEN -> VegaColors.Success
                    AccountBadgeColor.RED -> VegaColors.Danger
                    AccountBadgeColor.AMBER -> VegaColors.Gold
                    AccountBadgeColor.GRAY -> VegaColors.TextMuted
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    val metaText = buildString {
                        append(statusResult.label)
                        if (userInfo?.isTrial == "1") append(" · Deneme")
                        if (activePlaylistName.isNotBlank()) append(" · $activePlaylistName")
                    }
                    Text(
                        text = metaText,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            SettingsIconActionButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Hesap bilgilerini yenile",
                onClick = onRefreshClick,
                isLoading = isRefreshing
            )
        }
    }
}

@Composable
private fun SubscriptionCard(userInfo: XtreamUserInfo?) {
    SettingsGroupCard(title = "Abonelik bilgileri") {
        val expDateFormatted = AccountInfoFormatter.formatExpDate(userInfo?.expDate)
        SettingsInfoRow(
            label = "Bitiş tarihi",
            value = expDateFormatted
        )

        val remainingDays = AccountInfoFormatter.formatRemainingDays(userInfo?.expDate)
        if (remainingDays != null) {
            SettingsGroupDivider()
            val remColor = when (remainingDays.colorType) {
                AccountBadgeColor.GREEN -> VegaColors.Success
                AccountBadgeColor.RED -> VegaColors.Danger
                AccountBadgeColor.AMBER -> VegaColors.Gold
                AccountBadgeColor.GRAY -> VegaColors.TextMuted
            }
            SettingsInfoRow(
                label = "Kalan süre",
                value = remainingDays.label,
                valueColor = remColor
            )
        }

        val subProgress = AccountInfoFormatter.calculateSubscriptionProgress(
            userInfo?.createdAt,
            userInfo?.expDate
        )
        if (subProgress != null) {
            LinearProgressIndicator(
                progress = { subProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = VegaColors.Accent,
                trackColor = VegaColors.Border.copy(alpha = 0.6f)
            )
        }

        val createdAtFormatted = AccountInfoFormatter.formatCreatedAt(userInfo?.createdAt)
        if (createdAtFormatted != null) {
            SettingsGroupDivider()
            SettingsInfoRow(
                label = "Üyelik başlangıcı",
                value = createdAtFormatted
            )
        }
    }
}

@Composable
private fun ConnectionCard(userInfo: XtreamUserInfo?) {
    SettingsGroupCard(title = "Bağlantı") {
        val maxCons = userInfo?.maxConnections?.trim()?.ifBlank { null }
        SettingsInfoRow(
            label = "Aynı anda izleme hakkı",
            value = if (maxCons != null) "$maxCons cihaz" else "—"
        )

        val activeCons = userInfo?.activeCons?.trim()?.ifBlank { null }
        if (activeCons != null) {
            SettingsGroupDivider()
            SettingsInfoRow(
                label = "Şu an kullanımda",
                value = activeCons,
                valueColor = VegaColors.Accent
            )
        }
    }
}

@Composable
private fun ContentSummaryCard(contentCounts: Triple<Int, Int, Int>?) {
    SettingsGroupCard(title = "İçerik özeti") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContentCountColumn(
                title = "Canlı",
                count = AccountInfoFormatter.formatContentCount(contentCounts?.first),
                icon = Icons.Default.Tv,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(VegaColors.Border.copy(alpha = 0.55f))
            )
            ContentCountColumn(
                title = "Film",
                count = AccountInfoFormatter.formatContentCount(contentCounts?.second),
                icon = Icons.Default.Movie,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(VegaColors.Border.copy(alpha = 0.55f))
            )
            ContentCountColumn(
                title = "Dizi",
                count = AccountInfoFormatter.formatContentCount(contentCounts?.third),
                icon = Icons.Default.VideoLibrary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ContentCountColumn(
    title: String,
    count: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VegaColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = count,
            color = VegaColors.TextPrimary,
            fontSize = VegaText.BodyProminent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = VegaColors.TextMuted,
            fontSize = VegaText.Caption
        )
    }
}

@Composable
private fun SyncCard(
    lastSync: String,
    onSyncClick: () -> Unit
) {
    SettingsGroupCard(title = "İçerik güncelleme") {
        val formattedSync = AccountInfoFormatter.formatLastSync(lastSync)
        SettingsInfoRow(
            label = "Son güncelleme",
            value = formattedSync,
            valueColor = VegaColors.Accent
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SettingsActionButton(
                text = "Listeyi şimdi güncelle",
                variant = VegaButtonVariant.PRIMARY,
                icon = Icons.Default.Sync,
                onClick = onSyncClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LogoutCard(onLogoutClick: () -> Unit) {
    SettingsGroupCard(title = "Liste işlemleri") {
        SettingsActionRow(
            title = "Bu listeyi kaldır",
            subtitle = "Kanallar ve favoriler bu cihazdan silinir",
            icon = Icons.Default.DeleteOutline,
            accentColor = VegaColors.Danger,
            onClick = onLogoutClick
        )
    }
}
