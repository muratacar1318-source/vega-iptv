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
import com.tivimax.player.ui.design.vegaPanel
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
            containerColor = VegaColors.Surface,
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
        val isTwoColumn = maxWidth >= 780.dp

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vegaPanel(VegaShapes.Large)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarLetter = AccountInfoFormatter.getUserAvatarLetter(userInfo?.username)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(VegaColors.Accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarLetter,
                color = VegaColors.Accent,
                fontSize = VegaText.ScreenTitle,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = userInfo?.username?.ifBlank { "Kullanıcı" } ?: "Kullanıcı",
                color = VegaColors.TextPrimary,
                fontSize = VegaText.SectionTitle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = activePlaylistName,
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statusResult = AccountInfoFormatter.accountStatus(userInfo)
                val statusColor = when (statusResult.colorType) {
                    AccountBadgeColor.GREEN -> VegaColors.Success
                    AccountBadgeColor.RED -> VegaColors.Danger
                    AccountBadgeColor.AMBER -> VegaColors.Gold
                    AccountBadgeColor.GRAY -> VegaColors.TextMuted
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = VegaShapes.Small,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = statusResult.label,
                        color = statusColor,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                if (userInfo?.isTrial == "1") {
                    Surface(
                        color = VegaColors.Gold.copy(alpha = 0.15f),
                        shape = VegaShapes.Small,
                        border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Deneme",
                            color = VegaColors.Gold,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = VegaColors.Accent,
                trackColor = VegaColors.Border
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ContentCountBox(
                title = "Canlı kanal",
                count = AccountInfoFormatter.formatContentCount(contentCounts?.first),
                icon = Icons.Default.Tv,
                modifier = Modifier.weight(1f)
            )
            ContentCountBox(
                title = "Film",
                count = AccountInfoFormatter.formatContentCount(contentCounts?.second),
                icon = Icons.Default.Movie,
                modifier = Modifier.weight(1f)
            )
            ContentCountBox(
                title = "Dizi",
                count = AccountInfoFormatter.formatContentCount(contentCounts?.third),
                icon = Icons.Default.VideoLibrary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ContentCountBox(
    title: String,
    count: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VegaColors.Surface,
        shape = VegaShapes.Medium,
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VegaColors.Accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.BodyProminent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Caption
            )
        }
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
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            SettingsActionButton(
                text = "Bu listeyi kaldır",
                variant = VegaButtonVariant.DANGER,
                icon = Icons.Default.DeleteOutline,
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
