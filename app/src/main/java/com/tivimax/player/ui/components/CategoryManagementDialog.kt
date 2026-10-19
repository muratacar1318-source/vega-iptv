package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

@Composable
fun CategoryManagementDialog(
    viewModel: MainViewModel,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }

    val liveCats by viewModel.liveCategories.collectAsState()
    val vodCats by viewModel.vodCategories.collectAsState()
    val seriesCats by viewModel.seriesCategories.collectAsState()

    val hiddenCats by viewModel.hiddenCategories.collectAsState()
    val pinnedCats by viewModel.pinnedCategories.collectAsState()

    val currentRawCategories = when (selectedTab) {
        0 -> liveCats.filter { it.categoryId != "all" }.map { Triple(it.categoryId, it.categoryName, it.itemCount) }
        1 -> vodCats.filter { it.categoryId != "all" }.map { Triple(it.categoryId, it.categoryName, it.itemCount) }
        else -> seriesCats.filter { it.categoryId != "all" }.map { Triple(it.categoryId, it.categoryName, it.itemCount) }
    }

    val filteredCategories = remember(currentRawCategories, searchQuery, pinnedCats) {
        currentRawCategories.filter {
            searchQuery.isBlank() || it.second.contains(searchQuery, ignoreCase = true)
        }.sortedWith(
            compareByDescending<Triple<String, String, Int>> { pinnedCats.contains(it.first) }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = VegaShapes.Large,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(1.dp, VegaColors.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Başlık & Kapat Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Kategori yönetimi",
                                color = VegaColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "İstediğin kategorileri gizle veya en üste sabitle",
                                color = VegaColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = VegaColors.TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sekmeler (Canlı TV, Film, Dizi)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = VegaColors.SurfaceVariant,
                    contentColor = VegaColors.Accent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = VegaColors.Accent
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Canlı TV (${liveCats.size.coerceAtLeast(1) - 1})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) VegaColors.Accent else VegaColors.TextSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Filmler (${vodCats.size.coerceAtLeast(1) - 1})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) VegaColors.Accent else VegaColors.TextSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "Diziler (${seriesCats.size.coerceAtLeast(1) - 1})",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) VegaColors.Accent else VegaColors.TextSecondary
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Arama ve Hızlı Aksiyonlar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        keyboardOptions = tvKeyboardOptions(),
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Kategori ara...", fontSize = 13.sp, color = VegaColors.TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VegaColors.TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = VegaColors.TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VegaColors.Accent,
                            unfocusedBorderColor = VegaColors.Border,
                            focusedTextColor = VegaColors.TextPrimary,
                            unfocusedTextColor = VegaColors.TextPrimary,
                            focusedContainerColor = VegaColors.Card,
                            unfocusedContainerColor = VegaColors.Card
                        ),
                        modifier = Modifier.tvOpenKeyboardOnOk()
                            .weight(1f)
                            .height(48.dp),
                        shape = VegaShapes.Medium
                    )

                    Button(
                        onClick = {
                            viewModel.setAllCategoriesHidden(currentRawCategories.map { it.first }, isHidden = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.SurfaceVariant),
                        shape = VegaShapes.Medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Tümünü göster", fontSize = 11.sp, color = VegaColors.TextPrimary)
                    }

                    Button(
                        onClick = {
                            viewModel.setAllCategoriesHidden(currentRawCategories.map { it.first }, isHidden = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.SurfaceVariant),
                        shape = VegaShapes.Medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Tümünü gizle", fontSize = 11.sp, color = VegaColors.Danger)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Kategori Listesi
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCategories, key = { it.first }) { (catId, catName, count) ->
                        val isHidden = hiddenCats.contains(catId)
                        val isPinned = pinnedCats.contains(catId)

                        Surface(
                            shape = VegaShapes.Medium,
                            color = if (isHidden) VegaColors.Background else VegaColors.Card,
                            border = BorderStroke(
                                1.dp,
                                if (isPinned) VegaColors.Accent.copy(alpha = 0.5f) else VegaColors.Border
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Sabitleme İkonu
                                    IconButton(
                                        onClick = { viewModel.toggleCategoryPinned(catId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Sabitle",
                                            tint = if (isPinned) VegaColors.Accent else VegaColors.TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = catName,
                                            color = if (isHidden) VegaColors.TextMuted else VegaColors.TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (count > 0) {
                                            Text(
                                                text = "$count içerik",
                                                color = VegaColors.TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                Row(
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
                                        onCheckedChange = { isVisible ->
                                            viewModel.toggleCategoryHidden(catId, !isVisible)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
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

                Spacer(modifier = Modifier.height(14.dp))

                // Alt Kapat Butonu
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent),
                    shape = VegaShapes.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Kaydet ve kapat", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VegaColors.OnAccent)
                }
            }
        }
    }
}
