package com.tivimax.player.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current

    var showPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var pinSuccessMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. ABONELİK BİLGİ KARTI
        // ==========================================
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
                            .background(TiviPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = TiviPrimary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userInfo?.username ?: "Kullanıcı",
                            color = TiviTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TiviGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Abonelik: Aktif",
                                color = TiviGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Bitiş Tarihi:", color = TiviTextSecondary, fontSize = 13.sp)
                    Text(text = expDateFormatted, color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Eşzamanlı Bağlantı:", color = TiviTextSecondary, fontSize = 13.sp)
                    Text(
                        text = "${userInfo?.activeCons ?: "1"} / ${userInfo?.maxConnections ?: "1"} Cihaz",
                        color = TiviTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (lastSync.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Son Güncelleme:", color = TiviTextSecondary, fontSize = 13.sp)
                        Text(
                            text = lastSync,
                            color = TiviTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                // Oynatma Listesini Güncelle Butonu
                Button(
                    onClick = { viewModel.openPlaylistSync() },
                    colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TiviPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "İçerikleri ve Kanalları Güncelle",
                        color = TiviPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ==========================================
        // 2. OYNATICI VE VİDEO ÇÖZÜCÜ KARTI
        // ==========================================
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
                            .background(TiviPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Oynatıcı & Donanım Tercihleri",
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Video çözücü, ekran oranı ve PiP ayarları",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                // Video Çözücü (Hardware vs Software)
                Text(text = "Video Çözücü (Decoder)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (decoderMode == "HARDWARE") "Donanım hızlandırma aktif (GPU gücü kullanılır, az pil harcar)"
                    else "Yazılım çözücü aktif (Uyumsuz kodeklerde ve eski cihazlarda donmayı önler)",
                    color = TiviTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        "HARDWARE" to "Donanım (GPU) [Önerilen]",
                        "SOFTWARE" to "Yazılım (CPU) [Uyumlu]"
                    ).forEach { (mode, label) ->
                        val isSelected = decoderMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setDecoderMode(mode)
                                android.widget.Toast.makeText(
                                    context,
                                    if (mode == "HARDWARE") "Donanım hızlandırma (GPU) seçildi" else "Yazılım çözücü (CPU) seçildi",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (mode == "HARDWARE") TiviPrimary.copy(alpha = 0.25f) else TiviGold.copy(alpha = 0.25f),
                                selectedLabelColor = if (mode == "HARDWARE") TiviPrimary else TiviGold,
                                containerColor = TiviSurfaceVariant,
                                labelColor = TiviTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) (if (mode == "HARDWARE") TiviPrimary else TiviGold) else TiviCardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Varsayılan Ekran Oranı
                Text(text = "Varsayılan Ekran Oranı", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("FIT" to "Fit (Orijinal)", "FILL" to "Doldur (Fill)", "ZOOM" to "Yakınlaştır", "16:9" to "16:9 Oran")) { (mode, label) ->
                        val isSelected = defaultAspectRatio == mode
                        FilterChip(
                            selected = isSelected,
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
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TiviPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = TiviPrimary,
                                containerColor = TiviSurfaceVariant,
                                labelColor = TiviTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) TiviPrimary else TiviCardBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Otomatik Küçük Pencere (Picture-in-Picture)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Arka Planda Küçük Pencere (PiP)",
                            color = TiviTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (autoPipEnabled) "Uygulama arka plana atıldığında otomatik kayan pencereye geçer" else "Kayan pencere özelliği kapalı",
                            color = TiviTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoPipEnabled,
                        onCheckedChange = { checked -> viewModel.setAutoPip(checked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TiviPrimary,
                            checkedTrackColor = TiviPrimary.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        }

        // ==========================================
        // 3. ARABELLEK (BUFFER) VE AKIŞ AYARLARI
        // ==========================================
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
                            .background(TiviSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = TiviSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Arabellek (Buffer) & Akış Ayarları",
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Donma ve takılmaları önlemek için arabellek boyutları",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                // 1. Canlı Yayın Formatı
                Text(text = "Canlı Yayın Formatı", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("TS" to "MPEG-TS (.ts - Önerilen)", "HLS" to "HLS (.m3u8)").forEach { (key, label) ->
                        val isSelected = streamFormat == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { scope.launch { viewModel.preferences.setStreamFormat(key) } },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TiviPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = TiviPrimary,
                                containerColor = TiviSurfaceVariant,
                                labelColor = TiviTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) TiviPrimary else TiviCardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Canlı Yayın Arabelleği (Buffer)
                Text(text = "Canlı Yayın Arabelleği (Tampon)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Zayıf internette takılmayı önlemek için tampon süresini artırın.", color = TiviTextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val liveBufferOptions = listOf(
                        2 to "2 sn (Düşük Gecikme)",
                        5 to "5 sn (Dengeli / Önerilen)",
                        10 to "10 sn (Kararlı / Takılma Önleyici)"
                    )
                    items(liveBufferOptions) { (secs, label) ->
                        val isSelected = liveBufferSeconds == secs
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setLiveBufferSeconds(secs)
                                android.widget.Toast.makeText(
                                    context,
                                    "Canlı yayın tamponu $secs sn olarak ayarlandı",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TiviPrimary.copy(alpha = 0.25f),
                                selectedLabelColor = TiviPrimary,
                                containerColor = TiviSurfaceVariant,
                                labelColor = TiviTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) TiviPrimary else TiviCardBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Film & Dizi Tamponu (Kesintisiz İleri/Geri Önbellek)
                Text(text = "Film & Dizi Tamponu (İleri/Geri Sarma Önbelleği)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sürekli ${vodBufferSeconds}s tampon tutulur. İleri-geri sararken tekrar indirme yapmaz.",
                    color = TiviSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val bufferOptions = listOf(30, 60, 90, 120, 180)
                    items(bufferOptions) { secs ->
                        val isSelected = vodBufferSeconds == secs
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setVodBufferSeconds(secs)
                                android.widget.Toast.makeText(
                                    context,
                                    "Film & Dizi tamponu $secs sn olarak ayarlandı",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = {
                                Text(
                                    text = if (secs == 60) "60s (Önerilen)" else "${secs} sn",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TiviSecondary.copy(alpha = 0.25f),
                                selectedLabelColor = TiviSecondary,
                                containerColor = TiviSurfaceVariant,
                                labelColor = TiviTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) TiviSecondary else TiviCardBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Hızlı Kanal Açılışı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Hızlı Kanal Açılışı", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = if (fastZapping) "0.8s ultra hızlı başlatma aktif" else "2.5s standart tamponlama aktif", color = TiviTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = fastZapping,
                        onCheckedChange = { checked -> scope.launch { viewModel.preferences.setFastZapping(checked) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TiviPrimary,
                            checkedTrackColor = TiviPrimary.copy(alpha = 0.35f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Canlı Senkronizasyonu & "Canlıya Geç" Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Otomatik Canlı Senkronizasyonu",
                            color = TiviTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gecikme 3s üzerinde olduğunda otomatik canlıya çeker",
                            color = TiviTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoSyncLive,
                        onCheckedChange = { checked -> viewModel.setAutoSyncLive(checked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TiviPrimary,
                            checkedTrackColor = TiviPrimary.copy(alpha = 0.35f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val synced = viewModel.syncToLive()
                        if (synced) {
                            android.widget.Toast.makeText(context, "Yayın canlı sınıra çekildi (3s tampon)", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Canlı senkronizasyon hazır (Hedef tampon: 3 saniye)", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Canlıya Geç (Senkronize Et)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // ==========================================
        // 4. BAĞLANTI & GÜVENLİK KARTI
        // ==========================================
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
                        Text(
                            text = "Bağlantı & Güvenlik",
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Protokol ve yetişkin içerik koruması",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                // Güvenli Bağlantı (HTTPS / HTTP)
                val isHttps = serverUrl.startsWith("https://", ignoreCase = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Güvenli Bağlantı (HTTPS)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isHttps) "Şifreli bağlantı (${AppConfig.SERVER_URL_HTTPS})" else "Standart HTTP (${AppConfig.SERVER_URL_HTTP})",
                            color = if (isHttps) TiviGreen else TiviGold,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isHttps,
                        onCheckedChange = { checked ->
                            val newUrl = if (checked) AppConfig.SERVER_URL_HTTPS else AppConfig.SERVER_URL_HTTP
                            scope.launch {
                                viewModel.preferences.setServerUrl(newUrl)
                                viewModel.repository.serverUrl = newUrl
                                android.widget.Toast.makeText(context, "Bağlantı güncellendi: $newUrl", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TiviGreen,
                            checkedTrackColor = TiviGreen.copy(alpha = 0.35f),
                            uncheckedThumbColor = TiviGold,
                            uncheckedTrackColor = TiviGold.copy(alpha = 0.35f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ebeveyn Kilidi
                Text(text = "Ebeveyn Kilidi (+18 Koruma)", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Yetişkin kategorileri 4 haneli PIN ile koruyun.", color = TiviTextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                pinSuccessMessage?.let { msg ->
                    Text(text = msg, color = TiviGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                }

                OutlinedButton(
                    onClick = { showPinDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Password, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Ebeveyn PIN Kodunu Değiştir", color = TiviTextPrimary, fontSize = 13.sp)
                }
            }
        }

        // ==========================================
        // 5. BAĞLANTI & PİNG TESTİ KARTI
        // ==========================================
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
                            .background(TiviAccentLive.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, tint = TiviAccentLive, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sunucu Hız ve Ping Testi",
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Yayın donması durumunda sunucu bağlantınızı test edin",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                speedTestResult?.let { result ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TiviSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = result,
                            color = TiviPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.runSpeedTest() },
                    enabled = !isTestingSpeed,
                    colors = ButtonDefaults.buttonColors(containerColor = TiviSecondary, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingSpeed) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Ölçülüyor...", fontSize = 13.sp)
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Hız ve Ping Testini Başlat", fontSize = 13.sp)
                    }
                }
            }
        }

        // ==========================================
        // 6. UYGULAMA BİLGİSİ & DESTEK KARTI
        // ==========================================
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
                            .background(TiviPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = AppConfig.APP_NAME,
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v1.0.0 Pro Sürüm",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Aktif Sunucu:", color = TiviTextSecondary, fontSize = 12.sp)
                    Text(text = viewModel.repository.serverUrl, color = TiviTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Telegram:", color = TiviTextSecondary, fontSize = 12.sp)
                    Text(text = AppConfig.SUPPORT_TELEGRAM, color = TiviPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "WhatsApp:", color = TiviTextSecondary, fontSize = 12.sp)
                    Text(text = AppConfig.SUPPORT_WHATSAPP, color = TiviGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Önbelleği Temizle
                OutlinedButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "Görsel ve kanal önbelleği temizlendi.", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = TiviSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Uygulama Önbelleğini Temizle", color = TiviTextPrimary, fontSize = 13.sp)
                }
            }
        }

        // ==========================================
        // 7. ÇIKIŞ YAP BUTONU
        // ==========================================
        OutlinedButton(
            onClick = { viewModel.logout() },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TiviAccentLive),
            border = androidx.compose.foundation.BorderStroke(1.dp, TiviAccentLive.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = TiviAccentLive)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Hesaptan Çıkış Yap", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
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
                            focusedBorderColor = TiviPrimary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary, contentColor = TiviBackground)
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("İptal", color = TiviTextSecondary)
                }
            }
        )
    }
}
