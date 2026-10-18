package com.tivimax.player.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.XtreamUserInfo
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

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
            cancelFocusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = VegaColors.Surface,
            shape = VegaShapes.Large,
            title = {
                Text(
                    text = "Bu liste kaldırılsın mı?",
                    color = VegaColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Bu listenin kanalları ve favorileri bu cihazdan silinir. Diğer listelerin etkilenmez.",
                    color = VegaColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Danger),
                    shape = VegaShapes.Medium,
                    modifier = Modifier
                        .height(44.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = Color.White,
                            onClick = {
                                showLogoutDialog = false
                                onLogoutClick()
                            }
                        )
                ) {
                    Text("Bu listeyi kaldır", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    shape = VegaShapes.Medium,
                    border = BorderStroke(1.dp, VegaColors.Border),
                    modifier = Modifier
                        .height(44.dp)
                        .focusRequester(cancelFocusRequester)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            onClick = { showLogoutDialog = false }
                        )
                ) {
                    Text("Vazgeç", color = VegaColors.TextPrimary)
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Hesabım · $activePlaylistName",
            color = VegaColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileCard(userInfo = userInfo, isRefreshing = isRefreshing, onRefreshClick = onRefreshClick)
                    SubscriptionCard(userInfo = userInfo)
                    ConnectionCard(userInfo = userInfo)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ContentSummaryCard(contentCounts = contentCounts)
                    SyncCard(lastSync = lastSync, onSyncClick = onSyncClick)
                    LogoutCard(onLogoutClick = { showLogoutDialog = true })
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileCard(userInfo = userInfo, isRefreshing = isRefreshing, onRefreshClick = onRefreshClick)
                SubscriptionCard(userInfo = userInfo)
                ConnectionCard(userInfo = userInfo)
                ContentSummaryCard(contentCounts = contentCounts)
                SyncCard(lastSync = lastSync, onSyncClick = onSyncClick)
                LogoutCard(onLogoutClick = { showLogoutDialog = true })
            }
        }
    }
}
}

@Composable
private fun ProfileCard(
    userInfo: XtreamUserInfo?,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userInfo?.username?.ifBlank { "Kullanıcı" } ?: "Kullanıcı",
                    color = VegaColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (userInfo?.isTrial == "1") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = VegaColors.Gold.copy(alpha = 0.15f),
                            shape = VegaShapes.Small,
                            border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Deneme",
                                color = VegaColors.Gold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onRefreshClick,
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color.White, onClick = onRefreshClick)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = VegaColors.Accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Hesap bilgilerini yenile",
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(userInfo: XtreamUserInfo?) {
    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Abonelik Bilgileri",
                color = VegaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            val expDateFormatted = AccountInfoFormatter.formatExpDate(userInfo?.expDate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bitiş Tarihi", color = VegaColors.TextSecondary, fontSize = 13.sp)
                Text(expDateFormatted, color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            val remainingDays = AccountInfoFormatter.formatRemainingDays(userInfo?.expDate)
            if (remainingDays != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val remColor = when (remainingDays.colorType) {
                    AccountBadgeColor.GREEN -> VegaColors.Success
                    AccountBadgeColor.RED -> VegaColors.Danger
                    AccountBadgeColor.AMBER -> VegaColors.Gold
                    AccountBadgeColor.GRAY -> VegaColors.TextMuted
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kalan Süre", color = VegaColors.TextSecondary, fontSize = 13.sp)
                    Text(remainingDays.label, color = remColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            val subProgress = AccountInfoFormatter.calculateSubscriptionProgress(userInfo?.createdAt, userInfo?.expDate)
            if (subProgress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { subProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = VegaColors.Accent,
                    trackColor = VegaColors.Border
                )
            }

            val createdAtFormatted = AccountInfoFormatter.formatCreatedAt(userInfo?.createdAt)
            if (createdAtFormatted != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Üyelik Başlangıcı", color = VegaColors.TextSecondary, fontSize = 13.sp)
                    Text(createdAtFormatted, color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(userInfo: XtreamUserInfo?) {
    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Bağlantı",
                color = VegaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            val maxCons = userInfo?.maxConnections?.trim()?.ifBlank { null }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aynı anda izleme hakkı", color = VegaColors.TextSecondary, fontSize = 13.sp)
                Text(
                    text = if (maxCons != null) "$maxCons cihaz" else "—",
                    color = VegaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val activeCons = userInfo?.activeCons?.trim()?.ifBlank { null }
            if (activeCons != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Şu an kullanımda", color = VegaColors.TextSecondary, fontSize = 13.sp)
                    Text(activeCons, color = VegaColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ContentSummaryCard(contentCounts: Triple<Int, Int, Int>?) {
    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "İçerik Özeti",
                color = VegaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContentCountBox(
                    title = "Canlı Kanal",
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = VegaColors.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SyncCard(
    lastSync: String,
    onSyncClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Senkronizasyon",
                color = VegaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            val formattedSync = AccountInfoFormatter.formatLastSync(lastSync)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Son Güncelleme", color = VegaColors.TextSecondary, fontSize = 13.sp)
                Text(
                    text = formattedSync,
                    color = VegaColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSyncClick,
                colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                shape = VegaShapes.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .dpadFocusable(shape = VegaShapes.Medium, focusedBorderColor = Color.White, onClick = onSyncClick)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Şimdi senkronize et", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LogoutCard(onLogoutClick: () -> Unit) {
    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Button(
                onClick = onLogoutClick,
                colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Danger),
                shape = VegaShapes.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .dpadFocusable(shape = VegaShapes.Medium, focusedBorderColor = Color.White, onClick = onLogoutClick)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bu listeyi kaldır", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
