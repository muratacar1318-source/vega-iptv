package com.tivimax.player.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.model.XtreamUserInfo
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.dpadFocusable
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.tivimax.player.util.DeviceMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SettingsSection(val title: String, val icon: ImageVector, val subtitle: String) {
    ACCOUNT("Hesap ve Liste", Icons.Default.AccountCircle, "Sunucu, hesap ve liste güncelleme"),
    PLAYER("Oynatıcı ve Kodek", Icons.Default.Tune, "Video çözücü, tampon (buffer) & PiP"),
    APPEARANCE("Görünüm ve Altyazı", Icons.Default.Subtitles, "Altyazı boyutu, rengi & kategoriler"),
    DEVICE_MODE("Cihaz Modu", Icons.Default.Tv, "Telefon, Tablet ve TV çalışma düzeni"),
    PARENTAL("Ebeveyn Kontrolü", Icons.Default.Security, "+18 içerik kilidi & PIN kodu & HTTPS"),
    BACKUP_APP("Yedekleme ve Uygulama", Icons.Default.Backup, "Yedekle, önbellek, hız testi & sürüm")
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val userInfo by viewModel.userInfo.collectAsState()
    val speedTestResult by viewModel.speedTestResult.collectAsState()
    val isTestingSpeed by viewModel.isTestingSpeed.collectAsState()

    val streamFormat by viewModel.preferences.streamFormatFlow.collectAsState(initial = "TS")
    val fastZapping by viewModel.preferences.fastZappingFlow.collectAsState(initial = true)
    val defaultAspectRatio by viewModel.preferences.aspectRatioFlow.collectAsState(initial = "FIT")
    val serverUrl by viewModel.preferences.serverUrlFlow.collectAsState(initial = AppConfig.DEFAULT_SERVER_URL)
    val autoSyncLive by viewModel.autoSyncLive.collectAsState()
    val autoPipEnabled by viewModel.autoPipEnabled.collectAsState()
    val vodBufferSeconds by viewModel.vodBufferSeconds.collectAsState()
    val liveBufferSeconds by viewModel.liveBufferSeconds.collectAsState()
    val decoderMode by viewModel.decoderMode.collectAsState()
    val lastSync by viewModel.preferences.lastSyncTimestampFlow.collectAsState(initial = "")
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSection by remember { mutableStateOf(SettingsSection.ACCOUNT) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDeviceModeDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var pinSuccessMessage by remember { mutableStateOf<String?>(null) }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        if (isWideScreen) {
            // ==========================================
            // YATAY / ANDROID TV EKRANI (TIVIMATE TARZI SOL MENÜ + SAĞ PANEL)
            // ==========================================
            Row(modifier = Modifier.fillMaxSize()) {
                // SOL MENÜ: Kategori Sekmeleri (D-Pad Odaklanabilir)
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(TiviCardDark)
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "AYARLAR", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsSection.values().forEach { section ->
                        val isSelected = selectedSection == section
                        Surface(
                            onClick = { selectedSection = section },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) OpusCyan.copy(alpha = 0.16f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, OpusCyan.copy(alpha = 0.6f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .dpadFocusable(
                                    focusedBorderColor = OpusCyan,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { selectedSection = section }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) OpusCyan else TiviTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = section.title,
                                        color = if (isSelected) Color.White else TiviTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = section.subtitle,
                                        color = if (isSelected) OpusCyan.copy(alpha = 0.8f) else TiviTextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // AYIRAÇ ÇİZGİSİ
                VerticalDivider(color = TiviCardBorder, thickness = 1.dp)

                // SAĞ PANEL: Seçili Kategori Ayarları
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RenderSelectedSection(
                        section = selectedSection,
                        viewModel = viewModel,
                        userInfo = userInfo,
                        lastSync = lastSync,
                        decoderMode = decoderMode,
                        defaultAspectRatio = defaultAspectRatio,
                        autoPipEnabled = autoPipEnabled,
                        streamFormat = streamFormat,
                        liveBufferSeconds = liveBufferSeconds,
                        vodBufferSeconds = vodBufferSeconds,
                        fastZapping = fastZapping,
                        autoSyncLive = autoSyncLive,
                        serverUrl = serverUrl,
                        subtitleSize = subtitleSize,
                        subtitleColor = subtitleColor,
                        speedTestResult = speedTestResult,
                        isTestingSpeed = isTestingSpeed,
                        pinSuccessMessage = pinSuccessMessage,
                        context = context,
                        onOpenCategoryDialog = { showCategoryDialog = true },
                        onOpenPinDialog = { showPinDialog = true },
                        onOpenDeviceModeDialog = { showDeviceModeDialog = true }
                    )
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        } else {
            // ==========================================
            // DİKEY MOD (TELEFON / TABLET DİKEY) - DİKEY MENÜ & HER SEKMEYE ÖZEL SAYFA
            // ==========================================
            var activeSubPage by remember { mutableStateOf<SettingsSection?>(null) }

            if (activeSubPage == null) {
                // DİKEY AYARLAR KATEGORİ LİSTESİ
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "AYARLAR", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }

                    SettingsSection.values().forEach { section ->
                        Card(
                            onClick = { activeSubPage = section },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = TiviCard),
                            border = BorderStroke(1.dp, TiviCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(
                                    shape = RoundedCornerShape(14.dp),
                                    focusedBorderColor = OpusCyan,
                                    onClick = { activeSubPage = section }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(OpusCyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = section.icon,
                                            contentDescription = null,
                                            tint = OpusCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = section.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = section.subtitle,
                                            color = TiviTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TiviTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(90.dp))
                }
            } else {
                // ÖZEL DİKEY ALT SAYFA (SUB-PAGE)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // GERİ DÖNÜŞ BAŞLIĞI
                    Surface(
                        onClick = { activeSubPage = null },
                        shape = RoundedCornerShape(10.dp),
                        color = TiviCard,
                        border = BorderStroke(1.dp, TiviCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .dpadFocusable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = OpusCyan,
                                onClick = { activeSubPage = null }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = OpusCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = activeSubPage!!.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ALT SAYFA İÇERİĞİ
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RenderSelectedSection(
                            section = activeSubPage!!,
                            viewModel = viewModel,
                            userInfo = userInfo,
                            lastSync = lastSync,
                            decoderMode = decoderMode,
                            defaultAspectRatio = defaultAspectRatio,
                            autoPipEnabled = autoPipEnabled,
                            streamFormat = streamFormat,
                            liveBufferSeconds = liveBufferSeconds,
                            vodBufferSeconds = vodBufferSeconds,
                            fastZapping = fastZapping,
                            autoSyncLive = autoSyncLive,
                            serverUrl = serverUrl,
                            subtitleSize = subtitleSize,
                            subtitleColor = subtitleColor,
                            speedTestResult = speedTestResult,
                            isTestingSpeed = isTestingSpeed,
                            pinSuccessMessage = pinSuccessMessage,
                            context = context,
                            onOpenCategoryDialog = { showCategoryDialog = true },
                            onOpenPinDialog = { showPinDialog = true },
                            onOpenDeviceModeDialog = { showDeviceModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(90.dp))
                    }
                }
            }
        }
    }

    // KATEGORİ YÖNETİM DİYALOĞU
    if (showCategoryDialog) {
        CategoryManagementDialog(
            viewModel = viewModel,
            onDismiss = { showCategoryDialog = false }
        )
    }

    // PIN DEĞİŞTİRME DİYALOĞU
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = TiviSurface,
            title = { Text("Yeni PIN Kodu Belirle", color = TiviTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Yetişkin kategorileri korumak için 4 haneli yeni PIN kodunuzu girin:",
                        color = TiviTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4) newPinInput = it },
                        placeholder = { Text("Örn: 1234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OpusCyan,
                            unfocusedBorderColor = TiviCardBorder,
                            focusedTextColor = TiviTextPrimary,
                            unfocusedTextColor = TiviTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length == 4) {
                            scope.launch {
                                viewModel.preferences.setParentalPin(newPinInput)
                                pinSuccessMessage = "PIN kodu başarıyla güncellendi."
                                showPinDialog = false
                                newPinInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black)
                ) {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("İptal", color = TiviTextSecondary)
                }
            }
        )
    }

    // CİHAZ MODU SEÇİM DİYALOĞU
    if (showDeviceModeDialog) {
        DeviceModeSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showDeviceModeDialog = false }
        )
    }
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
private fun RenderSelectedSection(
    section: SettingsSection,
    viewModel: MainViewModel,
    userInfo: XtreamUserInfo?,
    lastSync: String,
    decoderMode: String,
    defaultAspectRatio: String,
    autoPipEnabled: Boolean,
    streamFormat: String,
    liveBufferSeconds: Int,
    vodBufferSeconds: Int,
    fastZapping: Boolean,
    autoSyncLive: Boolean,
    serverUrl: String,
    subtitleSize: String,
    subtitleColor: String,
    speedTestResult: String?,
    isTestingSpeed: Boolean,
    pinSuccessMessage: String?,
    context: Context,
    onOpenCategoryDialog: () -> Unit,
    onOpenPinDialog: () -> Unit,
    onOpenDeviceModeDialog: () -> Unit
) {
    val scope = rememberCoroutineScope()

    when (section) {
        // ==========================================
        // 1. HESAP VE OYNATMA LİSTESİ
        // ==========================================
        SettingsSection.ACCOUNT -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OpusCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = OpusCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = userInfo?.username ?: "Kullanıcı", color = TiviTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TiviGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Abonelik: Aktif", color = TiviGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Bitiş Tarihi
                    val expDateFormatted = remember(userInfo?.expDate) {
                        val exp = userInfo?.expDate
                        if (!exp.isNullOrEmpty() && exp != "null") {
                            val timestamp = exp.toLongOrNull()
                            if (timestamp != null && timestamp > 0) {
                                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                sdf.format(Date(timestamp * 1000L))
                            } else {
                                exp
                            }
                        } else {
                            "Süresiz"
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Bitiş Tarihi:", color = TiviTextSecondary, fontSize = 13.sp)
                            Text(text = "Aboneliğinizin sona ereceği tarih", color = TiviTextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                        Text(text = expDateFormatted, color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Eşzamanlı Bağlantı:", color = TiviTextSecondary, fontSize = 13.sp)
                            Text(text = "Aynı anda kullanabileceğiniz cihaz limiti", color = TiviTextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                        Text(text = "${userInfo?.activeCons ?: "1"} / ${userInfo?.maxConnections ?: "1"} Cihaz", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Aktif Sunucu:", color = TiviTextSecondary, fontSize = 13.sp)
                            Text(text = "Bağlı olduğunuz IPTV paneli", color = TiviTextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                        Text(text = viewModel.repository.serverUrl, color = TiviTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                    }

                    if (lastSync.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(text = "Son Güncelleme:", color = TiviTextSecondary, fontSize = 13.sp)
                                Text(text = "Kanalların en son senkronize edildiği an", color = TiviTextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                            Text(text = lastSync, color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Oynatma Listesini Şimdi Güncelle Butonu
                    Text(text = "Oynatma Listesi", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Yeni eklenen kanalları, VOD (Film/Dizi) içeriklerini ve kanal rehberini (EPG) hemen almak için listeyi tazeleyin.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.openPlaylistSync() },
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                onClick = { viewModel.openPlaylistSync() }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "İçerikleri ve Kanalları Şimdi Güncelle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "Hesap Çıkışı", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Geçerli abonelik oturumunuzu sonlandırır ve sizi giriş ekranına döndürür.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Çıkış Yap Butonu
                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color(0xFFEF5350),
                                shape = RoundedCornerShape(12.dp),
                                onClick = { viewModel.logout() }
                            )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFEF5350))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Hesaptan Çıkış Yap / Listeyi Sıfırla", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ==========================================
        // 2. OYNATICI VE KODEK
        // ==========================================
        SettingsSection.PLAYER -> {
            val playerEngine by viewModel.playerEngine.collectAsState()
            val defaultAudioLang by viewModel.defaultAudioLang.collectAsState()
            val defaultSubtitleLang by viewModel.defaultSubtitleLang.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // OYNATICI MOTORU SEÇİMİ (ExoPlayer / LibVLC / Harici)
                    Text(text = "Oynatıcı Motoru (Player Engine)", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (playerEngine) {
                            "VLC" -> "LibVLC Motoru aktif (Gelişmiş codec desteği ve bozuk yayın uyumluluğu)"
                            "EXTERNAL_VLC" -> "Harici Oynatıcı aktif (Videolar cihazdaki VLC/MX Player uygulamasında açılır)"
                            else -> "ExoPlayer + FFmpeg aktif (Anında kanal zapping ve düşük gecikme)"
                        },
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "EXOPLAYER" to "ExoPlayer (Media3)",
                            "VLC" to "VLC (LibVLC)",
                            "EXTERNAL_VLC" to "Harici VLC"
                        ).forEach { (engine, label) ->
                            val isSelected = playerEngine == engine
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = {
                                            scope.launch { viewModel.preferences.setPlayerEngine(engine) }
                                            Toast.makeText(context, "Oynatıcı motoru: $label", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // VARSAYILAN SES DİLİ SEÇİMİ
                    Text(text = "Varsayılan Ses Dili", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Çok dilli yayınlarda öncelikli tercih edilecek ses dili", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(listOf("tr" to "Türkçe", "en" to "İngilizce", "de" to "Almanca", "fr" to "Fransızca", "auto" to "Orijinal")) { (langCode, label) ->
                            val isSelected = defaultAudioLang == langCode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier.dpadFocusable(
                                    focusedBorderColor = OpusCyan,
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        scope.launch { viewModel.preferences.setDefaultAudioLang(langCode) }
                                        Toast.makeText(context, "Varsayılan Ses: $label", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // VARSAYILAN ALTYAZI DİLİ SEÇİMİ
                    Text(text = "Varsayılan Altyazı Dili", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Gömülü altyazılarda öncelikli otomatik açılacak altyazı dili", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(listOf("none" to "Kapalı", "tr" to "Türkçe", "en" to "İngilizce", "de" to "Almanca", "fr" to "Fransızca", "auto" to "Otomatik")) { (langCode, label) ->
                            val isSelected = defaultSubtitleLang == langCode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier.dpadFocusable(
                                    focusedBorderColor = OpusCyan,
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        scope.launch { viewModel.preferences.setDefaultSubtitleLang(langCode) }
                                        Toast.makeText(context, "Varsayılan Altyazı: $label", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    Text(text = "Video Çözücü (Decoder)", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (decoderMode == "HARDWARE") "Donanım hızlandırma aktif (GPU gücü kullanılır, akıcı oynatım)"
                        else "Yazılım çözücü aktif (Eski cihazlarda ve özel kodeklerde donmayı önler)",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("HARDWARE" to "Donanım (GPU) [Önerilen]", "SOFTWARE" to "Yazılım (CPU)").forEach { (mode, label) ->
                            val isSelected = decoderMode == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = {
                                            viewModel.setDecoderMode(mode)
                                            Toast.makeText(context, if (mode == "HARDWARE") "Donanım hızlandırma (GPU) seçildi" else "Yazılım çözücü seçildi", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Canlı TV Tampon Süresi
                    Text(text = "Canlı TV Tampon Süresi (Buffer)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Zayıf internette takılmayı önlemek için tampon süresini artırın.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(2 to "2s (Düşük Gecikme)", 5 to "5s (Önerilen)", 10 to "10s (Kararlı)").forEach { (secs, label) ->
                            val isSelected = liveBufferSeconds == secs
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = {
                                            viewModel.setLiveBufferSeconds(secs)
                                            Toast.makeText(context, "Canlı tampon: $secs sn", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Film & Dizi Tampon Süresi
                    Text(text = "Film & Dizi Tamponu (VOD Buffer)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "İleri/Geri sararken takılmaması için önbellek: ${vodBufferSeconds}s", color = OpusCyan, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(listOf(30, 60, 90, 120, 180)) { secs ->
                            val isSelected = vodBufferSeconds == secs
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier.dpadFocusable(
                                    focusedBorderColor = OpusCyan,
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        viewModel.setVodBufferSeconds(secs)
                                        Toast.makeText(context, "VOD tampon: $secs sn", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            ) {
                                Text(
                                    text = if (secs == 60) "60s (Önerilen)" else "${secs}s",
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Otomatik PiP & Hızlı Zapping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Arka Planda Küçük Pencere (PiP)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = if (autoPipEnabled) "Uygulama arka plana atıldığında otomatik küçük pencereye geçer" else "PiP özelliği kapalı", color = TiviTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoPipEnabled,
                            onCheckedChange = { checked -> viewModel.setAutoPip(checked) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OpusCyan, checkedTrackColor = OpusCyan.copy(alpha = 0.35f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Hızlı Kanal Açılışı", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = if (fastZapping) "0.8s ultra hızlı kanal geçişi aktif" else "Standart tamponlama aktif", color = TiviTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = fastZapping,
                            onCheckedChange = { checked -> scope.launch { viewModel.preferences.setFastZapping(checked) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = OpusCyan, checkedTrackColor = OpusCyan.copy(alpha = 0.35f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ekran Oranı
                    Text(text = "Varsayılan Ekran Oranı", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Oynatıcı açıldığında videonun ekrana nasıl sığdırılacağını belirler", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("FIT" to "Fit (Orijinal)", "FILL" to "Doldur", "ZOOM" to "Yakınlaştır", "16:9" to "16:9").forEach { (mode, label) ->
                            val isSelected = defaultAspectRatio == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = {
                                            scope.launch {
                                                viewModel.preferences.setAspectRatio(mode)
                                                val enumMode = when (mode) {
                                                    "FILL" -> AspectRatioMode.FILL
                                                    "ZOOM" -> AspectRatioMode.ZOOM
                                                    "16:9" -> AspectRatioMode.RATIO_16_9
                                                    else -> AspectRatioMode.FIT
                                                }
                                                viewModel.playerManager.setAspectRatio(enumMode)
                                            }
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. GÖRÜNÜM VE ALTYAZI
        // ==========================================
        SettingsSection.APPEARANCE -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Tema Seçimi
                    val appTheme by viewModel.appTheme.collectAsState()
                    Text(text = "Uygulama Teması", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Arayüz renk paletini kişiselleştirin", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "MIDNIGHT" to "Gece Mavisi",
                            "OCEAN" to "Okyanus",
                            "FOREST" to "Zümrüt",
                            "VIOLET" to "Ametist"
                        ).forEach { (key, label) ->
                            val isSelected = appTheme == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = { scope.launch { viewModel.preferences.setAppTheme(key) } }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    Text(text = "Altyazı Boyutu", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Desteklenen filmlerde altyazıların görünüm punto boyutu", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentSpInt = when (subtitleSize) {
                        "KUCUK" -> 14
                        "ORTA" -> 18
                        "BUYUK" -> 22
                        "COK_BUYUK" -> 26
                        else -> subtitleSize.toIntOrNull() ?: 18
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TiviSurfaceVariant),
                        border = BorderStroke(1.dp, TiviCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // "-" BUTONU (KÜÇÜLT)
                                Surface(
                                    onClick = {
                                        val newSp = (currentSpInt - 2).coerceAtLeast(12)
                                        scope.launch { viewModel.preferences.setSubtitleSize(newSp.toString()) }
                                    },
                                    enabled = currentSpInt > 12,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (currentSpInt > 12) TiviCard else TiviCardBorder,
                                    border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = OpusCyan,
                                            shape = RoundedCornerShape(10.dp),
                                            onClick = {
                                                val newSp = (currentSpInt - 2).coerceAtLeast(12)
                                                scope.launch { viewModel.preferences.setSubtitleSize(newSp.toString()) }
                                            }
                                        )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Küçült",
                                            tint = if (currentSpInt > 12) OpusCyan else TiviTextMuted,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // ORTA: PUNTO GOSTERGECI & CANLI ÖNİZLEME
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$currentSpInt SP Punto",
                                        color = OpusCyan,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Örnek Altyazı Metni",
                                        color = when (subtitleColor) {
                                            "SARI" -> Color(0xFFFFD700)
                                            "YESIL" -> Color(0xFF00E676)
                                            "MAVI" -> Color(0xFF40C4FF)
                                            else -> Color.White
                                        },
                                        fontSize = (currentSpInt * 0.8f).sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }

                                // "+" BUTONU (BÜYÜT)
                                Surface(
                                    onClick = {
                                        val newSp = (currentSpInt + 2).coerceAtMost(36)
                                        scope.launch { viewModel.preferences.setSubtitleSize(newSp.toString()) }
                                    },
                                    enabled = currentSpInt < 36,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (currentSpInt < 36) TiviCard else TiviCardBorder,
                                    border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = OpusCyan,
                                            shape = RoundedCornerShape(10.dp),
                                            onClick = {
                                                val newSp = (currentSpInt + 2).coerceAtMost(36)
                                                scope.launch { viewModel.preferences.setSubtitleSize(newSp.toString()) }
                                            }
                                        )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Büyüt",
                                            tint = if (currentSpInt < 36) OpusCyan else TiviTextMuted,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Altyazı Rengi
                    Text(text = "Altyazı Metin Rengi", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Desteklenen filmlerde altyazıların görünüm rengi", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "BEYAZ" to "Beyaz",
                            "SARI" to "Sarı",
                            "YESIL" to "Yeşil",
                            "MAVI" to "Açık Mavi"
                        ).forEach { (key, label) ->
                            val isSelected = subtitleColor == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = { scope.launch { viewModel.preferences.setSubtitleColor(key) } }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Kategori Gizleme / Düzenleme Yönetimi
                    Text(text = "Kategori Gizleme & Sıralama Yönetimi", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Canlı TV, Film ve Dizi kategorilerinden istemediklerinizi gizleyin veya sık izlediklerinizi en üste sabitleyin.",
                        color = TiviTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onOpenCategoryDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                onClick = onOpenCategoryDialog
                            )
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Kategorileri Düzenle / Gizle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = TiviCardBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Cihaz Modu
                    val currentDeviceMode by viewModel.deviceMode.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = "Cihaz Modu", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Mevcut: ${currentDeviceMode.badgeText}",
                                color = OpusCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentDeviceMode.description,
                                color = TiviTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = onOpenDeviceModeDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(10.dp),
                                onClick = onOpenDeviceModeDialog
                            )
                        ) {
                            Text("Değiştir", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. CİHAZ MODU VE EKRAN YÖNÜ
        // ==========================================
        SettingsSection.DEVICE_MODE -> {
            val activeDeviceMode by viewModel.deviceMode.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Üst Başlık & Açıklama
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OpusCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Cihaz Modu ve Yön Yönetimi", color = TiviTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Arayüzü ve ekran yönünü cihazınıza göre özelleştirin", color = TiviTextSecondary, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Aktif Mod Bilgi Kartı
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OpusCyan.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aktif Seçim: ${activeDeviceMode.badgeText}",
                                    color = OpusCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeDeviceMode.description,
                                    color = TiviTextPrimary.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = OpusCyan,
                                contentColor = Color.Black
                            ) {
                                Text(
                                    text = "AKTİF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Kullanmak İstediğiniz Cihaz Düzenini Seçin:",
                        color = TiviTextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 3 Cihaz Seçim Kartı
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeviceMode.values().forEach { mode ->
                            val isSelected = activeDeviceMode == mode
                            val icon = when (mode) {
                                DeviceMode.PHONE -> Icons.Default.PhoneAndroid
                                DeviceMode.TABLET -> Icons.Default.TabletAndroid
                                DeviceMode.TV -> Icons.Default.Tv
                            }
                            val modeBehaviorSummary = when (mode) {
                                DeviceMode.PHONE -> "Uygulama dikey kilitli; oynatıcı açılınca otomatik yatay tam ekran (immersive sticky); oynatıcı içinde tek dokunuşla döndürme butonu."
                                DeviceMode.TABLET -> "Ekran yönü serbest sensör (360° döndürme); master-detail iki sütunlu geniş ekran düzeni."
                                DeviceMode.TV -> "Uygulama genelinde yatay kilitli; tam 10-foot UI ve D-pad kumanda odağı."
                            }

                            Surface(
                                onClick = {
                                    viewModel.setDeviceMode(mode)
                                    Toast.makeText(context, "Cihaz modu güncellendi: ${mode.badgeText}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.14f) else TiviSurfaceVariant,
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        shape = RoundedCornerShape(14.dp),
                                        focusedBorderColor = OpusCyan,
                                        onClick = {
                                            viewModel.setDeviceMode(mode)
                                            Toast.makeText(context, "Cihaz modu güncellendi: ${mode.badgeText}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) OpusCyan.copy(alpha = 0.25f) else TiviCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) OpusCyan else Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = mode.badgeText,
                                                color = if (isSelected) OpusCyan else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = OpusCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = mode.description,
                                            color = TiviTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = modeBehaviorSummary,
                                            color = TiviTextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = TiviCardBorder)

                    // Cihaz Seçimini Sıfırla (İlk Açılış Sihirbazını Yeniden Tetikleme)
                    Text(text = "Cihaz Seçimini Sıfırla", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uygulama açılışındaki 'Cihazını Seç' ekranını tekrar görüntülemek ve cihaz tespitini baştan çalıştırmak için sıfırlayabilirsiniz.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.resetDeviceMode()
                            Toast.makeText(context, "Cihaz seçimi sıfırlandı. İlk kurulum ekranına yönlendiriliyorsunuz...", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TiviAccentLive),
                        border = BorderStroke(1.2.dp, TiviAccentLive.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                onClick = {
                                    viewModel.resetDeviceMode()
                                    Toast.makeText(context, "Cihaz seçimi sıfırlandı. İlk kurulum ekranına yönlendiriliyorsunuz...", Toast.LENGTH_LONG).show()
                                }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = TiviAccentLive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Cihaz Seçimini Sıfırla ve İlk Kuruluma Git", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ==========================================
        // 4. EBEVEYN KONTROLÜ
        // ==========================================
        SettingsSection.PARENTAL -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TiviGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = TiviGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Ebeveyn Kilidi (+18 Koruma)", color = TiviTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(text = "+18 yetişkin kanallar ve içerikler 4 haneli PIN ile kilitlenir", color = TiviTextSecondary, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    pinSuccessMessage?.let { msg ->
                        Text(text = msg, color = TiviGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Button(
                        onClick = onOpenPinDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                onClick = onOpenPinDialog
                            )
                    ) {
                        Icon(imageVector = Icons.Default.Password, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "4 Haneli Ebeveyn PIN Kodunu Değiştir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Güvenli Bağlantı (HTTPS)
                    val isHttps = serverUrl.startsWith("https://", ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Güvenli Bağlantı (HTTPS)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = if (isHttps) "Şifreli HTTPS bağlantısı aktif" else "Standart HTTP bağlantısı aktif", color = if (isHttps) TiviGreen else TiviGold, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isHttps,
                            onCheckedChange = { checked ->
                                val newUrl = if (checked) AppConfig.SERVER_URL_HTTPS else AppConfig.SERVER_URL_HTTP
                                scope.launch {
                                    viewModel.preferences.setServerUrl(newUrl)
                                    viewModel.repository.serverUrl = newUrl
                                    Toast.makeText(context, "Bağlantı güncellendi: $newUrl", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = TiviGreen, checkedTrackColor = TiviGreen.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. YEDEKLEME VE UYGULAMA
        // ==========================================
        SettingsSection.BACKUP_APP -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "Ayarları Yedekle ve Geri Yükle", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Tüm arabellek, çözücü ve görünüm ayarlarınızı cihazınıza yedekleyin.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                try {
                                    val backupFile = File(context.filesDir, "vega_settings_backup.json")
                                    val json = JSONObject().apply {
                                        put("timestamp", System.currentTimeMillis())
                                        put("version", "1.0.0")
                                    }
                                    backupFile.writeText(json.toString())
                                    Toast.makeText(context, "Ayarlar ve tercihler başarıyla yedeklendi.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Yedekleme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        val backupFile = File(context.filesDir, "vega_settings_backup.json")
                                        val json = JSONObject().apply {
                                            put("timestamp", System.currentTimeMillis())
                                            put("version", "1.0.0")
                                        }
                                        backupFile.writeText(json.toString())
                                        Toast.makeText(context, "Ayarlar ve tercihler başarıyla yedeklendi.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Yedekle", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val backupFile = File(context.filesDir, "vega_settings_backup.json")
                                if (backupFile.exists()) {
                                    Toast.makeText(context, "Yedeklenen ayarlar başarıyla geri yüklendi.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Kayıtlı yedek bulunamadı. Önce yedekleme yapınız.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TiviCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .dpadFocusable(
                                    focusedBorderColor = OpusCyan,
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        val backupFile = File(context.filesDir, "vega_settings_backup.json")
                                        if (backupFile.exists()) {
                                            Toast.makeText(context, "Yedeklenen ayarlar başarıyla geri yüklendi.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Kayıtlı yedek bulunamadı. Önce yedekleme yapınız.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.Restore, contentDescription = null, tint = TiviTextPrimary, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Geri Yükle", color = TiviTextPrimary, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Sunucu Hız & Ping Testi
                    Text(text = "Sunucu Hız ve Ping Testi", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Yayın donması durumunda sunucu bağlantı hızınızı ve gecikmenizi test edin.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    speedTestResult?.let { result ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TiviSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(text = result, color = OpusCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(10.dp))
                        }
                    }

                    Button(
                        onClick = { viewModel.runSpeedTest() },
                        enabled = !isTestingSpeed,
                        colors = ButtonDefaults.buttonColors(containerColor = TiviSecondary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .dpadFocusable(
                                focusedBorderColor = OpusCyan,
                                shape = RoundedCornerShape(10.dp),
                                onClick = { viewModel.runSpeedTest() }
                            )
                    ) {
                        if (isTestingSpeed) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Ölçülüyor...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Hız ve Ping Testini Başlat", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Uygulama Önbelleğini Temizle
                    OutlinedButton(
                        onClick = {
                            try {
                                context.cacheDir.deleteRecursively()
                                coil.Coil.imageLoader(context).diskCache?.clear()
                                coil.Coil.imageLoader(context).memoryCache?.clear()
                                Toast.makeText(context, "Görsel ve kanal önbelleği temizlendi.", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Önbellek temizlendi.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TiviCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .dpadFocusable(
                                focusedBorderColor = OpusCyan,
                                shape = RoundedCornerShape(10.dp),
                                onClick = {
                                    try {
                                        context.cacheDir.deleteRecursively()
                                        coil.Coil.imageLoader(context).diskCache?.clear()
                                        coil.Coil.imageLoader(context).memoryCache?.clear()
                                        Toast.makeText(context, "Görsel ve kanal önbelleği temizlendi.", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Önbellek temizlendi.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Uygulama Önbelleğini Temizle", color = TiviTextPrimary, fontSize = 12.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Otomatik Güncelleme Kontrolü (Madde 9)
                    val isCheckingUpdate by viewModel.autoUpdateManager.isChecking.collectAsState()

                    Button(
                        onClick = { viewModel.checkForUpdates(isManual = true) },
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(10.dp),
                                onClick = { viewModel.checkForUpdates(isManual = true) }
                            )
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Kontrol Ediliyor...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Güncellemeleri Kontrol Et (GitHub)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sürüm ve Destek
                    Text(text = "Destek ve İletişim", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Uygulama Sürümü:", color = TiviTextSecondary, fontSize = 12.sp)
                        Text(text = "${AppConfig.APP_NAME} v${com.tivimax.player.BuildConfig.VERSION_NAME} Pro", color = TiviTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Telegram Desteği:", color = TiviTextSecondary, fontSize = 12.sp)
                        Text(text = AppConfig.SUPPORT_TELEGRAM, color = OpusCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "WhatsApp Desteği:", color = TiviTextSecondary, fontSize = 12.sp)
                        Text(text = AppConfig.SUPPORT_WHATSAPP, color = TiviGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Cihaz Modu Seçim ve Değiştirme Modal Diyaloğu.
 * Kullanıcının uygulama açıkken hızlıca Telefon / Tablet / TV modları arasında geçiş yapmasını sağlar.
 */
@Composable
fun DeviceModeSelectionDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentMode by viewModel.deviceMode.collectAsState()
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TiviSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = OpusCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cihaz Modunu Değiştir",
                    color = TiviTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Kullanım senaryonuza göre arayüzü ve ekran yönünü anında uyarlayın:",
                    color = TiviTextSecondary,
                    fontSize = 12.5.sp
                )

                DeviceMode.values().forEach { mode ->
                    val isSelected = selectedMode == mode
                    val icon = when (mode) {
                        DeviceMode.PHONE -> Icons.Default.PhoneAndroid
                        DeviceMode.TABLET -> Icons.Default.TabletAndroid
                        DeviceMode.TV -> Icons.Default.Tv
                    }
                    Surface(
                        onClick = { selectedMode = mode },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) OpusCyan.copy(alpha = 0.15f) else TiviCard,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusable(
                                shape = RoundedCornerShape(12.dp),
                                focusedBorderColor = OpusCyan,
                                onClick = { selectedMode = mode }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) OpusCyan.copy(alpha = 0.25f) else TiviSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) OpusCyan else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.badgeText,
                                    color = if (isSelected) OpusCyan else Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.description,
                                    color = TiviTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = OpusCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                // Cihaz seçimini sıfırla seçeneği
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.resetDeviceMode()
                            Toast.makeText(context, "Cihaz seçimi sıfırlandı", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = TiviAccentLive,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cihaz seçimini sıfırla (İlk açılış sihirbazı)",
                        color = TiviAccentLive,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.setDeviceMode(selectedMode)
                    Toast.makeText(context, "Cihaz modu güncellendi: ${selectedMode.badgeText}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Uygula", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = TiviTextSecondary)
            }
        }
    )
}

