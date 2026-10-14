package com.tivimax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel

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
                .thenBy { it.second }
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
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F141C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638))
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
                            tint = OpusCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Kategori Yönetimi",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "İstediğiniz kategorileri gizleyin veya en üste sabitleyin",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sekmeler (Canlı TV, Film, Dizi)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF161D28),
                    contentColor = TiviPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = OpusCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Canlı TV (${liveCats.size.coerceAtLeast(1) - 1})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Filmler (${vodCats.size.coerceAtLeast(1) - 1})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Diziler (${seriesCats.size.coerceAtLeast(1) - 1})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
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
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Kategori ara...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Color(0xFF94A3B8))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OpusCyan,
                            unfocusedBorderColor = Color(0xFF1E2638),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF121824),
                            unfocusedContainerColor = Color(0xFF121824)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.setAllCategoriesHidden(currentRawCategories.map { it.first }, isHidden = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Tümünü Göster", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = {
                            viewModel.setAllCategoriesHidden(currentRawCategories.map { it.first }, isHidden = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B28)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Tümünü Gizle", fontSize = 11.sp, color = Color(0xFFFF6B81))
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isHidden) Color(0xFF12151B) else Color(0xFF161D28),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPinned) OpusCyan.copy(alpha = 0.5f) else Color(0xFF1E2638)
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
                                            imageVector = if (isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                            contentDescription = "Sabitle",
                                            tint = if (isPinned) OpusCyan else Color(0xFF475569),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = catName,
                                            color = if (isHidden) Color(0xFF64748B) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (count > 0) {
                                            Text(
                                                text = "$count içerik",
                                                color = Color(0xFF94A3B8),
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
                                        color = if (isHidden) Color(0xFFEF4444) else Color(0xFF10B981),
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
                                            checkedTrackColor = OpusCyan,
                                            uncheckedThumbColor = Color(0xFF64748B),
                                            uncheckedTrackColor = Color(0xFF1E2638)
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
                    colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Kaydet ve Kapat", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
