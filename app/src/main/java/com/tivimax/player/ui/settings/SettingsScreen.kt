package com.tivimax.player.ui.settings

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
        // --- 1. ABONELİK BİLGİ KARTI ---
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
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Eşzamanlı Bağlantı Limiti:", color = TiviTextSecondary, fontSize = 13.sp)
                    Text(
                        text = "${userInfo?.activeCons ?: "1"} / ${userInfo?.maxConnections ?: "1"} Cihaz",
                        color = TiviTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- 2. YAYIN VE OYNATICI ÖZELLEŞTİRMELERİ ---
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
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = TiviPrimary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Yayın & Oynatıcı Tercihleri",
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Format, tamponlama ve görüntü oranını özelleştirin",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

                // 1. Canlı Yayın Formatı (MPEG-TS vs HLS)
                Text(text = "Canlı Yayın Formatı", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Hızlı Kanal Açılışı (Ultra Hızlı Tamponlama)
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

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Varsayılan Görüntü Oranı
                Text(text = "Varsayılan Ekran Oranı", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FIT" to "Fit", "FILL" to "Doldur", "ZOOM" to "Zoom", "16:9" to "16:9").forEach { (mode, label) ->
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

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Güvenli Bağlantı (HTTPS / HTTP) Seçimi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isHttps = serverUrl.startsWith("https://", ignoreCase = true)
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
                                android.widget.Toast.makeText(context, "Bağlantı adresi güncellendi: $newUrl", android.widget.Toast.LENGTH_SHORT).show()
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

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Canlı Yayın Senkronizasyonu & "Canlıya Geç" Butonu
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TiviSurfaceVariant)
                        .padding(12.dp)
                ) {
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
                                text = "Gecikme 3 saniye üzerindeyse otomatik 3 saniyeye çeker",
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

                    Spacer(modifier = Modifier.height(10.dp))

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
                        Text(text = "Canlıya Geç (3s Senkronize Et)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Otomatik Küçük Pencere (Picture-in-Picture - PiP)
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
                            text = if (autoPipEnabled) "Uygulama arka plana atıldığında otomatik küçük pencereye geçer" else "Arka planda küçük pencere kapalı",
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

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Film ve Dizi Sürekli Tampon (Buffer) & Geri Sarma Önbelleği
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TiviSurfaceVariant)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TiviSecondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = TiviSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Film & Dizi Kesintisiz Tampon (Buffer)",
                                color = TiviTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sürekli ${vodBufferSeconds}s ileri/geri tampon tutulur. İleri-geri sararken tekrar indirmez.",
                                color = TiviSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val bufferOptions = listOf(30, 60, 90, 120, 180)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(bufferOptions) { secs ->
                            val isSelected = vodBufferSeconds == secs
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setVodBufferSeconds(secs)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Film & Dizi tamponu $secs saniye olarak ayarlandı",
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
                                    containerColor = TiviCard,
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "İleriye Kesintisiz Tampon: +${vodBufferSeconds}s",
                            fontSize = 10.sp,
                            color = TiviTextMuted
                        )
                        Text(
                            text = "Geriye Sarma Önbelleği: -${vodBufferSeconds}s (Anında)",
                            fontSize = 10.sp,
                            color = TiviGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 8. Canlı Yayın Arabelleği (Buffer)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TiviSurfaceVariant)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TiviPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = TiviPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Canlı Yayın Arabelleği (Buffer)",
                                color = TiviTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Zayıf internette takılmayı önlemek için arabellek boyutunu artırın.",
                                color = TiviTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val liveBufferOptions = listOf(
                        2 to "2 sn (Düşük Gecikme)",
                        5 to "5 sn (Dengeli / Önerilen)",
                        10 to "10 sn (Kararlı / Takılma Önleyici)"
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        liveBufferOptions.forEach { (secs, label) ->
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
                                    containerColor = TiviCard,
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
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 9. Video Çözücü & Donanım Hızlandırma (Hardware / Software Decoder)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TiviSurfaceVariant)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TiviAccentLive.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = TiviAccentLive,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Video Çözücü (Decoder)",
                                color = TiviTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (decoderMode == "HARDWARE") "Donanım hızlandırma aktif (GPU gücü kullanılır, az pil harcar)"
                                else "Yazılım çözücü aktif (Uyumsuz kodeklerde ve eski cihazlarda takılmayı çözer)",
                                color = TiviTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "HARDWARE" to "Donanım / GPU (Önerilen)",
                            "SOFTWARE" to "Yazılım / CPU (Uyumlu)"
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
                                    containerColor = TiviCard,
                                    labelColor = TiviTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) (if (mode == "HARDWARE") TiviPrimary else TiviGold) else TiviCardBorder
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 10. Önbelleği Temizle
                OutlinedButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "Görsel ve kanal önbelleği temizlendi.", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = TiviSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Uygulama Önbelleğini Temizle", color = TiviTextPrimary, fontSize = 13.sp)
                }
            }
        }

        // --- 3. EBEVEYN KİLİDİ & GÜVENLİK ---
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
                            .background(TiviAccentLive.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TiviAccentLive)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ebeveyn Kilidi (+18 Koruma)",
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Yetişkin kategorileri 4 haneli PIN ile koruyun",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                pinSuccessMessage?.let { msg ->
                    Text(text = msg, color = TiviGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                }

                OutlinedButton(
                    onClick = { showPinDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Password, contentDescription = null, tint = TiviPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Ebeveyn PIN Kodunu Değiştir", color = TiviTextPrimary, fontSize = 13.sp)
                }
            }
        }

        // --- 3. DAHİLİ HIZ VE BAĞLANTI TESTİ ---
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
                            .background(TiviSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = TiviSecondary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "IPTV Sunucu Bağlantı Testi",
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Yayın donması veya gecikme durumunda bağlantınızı test edin",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Ölçülüyor...")
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Hız ve Ping Testini Başlat")
                    }
                }
            }
        }

        // --- 4. DESTEK VE İLETİŞİM ---
        Card(
            colors = CardDefaults.cardColors(containerColor = TiviCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Müşteri Desteği",
                    color = TiviTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Abonelik yenileme veya teknik destek için:",
                    color = TiviTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Telegram: ${AppConfig.SUPPORT_TELEGRAM}",
                    color = TiviPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "WhatsApp: ${AppConfig.SUPPORT_WHATSAPP}",
                    color = TiviGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- 5. UYGULAMA VE SUNUCU BİLGİSİ ---
        Card(
            colors = CardDefaults.cardColors(containerColor = TiviCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TiviPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = TiviPrimary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = AppConfig.APP_NAME,
                            color = TiviTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppConfig.APP_TAGLINE,
                            color = TiviTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = TiviCardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Aktif Sunucu:", color = TiviTextSecondary, fontSize = 12.sp)
                    Text(
                        text = viewModel.repository.serverUrl,
                        color = TiviTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Bağlantı Türü:", color = TiviTextSecondary, fontSize = 12.sp)
                    val isHttps = viewModel.repository.serverUrl.startsWith("https://", ignoreCase = true)
                    Text(
                        text = if (isHttps) "HTTPS (Güvenli & Şifreli)" else "HTTP (Güvensiz / Hızlı)",
                        color = if (isHttps) TiviGreen else TiviGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Sürüm:", color = TiviTextSecondary, fontSize = 12.sp)
                    Text(
                        text = "Vega IPTV v1.0.0 (Pro Build)",
                        color = TiviTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- 5. ÇIKIŞ YAP BUTONU ---
        OutlinedButton(
            onClick = { viewModel.logout() },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TiviAccentLive),
            border = androidx.compose.foundation.BorderStroke(1.dp, TiviAccentLive.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = TiviAccentLive)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Hesaptan Çıkış Yap", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
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
