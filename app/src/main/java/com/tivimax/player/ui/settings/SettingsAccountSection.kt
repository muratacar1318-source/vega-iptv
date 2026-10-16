package com.tivimax.player.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.XtreamUserInfo
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable

@Composable
fun SettingsAccountSection(
    userInfo: XtreamUserInfo?,
    lastSync: String,
    autoSyncLive: Boolean,
    onSyncClick: () -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onLogoutClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TiviCard),
        border = BorderStroke(1.dp, TiviCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(OpusCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(text = userInfo?.username ?: "Kullanıcı", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Abonelik Durumu: Aktif", color = TiviGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TiviCardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bağlantı Limiti", color = TiviTextSecondary, fontSize = 13.sp)
                Text("${userInfo?.activeCons ?: "0"} / ${userInfo?.maxConnections ?: "1"} Cihaz", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Son Senkronizasyon", color = TiviTextSecondary, fontSize = 13.sp)
                Text(lastSync.ifBlank { "Henüz yapılmadı" }, color = OpusCyan, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSyncClick,
                colors = ButtonDefaults.buttonColors(containerColor = OpusCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .dpadFocusable(shape = RoundedCornerShape(10.dp), focusedBorderColor = Color.White, onClick = onSyncClick)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Şimdi Senkronize Et", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onLogoutClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .dpadFocusable(shape = RoundedCornerShape(10.dp), focusedBorderColor = Color.White, onClick = onLogoutClick)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Çıkış Yap / Profil Değiştir", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
