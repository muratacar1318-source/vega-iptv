package com.tivimax.player.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
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
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import kotlinx.coroutines.launch
import com.tivimax.player.util.DeviceMode

enum class SettingsSection(val title: String, val icon: ImageVector, val subtitle: String) {
    PLAYLISTS("Listelerim", Icons.AutoMirrored.Filled.PlaylistPlay, "Oynatma listelerini ekle ve aralarında geç"),
    ACCOUNT("Hesabım", Icons.Default.AccountCircle, "Abonelik bilgileri ve içerik listesi"),
    PLAYER("Oynatıcı", Icons.Default.Tune, "Ses ve altyazı dili, görüntü ve akıcılık"),
    APPEARANCE("Görünüm", Icons.Default.Subtitles, "Tema, kanal listesi ve altyazı görünümü"),
    DEVICE_MODE("Cihaz Türü", Icons.Default.Tv, "Telefon, tablet veya TV düzeni"),
    PARENTAL("Ebeveyn Kilidi", Icons.Default.Security, "Yetişkin içerikleri şifreyle kilitle"),
    BACKUP_APP("Güncelleme ve Bakım", Icons.Default.Backup, "Güncellemeler, bağlantı testi ve sürüm bilgisi")
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val userInfo by viewModel.userInfo.collectAsState()
    val activePlaylist by viewModel.activePlaylist.collectAsState()
    val targetSectionStr by viewModel.targetSettingsSection.collectAsState()
    val speedTestResult by viewModel.speedTestResult.collectAsState()
    val isTestingSpeed by viewModel.isTestingSpeed.collectAsState()

    val defaultAspectRatio by viewModel.preferences.aspectRatioFlow.collectAsState(initial = "FIT")
    val serverUrl by viewModel.preferences.serverUrlFlow.collectAsState(initial = AppConfig.DEFAULT_SERVER_URL)
    val autoPipEnabled by viewModel.autoPipEnabled.collectAsState()
    val vodBufferSeconds by viewModel.vodBufferSeconds.collectAsState()
    val liveBufferSeconds by viewModel.liveBufferSeconds.collectAsState()
    val decoderMode by viewModel.decoderMode.collectAsState()
    val lastSync by viewModel.preferences.lastSyncTimestampFlow.collectAsState(initial = "")
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSection by remember { mutableStateOf(SettingsSection.PLAYLISTS) }
    var activeSubPage by remember { mutableStateOf<SettingsSection?>(null) }

