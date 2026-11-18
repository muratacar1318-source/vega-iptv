package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.tv.StreamVaultSearchField

/**
 * "Tümü ▾" tıklandığında açılan tam kategori seçim BottomSheet'i.
 * Kategori içi anlık arama ve kategori kanal sayılarını listeler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectorBottomSheet(
    categories: List<LiveCategory>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(categories, searchQuery) {
        val unique = categories.distinctBy { it.categoryId }
        if (searchQuery.isBlank()) unique
        else {
            unique.filter {
                TurkishStringNormalizer.matches(it.categoryName, searchQuery)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VegaColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = VegaColors.Border) },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Başlık & Kapat
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Tüm Kategoriler",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${categories.size} kategori mevcut",
                        color = VegaColors.Accent,
                        fontSize = VegaText.Caption
                    )
                }

                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.SurfaceElevated,
                    border = BorderStroke(1.dp, VegaColors.Border),
                    modifier = Modifier
                        .size(40.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            focusedBorderColor = VegaColors.FocusBorder,
                            scale = 1.04f,
                            onClick = onDismiss
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = VegaColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TV-güvenli Kategori İçi Arama Çubuğu
            StreamVaultSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Kategori ara..."
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Kategori Listesi (Tek odak ve tıklama hedefi)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = filteredCategories,
                    key = { index, cat -> "cat_sheet_${cat.categoryId}_$index" }
                ) { _, category ->
                    val isSelected = category.categoryId == selectedCategory
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        shape = VegaShapes.Medium,
                        color = when {
                            isFocused -> VegaColors.SurfaceVariant
                            isSelected -> VegaColors.Accent.copy(alpha = 0.16f)
                            else -> VegaColors.SurfaceElevated
                        },
                        border = if (isFocused) null else BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) VegaColors.Accent else VegaColors.Border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.FocusBorder,
                                shape = VegaShapes.Medium,
                                scale = 1.02f,
                                onClick = {
                                    onCategorySelected(category.categoryId)
                                    onDismiss()
                                },
                                onFocusStateChange = { isFocused = it }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (category.categoryId == "favorites") Icons.Default.Star else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) VegaColors.Accent else if (category.categoryId == "favorites") VegaColors.Gold else VegaColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )

                                Text(
                                    text = category.categoryName,
                                    color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = VegaText.Body,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            if (category.itemCount > 0) {
                                Surface(
                                    shape = VegaShapes.Small,
                                    color = if (isSelected) VegaColors.Accent else VegaColors.SurfaceAccent
                                ) {
                                    Text(
                                        text = "${category.itemCount} kanal",
                                        color = if (isSelected) VegaColors.OnAccent else VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
