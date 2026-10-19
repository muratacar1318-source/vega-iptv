package com.tivimax.player.ui.live.modern

import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable

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
        if (searchQuery.isBlank()) categories
        else {
            categories.filter {
                TurkishStringNormalizer.matches(it.categoryName, searchQuery)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TiviSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TiviTextMuted) },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
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
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${categories.size} Kategori Mevcut",
                        color = OpusCyan,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Kategori İçi Arama Çubuğu
            OutlinedTextField(
                keyboardOptions = tvKeyboardOptions(),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Kategori ara...",
                        color = TiviTextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = OpusCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Temizle",
                                tint = TiviTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TiviCard,
                    unfocusedContainerColor = TiviCard,
                    focusedBorderColor = OpusCyan,
                    unfocusedBorderColor = TiviCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.tvOpenKeyboardOnOk()
                    .fillMaxWidth()
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Kategori Listesi
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = filteredCategories.distinctBy { it.categoryId },
                    key = { it.categoryId }
                ) { category ->
                    val isSelected = category.categoryId == selectedCategory

                    Surface(
                        onClick = {
                            onCategorySelected(category.categoryId)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) OpusCyan.copy(alpha = 0.16f) else TiviCard,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) OpusCyan else TiviCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusable(
                                focusedBorderColor = OpusCyan,
                                shape = RoundedCornerShape(10.dp),
                                onClick = {
                                    onCategorySelected(category.categoryId)
                                    onDismiss()
                                }
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (category.categoryId == "favorites") Icons.Default.Star else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) OpusCyan else if (category.categoryId == "favorites") TiviGold else TiviTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )

                                Text(
                                    text = category.categoryName,
                                    color = if (isSelected) Color.White else TiviTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            if (category.itemCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) OpusCyan else Color.White.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        text = "${category.itemCount} kanal",
                                        color = if (isSelected) Color.Black else TiviTextSecondary,
                                        fontSize = 11.sp,
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
