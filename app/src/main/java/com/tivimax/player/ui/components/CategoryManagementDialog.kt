package com.tivimax.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tivimax.player.ui.AdultFilter
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.*
import kotlinx.coroutines.delay

@Composable
fun CategoryManagementDialog(
    viewModel: MainViewModel,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var canInteract by remember { mutableStateOf(false) }
    val firstTabFocusRequester = remember { FocusRequester() }

    val liveCats by viewModel.liveCategories.collectAsState()
    val vodCats by viewModel.vodCategories.collectAsState()
    val seriesCats by viewModel.seriesCategories.collectAsState()
    val isParentalUnlocked by viewModel.isParentalUnlocked.collectAsState()

    val hiddenCats by viewModel.hiddenCategories.collectAsState()
    val pinnedCats by viewModel.pinnedCategories.collectAsState()

    val safeLiveCats = remember(liveCats, isParentalUnlocked) {
        liveCats
            .filter { it.categoryId != "all" && (isParentalUnlocked || !AdultFilter.isAdult(it.categoryName)) }
            .distinctBy { it.categoryId }
    }
    val safeVodCats = remember(vodCats, isParentalUnlocked) {
        vodCats
            .filter { it.categoryId != "all" && (isParentalUnlocked || !AdultFilter.isAdult(it.categoryName)) }
            .distinctBy { it.categoryId }
    }
    val safeSeriesCats = remember(seriesCats, isParentalUnlocked) {
        seriesCats
            .filter { it.categoryId != "all" && (isParentalUnlocked || !AdultFilter.isAdult(it.categoryName)) }
            .distinctBy { it.categoryId }
    }

    val currentRawCategories = remember(selectedTab, safeLiveCats, safeVodCats, safeSeriesCats) {
        when (selectedTab) {
            0 -> safeLiveCats.map { Triple(it.categoryId, it.categoryName, it.itemCount) }
            1 -> safeVodCats.map { Triple(it.categoryId, it.categoryName, it.itemCount) }
            else -> safeSeriesCats.map { Triple(it.categoryId, it.categoryName, it.itemCount) }
        }
    }

    val filteredCategories = remember(currentRawCategories, searchQuery, pinnedCats) {
        currentRawCategories
            .filter { searchQuery.isBlank() || it.second.contains(searchQuery, ignoreCase = true) }
            .distinctBy { it.first }
            .sortedWith(
                compareByDescending<Triple<String, String, Int>> { pinnedCats.contains(it.first) }
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (firstTabFocusRequester.requestFocusSafely()) break
            }
            delay(180)
            canInteract = true
        }
        Box(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .vegaPanel(VegaShapes.Hero)
                .onPreviewKeyEvent { event ->
                    !canInteract &&
                        event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP &&
                        when (event.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> true
                            else -> false
                        }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                VegaColors.Accent.copy(alpha = 0.12f),
                                Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
                    .padding(22.dp)
            ) {
                // Başlık ve Kapat Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = VegaShapes.Medium,
                            color = VegaColors.Accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.32f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Kategori yönetimi",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "İstediğin kategorileri gizle veya en üste sabitle",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    VegaIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Kapat",
                        onClick = onDismiss
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sekmeler (Canlı TV, Filmler, Diziler) — Tek odaklı TV uyumlu seçim çipleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VegaChoiceChip(
                        label = "Canlı TV (${safeLiveCats.size})",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(firstTabFocusRequester)
                    )
                    VegaChoiceChip(
                        label = "Filmler (${safeVodCats.size})",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    VegaChoiceChip(
                        label = "Diziler (${safeSeriesCats.size})",
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Arama Alanı
                OutlinedTextField(
                    keyboardOptions = tvKeyboardOptions(),
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Kategori ara…", fontSize = VegaText.Secondary, color = VegaColors.TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VegaColors.Accent) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .dpadFocusable(
                                        shape = VegaShapes.Pill,
                                        focusedBorderColor = VegaColors.FocusBorder,
                                        onClick = { searchQuery = "" }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Temizle",
                                    tint = VegaColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VegaColors.FocusBorder,
                        unfocusedBorderColor = VegaColors.Border,
                        focusedTextColor = VegaColors.TextPrimary,
                        unfocusedTextColor = VegaColors.TextPrimary,
                        focusedContainerColor = VegaColors.SurfaceElevated,
                        unfocusedContainerColor = VegaColors.Card
                    ),
                    modifier = Modifier
                        .tvOpenKeyboardOnOk()
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = VegaShapes.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Toplu Aksiyon Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VegaButton(
                        text = "Tümünü göster",
                        variant = VegaButtonVariant.SECONDARY,
                        icon = Icons.Default.Visibility,
                        onClick = {
                            viewModel.setAllCategoriesHidden(currentRawCategories.map { it.first }, isHidden = false)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VegaButton(
                        text = "Tümünü gizle",
                        variant = VegaButtonVariant.SECONDARY,
                        icon = Icons.Default.VisibilityOff,
                        onClick = {
                            viewModel.setAllCategoriesHidden(currentRawCategories.map { it.first }, isHidden = true)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Kategori Listesi
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredCategories, key = { idx, item -> "cat_mgmt_${selectedTab}_${item.first}_$idx" }) { _, (catId, catName, count) ->
                        val isHidden = hiddenCats.contains(catId)
                        val isPinned = pinnedCats.contains(catId)

                        CategoryManagementRow(
                            name = catName,
                            count = count,
                            isHidden = isHidden,
                            isPinned = isPinned,
                            onTogglePin = { viewModel.toggleCategoryPinned(catId) },
                            onToggleVisibility = { viewModel.toggleCategoryHidden(catId, !isHidden) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Alt Kapat Butonu
                VegaButton(
                    text = "Kaydet ve kapat",
                    variant = VegaButtonVariant.PRIMARY,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CategoryManagementRow(
    name: String,
    count: Int,
    isHidden: Boolean,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    var pinFocused by remember { mutableStateOf(false) }
    var toggleFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Medium,
        color = if (isHidden) VegaColors.Background.copy(alpha = 0.7f) else VegaColors.Card,
        border = BorderStroke(
            1.dp,
            if (isPinned) VegaColors.Accent.copy(alpha = 0.5f) else VegaColors.Border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Sabitleme Butonu (Tek odaklı)
                Surface(
                    shape = VegaShapes.Small,
                    color = when {
                        pinFocused -> VegaColors.Accent.copy(alpha = 0.24f)
                        isPinned -> VegaColors.Accent.copy(alpha = 0.14f)
                        else -> VegaColors.SurfaceVariant
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            focusedBorderColor = VegaColors.FocusBorder,
                            onClick = onTogglePin,
                            onFocusStateChange = { pinFocused = it }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Sabitle",
                            tint = if (isPinned || pinFocused) VegaColors.Accent else VegaColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = if (isHidden) VegaColors.TextMuted else VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (count > 0) {
                        Text(
                            text = "$count içerik",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Görünürlük Anahtarı (Tek odaklı satır — Switch odaklanmaz, tek OK ile değişir)
            Surface(
                shape = VegaShapes.Small,
                color = if (toggleFocused) VegaColors.SurfaceElevated else Color.Transparent,
                modifier = Modifier.dpadFocusable(
                    shape = VegaShapes.Small,
                    focusedBorderColor = VegaColors.FocusBorder,
                    onClick = onToggleVisibility,
                    onFocusStateChange = { toggleFocused = it }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isHidden) "Gizli" else "Görünür",
                        color = if (isHidden) VegaColors.Danger else VegaColors.Success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = !isHidden,
                        onCheckedChange = null,
                        modifier = Modifier.focusProperties { canFocus = false },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VegaColors.OnAccent,
                            checkedTrackColor = VegaColors.Accent,
                            uncheckedThumbColor = VegaColors.TextMuted,
                            uncheckedTrackColor = VegaColors.Border
                        )
                    )
                }
            }
        }
    }
}
