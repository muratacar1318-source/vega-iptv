package com.tivimax.player.ui.live.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable

/**
 * "Tümünü Gör" tıklandığında açılan tam kategori ızgara/liste görünümü.
 * Geri tuşuna basıldığında raf düzenine geri döner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAllCategoryView(
    categoryTitle: String,
    channels: List<LiveStream>,
    epgSummaryMap: Map<Int, EpgProgram>,
    favoriteIds: Set<String>,
    currentPlayingId: Int?,
    onChannelClick: (LiveStream) -> Unit,
    onFavoriteToggle: (LiveStream) -> Unit,
    onLoadEpg: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Donanım veya sistem geri tuşunu yakala
    BackHandler {
        onBack()
    }

    var localQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val filteredList = remember(channels, localQuery) {
        if (localQuery.isBlank()) {
            channels
        } else {
            val q = localQuery.trim().lowercase()
            channels.filter { it.name.lowercase().contains(q) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // Üst Bar
        Surface(
            color = TiviSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.dpadFocusable(
                        focusedBorderColor = OpusCyan,
                        onClick = onBack
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri Dön",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = categoryTitle,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredList.size} Kanal",
                        color = OpusCyan,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Kategori İçi Arama Çubuğu
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            placeholder = {
                Text(
                    text = "$categoryTitle içinde ara...",
                    color = TiviTextMuted,
                    fontSize = 12.sp
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
                if (localQuery.isNotEmpty()) {
                    IconButton(onClick = { localQuery = "" }) {
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TiviCard,
                unfocusedContainerColor = TiviCard,
                focusedBorderColor = OpusCyan,
                unfocusedBorderColor = TiviCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(50.dp)
        )

        // Kanal Izgarası (Grid)
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.live_shelf_empty),
                    color = TiviTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 90.dp // Bottom bar arkasında kalmaması için
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredList,
                    key = { it.streamId }
                ) { channel ->
                    val isFav = favoriteIds.contains(channel.streamId.toString())
                    val isPlaying = currentPlayingId == channel.streamId

                    ShelfChannelCard(
                        channel = channel,
                        epg = epgSummaryMap[channel.streamId],
                        isFavorite = isFav,
                        isPlaying = isPlaying,
                        onChannelClick = { onChannelClick(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel) },
                        onLoadEpg = { onLoadEpg(channel.streamId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
