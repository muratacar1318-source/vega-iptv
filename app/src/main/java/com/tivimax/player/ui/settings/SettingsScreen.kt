package com.tivimax.player.ui.settings

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.model.XtreamUserInfo
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.theme.AppThemes
import com.tivimax.player.theme.VegaPalette
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.ParentalPin
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import com.tivimax.player.ui.design.VegaButtonVariant
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.util.CrashLogger
import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.LocalIsTvDevice
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsSection(val title: String, val icon: ImageVector, val subtitle: String) {
    PLAYLISTS("Listelerim", Icons.AutoMirrored.Filled.PlaylistPlay, "Oynatma listelerini yönet"),
    ACCOUNT("Hesabım", Icons.Default.AccountCircle, "Abonelik ve içerik özeti"),
    PLAYER("Oynatıcı", Icons.Default.Tune, "Ses, altyazı ve görüntü"),
    APPEARANCE("Görünüm", Icons.Default.Palette, "Tema, ekran düzeni ve altyazı"),
    DEVICE_MODE("Cihaz Türü", Icons.Default.Tv, "Telefon, tablet veya TV düzeni"),
    PARENTAL("Ebeveyn Kilidi", Icons.Default.Security, "Yetişkin içerik koruması"),
    BACKUP_APP("Güncelleme ve Bakım", Icons.Default.SystemUpdate, "Güncelleme, bağlantı testi ve destek")
}

private enum class PlayerDialogType {
    AUDIO_LANG,
    SUBTITLE_LANG,
    STREAM_FORMAT,
    LIVE_BUFFER,
    VOD_BUFFER,
    DECODER_MODE,
    ASPECT_RATIO
}

private data class ThemeOption(
    val key: String,
    val label: String,
    val palette: VegaPalette
)

private val VegaThemeOptions = listOf(
    ThemeOption("MIDNIGHT", "Klasik Mavi", AppThemes.ClassicBlue),
    ThemeOption("AMOLED", "AMOLED Siyah", AppThemes.AmoledBlack),
    ThemeOption("OCEAN", "Okyanus", AppThemes.Ocean),
    ThemeOption("FOREST", "Orman", AppThemes.Forest),
    ThemeOption("VIOLET", "Mor", AppThemes.Violet),
    ThemeOption("SUNSET", "Gün Batımı", AppThemes.Sunset),
    ThemeOption("ROSE", "Gül", AppThemes.Rose),
    ThemeOption("VINTAGE", "Vintage", AppThemes.Vintage)
)

private fun themeDisplayLabel(themeKey: String): String {
    if (themeKey.equals("CLASSIC_BLUE", ignoreCase = true)) return "Klasik Mavi"
    return VegaThemeOptions.firstOrNull { it.key.equals(themeKey, ignoreCase = true) }?.label ?: "Klasik Mavi"
}

private fun deviceModeDisplayTitle(mode: DeviceMode): String = when (mode) {
    DeviceMode.PHONE -> "Telefon"
    DeviceMode.TABLET -> "Tablet"
    DeviceMode.TV -> "Televizyon"
}