    LaunchedEffect(targetSectionStr) {
        if (targetSectionStr == "PLAYLISTS") {
            selectedSection = SettingsSection.PLAYLISTS
            activeSubPage = SettingsSection.PLAYLISTS
            viewModel.targetSettingsSection.value = null
        }
    }
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
            .background(VegaColors.Background)
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
                        .background(VegaColors.Surface)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Ayarlar", color = VegaColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsSection.values().forEach { section ->
                        val isSelected = selectedSection == section
                        Surface(
                            onClick = { selectedSection = section },
                            shape = VegaShapes.Medium,
                            color = if (isSelected) VegaColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.6f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .dpadFocusable(
                                    focusedBorderColor = VegaColors.Accent,
                                    shape = VegaShapes.Medium,
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
                                    tint = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = section.title,
                                        color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = section.subtitle,
                                        color = if (isSelected) VegaColors.Accent.copy(alpha = 0.8f) else VegaColors.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // AYIRAÇ ÇİZGİSİ
                VerticalDivider(color = VegaColors.Border, thickness = 1.dp)

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
                        liveBufferSeconds = liveBufferSeconds,
                        vodBufferSeconds = vodBufferSeconds,
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
            BackHandler(enabled = activeSubPage != null) {
                activeSubPage = null
            }

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
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Ayarlar", color = VegaColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }

                    SettingsSection.values().forEach { section ->
                        Card(
                            onClick = { activeSubPage = section },
                            shape = VegaShapes.Medium,
                            colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(
                                    shape = VegaShapes.Medium,
                                    focusedBorderColor = VegaColors.Accent,
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
                                            .clip(VegaShapes.Small)
                                            .background(VegaColors.Accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = section.icon,
                                            contentDescription = null,
                                            tint = VegaColors.Accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = section.title,
                                            color = VegaColors.TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = section.subtitle,
                                            color = VegaColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = VegaColors.TextMuted,
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
                        shape = VegaShapes.Medium,
                        color = VegaColors.Card,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.Accent,
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
                                tint = VegaColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = activeSubPage!!.title,
                                color = VegaColors.TextPrimary,
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
                            liveBufferSeconds = liveBufferSeconds,
                            vodBufferSeconds = vodBufferSeconds,
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
            title = { Text("Yeni şifre", color = TiviTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "4 haneli yeni şifreyi gir.",
                        color = TiviTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4) newPinInput = it },
                        placeholder = { Text("••••") },
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
                                pinSuccessMessage = "Şifre güncellendi."
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
    liveBufferSeconds: Int,
    vodBufferSeconds: Int,
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
        // 0. LİSTELERİM
        // ==========================================
        SettingsSection.PLAYLISTS -> {
            SettingsPlaylistsSection(viewModel = viewModel)
        }

        // ==========================================
        // 1. HESAP VE OYNATMA LİSTESİ
        // ==========================================
        SettingsSection.ACCOUNT -> {
            val contentCounts by viewModel.accountContentCounts.collectAsState()
            val isRefreshing by viewModel.accountRefreshing.collectAsState()
            val activePlaylist by viewModel.activePlaylist.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.loadAccountContentCounts()
                viewModel.refreshAccountInfo(silent = true)
            }

            SettingsAccountSection(
                userInfo = userInfo,
                lastSync = lastSync,
                contentCounts = contentCounts,
                isRefreshing = isRefreshing,
                onRefreshClick = {
                    viewModel.refreshAccountInfo(silent = false) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onSyncClick = { viewModel.refreshPlaylistContent() },
                onLogoutClick = { viewModel.logout() },
                activePlaylistName = activePlaylist?.name ?: "Ana liste"
            )
        }

        // ==========================================
        // 2. OYNATICI
        // ==========================================
        SettingsSection.PLAYER -> {
            val defaultAudioLang by viewModel.defaultAudioLang.collectAsState()
            val defaultSubtitleLang by viewModel.defaultSubtitleLang.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Tercih Edilen Ses Dili
                    Text(text = "Tercih edilen ses dili", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Birden fazla dili olan yayınlarda önce bu dil açılır.", color = TiviTextSecondary, fontSize = 11.sp)
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
                                        Toast.makeText(context, "Tercih edilen ses dili: $label", Toast.LENGTH_SHORT).show()
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

                    // Tercih Edilen Altyazı Dili
                    Text(text = "Tercih edilen altyazı dili", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Altyazısı olan içeriklerde kendiliğinden açılacak dil.", color = TiviTextSecondary, fontSize = 11.sp)
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
                                        Toast.makeText(context, "Tercih edilen altyazı dili: $label", Toast.LENGTH_SHORT).show()
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

                    // Görüntü İşleme
                    Text(text = "Görüntü işleme", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Görüntü takılıyor, renkler bozuk ya da ekran siyah kalıyorsa 'Uyumlu'yu dene.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("HARDWARE" to "Hızlı (önerilen)", "SOFTWARE" to "Uyumlu").forEach { (mode, label) ->
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
                                            val toastVal = if (mode == "HARDWARE") "Hızlı" else "Uyumlu"
                                            Toast.makeText(context, "Görüntü işleme: $toastVal", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // G-043: Canlı yayın biçimi (Standart = TS, HLS = düşük gecikme denemesi)
                    val streamFormat by viewModel.streamFormat.collectAsState()
                    Text(text = "Canlı yayın biçimi", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Yayın canlıdan geride kalıyorsa 'HLS'yi dene. Kanal açılmazsa ya da takılırsa 'Standart'a dön. Değişiklik bir sonraki açtığın kanalda geçerli olur.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("TS" to "Standart (önerilen)", "HLS" to "HLS (düşük gecikme)").forEach { (format, label) ->
                             val isSelected = streamFormat == format
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
                                            viewModel.setStreamFormat(format)
                                            val toastVal = if (format == "HLS") "HLS" else "Standart"
                                            Toast.makeText(context, "Canlı yayın biçimi: $toastVal", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Canlı Yayın Ön Yükleme
                    Text(text = "Canlı yayın ön yükleme", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "İnternetin zayıfsa artır: kanal biraz geç açılır ama daha az takılır.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            2 to "2 sn (hızlı açılış)",
                            5 to "5 sn (önerilen)",
                            10 to "10 sn (en az takılma)"
                        ).forEach { (secs, label) ->
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
                                            Toast.makeText(context, "Canlı yayın ön yükleme: $secs sn", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Film ve Dizi Ön Yükleme
                    Text(text = "Film ve dizi ön yükleme", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Takılma oluyorsa artır.", color = TiviTextSecondary, fontSize = 11.sp)
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
                                        Toast.makeText(context, "Film ve dizi ön yükleme: $secs sn", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            ) {
                                Text(
                                    text = if (secs == 60) "60 sn (önerilen)" else "$secs sn",
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Küçük Pencerede Devam Et (PiP)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Küçük pencerede devam et", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (autoPipEnabled) "Uygulamadan çıkınca canlı yayın köşede küçük pencerede oynamaya devam eder. Film ve diziler duraklatılır." else "Kapalı.",
                                color = TiviTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = autoPipEnabled,
                            onCheckedChange = { checked -> viewModel.setAutoPip(checked) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OpusCyan, checkedTrackColor = OpusCyan.copy(alpha = 0.35f))
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Görüntü Boyutu
                    Text(text = "Görüntü boyutu", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Video ekrana nasıl sığdırılsın.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("FIT" to "Orijinal", "FILL" to "Doldur", "ZOOM" to "Yakınlaştır", "16:9" to "16:9").forEach { (mode, label) ->
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
                                                Toast.makeText(context, "Görüntü boyutu: $label", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. GÖRÜNÜM
        // ==========================================
        SettingsSection.APPEARANCE -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Canlı TV Görünümü
                    val liveLayoutMode by viewModel.liveLayoutMode.collectAsState()
                    Text(text = "Canlı TV görünümü", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Canlı yayın ekranının görünüm düzeni.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Raflar
                        val isShelfSelected = liveLayoutMode == UserPreferences.LIVE_LAYOUT_SHELF
                        Surface(
                            onClick = {
                                scope.launch {
                                    viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF)
                                    Toast.makeText(context, "Canlı TV görünümü: Raflar", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isShelfSelected) OpusCyan.copy(alpha = 0.16f) else TiviSurfaceVariant,
                            border = BorderStroke(1.5.dp, if (isShelfSelected) OpusCyan else TiviCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .dpadFocusable(
                                    focusedBorderColor = OpusCyan,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = {
                                        scope.launch {
                                            viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF)
                                            Toast.makeText(context, "Canlı TV görünümü: Raflar", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewCarousel,
                                        contentDescription = null,
                                        tint = if (isShelfSelected) OpusCyan else TiviTextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    RadioButton(
                                        selected = isShelfSelected,
                                        onClick = {
                                            scope.launch {
                                                viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF)
                                                Toast.makeText(context, "Canlı TV görünümü: Raflar", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = OpusCyan)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Raflar",
                                    color = if (isShelfSelected) Color.White else TiviTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Kanallar kategorilere göre yan yana kaydırılan satırlarda",
                                    color = if (isShelfSelected) OpusCyan.copy(alpha = 0.9f) else TiviTextSecondary,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // 2. Liste
                        val isClassicSelected = liveLayoutMode == UserPreferences.LIVE_LAYOUT_CLASSIC
                        Surface(
                            onClick = {
                                scope.launch {
                                    viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_CLASSIC)
                                    Toast.makeText(context, "Canlı TV görünümü: Liste", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isClassicSelected) OpusCyan.copy(alpha = 0.16f) else TiviSurfaceVariant,
                            border = BorderStroke(1.5.dp, if (isClassicSelected) OpusCyan else TiviCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .dpadFocusable(
                                    focusedBorderColor = OpusCyan,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = {
                                        scope.launch {
                                            viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_CLASSIC)
                                            Toast.makeText(context, "Canlı TV görünümü: Liste", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                                        contentDescription = null,
                                        tint = if (isClassicSelected) OpusCyan else TiviTextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    RadioButton(
                                        selected = isClassicSelected,
                                        onClick = {
                                            scope.launch {
                                                viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_CLASSIC)
                                                Toast.makeText(context, "Canlı TV görünümü: Liste", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = OpusCyan)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Liste",
                                    color = if (isClassicSelected) Color.White else TiviTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Kategoriler üstte, kanallar altta liste halinde",
                                    color = if (isClassicSelected) OpusCyan.copy(alpha = 0.9f) else TiviTextSecondary,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Film ve Dizi Kategorileri (Telefon)
                    val portraitNavStyle by viewModel.portraitCategoryNavStyle.collectAsState()
                    Text(text = "Film ve dizi kategorileri (telefon)", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Telefonda film ve dizi kategorilerinin nasıl gösterileceği.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(UserPreferences.PORTRAIT_CAT_NAV_CHIPS, "Üstte kaydırmalı", "Kategoriler üstte, afişler altta"),
                            Triple(UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR, "Yan panel", "Kategoriler solda liste halinde"),
                            Triple(UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN, "Açılır menü", "Kategoriler tek bir menüde, ekranda daha çok yer kalır")
                        ).forEach { (style, label, desc) ->
                            val isSelected = portraitNavStyle == style
                            Surface(
                                onClick = {
                                    viewModel.setPortraitCategoryNavStyle(style)
                                    Toast.makeText(context, "Kategori görünümü: $label", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.16f) else TiviSurfaceVariant,
                                border = BorderStroke(1.5.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(12.dp),
                                        onClick = {
                                            viewModel.setPortraitCategoryNavStyle(style)
                                            Toast.makeText(context, "Kategori görünümü: $label", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = when (style) {
                                                UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR -> Icons.AutoMirrored.Filled.ViewSidebar
                                                UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN -> Icons.Default.ExpandCircleDown
                                                else -> Icons.AutoMirrored.Filled.ViewList
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) OpusCyan else TiviTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setPortraitCategoryNavStyle(style)
                                                Toast.makeText(context, "Kategori görünümü: $label", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = OpusCyan),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TiviTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        color = if (isSelected) OpusCyan.copy(alpha = 0.9f) else TiviTextSecondary,
                                        fontSize = 9.5.sp,
                                        lineHeight = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Tema Seçimi
                    val appTheme by viewModel.appTheme.collectAsState()
                    Text(text = "Renk teması", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Uygulamanın renklerini seç.", color = TiviTextSecondary, fontSize = 11.sp)
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
                                        onClick = {
                                            scope.launch { viewModel.preferences.setAppTheme(key) }
                                            Toast.makeText(context, "Renk teması: $label", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch { viewModel.preferences.setAppTheme(key) }
                                            Toast.makeText(context, "Renk teması: $label", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) OpusCyan else TiviTextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Altyazı Boyutu
                    Text(text = "Altyazı boyutu", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Tüm içeriklerde altyazıların büyüklüğü.", color = TiviTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentNorm = when (subtitleSize) {
                        "KUCUK" -> "14"
                        "ORTA" -> "18"
                        "BUYUK" -> "22"
                        "COK_BUYUK" -> "26"
                        else -> subtitleSize
                    }
                    val previewSp = currentNorm.toFloatOrNull() ?: 18f

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TiviSurfaceVariant),
                        border = BorderStroke(1.dp, TiviCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Örnek altyazı",
                                color = when (subtitleColor) {
                                    "SARI" -> Color(0xFFFFD700)
                                    "YESIL" -> Color(0xFF00E676)
                                    "MAVI" -> Color(0xFF40C4FF)
                                    else -> Color.White
                                },
                                fontSize = (previewSp * 0.85f).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val sizeOptions = listOf(
                        "14" to "Küçük (14)",
                        "18" to "Orta (18)",
                        "22" to "Büyük (22)",
                        "26" to "Çok büyük (26)"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sizeOptions.forEach { (sizeVal, label) ->
                            val isSelected = currentNorm == sizeVal
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
                                            viewModel.setSubtitleSize(sizeVal)
                                            Toast.makeText(context, "Altyazı boyutu: $label", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setSubtitleSize(sizeVal)
                                            Toast.makeText(context, "Altyazı boyutu: $label", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) OpusCyan else TiviTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Altyazı Rengi
                    Text(text = "Altyazı rengi", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Altyazıların ekrandaki rengi.", color = TiviTextSecondary, fontSize = 11.sp)
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
                                        onClick = {
                                            scope.launch { viewModel.preferences.setSubtitleColor(key) }
                                            Toast.makeText(context, "Altyazı rengi: $label", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch { viewModel.preferences.setSubtitleColor(key) }
                                            Toast.makeText(context, "Altyazı rengi: $label", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) OpusCyan else TiviTextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // Kategorileri Düzenle
                    Text(text = "Kategorileri düzenle", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "İstemediğin kategorileri gizle, sık izlediklerini en üste al.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
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
                        Text(text = "Kategorileri düzenle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ==========================================
        // 4. CİHAZ TÜRÜ
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
                            Text(text = "Cihaz türü", color = TiviTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Uygulamanın düzenini cihazına göre ayarla.", color = TiviTextSecondary, fontSize = 11.sp)
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
                                    text = "Şu an: ${activeDeviceMode.badgeText}",
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
                                    text = "Aktif",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Bir düzen seç:",
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
                                    Toast.makeText(context, "Cihaz türü: ${mode.badgeText}", Toast.LENGTH_SHORT).show()
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
                                            Toast.makeText(context, "Cihaz türü: ${mode.badgeText}", Toast.LENGTH_SHORT).show()
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

                    // İlk Kurulum Ekranını Tekrar Göster
                    Text(text = "İlk kurulum ekranını tekrar göster", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uygulama bir sonraki açılışta cihaz türünü yeniden sorar.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.resetDeviceMode()
                            Toast.makeText(context, "İlk kurulum ekranı sıfırlandı.", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "İlk kurulum ekranı sıfırlandı.", Toast.LENGTH_SHORT).show()
                                }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = TiviAccentLive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Sıfırla", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ==========================================
        // 5. EBEVEYN KİLİDİ
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
                            Text(text = "Ebeveyn kilidi", color = TiviTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Yetişkin kategorilerini açmak için 4 haneli şifre istenir.", color = TiviTextSecondary, fontSize = 11.sp)
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
                            text = "Şifreyi değiştir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ==========================================
        // 6. GÜNCELLEME VE BAKIM
        // ==========================================
        SettingsSection.BACKUP_APP -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = TiviCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // 1. Güncellemeleri Kontrol Et
                    val isCheckingUpdate by viewModel.autoUpdateManager.isChecking.collectAsState()

                    Button(
                        onClick = { viewModel.checkForUpdates(isManual = true) },
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(10.dp),
                                onClick = { viewModel.checkForUpdates(isManual = true) }
                            )
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Kontrol ediliyor…", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Güncellemeleri kontrol et", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // 2. Bağlantı Testi
                    Text(text = "Bağlantı testi", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Yayınlar takılıyorsa bağlantını kontrol et.", color = TiviTextSecondary, fontSize = 11.sp)
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
                            Text(text = "Ölçülüyor…", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Testi başlat", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // 3. Önbelleği Temizle
                    Text(text = "Önbelleği temizle", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Kanal logoları ve resimler yeniden yüklenir. Listelerin silinmez.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                context.cacheDir.deleteRecursively()
                                coil.Coil.imageLoader(context).diskCache?.clear()
                                coil.Coil.imageLoader(context).memoryCache?.clear()
                                Toast.makeText(context, "Önbellek temizlendi", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Önbellek temizlendi", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "Önbellek temizlendi", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Önbellek temizlendi", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Önbelleği temizle", color = TiviTextPrimary, fontSize = 12.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                    // 5. Hakkında
                    Text(text = "Hakkında", color = TiviTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Sürüm: ${AppConfig.APP_NAME} ${com.tivimax.player.BuildConfig.VERSION_NAME}", color = TiviTextSecondary, fontSize = 12.sp)
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
        containerColor = VegaColors.Surface,
        shape = VegaShapes.Large,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cihaz türünü değiştir",
                    color = VegaColors.TextPrimary,
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
                    text = "Uygulamayı hangi cihazda kullanıyorsun?",
                    color = VegaColors.TextSecondary,
                    fontSize = 13.sp
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
                        shape = VegaShapes.Medium,
                        color = if (isSelected) VegaColors.Accent.copy(alpha = 0.15f) else VegaColors.Card,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) VegaColors.Accent else VegaColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = VegaColors.Accent,
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
                                    .clip(VegaShapes.Small)
                                    .background(if (isSelected) VegaColors.Accent.copy(alpha = 0.25f) else VegaColors.SurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) VegaColors.Accent else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.badgeText,
                                    color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.description,
                                    color = VegaColors.TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
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
                            Toast.makeText(context, "İlk kurulum ekranı sıfırlandı.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = VegaColors.Danger,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "İlk kurulum ekranını tekrar göster",
                        color = VegaColors.Danger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.setDeviceMode(selectedMode)
                    Toast.makeText(context, "Cihaz türü: ${selectedMode.badgeText}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                shape = VegaShapes.Medium
            ) {
                Text("Uygula", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = VegaColors.TextSecondary)
            }
        }
    )
}

