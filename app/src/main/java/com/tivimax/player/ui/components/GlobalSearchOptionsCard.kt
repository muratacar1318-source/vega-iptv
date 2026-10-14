package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.theme.*

@Composable
fun GlobalSearchOptionsCard(
    query: String,
    currentScope: String, // "live", "vod", "series"
    isAllCategory: Boolean,
    onSearchAllLive: () -> Unit,
    onSearchAllVod: () -> Unit,
    onSearchAllSeries: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TiviCard.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, TiviCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TiviPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "\"${query.take(20)}\" için Diğer Bölümlerde Ara",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // TÜM KANALLARDA ARA
                OutlinedButton(
                    onClick = onSearchAllLive,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (currentScope == "live" && isAllCategory) TiviPrimary.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (currentScope == "live" && isAllCategory) TiviPrimary else TiviCardBorder),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (currentScope == "live" && !isAllCategory) "Tüm Kanallarda" else "Tüm Kanallar",
                        fontSize = 9.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // TÜM FİLMLERDE ARA
                OutlinedButton(
                    onClick = onSearchAllVod,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (currentScope == "vod" && isAllCategory) TiviSecondary.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (currentScope == "vod" && isAllCategory) TiviSecondary else TiviCardBorder),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = TiviSecondary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (currentScope == "vod" && !isAllCategory) "Tüm Filmlerde" else "Tüm Filmler",
                        fontSize = 9.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // TÜM DİZİLERDE ARA
                OutlinedButton(
                    onClick = onSearchAllSeries,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (currentScope == "series" && isAllCategory) TiviGold.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (currentScope == "series" && isAllCategory) TiviGold else TiviCardBorder),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = TiviGold, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (currentScope == "series" && !isAllCategory) "Tüm Dizilerde" else "Tüm Diziler",
                        fontSize = 9.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