private fun deviceModeDisplayDescription(mode: DeviceMode): String = when (mode) {
    DeviceMode.PHONE -> "Dikey düzen; video açılınca yatay tam ekran"
    DeviceMode.TABLET -> "Geniş ekran iki sütunlu düzen"
    DeviceMode.TV -> "Kumanda odaklı tam ekran yatay düzen"
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val userInfo by viewModel.userInfo.collectAsState()
    val activePlaylist by viewModel.activePlaylist.collectAsState()
    val targetSectionStr by viewModel.targetSettingsSection.collectAsState()
    val speedTestResult by viewModel.speedTestResult.collectAsState()
    val isTestingSpeed by viewModel.isTestingSpeed.collectAsState()

    val defaultAspectRatio by viewModel.preferences.aspectRatioFlow.collectAsState(initial = "FIT")
    val autoPipEnabled by viewModel.autoPipEnabled.collectAsState()
    val autoFrameRate by viewModel.autoFrameRate.collectAsState()
    val autoStartLastChannel by viewModel.autoStartLastChannel.collectAsState()
    val vodBufferSeconds by viewModel.vodBufferSeconds.collectAsState()
    val liveBufferSeconds by viewModel.liveBufferSeconds.collectAsState()
    val decoderMode by viewModel.decoderMode.collectAsState()
    val streamFormat by viewModel.streamFormat.collectAsState()
    val lastSync by viewModel.preferences.lastSyncTimestampFlow.collectAsState(initial = "")
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()
    val storedParentalPin by viewModel.parentalPin.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val activeDeviceMode by viewModel.deviceMode.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.PLAYLISTS) }
    var activeSubPage by rememberSaveable { mutableStateOf<SettingsSection?>(null) }

    LaunchedEffect(targetSectionStr) {
        if (targetSectionStr == "PLAYLISTS") {
            selectedSection = SettingsSection.PLAYLISTS
            activeSubPage = SettingsSection.PLAYLISTS
            viewModel.targetSettingsSection.value = null
        }
    }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var currentPinInput by remember { mutableStateOf("") }
    var repeatPinInput by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }
    var pinSuccessMessage by remember { mutableStateOf<String?>(null) }

    val configuration = LocalConfiguration.current
    val isTv = LocalIsTvDevice.current
    val isWideScreen = isTv ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        if (isWideScreen) {
            // ==========================================
            // YATAY / ANDROID TV DÜZENİ (260dp SAKİN SOL RAY + SAĞ İÇERİK)
            // ==========================================
            val contentScrollState = rememberScrollState()
            LaunchedEffect(selectedSection) {
                contentScrollState.scrollTo(0)
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // SOL RAY (260dp, sakin yüzey + ince sağ ayırıcı)
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(VegaColors.Surface.copy(alpha = 0.86f))
                        .padding(horizontal = 12.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Ayarlar",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.ScreenTitle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SettingsSection.entries.forEach { section ->
                            SettingsRailItem(
                                title = section.title,
                                subtitle = "",
                                icon = section.icon,
                                selected = selectedSection == section,
                                onClick = { selectedSection = section }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(VegaColors.Border.copy(alpha = 0.6f))
                )

                // SAĞ İÇERİK PANELİ
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 780.dp)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(contentScrollState)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        SettingsSectionHeader(
                            title = selectedSection.title,
                            description = selectedSection.subtitle,
                            icon = selectedSection.icon
                        )

                        RenderSelectedSection(
                            section = selectedSection,
                            viewModel = viewModel,
                            userInfo = userInfo,
                            lastSync = lastSync,
                            decoderMode = decoderMode,
                            streamFormat = streamFormat,
                            defaultAspectRatio = defaultAspectRatio,
                            autoPipEnabled = autoPipEnabled,
                            autoFrameRate = autoFrameRate,
                            autoStartLastChannel = autoStartLastChannel,
                            liveBufferSeconds = liveBufferSeconds,
                            vodBufferSeconds = vodBufferSeconds,
                            subtitleSize = subtitleSize,
                            subtitleColor = subtitleColor,
                            storedParentalPin = storedParentalPin,
                            speedTestResult = speedTestResult,
                            isTestingSpeed = isTestingSpeed,
                            pinSuccessMessage = pinSuccessMessage,
                            context = context,
                            onOpenCategoryDialog = { showCategoryDialog = true },
                            onOpenPinDialog = { showPinDialog = true }
                        )

                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        } else {
            // ==========================================
            // DİKEY TELEFON DÜZENİ (GRUPLANMIŞ LİSTE <-> TAM EKRAN ALT SAYFA)
            // ==========================================
            BackHandler(enabled = activeSubPage != null) {
                activeSubPage = null
            }

            AnimatedContent(
                targetState = activeSubPage,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 6 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 6 } + fadeOut())
                    }
                },
                label = "settingsPortraitTransition"
            ) { currentPage ->
                if (currentPage == null) {
                    val activeThemeLabel = remember(appTheme) { themeDisplayLabel(appTheme) }
                    val playerSummary = remember(decoderMode, liveBufferSeconds) {
                        "${if (decoderMode == "SOFTWARE") "Uyumlu" else "Hızlı"} · $liveBufferSeconds sn"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "Ayarlar",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.ScreenTitle,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )

                        // Grup 1: Hesap ve Listeler
                        SettingsGroupCard(title = "Hesap ve listeler") {
                            SettingsCategoryListItem(
                                title = SettingsSection.PLAYLISTS.title,
                                subtitle = "",
                                icon = SettingsSection.PLAYLISTS.icon,
                                valueSummary = activePlaylist?.name ?: "",
                                inGroup = true,
                                onClick = { activeSubPage = SettingsSection.PLAYLISTS }
                            )
                            SettingsGroupDivider()
                            SettingsCategoryListItem(
                                title = SettingsSection.ACCOUNT.title,
                                subtitle = "",
                                icon = SettingsSection.ACCOUNT.icon,
                                valueSummary = userInfo?.username?.ifBlank { null } ?: "",
                                inGroup = true,
                                onClick = { activeSubPage = SettingsSection.ACCOUNT }
                            )
                        }

                        // Grup 2: Tercihler
                        SettingsGroupCard(title = "Tercihler") {
                            SettingsCategoryListItem(
                                title = SettingsSection.PLAYER.title,
                                subtitle = "",
                                icon = SettingsSection.PLAYER.icon,
                                valueSummary = playerSummary,
                                inGroup = true,
                                onClick = { activeSubPage = SettingsSection.PLAYER }
                            )
                            SettingsGroupDivider()
                            SettingsCategoryListItem(
                                title = SettingsSection.APPEARANCE.title,
                                subtitle = "",
                                icon = SettingsSection.APPEARANCE.icon,
                                valueSummary = activeThemeLabel,
                                inGroup = true,
                                onClick = { activeSubPage = SettingsSection.APPEARANCE }
                            )
                            SettingsGroupDivider()
                            SettingsCategoryListItem(
                                title = SettingsSection.DEVICE_MODE.title,
                                subtitle = "",
                                icon = SettingsSection.DEVICE_MODE.icon,
                                valueSummary = deviceModeDisplayTitle(activeDeviceMode),
                                inGroup = true,
                                onClick = { activeSubPage = SettingsSection.DEVICE_MODE }
                            )
                            SettingsGroupDivider()
                            SettingsCategoryListItem(
                                title = SettingsSection.PARENTAL.title,
                                subtitle = "",
                                icon = SettingsSection.PARENTAL.icon,
                                valueSummary = if (storedParentalPin.isNotEmpty()) "Açık" else "Kapalı",
                                inGroup = true,
                                onClick = { activeSubPage = SettingsSection.PARENTAL }
                            )
                        }

                        // Grup 3: Sistem
                        SettingsGroupCard(title = "Sistem") {
                            SettingsCategoryListItem(
                                title = SettingsSection.BACKUP_APP.title,
                                subtitle = "",
                                icon = SettingsSection.BACKUP_APP.icon,
                                valueSummary = "v${com.tivimax.player.BuildConfig.VERSION_NAME}",
                                inGroup = true,
                                onClick = { activeSubPage = SettingsSection.BACKUP_APP }
                            )
                        }

                        Spacer(modifier = Modifier.height(88.dp))
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SettingsMobileTopBar(
                            title = currentPage.title,
                            onBack = { activeSubPage = null }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            RenderSelectedSection(
                                section = currentPage,
                                viewModel = viewModel,
                                userInfo = userInfo,
                                lastSync = lastSync,
                                decoderMode = decoderMode,
                                streamFormat = streamFormat,
                                defaultAspectRatio = defaultAspectRatio,
                                autoPipEnabled = autoPipEnabled,
                                autoFrameRate = autoFrameRate,
                                autoStartLastChannel = autoStartLastChannel,
                                liveBufferSeconds = liveBufferSeconds,
                                vodBufferSeconds = vodBufferSeconds,
                                subtitleSize = subtitleSize,
                                subtitleColor = subtitleColor,
                                storedParentalPin = storedParentalPin,
                                speedTestResult = speedTestResult,
                                isTestingSpeed = isTestingSpeed,
                                pinSuccessMessage = pinSuccessMessage,
                                context = context,
                                onOpenCategoryDialog = { showCategoryDialog = true },
                                onOpenPinDialog = { showPinDialog = true }
                            )

                            Spacer(modifier = Modifier.height(88.dp))
                        }
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

    // PIN DEĞİŞTİRME / BELİRLEME DİYALOĞU
    if (showPinDialog) {
        val pinSet = storedParentalPin.isNotEmpty()
        val firstPinFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (firstPinFocusRequester.requestFocusSafely()) break
            }
        }
        val closeDialog = {
            showPinDialog = false
            newPinInput = ""
            currentPinInput = ""
            repeatPinInput = ""
            pinDialogError = null
        }
        AlertDialog(
            onDismissRequest = closeDialog,
            containerColor = VegaColors.SurfaceElevated,
            shape = VegaShapes.Large,
            title = {
                Text(
                    text = if (pinSet) "Ebeveyn şifresini değiştir" else "Ebeveyn şifresi belirle",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (pinSet) {
                            "Mevcut şifreni ve yeni 4 haneli şifreni gir."
                        } else {
                            "+18 içerikler için 4 haneli şifre belirle."
                        },
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary
                    )
                    if (pinSet) {
                        PinDialogField(
                            value = currentPinInput,
                            label = "Mevcut şifre",
                            isError = pinDialogError != null,
                            modifier = Modifier.focusRequester(firstPinFocusRequester)
                        ) {
                            currentPinInput = it
                            pinDialogError = null
                        }
                    }
                    PinDialogField(
                        value = newPinInput,
                        label = if (pinSet) "Yeni şifre" else "Şifre",
                        isError = pinDialogError != null,
                        modifier = if (!pinSet) Modifier.focusRequester(firstPinFocusRequester) else Modifier
                    ) {
                        newPinInput = it
                        pinDialogError = null
                    }
                    PinDialogField(
                        value = repeatPinInput,
                        label = "Şifre tekrar",
                        isError = pinDialogError != null
                    ) {
                        repeatPinInput = it
                        pinDialogError = null
                    }
                    pinDialogError?.let { msg ->
                        Text(
                            text = msg,
                            color = VegaColors.Danger,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                SettingsActionButton(
                    text = "Kaydet",
                    variant = VegaButtonVariant.PRIMARY,
                    onClick = {
                        val hata = if (pinSet && !ParentalPin.canUnlock(storedParentalPin, currentPinInput)) {
                            "Mevcut şifre yanlış"
                        } else {
                            ParentalPin.validateNewPin(newPinInput, repeatPinInput)
                        }
                        if (hata != null) {
                            pinDialogError = hata
                        } else {
                            val yeniPin = newPinInput
                            scope.launch {
                                viewModel.preferences.setParentalPin(yeniPin)
                                pinSuccessMessage = if (pinSet) "Şifre güncellendi." else "Şifre belirlendi."
                                closeDialog()
                            }
                        }
                    }
                )
            },
            dismissButton = {
                SettingsActionButton(
                    text = "İptal",
                    variant = VegaButtonVariant.SECONDARY,
                    onClick = closeDialog
                )
            }
        )
    }
}

@Composable
private fun PinDialogField(
    value: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { ch -> ch in '0'..'9' }) onChange(it) },
        label = { Text(label) },
        placeholder = { Text("••••") },
        isError = isError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = tvKeyboardOptions(KeyboardOptions(keyboardType = KeyboardType.NumberPassword)),
        shape = VegaShapes.Medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VegaColors.Accent,
            unfocusedBorderColor = VegaColors.Border,
            errorBorderColor = VegaColors.Danger,
            focusedLabelColor = VegaColors.Accent,
            unfocusedLabelColor = VegaColors.TextSecondary,
            focusedContainerColor = VegaColors.Surface,
            unfocusedContainerColor = VegaColors.Surface,
            focusedTextColor = VegaColors.TextPrimary,
            unfocusedTextColor = VegaColors.TextPrimary
        ),
        modifier = modifier
            .tvOpenKeyboardOnOk()
            .fillMaxWidth()
    )
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
private fun RenderSelectedSection(
    section: SettingsSection,
    viewModel: MainViewModel,
    userInfo: XtreamUserInfo?,
    lastSync: String,
    decoderMode: String,
    streamFormat: String,
    defaultAspectRatio: String,
    autoPipEnabled: Boolean,
    autoFrameRate: Boolean,
    autoStartLastChannel: Boolean,
    liveBufferSeconds: Int,
    vodBufferSeconds: Int,
    subtitleSize: String,
    subtitleColor: String,
    storedParentalPin: String,
    speedTestResult: String?,
    isTestingSpeed: Boolean,
    pinSuccessMessage: String?,
    context: Context,
    onOpenCategoryDialog: () -> Unit,
    onOpenPinDialog: () -> Unit
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
        // 1. HESABIM
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
            var openDialog by remember { mutableStateOf<PlayerDialogType?>(null) }

            val audioOptions = remember {
                listOf(
                    SettingsChoiceItem("tr", "Türkçe"),
                    SettingsChoiceItem("en", "İngilizce"),
                    SettingsChoiceItem("de", "Almanca"),
                    SettingsChoiceItem("fr", "Fransızca"),
                    SettingsChoiceItem("auto", "Orijinal")
                )
            }
            val subtitleOptions = remember {
                listOf(
                    SettingsChoiceItem("none", "Kapalı"),
                    SettingsChoiceItem("tr", "Türkçe"),
                    SettingsChoiceItem("en", "İngilizce"),
                    SettingsChoiceItem("de", "Almanca"),
                    SettingsChoiceItem("fr", "Fransızca"),
                    SettingsChoiceItem("auto", "Otomatik")
                )
            }
            val streamFormatOptions = remember {
                listOf(
                    SettingsChoiceItem("TS", "Standart (önerilen)", "En kararlı canlı yayın akışı"),
                    SettingsChoiceItem("HLS", "Düşük gecikme", "Yayın geride kalıyorsa dene")
                )
            }
            val liveBufferOptions = remember {
                listOf(
                    SettingsChoiceItem(2, "2 sn", "Hızlı kanal açılışı"),
                    SettingsChoiceItem(5, "5 sn (önerilen)", "Dengeli açılış ve akıcılık"),
                    SettingsChoiceItem(10, "10 sn", "Zayıf bağlantıda en az takılma")
                )
            }
            val vodBufferOptions = remember {
                listOf(
                    SettingsChoiceItem(30, "30 sn"),
                    SettingsChoiceItem(60, "60 sn"),
                    SettingsChoiceItem(90, "90 sn"),
                    SettingsChoiceItem(120, "120 sn"),
                    SettingsChoiceItem(180, "180 sn (önerilen)")
                )
            }
            val decoderOptions = remember {
                listOf(
                    SettingsChoiceItem("HARDWARE", "Hızlı (önerilen)", "Daha akıcı görüntü ve düşük güç"),
                    SettingsChoiceItem("SOFTWARE", "Uyumlu", "Siyah ekran veya takılmada dene")
                )
            }
            val aspectRatioOptions = remember {
                listOf(
                    SettingsChoiceItem("FIT", "Orijinal", "Oranı bozmadan sığdırır"),
                    SettingsChoiceItem("FILL", "Doldur", "Tüm ekranı kaplar"),
                    SettingsChoiceItem("ZOOM", "Yakınlaştır", "Kenar boşluklarını kırpar"),
                    SettingsChoiceItem("16:9", "16:9", "Geniş ekran oranına sabitler")
                )
            }

            when (openDialog) {
                PlayerDialogType.AUDIO_LANG -> {
                    SettingsSelectionDialog(
                        title = "Tercih edilen ses dili",
                        options = audioOptions,
                        selectedValue = defaultAudioLang,
                        onSelect = { code ->
                            val label = audioOptions.firstOrNull { it.value == code }?.label ?: code
                            scope.launch { viewModel.preferences.setDefaultAudioLang(code) }
                            Toast.makeText(context, "Ses dili: $label", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { openDialog = null }
                    )
                }
                PlayerDialogType.SUBTITLE_LANG -> {
                    SettingsSelectionDialog(
                        title = "Tercih edilen altyazı dili",
                        options = subtitleOptions,
                        selectedValue = defaultSubtitleLang,
                        onSelect = { code ->
                            val label = subtitleOptions.firstOrNull { it.value == code }?.label ?: code
                            scope.launch { viewModel.preferences.setDefaultSubtitleLang(code) }
                            Toast.makeText(context, "Altyazı dili: $label", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { openDialog = null }
                    )
                }
                PlayerDialogType.STREAM_FORMAT -> {
                    SettingsSelectionDialog(
                        title = "Canlı yayın biçimi",
                        subtitle = "Sonraki açtığın kanalda geçerli olur.",
                        options = streamFormatOptions,
                        selectedValue = streamFormat,
                        onSelect = { fmt ->
                            viewModel.setStreamFormat(fmt)
                            val label = if (fmt == "HLS") "Düşük gecikme" else "Standart"
                            Toast.makeText(context, "Yayın biçimi: $label", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { openDialog = null }
                    )
                }
                PlayerDialogType.LIVE_BUFFER -> {
                    SettingsSelectionDialog(
                        title = "Canlı yayın ön yükleme",
                        options = liveBufferOptions,
                        selectedValue = liveBufferSeconds,
                        onSelect = { secs ->
                            viewModel.setLiveBufferSeconds(secs)
                            Toast.makeText(context, "Canlı ön yükleme: $secs sn", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { openDialog = null }
                    )
                }
                PlayerDialogType.VOD_BUFFER -> {
                    SettingsSelectionDialog(
                        title = "Film ve dizi ön yükleme",
                        options = vodBufferOptions,
                        selectedValue = vodBufferSeconds,
                        onSelect = { secs ->
                            viewModel.setVodBufferSeconds(secs)
                            Toast.makeText(context, "Film ve dizi ön yükleme: $secs sn", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { openDialog = null }
                    )
                }
                PlayerDialogType.DECODER_MODE -> {
                    SettingsSelectionDialog(
                        title = "Görüntü işleme",
                        options = decoderOptions,
                        selectedValue = decoderMode,
                        onSelect = { mode ->
                            viewModel.setDecoderMode(mode)
                            val label = if (mode == "HARDWARE") "Hızlı" else "Uyumlu"
                            Toast.makeText(context, "Görüntü işleme: $label", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { openDialog = null }
                    )
                }
                PlayerDialogType.ASPECT_RATIO -> {
                    SettingsSelectionDialog(
                        title = "Görüntü boyutu",
                        options = aspectRatioOptions,
                        selectedValue = defaultAspectRatio,
                        onSelect = { mode ->
                            val label = aspectRatioOptions.firstOrNull { it.value == mode }?.label ?: mode
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
                        },
                        onDismiss = { openDialog = null }
                    )
                }
                null -> Unit
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SettingsGroupCard(title = "Dil tercihleri") {
                    SettingsValueRow(
                        title = "Ses dili",
                        subtitle = "Çok dilli yayınlarda öncelikli dil",
                        valueText = audioOptions.firstOrNull { it.value == defaultAudioLang }?.label ?: "Türkçe",
                        icon = Icons.Default.VolumeUp,
                        onClick = { openDialog = PlayerDialogType.AUDIO_LANG }
                    )
                    SettingsGroupDivider()
                    SettingsValueRow(
                        title = "Altyazı dili",
                        subtitle = "Otomatik açılacak altyazı dili",
                        valueText = subtitleOptions.firstOrNull { it.value == defaultSubtitleLang }?.label ?: "Türkçe",
                        icon = Icons.Default.ClosedCaption,
                        onClick = { openDialog = PlayerDialogType.SUBTITLE_LANG }
                    )
                }

                SettingsGroupCard(title = "Yayın ve akıcılık") {
                    SettingsValueRow(
                        title = "Canlı yayın biçimi",
                        subtitle = "Akış kararlılığı ve gecikme tercihi",
                        valueText = if (streamFormat == "HLS") "Düşük gecikme" else "Standart",
                        icon = Icons.Default.LiveTv,
                        onClick = { openDialog = PlayerDialogType.STREAM_FORMAT }
                    )
                    SettingsGroupDivider()
                    SettingsValueRow(
                        title = "Canlı yayın ön yükleme",
                        subtitle = "Zayıf bağlantıda takılmayı azaltır",
                        valueText = "$liveBufferSeconds sn",
                        icon = Icons.Default.Speed,
                        onClick = { openDialog = PlayerDialogType.LIVE_BUFFER }
                    )
                    SettingsGroupDivider()
                    SettingsValueRow(
                        title = "Film ve dizi ön yükleme",
                        subtitle = "İzleme öncesi tampon süresi",
                        valueText = "$vodBufferSeconds sn",
                        icon = Icons.Default.Movie,
                        onClick = { openDialog = PlayerDialogType.VOD_BUFFER }
                    )
                    SettingsGroupDivider()
                    SettingsValueRow(
                        title = "Görüntü işleme",
                        subtitle = "Siyah ekran veya takılmada uyumlu seç",
                        valueText = if (decoderMode == "SOFTWARE") "Uyumlu" else "Hızlı",
                        icon = Icons.Default.HighQuality,
                        onClick = { openDialog = PlayerDialogType.DECODER_MODE }
                    )
                }

                SettingsGroupCard(title = "Görüntü ve davranış") {
                    SettingsValueRow(
                        title = "Görüntü boyutu",
                        subtitle = "Videonun ekrana yerleşme biçimi",
                        valueText = aspectRatioOptions.firstOrNull { it.value == defaultAspectRatio }?.label ?: "Orijinal",
                        icon = Icons.Default.AspectRatio,
                        onClick = { openDialog = PlayerDialogType.ASPECT_RATIO }
                    )
                    SettingsGroupDivider()
                    SettingsToggleRow(
                        title = "Kare hızı eşleme",
                        subtitle = "Ekran yenilemesini yayına uyarlar",
                        checked = autoFrameRate,
                        icon = Icons.Default.DisplaySettings,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.preferences.setAutoFrameRate(enabled) }
                        }
                    )
                    SettingsGroupDivider()
                    SettingsToggleRow(
                        title = "Küçük pencerede devam et",
                        subtitle = "Çıkınca canlı yayın küçük pencerede sürer",
                        checked = autoPipEnabled,
                        icon = Icons.Default.PictureInPictureAlt,
                        onCheckedChange = { enabled -> viewModel.setAutoPip(enabled) }
                    )
                    SettingsGroupDivider()
                    SettingsToggleRow(
                        title = "Son kanalla açıl",
                        subtitle = "Açılışta son izlenen kanalı başlatır",
                        checked = autoStartLastChannel,
                        icon = Icons.Default.PlayCircleOutline,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.preferences.setAutoStartLastChannel(enabled) }
                        }
                    )
                }
            }
        }

        // ==========================================
        // 3. GÖRÜNÜM
        // ==========================================
        SettingsSection.APPEARANCE -> {
            val liveLayoutMode by viewModel.liveLayoutMode.collectAsState()
            val portraitNavStyle by viewModel.portraitCategoryNavStyle.collectAsState()
            val appTheme by viewModel.appTheme.collectAsState()
            var showPortraitNavDialog by remember { mutableStateOf(false) }

            val portraitNavOptions = remember {
                listOf(
                    SettingsChoiceItem(
                        UserPreferences.PORTRAIT_CAT_NAV_CHIPS,
                        "Üstte kaydırmalı",
                        "Kategoriler üst şeritte"
                    ),
                    SettingsChoiceItem(
                        UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR,
                        "Yan panel",
                        "Kategoriler solda liste halinde"
                    ),
                    SettingsChoiceItem(
                        UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN,
                        "Açılır menü",
                        "Afişler için en geniş alan"
                    )
                )
            }

            if (showPortraitNavDialog) {
                SettingsSelectionDialog(
                    title = "Film ve dizi kategorileri",
                    options = portraitNavOptions,
                    selectedValue = portraitNavStyle,
                    onSelect = { style ->
                        val label = portraitNavOptions.firstOrNull { it.value == style }?.label ?: ""
                        viewModel.setPortraitCategoryNavStyle(style)
                        Toast.makeText(context, "Kategori görünümü: $label", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showPortraitNavDialog = false }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SettingsGroupCard(title = "Renk teması") {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        val columns = if (maxWidth >= 520.dp) 4 else 2
                        val rows = remember(columns) { VegaThemeOptions.chunked(columns) }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { option ->
                                        val isSelected = appTheme == option.key ||
                                            (option.key == "MIDNIGHT" && appTheme.equals("CLASSIC_BLUE", ignoreCase = true))
                                        SettingsThemePreviewCard(
                                            label = option.label,
                                            palette = option.palette,
                                            selected = isSelected,
                                            onClick = {
                                                scope.launch { viewModel.preferences.setAppTheme(option.key) }
                                                Toast.makeText(context, "Renk teması: ${option.label}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(columns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsGroupCard(title = "Ekran düzeni") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Canlı TV görünümü",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsOptionCard(
                                title = "Raflar",
                                description = "Kategorilere göre yatay satırlar",
                                icon = Icons.Default.ViewCarousel,
                                selected = liveLayoutMode == UserPreferences.LIVE_LAYOUT_SHELF,
                                compactVertical = true,
                                onClick = {
                                    scope.launch {
                                        viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_SHELF)
                                        Toast.makeText(context, "Canlı TV görünümü: Raflar", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SettingsOptionCard(
                                title = "Liste",
                                description = "Kategoriler üstte, kanallar altta",
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                selected = liveLayoutMode == UserPreferences.LIVE_LAYOUT_CLASSIC,
                                compactVertical = true,
                                onClick = {
                                    scope.launch {
                                        viewModel.setLiveLayoutMode(UserPreferences.LIVE_LAYOUT_CLASSIC)
                                        Toast.makeText(context, "Canlı TV görünümü: Liste", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    SettingsGroupDivider()

                    SettingsValueRow(
                        title = "Kategori çubuğu (telefon)",
                        subtitle = "Film ve dizi kategorilerinin konumu",
                        valueText = portraitNavOptions.firstOrNull { it.value == portraitNavStyle }?.label ?: "Üstte kaydırmalı",
                        icon = Icons.Default.ViewAgenda,
                        onClick = { showPortraitNavDialog = true }
                    )
                }

                SettingsGroupCard(title = "Altyazı görünümü") {
                    val currentNorm = when (subtitleSize) {
                        "KUCUK" -> "14"
                        "ORTA" -> "18"
                        "BUYUK" -> "22"
                        "COK_BUYUK" -> "26"
                        else -> subtitleSize
                    }
                    val previewSp = currentNorm.toFloatOrNull() ?: 18f
                    val previewColor = when (subtitleColor) {
                        "SARI" -> VegaColors.Gold
                        "YESIL" -> VegaColors.Success
                        "MAVI" -> AppThemes.ClassicBlue.brand
                        else -> Color.White
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = VegaShapes.Medium,
                            color = VegaColors.Background,
                            border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Örnek altyazı metni",
                                    color = previewColor,
                                    fontSize = (previewSp * 0.85f).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Boyut",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Medium
                            )
                            val sizeOptions = listOf(
                                "14" to "Küçük",
                                "18" to "Orta",
                                "22" to "Büyük",
                                "26" to "Çok büyük"
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                sizeOptions.forEach { (sizeVal, label) ->
                                    SettingsChoiceChip(
                                        label = label,
                                        selected = currentNorm == sizeVal,
                                        onClick = {
                                            viewModel.setSubtitleSize(sizeVal)
                                            Toast.makeText(context, "Altyazı boyutu: $label", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Renk",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Medium
                            )
                            val colorOptions = listOf(
                                Triple("BEYAZ", "Beyaz", Color.White),
                                Triple("SARI", "Sarı", VegaColors.Gold),
                                Triple("YESIL", "Yeşil", VegaColors.Success),
                                Triple("MAVI", "Mavi", AppThemes.ClassicBlue.brand)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                colorOptions.forEach { (key, label, swatch) ->
                                    SettingsChoiceChip(
                                        label = label,
                                        selected = subtitleColor == key,
                                        swatchColor = swatch,
                                        onClick = {
                                            scope.launch { viewModel.preferences.setSubtitleColor(key) }
                                            Toast.makeText(context, "Altyazı rengi: $label", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsGroupCard(title = "Kategori yönetimi") {
                    SettingsActionRow(
                        title = "Kategorileri düzenle",
                        subtitle = "Kategorileri gizle veya en üste sabitle",
                        icon = Icons.Default.Tune,
                        onClick = onOpenCategoryDialog
                    )
                }
            }
        }

        // ==========================================
        // 4. CİHAZ TÜRÜ
        // ==========================================
        SettingsSection.DEVICE_MODE -> {
            val activeDeviceMode by viewModel.deviceMode.collectAsState()

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SettingsGroupCard(
                    title = "Cihaz düzeni",
                    subtitle = "Aktif: ${deviceModeDisplayTitle(activeDeviceMode)}"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviceMode.entries.forEach { mode ->
                            val icon = when (mode) {
                                DeviceMode.PHONE -> Icons.Default.PhoneAndroid
                                DeviceMode.TABLET -> Icons.Default.TabletAndroid
                                DeviceMode.TV -> Icons.Default.Tv
                            }
                            SettingsOptionCard(
                                title = deviceModeDisplayTitle(mode),
                                description = deviceModeDisplayDescription(mode),
                                icon = icon,
                                selected = activeDeviceMode == mode,
                                onClick = {
                                    viewModel.setDeviceMode(mode)
                                    Toast.makeText(
                                        context,
                                        "Cihaz türü: ${deviceModeDisplayTitle(mode)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                SettingsGroupCard(title = "İlk kurulum") {
                    SettingsActionRow(
                        title = "İlk kurulumu tekrar göster",
                        subtitle = "Sonraki açılışta cihaz türünü yeniden sorar",
                        icon = Icons.Default.RestartAlt,
                        accentColor = VegaColors.Danger,
                        onClick = {
                            viewModel.resetDeviceMode()
                            Toast.makeText(context, "İlk kurulum sıfırlandı.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // ==========================================
        // 5. EBEVEYN KİLİDİ
        // ==========================================
        SettingsSection.PARENTAL -> {
            val pinSet = storedParentalPin.isNotEmpty()

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SettingsGroupCard(title = "Şifre koruması") {
                    SettingsInfoRow(
                        label = "Kilit durumu",
                        value = if (pinSet) "Şifre belirlendi" else "Şifre belirlenmedi",
                        valueColor = if (pinSet) VegaColors.Success else VegaColors.TextMuted
                    )
                    if (pinSuccessMessage != null) {
                        Text(
                            text = pinSuccessMessage,
                            color = VegaColors.Success,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }
                    SettingsGroupDivider()
                    SettingsActionRow(
                        title = if (pinSet) "Şifreyi değiştir" else "Şifre belirle",
                        subtitle = "Yetişkin kategorileri 4 haneli şifreyle açılır",
                        icon = Icons.Default.Lock,
                        accentColor = VegaColors.Success,
                        onClick = onOpenPinDialog
                    )
                }

                SettingsGroupCard(title = "Kategori kontrolü") {
                    SettingsActionRow(
                        title = "Kategorileri gizle veya sırala",
                        subtitle = "İstemediğin kategorileri gizle, sık izlediklerini sabitle",
                        icon = Icons.Default.VisibilityOff,
                        onClick = onOpenCategoryDialog
                    )
                }
            }
        }

        // ==========================================
        // 6. GÜNCELLEME VE BAKIM
        // ==========================================
        SettingsSection.BACKUP_APP -> {
            val isCheckingUpdate by viewModel.autoUpdateManager.isChecking.collectAsState()
            val playlistSyncState by viewModel.playlistSyncState.collectAsState()
            var crashCount by remember { mutableIntStateOf(0) }
            var isClearingCache by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                crashCount = withContext(Dispatchers.IO) {
                    CrashLogger.listReports(context).size
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SettingsGroupCard(title = "Uygulama güncellemesi") {
                    SettingsActionRow(
                        title = "Güncellemeleri kontrol et",
                        subtitle = if (isCheckingUpdate) "Yeni sürüm denetleniyor…" else "Yeni sürüm olup olmadığını kontrol et",
                        icon = Icons.Default.SystemUpdate,
                        isLoading = isCheckingUpdate,
                        onClick = { viewModel.checkForUpdates(isManual = true) }
                    )
                }

                SettingsGroupCard(title = "Bağlantı ve önbellek") {
                    SettingsActionRow(
                        title = "Bağlantı testi",
                        subtitle = if (isTestingSpeed) "Bağlantı hızın ölçülüyor…" else "Yayınlar takılıyorsa bağlantını kontrol et",
                        icon = Icons.Default.NetworkCheck,
                        isLoading = isTestingSpeed,
                        onClick = { viewModel.runSpeedTest() }
                    )

                    speedTestResult?.let { result ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .background(VegaColors.SurfaceVariant, VegaShapes.Small)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = result,
                                color = VegaColors.Accent,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    SettingsGroupDivider()

                    SettingsActionRow(
                        title = "Önbelleği temizle",
                        subtitle = if (isClearingCache) "Önbellek temizleniyor…" else "Logolar ve görseller yenilenir, listelerin silinmez",
                        icon = Icons.Default.CleaningServices,
                        isLoading = isClearingCache,
                        onClick = {
                            if (isClearingCache) return@SettingsActionRow
                            isClearingCache = true
                            val isSyncingNow = playlistSyncState.isSyncing
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val syncDir = File(context.cacheDir, "sync")
                                        context.cacheDir.listFiles()?.forEach { child ->
                                            if (isSyncingNow && child.absolutePath == syncDir.absolutePath) {
                                                return@forEach
                                            }
                                            child.deleteRecursively()
                                        }
                                    }
                                    runCatching {
                                        coil.Coil.imageLoader(context).diskCache?.clear()
                                    }
                                }
                                runCatching {
                                    coil.Coil.imageLoader(context).memoryCache?.clear()
                                }
                                isClearingCache = false
                                Toast.makeText(context, "Önbellek temizlendi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                SettingsGroupCard(title = "Destek ve hakkında") {
                    SettingsInfoRow(
                        label = "Sürüm",
                        value = "${AppConfig.APP_NAME} ${com.tivimax.player.BuildConfig.VERSION_NAME}",
                        valueColor = VegaColors.Accent
                    )

                    SettingsGroupDivider()

                    // G-051/G-062: "Sorun bildir" her zaman görünür; "Kayıtları sil" yalnızca kayıt varken görünür.
                    SettingsActionRow(
                        title = "Sorun bildir",
                        subtitle = if (crashCount > 0) "Son çökmeler: $crashCount kayıt" else "Hata raporunu paylaş",
                        icon = Icons.Default.BugReport,
                        onClick = {
                            scope.launch {
                                val reportText = withContext(Dispatchers.IO) {
                                    CrashLogger.buildShareText(context)
                                }
                                try {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "${AppConfig.APP_NAME} hata raporu")
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                    }
                                    val chooser = Intent.createChooser(send, "Hata raporunu gönder").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(chooser)
                                } catch (e: Exception) {
                                    Log.w("SettingsScreen", "Hata raporu paylaşılamadı: ${e.javaClass.simpleName}")
                                    Toast.makeText(context, "Bu cihazda paylaşım uygulaması yok", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    if (crashCount > 0) {
                        SettingsGroupDivider()
                        SettingsActionRow(
                            title = "Kayıtları sil",
                            subtitle = "Biriken çökme kayıtlarını temizler",
                            icon = Icons.Default.DeleteOutline,
                            accentColor = VegaColors.Danger,
                            onClick = {
                                scope.launch {
                                    crashCount = withContext(Dispatchers.IO) {
                                        CrashLogger.clear(context)
                                        CrashLogger.listReports(context).size
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cihaz Modu Seçim ve Değiştirme Modal Diyaloğu.
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
        containerColor = VegaColors.SurfaceElevated,
        shape = VegaShapes.Large,
        title = {
            Text(
                text = "Cihaz türünü değiştir",
                color = VegaColors.TextPrimary,
                fontSize = VegaText.SectionTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceMode.entries.forEach { mode ->
                    val icon = when (mode) {
                        DeviceMode.PHONE -> Icons.Default.PhoneAndroid
                        DeviceMode.TABLET -> Icons.Default.TabletAndroid
                        DeviceMode.TV -> Icons.Default.Tv
                    }
                    SettingsOptionCard(
                        title = deviceModeDisplayTitle(mode),
                        description = deviceModeDisplayDescription(mode),
                        icon = icon,
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SettingsActionButton(
                    text = "İlk kurulumu tekrar göster",
                    variant = VegaButtonVariant.TEXT,
                    icon = Icons.Default.RestartAlt,
                    onClick = {
                        viewModel.resetDeviceMode()
                        Toast.makeText(context, "İlk kurulum sıfırlandı.", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            SettingsActionButton(
                text = "Uygula",
                variant = VegaButtonVariant.PRIMARY,
                onClick = {
                    viewModel.setDeviceMode(selectedMode)
                    Toast.makeText(
                        context,
                        "Cihaz türü: ${deviceModeDisplayTitle(selectedMode)}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            )
        },
        dismissButton = {
            SettingsActionButton(
                text = "İptal",
                variant = VegaButtonVariant.SECONDARY,
                onClick = onDismiss
            )
        }
    )
}
