package com.tivimax.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.theme.*
import com.tivimax.player.ui.design.VegaColors
import kotlinx.coroutines.launch

private val NeonCyan = VegaColors.Accent
private val DarkChipBg = VegaColors.Card
private val DarkChipBorder = VegaColors.Border

/**
 * Modern ve akıcı dikey konum kategori yatay gezinme çubuğu (Chips Row).
 * - "Tümü" seçeneği ve kategori çiplerini yatay kaydırılabilir olarak sunar.
 * - Seçili kategoriyi otomatik görünür alana kaydırır.
 * - Sonda bulunan "Kategoriler ▾" butonuyla arama destekli tam liste BottomSheet açar.
 */
@Composable
fun MediaCategoryNavigationBar(
    categories: List<CategoryUiItem>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    totalCount: Int,
    allLabel: String = "Tümü",
    defaultIcon: ImageVector = Icons.Default.Movie,
    onOpenAllCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeCategories = remember(categories) {
        categories
            .filter { it.id != "all" }
            .distinctBy { it.id }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId == "all" || selectedCategoryId.isEmpty()) {
            listState.animateScrollToItem(0)
        } else {
            val index = safeCategories.indexOfFirst { it.id == selectedCategoryId }
            if (index >= 0) {
                listState.animateScrollToItem(index + 1)
            }
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. "Tümü" Çipi
        item(key = "media_cat_all") {
            val isSelected = selectedCategoryId == "all" || selectedCategoryId.isEmpty()
            MediaChipItem(
                title = allLabel,
                count = totalCount,
                isSelected = isSelected,
                leadingIcon = defaultIcon,
                onClick = { onCategorySelected("all") }
            )
        }

        // 2. Kategori Çipleri
        items(safeCategories, key = { "media_cat_${it.id}" }) { cat ->
            val isSelected = cat.id == selectedCategoryId
            MediaChipItem(
                title = cat.name,
                count = cat.count,
                isSelected = isSelected,
                leadingIcon = cat.icon,
                onClick = { onCategorySelected(cat.id) }
            )
        }

        // 3. "Tüm Kategoriler ▾" Butonu
        item(key = "media_cat_more_btn") {
            Surface(
                shape = RoundedCornerShape(50),
                color = DarkChipBg,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onOpenAllCategories() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Tüm Kategoriler",
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Kategoriler",
                        color = NeonCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Kompakt Başlık Seçici Barı (DROPDOWN Modu İçin).
 * Ekranın üstünde tek satırlık zarif bir kart olarak seçili kategoriyi ve içerik sayısını gösterir.
 */
@Composable
fun MediaCategoryDropdownBar(
    selectedCategoryName: String,
    selectedCategoryCount: Int,
    onOpenCategorySheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkChipBg,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenCategorySheet() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Kategori",
                        color = TiviTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = selectedCategoryName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedCategoryCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "$selectedCategoryCount",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Kategoriyi Değiştir",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Tekil Kategori Çipi (Chip Item).
 */
@Composable
private fun MediaChipItem(
    title: String,
    count: Int,
    isSelected: Boolean,
    leadingIcon: ImageVector,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) NeonCyan else DarkChipBg,
        animationSpec = tween(150),
        label = "chip_bg"
    )
    val textColor = if (isSelected) Color.Black else Color.White
    val iconColor = if (isSelected) Color.Black else NeonCyan
    val border = if (isSelected) null else BorderStroke(1.dp, DarkChipBorder)

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = border,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = title,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            if (count > 0) {
                val badgeBg = if (isSelected) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f)
                val badgeText = if (isSelected) Color.Black else VegaColors.TextSecondary
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$count",
                        color = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Arama Destekli Kategori Seçici Bottom Sheet.
 * Çok sayıda kategori arasında anında filtreleme ve 1 dokunuşla seçim imkanı sunar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCategoryBottomSheet(
    title: String = "Kategoriler",
    categories: List<CategoryUiItem>,
    selectedCategoryId: String,
    totalCount: Int,
    onCategorySelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val safeCategories = remember(categories) {
        categories
            .filter { it.id != "all" }
            .distinctBy { it.id }
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(safeCategories, searchQuery) {
        if (searchQuery.isBlank()) {
            safeCategories
        } else {
            safeCategories.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = TiviBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Başlık
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${safeCategories.size} Kategori",
                    color = TiviTextSecondary,
                    fontSize = 12.sp
                )
            }

            // Arama Kutusu
            OutlinedTextField(
                keyboardOptions = tvKeyboardOptions(),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Kategori ara...", color = TiviTextSecondary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Temizle",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkChipBorder,
                    focusedContainerColor = DarkChipBg,
                    unfocusedContainerColor = DarkChipBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.tvOpenKeyboardOnOk()
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Kategori Listesi
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Tüm İçerikler" Seçeneği
                if (searchQuery.isBlank()) {
                    item(key = "bottom_sheet_cat_all") {
                        val isSelected = selectedCategoryId == "all" || selectedCategoryId.isEmpty()
                        CategorySheetItem(
                            name = "Tüm İçerikler",
                            count = totalCount,
                            icon = Icons.Default.GridView,
                            isSelected = isSelected,
                            onClick = {
                                onCategorySelected("all")
                                onDismissRequest()
                            }
                        )
                    }
                }

                items(filteredCategories, key = { "bottom_sheet_${it.id}" }) { cat ->
                    val isSelected = cat.id == selectedCategoryId
                    CategorySheetItem(
                        name = cat.name,
                        count = cat.count,
                        icon = cat.icon,
                        isSelected = isSelected,
                        onClick = {
                            onCategorySelected(cat.id)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySheetItem(
    name: String,
    count: Int,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = name,
                    color = if (isSelected) NeonCyan else Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (count > 0) {
                    Text(
                        text = "$count içerik",
                        color = TiviTextSecondary,
                        fontSize = 11.5.sp
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seçili",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dikey Kategori Navigasyon Modunu Değiştiren Hızlı Geçiş Butonu (Toggle).
 * Kullanıcının Yatay Çipler, Sol Panel veya Açılır Menü arasında tek tıkla geçiş yapmasını sağlar.
 */
@Composable
fun CategoryNavStyleToggleButton(
    currentStyle: String,
    onStyleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val (label, icon) = when (currentStyle) {
        UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR -> Pair("Sol Panel", Icons.AutoMirrored.Filled.ViewSidebar)
        UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN -> Pair("Açılır Menü", Icons.Default.ExpandCircleDown)
        else -> Pair("Çipler", Icons.AutoMirrored.Filled.ViewList)
    }

    Box(modifier = modifier) {
        ActionChipButton(
            text = label,
            icon = icon,
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(TiviSurface)
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "Yatay Çipler (Tam Ekran)",
                            color = if (currentStyle == UserPreferences.PORTRAIT_CAT_NAV_CHIPS) NeonCyan else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Geniş afiş ızgarası ve üstte kategori çipleri",
                            color = TiviTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = null,
                        tint = if (currentStyle == UserPreferences.PORTRAIT_CAT_NAV_CHIPS) NeonCyan else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onStyleChange(UserPreferences.PORTRAIT_CAT_NAV_CHIPS)
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "Klasik Sol Panel",
                            color = if (currentStyle == UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR) NeonCyan else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Solda sabit kategori sütunu ve sağda içerikler",
                            color = TiviTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewSidebar,
                        contentDescription = null,
                        tint = if (currentStyle == UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR) NeonCyan else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onStyleChange(UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR)
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "Kompakt Açılır Menü",
                            color = if (currentStyle == UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN) NeonCyan else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tek satırlık kategori seçici ve maksimum alan",
                            color = TiviTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ExpandCircleDown,
                        contentDescription = null,
                        tint = if (currentStyle == UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN) NeonCyan else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onStyleChange(UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN)
                    expanded = false
                }
            )
        }
    }
}
