package com.tivimax.player.ui.live

import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.GlobalSearchOptionsCard
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(viewModel: MainViewModel) {
    val categories by viewModel.visibleLiveCategories.collectAsState()
    val allCategories by viewModel.liveCategories.collectAsState()
    val hiddenCategories by viewModel.hiddenCategories.collectAsState()
    val isParentalUnlocked by viewModel.isParentalUnlocked.collectAsState()

    val channels by viewModel.liveStreams.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val isLoading by viewModel.liveLoading.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val currentSort by viewModel.liveSortOption.collectAsState()

    val isMiniPlayerActive by viewModel.isMiniPlayerActive.collectAsState()
    val activePreviewChannel by viewModel.activePreviewChannel.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    val currentEpgList by viewModel.currentEpgList.collectAsState()

    val globalQuery by viewModel.globalSearchQuery.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var showParentalPinDialog by remember { mutableStateOf(false) }
    var parentalPinInput by remember { mutableStateOf("") }
    var parentalPinError by remember { mutableStateOf(false) }
    var selectedProgramDetail by remember { mutableStateOf<Pair<LiveStream, EpgProgram>?>(null) }

    LaunchedEffect(globalQuery) {
        if (globalQuery.isNotBlank() && searchQuery != globalQuery) {
            searchQuery = globalQuery
            viewModel.setLiveSearchQuery(globalQuery)
        }
    }

    val filteredChannels = remember(channels, searchQuery, currentSort) {
        try {
            val list = if (searchQuery.isBlank()) {
                channels
            } else {
                val q = searchQuery.trim().lowercase()
                channels.filter { (it.name ?: "").lowercase().contains(q) }
            }

            val sorted = when (currentSort) {
                SortOption.DEFAULT -> list
                SortOption.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
                SortOption.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
                SortOption.ADDED_DESC -> list.sortedByDescending { it.added?.toLongOrNull() ?: it.streamId.toLong() }
                SortOption.RATING_DESC -> list.sortedBy { it.num }
            }
            sorted.distinctBy { it.streamId }
        } catch (e: Exception) {
            channels
        }
    }

    val displayedChannels = remember(filteredChannels, selectedCategory, favoriteIds) {
        if (selectedCategory == "favorites") {
            filteredChannels.filter { favoriteIds.contains(it.streamId.toString()) }
        } else {
            filteredChannels
        }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    // Geniş ekranda ilk açılışta ilk kanalı otomatik önizlemeye al
    LaunchedEffect(isWideScreen, channels) {
        if (isWideScreen && activePreviewChannel == null && channels.isNotEmpty()) {
            viewModel.previewChannel(channels.first())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        if (isWideScreen) {
            // ====================================================================
            // 4 BÖLMELİ PROFESYONEL CANLI TV KONTROL MERKEZİ (TIVIMATE & OPUS IPTV)
            // ====================================================================
            LiveTvWideScreenLayout(
                viewModel = viewModel,
                categories = categories,
                allCategories = allCategories,
                selectedCategory = selectedCategory,
                displayedChannels = displayedChannels,
                activePreviewChannel = activePreviewChannel,
                epgSummaryMap = epgSummaryMap,
                currentEpgList = currentEpgList,
                favoriteIds = favoriteIds,
                recentChannels = recentChannels,
                isLoading = isLoading,
                isParentalUnlocked = isParentalUnlocked,
                onOpenCategoryManager = { showCategoryManagerDialog = true },
                onOpenParentalDialog = {
                    parentalPinInput = ""
                    parentalPinError = false
                    showParentalPinDialog = true
                },
                onSelectProgramDetail = { stream, prog -> selectedProgramDetail = Pair(stream, prog) }
            )
        } else {
            // ====================================================================
            // DİKEY TELEFON DÜZENİ (PORTRAIT MOBILE LAYOUT)
            // ====================================================================
            LiveTvPortraitLayout(
                viewModel = viewModel,
                categories = categories,
                allCategories = allCategories,
                selectedCategory = selectedCategory,
                displayedChannels = displayedChannels,
                activePreviewChannel = activePreviewChannel,
                isMiniPlayerActive = isMiniPlayerActive,
                epgSummaryMap = epgSummaryMap,
                favoriteIds = favoriteIds,
                recentChannels = recentChannels,
                currentSort = currentSort,
                isLoading = isLoading,
                searchQuery = searchQuery,
                isParentalUnlocked = isParentalUnlocked,
                onSearchQueryChange = {
                    searchQuery = it
                    viewModel.setLiveSearchQuery(it)
                },
                onOpenCategoryManager = { showCategoryManagerDialog = true },
                onOpenParentalDialog = {
                    parentalPinInput = ""
                    parentalPinError = false
                    showParentalPinDialog = true
                },
                onSelectProgramDetail = { stream, prog -> selectedProgramDetail = Pair(stream, prog) }
            )
        }

        // --- KATEGORİ YÖNETİMİ DİYALOGU ---
        if (showCategoryManagerDialog) {
            var catSearch by remember { mutableStateOf("") }
            val filteredAllCats = remember(allCategories, catSearch) {
                if (catSearch.isBlank()) allCategories.filter { it.categoryId != "all" }
                else allCategories.filter { it.categoryId != "all" && it.categoryName.lowercase().contains(catSearch.lowercase()) }
            }

            AlertDialog(
                onDismissRequest = { showCategoryManagerDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = OpusCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kategori Yönetimi", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        Text(
                            "İzlemediğiniz yabancı veya gereksiz kategorileri gizleyerek listenizi sadeleştirin.",
                            color = TiviTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = catSearch,
                            onValueChange = { catSearch = it },
                            placeholder = { Text("Kategori ara...", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusCyan,
                                unfocusedBorderColor = TiviCardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredAllCats, key = { it.categoryId }) { cat ->
                                val isHidden = hiddenCategories.contains(cat.categoryId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleCategoryHidden(cat.categoryId) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cat.categoryName,
                                        color = if (isHidden) TiviTextSecondary.copy(alpha = 0.5f) else Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isHidden) "Gizli" else "Görünür",
                                        tint = if (isHidden) Color(0xFFEF5350) else OpusCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCategoryManagerDialog = false }) {
                        Text("Tamam", color = OpusCyan, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = TiviSurface
            )
        }

        // --- EBEVEYN KİLİDİ PIN DİYALOGU ---
        if (showParentalPinDialog) {
            AlertDialog(
                onDismissRequest = { showParentalPinDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF5350))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ebeveyn Kilidi", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            "Yetişkin (+18) içerikleri açmak için 4 haneli PIN kodunuzu girin (Varsayılan: 0000):",
                            color = TiviTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = parentalPinInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                    parentalPinInput = it
                                    parentalPinError = false
                                }
                            },
                            isError = parentalPinError,
                            singleLine = true,
                            placeholder = { Text("****") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusCyan,
                                errorBorderColor = Color.Red
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (parentalPinError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Hatalı PIN kodu! Tekrar deneyin.", color = Color.Red, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.unlockParental(parentalPinInput)) {
                                showParentalPinDialog = false
                            } else {
                                parentalPinError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan)
                    ) {
                        Text("Kilidi Aç", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showParentalPinDialog = false }) {
                        Text("İptal", color = TiviTextSecondary)
                    }
                },
                containerColor = TiviSurface
            )
        }

        // --- EPG PROGRAM DETAY BOTTOM SHEET ---
        if (selectedProgramDetail != null) {
            val (channel, program) = selectedProgramDetail!!
            ModalBottomSheet(
                onDismissRequest = { selectedProgramDetail = null },
                containerColor = TiviSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TiviCardBorder) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncShimmerImage(
                            model = channel.streamIcon,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            placeholderIcon = Icons.Default.Tv
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(channel.name, color = OpusCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(program.getFormattedTime(), color = TiviTextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = program.getDecodedTitle(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val progress = program.getProgressFraction()
                    LinearProgressIndicator(
                        progress = { progress },
                        color = OpusCyan,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (!program.description.isNullOrBlank()) program.description else "Bu program için detaylı özet bilgisi sunucu tarafından iletilmedi.",
                        color = TiviTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            selectedProgramDetail = null
                            viewModel.playLiveChannel(channel)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OpusCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bu Kanalı Tam Ekran İzle", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ====================================================================
// 4 BÖLMELİ PROFESYONEL CANLI TV KONTROL MERKEZİ (GENİŞ EKRAN / ANDROID TV)
// ====================================================================
@OptIn(UnstableApi::class)
@Composable
fun LiveTvWideScreenLayout(
    viewModel: MainViewModel,
    categories: List<LiveCategory>,
    allCategories: List<LiveCategory>,
    selectedCategory: String,
    displayedChannels: List<LiveStream>,
    activePreviewChannel: LiveStream?,
    epgSummaryMap: Map<Int, EpgProgram>,
    currentEpgList: List<EpgProgram>,
    favoriteIds: Set<String>,
    recentChannels: List<LiveStream>,
    isLoading: Boolean,
    isParentalUnlocked: Boolean,
    onOpenCategoryManager: () -> Unit,
    onOpenParentalDialog: () -> Unit,
    onSelectProgramDetail: (LiveStream, EpgProgram) -> Unit
) {
    var categorySearchQuery by remember { mutableStateOf("") }
    var channelSearchQuery by remember { mutableStateOf("") }
    var isChannelSearchOpen by remember { mutableStateOf(false) }
    var timeRangeHours by remember { mutableStateOf(2) } // 2, 4, 8 saat
    var isFullscreenEpg by remember { mutableStateOf(false) }

    val timelineScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val filteredCategories = remember(categories, categorySearchQuery) {
        if (categorySearchQuery.isBlank()) categories
        else categories.filter { it.categoryName.lowercase().contains(categorySearchQuery.trim().lowercase()) }
    }

    val finalChannels = remember(displayedChannels, channelSearchQuery) {
        if (channelSearchQuery.isBlank()) displayedChannels
        else displayedChannels.filter { (it.name ?: "").lowercase().contains(channelSearchQuery.trim().lowercase()) }
    }

    // Zaman Hesaplamaları
    val calendar = remember { Calendar.getInstance() }
    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(Calendar.MINUTE)
    val currentTimeStr = String.format("%02d:%02d", currentHour, currentMinute)
    val currentDateStr = remember {
        SimpleDateFormat("dd MMMM, EEEE", Locale("tr")).format(Date())
    }

    val slotWidthDp = when (timeRangeHours) {
        2 -> 160.dp
        4 -> 110.dp
        else -> 80.dp
    }
    val totalHalfHours = timeRangeHours * 2 + 1
    val totalTimelineWidthDp = slotWidthDp * totalHalfHours
    val currentMinuteOffsetDp = ((currentMinute / 30f) * slotWidthDp.value).dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // ====================================================================
        // BÖLGE 1: SOL MENÜ / KATEGORİ ÇEKMECESİ (~240dp)
        // ====================================================================
        Surface(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight(),
            color = TiviSurface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Başlık & Logo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(OpusCyan, TiviPrimary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CANLI TV",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(onClick = onOpenCategoryManager, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Kategori Yönetimi", tint = TiviTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                // 1. Hızlı Filtreler (Favoriler, İzlemeye Devam Et, Son İzlenenler)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TiviCardDark)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Favoriler
                    val isFavSelected = selectedCategory == "favorites"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFavSelected) TiviGold.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { viewModel.selectedLiveCategory.value = "favorites" }
                            .dpadFocusable(focusedBorderColor = TiviGold)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = TiviGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Favoriler",
                            color = if (isFavSelected) TiviGold else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isFavSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (favoriteIds.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TiviGold.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text("${favoriteIds.size}", color = TiviGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Son Görüntülenenler (Temizle butonlu)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Son İzlenenler", color = TiviTextSecondary, fontSize = 11.sp)
                        }
                        if (recentChannels.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearRecentChannels() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Temizle", tint = TiviTextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Modern Arama Kutusu ("Kategorileri ara...")
                OutlinedTextField(
                    value = categorySearchQuery,
                    onValueChange = { categorySearchQuery = it },
                    placeholder = { Text("Kategorileri ara...", fontSize = 11.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(15.dp))
                    },
                    trailingIcon = {
                        if (categorySearchQuery.isNotEmpty()) {
                            IconButton(onClick = { categorySearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(13.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OpusCyan,
                        unfocusedBorderColor = TiviCardBorder,
                        focusedContainerColor = TiviCardDark,
                        unfocusedContainerColor = TiviCardDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 3. "Tüm Kanallar" Öğesi ve Toplam Kanal Sayısı Rozeti (Badge)
                val isAllSelected = selectedCategory == "all"
                val totalChannelsCount = remember(allCategories, displayedChannels) {
                    allCategories.firstOrNull { it.categoryId == "all" }?.itemCount ?: displayedChannels.size
                }
                Surface(
                    onClick = { viewModel.loadLiveStreams("all") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllSelected) OpusCyan.copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocusable(focusedBorderColor = OpusCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAllSelected) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(OpusCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(Icons.Default.AllInclusive, contentDescription = null, tint = if (isAllSelected) OpusCyan else TiviTextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tüm Kanallar",
                            color = if (isAllSelected) OpusCyan else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAllSelected) OpusCyan.copy(alpha = 0.25f) else TiviCardDark)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("$totalChannelsCount", color = if (isAllSelected) OpusCyan else TiviTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                // 4. Aşağı Kaydırılabilir Kategori Listesi
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredCategories.filter { it.categoryId != "all" }, key = { it.categoryId }) { category ->
                        val isSelected = category.categoryId == selectedCategory
                        val categoryIcon = getCategoryIcon(category.categoryName)

                        Surface(
                            onClick = { viewModel.loadLiveStreams(category.categoryId) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) OpusCyan.copy(alpha = 0.16f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(focusedBorderColor = OpusCyan)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(OpusCyan)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) OpusCyan else TiviTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = category.categoryName,
                                    color = if (isSelected) OpusCyan else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (category.itemCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) OpusCyan.copy(alpha = 0.25f) else TiviCardDark)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${category.itemCount}",
                                            color = if (isSelected) OpusCyan else TiviTextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // En Altta: Ebeveyn Kilidi Butonu
                Surface(
                    onClick = {
                        if (isParentalUnlocked) viewModel.lockParental()
                        else onOpenParentalDialog()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isParentalUnlocked) Color(0xFFEF5350).copy(alpha = 0.2f) else TiviCardDark,
                    border = BorderStroke(1.dp, if (isParentalUnlocked) Color(0xFFEF5350) else TiviCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .dpadFocusable(focusedBorderColor = Color(0xFFEF5350))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isParentalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isParentalUnlocked) "+18 Açık (Kilitle)" else "+18 Korumalı",
                            color = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        VerticalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

        // ====================================================================
        // SAĞ ALAN: ÜST (BÖLGE 2 & BÖLGE 3) + ALT (BÖLGE 4)
        // ====================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black)
        ) {
            // Eğer tam ekran rehber modu aktif değilse üst önizleme ve detay panellerini göster
            if (!isFullscreenEpg) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ====================================================================
                    // BÖLGE 2: ÜST-ORTA CANLI MİNİ ÖNİZLEME PENCERESİ (Preview Box)
                    // ====================================================================
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = BorderStroke(1.5.dp, if (activePreviewChannel != null) OpusCyan.copy(alpha = 0.5f) else TiviCardBorder),
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                    ) {
                        if (activePreviewChannel != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { viewModel.expandMiniPlayerToFullscreen() }
                                    .dpadFocusable(focusedBorderColor = OpusCyan)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            layoutParams = FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            player = viewModel.playerManager.initializePlayer()
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Üst Bilgi Barı: CANLI rozeti ve Kanal Adı
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(TiviAccentLive)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text("CANLI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = activePreviewChannel.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.expandMiniPlayerToFullscreen() },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Tam Ekran", tint = OpusCyan)
                                    }
                                }
                            }
                        } else {
                            // Opus IPTV Tarzı Şık Placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(TiviCardDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = TiviTextMuted, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Hiçbir şey oynatılmıyor", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        "Kanal listesinde bir kanala tıklayarak seçim yapın",
                                        color = TiviTextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // ====================================================================
                    // BÖLGE 3: ÜST-SAĞ SEÇİLİ KANAL VE PROGRAM DETAY REHBERİ
                    // ====================================================================
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TiviCardDark),
                        border = BorderStroke(1.dp, TiviCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (activePreviewChannel != null) {
                            val channel = activePreviewChannel
                            val epg = epgSummaryMap[channel.streamId]
                            val isFavorite = favoriteIds.contains(channel.streamId.toString())

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                // Kanal Başlık Satırı
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                            .padding(2.dp),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Kanal #${channel.num}",
                                            color = TiviTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.toggleFavoriteLive(channel.streamId.toString()) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Favori",
                                            tint = if (isFavorite) TiviGold else TiviTextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                                // PROGRAM Başlığı ve O an Yayında Olan İçerik
                                val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
                                val programTime = epg?.getFormattedTime() ?: "Canlı Akış"
                                val progress = epg?.getProgressFraction() ?: 0.5f

                                Text(
                                    text = "PROGRAM",
                                    color = OpusCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = programTitle,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = programTime,
                                    color = TiviTextSecondary,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp)),
                                    color = OpusCyan,
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // BUGÜN SIRADA (Günün Geri Kalanı / Schedule)
                                Text(
                                    text = "BUGÜN SIRADA",
                                    color = TiviGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                val upcomingPrograms = remember(currentEpgList) {
                                    if (currentEpgList.size > 1) currentEpgList.drop(1).take(2)
                                    else emptyList()
                                }

                                if (upcomingPrograms.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        upcomingPrograms.forEach { upProg ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(TiviCard)
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    val timeOnly = upProg.getFormattedTime().split("-").firstOrNull()?.trim() ?: "Sırada"
                                                    Text(timeOnly, color = OpusCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = upProg.getDecodedTitle(),
                                                    color = TiviTextPrimary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = if (!epg?.description.isNullOrBlank()) epg.description!! else "Günün sonraki yayın akışı için EPG yükleniyor...",
                                        color = TiviTextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Detayları görmek için bir kanal seçin", color = TiviTextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ====================================================================
            // BÖLGE 4: ALT ZAMAN ÇİZELGELİ EPG TABLOSU VE KANAL LİSTESİ
            // ====================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Üst Araç Çubuğu (Toolbar)
                Surface(
                    color = TiviSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Genel Arama Aç/Kapa
                        IconButton(
                            onClick = { isChannelSearchOpen = !isChannelSearchOpen },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Arama", tint = if (isChannelSearchOpen) OpusCyan else Color.White, modifier = Modifier.size(17.dp))
                        }

                        if (isChannelSearchOpen) {
                            OutlinedTextField(
                                value = channelSearchQuery,
                                onValueChange = { channelSearchQuery = it },
                                placeholder = { Text("Kanal ara...", fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OpusCyan,
                                    unfocusedBorderColor = TiviCardBorder,
                                    focusedContainerColor = TiviCardDark,
                                    unfocusedContainerColor = TiviCardDark,
                                    focusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(34.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // EPG Yenile
                        IconButton(
                            onClick = {
                                activePreviewChannel?.let { viewModel.loadEpgForChannel(it.streamId) }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "EPG Yenile", tint = TiviTextSecondary, modifier = Modifier.size(17.dp))
                        }

                        // Tam Ekran Rehber Modu
                        IconButton(
                            onClick = { isFullscreenEpg = !isFullscreenEpg },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreenEpg) Icons.Default.VerticalAlignBottom else Icons.Default.CalendarViewWeek,
                                contentDescription = "Tam Ekran Rehber",
                                tint = if (isFullscreenEpg) OpusCyan else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Zaman Aralığı Seçici ("2 Saat", "4 Saat", "8 Saat")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(2 to "2 Saat", 4 to "4 Saat", 8 to "8 Saat").forEach { (hours, label) ->
                                val isRangeSelected = timeRangeHours == hours
                                Surface(
                                    onClick = { timeRangeHours = hours },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isRangeSelected) OpusCyan.copy(alpha = 0.2f) else TiviCardDark,
                                    border = BorderStroke(1.dp, if (isRangeSelected) OpusCyan else TiviCardBorder),
                                    modifier = Modifier.dpadFocusable(focusedBorderColor = OpusCyan)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isRangeSelected) OpusCyan else TiviTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // O Günün Tarihi
                        Text(
                            text = currentDateStr,
                            color = TiviTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // "< Şimdi >" Butonu (Tıklanınca anlık saat çizgisine kaydırır)
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    timelineScrollState.animateScrollTo(currentMinuteOffsetDp.value.toInt() * 2)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = OpusCyan.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, OpusCyan),
                            modifier = Modifier.dpadFocusable(focusedBorderColor = OpusCyan)
                        ) {
                            Text(
                                text = "< Şimdi >",
                                color = OpusCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

                // EPG Tablosu: Sol Kanal Sütunu + Sağ Zaman Çizelgesi (Timeline Grid)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val channelsListState = rememberLazyListState()

                    // SOL KANAL SÜTUNU (~200dp)
                    Surface(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight(),
                        color = TiviSurface
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sütun Başlığı
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(TiviCardDark)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("KANAL", color = TiviTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            LazyColumn(
                                state = channelsListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(finalChannels, key = { "wide_${it.streamId}" }) { channel ->
                                    val isSelected = activePreviewChannel?.streamId == channel.streamId
                                    val isFavorite = favoriteIds.contains(channel.streamId.toString())
                                    val badges = extractQualityBadges(channel.name)

                                    Surface(
                                        onClick = { viewModel.previewChannel(channel) },
                                        color = if (isSelected) OpusCyan.copy(alpha = 0.18f) else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .dpadFocusable(focusedBorderColor = OpusCyan)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(24.dp)
                                                        .clip(RoundedCornerShape(1.5.dp))
                                                        .background(OpusCyan)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }

                                            AsyncShimmerImage(
                                                model = channel.streamIcon,
                                                contentDescription = channel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black),
                                                placeholderIcon = Icons.Default.Tv
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = channel.name,
                                                    color = if (isSelected) OpusCyan else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (badges.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        badges.take(2).forEach { QualityBadge(it) }
                                                    }
                                                }
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleFavoriteLive(channel.streamId.toString()) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = null,
                                                    tint = if (isFavorite) TiviGold else TiviTextMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }

                    VerticalDivider(color = TiviCardBorder, thickness = 1.dp)

                    // SAĞ ZAMAN ÇİZELGESİ (TIMELINE GRID)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(timelineScrollState)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(totalTimelineWidthDp)
                                .fillMaxHeight()
                        ) {
                            // Saat Dilimleri Başlığı
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(TiviCardDark)
                            ) {
                                for (slot in 0 until totalHalfHours) {
                                    val slotMinuteTotal = (currentHour * 60) + (slot * 30)
                                    val slotH = (slotMinuteTotal / 60) % 24
                                    val slotM = slotMinuteTotal % 60
                                    val timeLabel = String.format("%02d:%02d", slotH, slotM)

                                    Box(
                                        modifier = Modifier
                                            .width(slotWidthDp)
                                            .fillMaxHeight()
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(timeLabel, color = TiviTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Kanal Program Satırları
                            LazyColumn(
                                state = channelsListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(finalChannels, key = { "grid_${it.streamId}" }) { channel ->
                                    val epg = epgSummaryMap[channel.streamId]
                                    val progTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
                                    val progTime = epg?.getFormattedTime() ?: "Canlı Akış"

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // O anki Canlı Program Kutusu
                                        Surface(
                                            onClick = {
                                                viewModel.previewChannel(channel)
                                                if (epg != null) onSelectProgramDetail(channel, epg)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = TiviCardDark,
                                            border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .width(slotWidthDp * 1.5f)
                                                .fillMaxHeight()
                                                .dpadFocusable(focusedBorderColor = OpusCyan)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(TiviAccentLive)
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("CANLI", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = progTitle,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(progTime, color = TiviTextSecondary, fontSize = 9.sp)
                                            }
                                        }

                                        // Sonraki Program Kutusu
                                        Surface(
                                            onClick = { viewModel.previewChannel(channel) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = TiviCard,
                                            border = BorderStroke(0.5.dp, TiviCardBorder),
                                            modifier = Modifier
                                                .width(slotWidthDp * 1.2f)
                                                .fillMaxHeight()
                                                .dpadFocusable(focusedBorderColor = OpusCyan)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text("Sonraki Kuşak", color = TiviTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Yayın Akışı", color = TiviTextMuted, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.25f), thickness = 0.5.dp)
                                }
                            }
                        }

                        // PARLAK CAMGÖBEĞİ / NEON ZAMAN ÇİZGİSİ (#00E5FF) & SAAT BALONU
                        Box(
                            modifier = Modifier
                                .offset(x = currentMinuteOffsetDp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(OpusCyan)
                        ) {
                            // Üstündeki Saat Balonu
                            Box(
                                modifier = Modifier
                                    .offset(x = (-22).dp, y = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OpusCyan)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentTimeStr,
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// DİKEY TELEFON DÜZENİ (PORTRAIT MOBILE LAYOUT)
// ====================================================================
@OptIn(UnstableApi::class)
@Composable
fun LiveTvPortraitLayout(
    viewModel: MainViewModel,
    categories: List<LiveCategory>,
    allCategories: List<LiveCategory>,
    selectedCategory: String,
    displayedChannels: List<LiveStream>,
    activePreviewChannel: LiveStream?,
    isMiniPlayerActive: Boolean,
    epgSummaryMap: Map<Int, EpgProgram>,
    favoriteIds: Set<String>,
    recentChannels: List<LiveStream>,
    currentSort: SortOption,
    isLoading: Boolean,
    searchQuery: String,
    isParentalUnlocked: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onOpenCategoryManager: () -> Unit,
    onOpenParentalDialog: () -> Unit,
    onSelectProgramDetail: (LiveStream, EpgProgram) -> Unit
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isEpgTimelineMode by remember { mutableStateOf(false) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarExpanded) 125.dp else 46.dp,
        animationSpec = tween(220),
        label = "SidebarWidth"
    )

    var visibleLimit by remember(displayedChannels) { mutableStateOf(40) }
    val pagedChannels = remember(displayedChannels, visibleLimit) {
        displayedChannels.take(visibleLimit)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // DİKEY CANLI MİNİ-PLAYER ÖNİZLEME
        AnimatedVisibility(
            visible = isMiniPlayerActive && activePreviewChannel != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            activePreviewChannel?.let { channel ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .clickable { viewModel.expandMiniPlayerToFullscreen() }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                player = viewModel.playerManager.initializePlayer()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TiviAccentLive)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("CANLI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.expandMiniPlayerToFullscreen() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Tam Ekran", tint = OpusCyan, modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { viewModel.closeMiniPlayer() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // TIVIMATE SOL DİKEY KATEGORİ SÜTUNU & SAĞ KANAL LİSTESİ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // SOL PANEL: DİKEY KATEGORİ LİSTESİ
            Surface(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight(),
                color = TiviSurface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSidebarExpanded = !isSidebarExpanded }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isSidebarExpanded) Arrangement.SpaceBetween else Arrangement.Center
                    ) {
                        if (isSidebarExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("KATEGORİLER", color = TiviTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = onOpenCategoryManager, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Tune, contentDescription = "Yönetim", tint = OpusCyan, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Icon(
                            imageVector = if (isSidebarExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Aç/Kapa",
                            tint = OpusCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    HorizontalDivider(color = TiviCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Favoriler
                        item {
                            val isFavSelected = selectedCategory == "favorites"
                            Surface(
                                onClick = { viewModel.selectedLiveCategory.value = "favorites" },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFavSelected) TiviGold.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isFavSelected) {
                                        Box(modifier = Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(1.5.dp)).background(TiviGold))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(Icons.Default.Star, contentDescription = null, tint = if (isFavSelected) TiviGold else TiviTextSecondary, modifier = Modifier.size(16.dp))
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Favoriler", color = if (isFavSelected) TiviGold else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Kategoriler
                        items(categories, key = { it.categoryId }) { category ->
                            val isSelected = category.categoryId == selectedCategory
                            val categoryIcon = getCategoryIcon(category.categoryName)

                            Surface(
                                onClick = { viewModel.loadLiveStreams(category.categoryId) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Box(modifier = Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(1.5.dp)).background(OpusCyan))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Icon(categoryIcon, contentDescription = null, tint = if (isSelected) OpusCyan else TiviTextSecondary, modifier = Modifier.size(16.dp))
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val countSuffix = if (category.itemCount > 0) " (${category.itemCount})" else ""
                                        Text("${category.categoryName}$countSuffix", color = if (isSelected) OpusCyan else Color.White, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        // Ebeveyn Kilidi
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = {
                                    if (isParentalUnlocked) viewModel.lockParental()
                                    else onOpenParentalDialog()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isParentalUnlocked) Color(0xFFEF5350).copy(alpha = 0.2f) else TiviCard,
                                border = BorderStroke(1.dp, if (isParentalUnlocked) Color(0xFFEF5350) else TiviCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (isParentalUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, tint = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary, modifier = Modifier.size(16.dp))
                                    if (isSidebarExpanded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isParentalUnlocked) "+18 Açık (Kilitle)" else "+18 Korumalı", color = if (isParentalUnlocked) Color(0xFFEF5350) else TiviTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VerticalDivider(color = TiviCardBorder.copy(alpha = 0.6f), thickness = 1.dp)

            // SAĞ PANEL: ARAMA & KANAL LİSTESİ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Arama ve Sıralama Çubuğu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Kanal veya program ara...", fontSize = 12.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = TiviTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OpusCyan,
                            unfocusedBorderColor = TiviCardBorder,
                            focusedContainerColor = TiviCard,
                            unfocusedContainerColor = TiviCard,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sıralama
                    Box {
                        Surface(
                            onClick = { isSortMenuOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            color = TiviCard,
                            border = BorderStroke(1.dp, TiviCardBorder),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sort, contentDescription = "Sırala", tint = OpusCyan, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (currentSort) {
                                        SortOption.DEFAULT -> "Varsayılan"
                                        SortOption.NAME_ASC -> "A - Z"
                                        SortOption.NAME_DESC -> "Z - A"
                                        SortOption.ADDED_DESC -> "Eklenme"
                                        SortOption.RATING_DESC -> "Kanal No"
                                    },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isSortMenuOpen,
                            onDismissRequest = { isSortMenuOpen = false },
                            modifier = Modifier.background(TiviSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Varsayılan Sıra", color = if (currentSort == SortOption.DEFAULT) OpusCyan else Color.White, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Reorder, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setLiveSort(SortOption.DEFAULT)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("A - Z (İsme Göre)", color = if (currentSort == SortOption.NAME_ASC) OpusCyan else Color.White, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setLiveSort(SortOption.NAME_ASC)
                                    isSortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Z - A (İsme Göre Ters)", color = if (currentSort == SortOption.NAME_DESC) OpusCyan else Color.White, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TiviTextSecondary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.setLiveSort(SortOption.NAME_DESC)
                                    isSortMenuOpen = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // TV Rehberi / Liste Görünümü
                    Surface(
                        onClick = { isEpgTimelineMode = !isEpgTimelineMode },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isEpgTimelineMode) OpusCyan.copy(alpha = 0.2f) else TiviCard,
                        border = BorderStroke(1.dp, if (isEpgTimelineMode) OpusCyan else TiviCardBorder),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isEpgTimelineMode) Icons.Default.CalendarViewWeek else Icons.Default.ViewList,
                                contentDescription = "Görünüm",
                                tint = if (isEpgTimelineMode) OpusCyan else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEpgTimelineMode) "Rehber" else "Liste",
                                color = if (isEpgTimelineMode) OpusCyan else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Son İzlenenler (Recents Bar)
                if (recentChannels.isNotEmpty() && searchQuery.isEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        items(recentChannels, key = { "recent_${it.streamId}" }) { channel ->
                            Surface(
                                onClick = { viewModel.previewChannel(channel) },
                                shape = RoundedCornerShape(8.dp),
                                color = TiviCard,
                                border = BorderStroke(1.dp, TiviCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.Black),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = channel.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OpusCyan)
                        }
                    } else if (displayedChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "\"$searchQuery\" için kanal bulunamadı." else "Bu kategoride yayın yok.",
                                    color = TiviTextSecondary,
                                    fontSize = 13.sp
                                )
                                if (searchQuery.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    GlobalSearchOptionsCard(
                                        query = searchQuery,
                                        currentScope = "live",
                                        isAllCategory = selectedCategory == "all",
                                        onSearchAllLive = { viewModel.searchAcrossAllLive(searchQuery) },
                                        onSearchAllVod = { viewModel.searchAcrossAllVod(searchQuery) },
                                        onSearchAllSeries = { viewModel.searchAcrossAllSeries(searchQuery) }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = pagedChannels,
                                key = { "${it.streamId}_${it.num}" },
                                contentType = { "live_channel" }
                            ) { channel ->
                                val isFavorite = favoriteIds.contains(channel.streamId.toString())
                                val isCurrentPlaying = activePreviewChannel?.streamId == channel.streamId && isMiniPlayerActive

                                if (isEpgTimelineMode) {
                                    EpgTimelineRow(
                                        channel = channel,
                                        epg = epgSummaryMap[channel.streamId],
                                        isFavorite = isFavorite,
                                        isPlaying = isCurrentPlaying,
                                        onLoadEpg = { viewModel.loadEpgSummary(channel.streamId) },
                                        onChannelClick = { viewModel.previewChannel(channel) },
                                        onProgramClick = { onSelectProgramDetail(channel, it) },
                                        onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                                    )
                                } else {
                                    EpgChannelCard(
                                        channel = channel,
                                        epg = epgSummaryMap[channel.streamId],
                                        isFavorite = isFavorite,
                                        isPlaying = isCurrentPlaying,
                                        onLoadEpg = { viewModel.loadEpgSummary(channel.streamId) },
                                        onChannelClick = { viewModel.previewChannel(channel) },
                                        onPlayFullscreen = { viewModel.playLiveChannel(channel) },
                                        onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                                    )
                                }
                            }

                            if (visibleLimit < displayedChannels.size) {
                                item {
                                    LaunchedEffect(Unit) { visibleLimit += 30 }
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = OpusCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpgChannelCard(
    channel: LiveStream,
    epg: EpgProgram? = null,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onLoadEpg: () -> Unit = {},
    onChannelClick: () -> Unit,
    onPlayFullscreen: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    LaunchedEffect(channel.streamId) {
        onLoadEpg()
    }

    val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
    val programTime = epg?.getFormattedTime() ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "Canlı Yayın")
    val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else 0.45f)
    val badges = extractQualityBadges(channel.name)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.12f) else TiviCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isPlaying) BorderStroke(1.5.dp, OpusCyan) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChannelClick() }
            .dpadFocusable(focusedBorderColor = OpusCyan)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncShimmerImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F141F))
                        .padding(3.dp),
                    placeholderIcon = Icons.Default.Tv
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            color = if (isPlaying) OpusCyan else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (badges.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            badges.take(2).forEach { QualityBadge(it) }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = programTitle,
                        color = TiviTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(text = programTime, color = TiviTextMuted, fontSize = 10.sp)
                }

                IconButton(onClick = onPlayFullscreen) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Tam Ekran Oynat", tint = OpusCyan, modifier = Modifier.size(28.dp))
                }

                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) TiviGold else TiviTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = if (isPlaying) OpusCyan else TiviPrimary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun EpgTimelineRow(
    channel: LiveStream,
    epg: EpgProgram?,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onLoadEpg: () -> Unit,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit,
    onFavoriteToggle: () -> Unit
) {
    LaunchedEffect(channel.streamId) {
        if (epg == null) onLoadEpg()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.16f) else TiviCard),
        border = BorderStroke(1.dp, if (isPlaying) OpusCyan else TiviCardBorder.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .dpadFocusable(focusedBorderColor = OpusCyan)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onChannelClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncShimmerImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black),
                    placeholderIcon = Icons.Default.Tv
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = if (isPlaying) OpusCyan else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                }

                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(22.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) TiviGold else TiviTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            VerticalDivider(color = TiviCardBorder, thickness = 1.dp)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (epg != null) onProgramClick(epg) else onChannelClick()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (epg != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(TiviAccentLive)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("CANLI", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = epg.getDecodedTitle(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = epg.getFormattedTime(), color = TiviTextSecondary, fontSize = 10.sp)
                        }

                        LinearProgressIndicator(
                            progress = { epg.getProgressFraction() },
                            color = OpusCyan,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                        )

                        Text(
                            text = if (!epg.description.isNullOrBlank()) epg.description!! else "Detaylar için dokunun...",
                            color = TiviTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Elektronik program rehberi (EPG) alınıyor...", color = TiviTextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("all") || lower.contains("tüm") -> Icons.Default.AllInclusive
        lower.contains("favori") -> Icons.Default.Star
        lower.contains("spor") || lower.contains("sport") -> Icons.Default.SportsSoccer
        lower.contains("sinema") || lower.contains("film") || lower.contains("movie") -> Icons.Default.Movie
        lower.contains("belgesel") || lower.contains("doc") -> Icons.Default.Public
        lower.contains("haber") || lower.contains("news") -> Icons.Default.Newspaper
        lower.contains("çocuk") || lower.contains("kid") || lower.contains("anim") -> Icons.Default.ChildCare
        lower.contains("müzik") || lower.contains("music") -> Icons.Default.MusicNote
        lower.contains("ulusal") || lower.contains("genel") -> Icons.Default.Tv
        else -> Icons.Default.LiveTv
    }
}
