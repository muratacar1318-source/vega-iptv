package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.SortOption
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable

/**
 * Canlı TV Sıralama Seçenekleri BottomSheet'i.
 * Varsayılan, A-Z, Z-A, Kanal Numarası ve Eklenme/Popülerlik seçeneklerini sunar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSortBottomSheet(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
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
            // Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Kanalları Sırala",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val sortItems = listOf(
                Triple(SortOption.DEFAULT, "Varsayılan Sıralama", "Sunucu orijinal liste sırası"),
                Triple(SortOption.NAME_ASC, "A – Z (İsme Göre)", "Kanal isimlerine göre alfabetik sıra"),
                Triple(SortOption.NAME_DESC, "Z – A (İsme Göre Ters)", "Z'den A'ya ters alfabetik sıra"),
                Triple(SortOption.RATING_DESC, "Kanal Numarası", "Kanal numaralarına göre küçükten büyüğe"),
                Triple(SortOption.ADDED_DESC, "En Çok İzlenen / Eklenme", "Popülerlik ve yeni eklenenler")
            )

            sortItems.forEach { (option, label, desc) ->
                val isSelected = currentSort == option

                Surface(
                    onClick = {
                        onSortSelected(option)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) OpusCyan.copy(alpha = 0.16f) else TiviCard,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) OpusCyan else TiviCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                onSortSelected(option)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = if (isSelected) OpusCyan else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                color = TiviTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSortSelected(option)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = OpusCyan)
                        )
                    }
                }
            }
        }
    }
}
