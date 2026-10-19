package com.tivimax.player.ui.live.shelf

import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
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
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable

/**
 * Vega IPTV Raf Düzeni (Netflix Tarzı Yatay Raflı Canlı TV Görünümü).
 * Hero öne çıkan kanal alanı, dinamik raflar (Devam Et, Favoriler, Şu An Canlı ve Kategoriler),
 * hızlı arama ızgarası, yenileme ve düzen değiştirici içerir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfLiveLayout(
    allLiveStreams: List<LiveStream>,
    visibleCategories: List<LiveCategory>,
    recentChannels: List<LiveStream>,
    favoriteIds: Set<String>,
    epgSummaryMap: Map<Int, EpgProgram>,
    currentPlayingId: Int?,
    isLoading: Boolean,
    onChannelClick: (LiveStream) -> Unit,
    onFavoriteToggle: (LiveStream) -> Unit,
    onLoadEpg: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    BackHandler(enabled = searchQuery.isNotBlank()) {
        searchQuery = ""
    }

    // "Tümünü Gör" ile açılan kategori durumu
    var selectedSeeAllCategory by remember { mutableStateOf<Pair<String, List<LiveStream>>?>(null) }

    // Eğer "Tümünü Gör" açıksa o ekranı göster
    if (selectedSeeAllCategory != null) {
        val (catTitle, catChannels) = selectedSeeAllCategory!!
        SeeAllCategoryView(
            categoryTitle = catTitle,
            channels = catChannels,
            epgSummaryMap = epgSummaryMap,
            favoriteIds = favoriteIds,
            currentPlayingId = currentPlayingId,
            onChannelClick = onChannelClick,
            onFavoriteToggle = onFavoriteToggle,
            onLoadEpg = onLoadEpg,
            onBack = { selectedSeeAllCategory = null },
            modifier = modifier
        )
        return
    }

    // Arama filtreleme (searchQuery doluysa tek bir sonuç ızgarası gösterilir)
    val isSearching = searchQuery.isNotBlank()
    val searchResults = remember(allLiveStreams, searchQuery) {
        if (!isSearching) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            allLiveStreams.filter { it.name.lowercase().contains(q) }
        }
    }

    // Kategorilere göre gruplanmış kanallar
    val streamsByCategory = remember(allLiveStreams) {
        allLiveStreams.groupBy { it.categoryId }
    }

    // Favori kanallar listesi
    val favoriteChannels = remember(allLiveStreams, favoriteIds) {
        allLiveStreams.filter { favoriteIds.contains(it.streamId.toString()) }
    }

    // "Şu An Canlı" popüler/akışı olan kanallar (EPG özeti olan veya ilk kanallar)
    val nowPlayingChannels = remember(allLiveStreams, epgSummaryMap) {
        val withEpg = allLiveStreams.filter { epgSummaryMap.containsKey(it.streamId) }
        if (withEpg.isNotEmpty()) withEpg else allLiveStreams.take(20)
    }

    // Hero Kartı için Hedef Kanal: Son izlenen, yoksa ilk favori, yoksa ilk kanal
    val heroChannel = remember(recentChannels, favoriteChannels, allLiveStreams) {
        recentChannels.firstOrNull() ?: favoriteChannels.firstOrNull() ?: allLiveStreams.firstOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // ====================================================================
        // ÜST BAR: Başlık, Arama, Yenile, Klasik Düzene Geç İkonu
        // ====================================================================
        Surface(
            color = TiviSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sol: Logo & Başlık
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = OpusCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Canlı TV",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = OpusCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OpusCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "RAF DÜZENİ",
                            color = OpusCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Sağ: Hızlı İşlem Butonları (Yenile & Klasik Düzene Geçiş)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(38.dp)
                            .dpadFocusable(focusedBorderColor = OpusCyan, onClick = onRefresh)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Düzen Değiştirici İkon (Klasik Düzene Geç)
                    IconButton(
                        onClick = onToggleLayout,
                        modifier = Modifier
                            .size(38.dp)
                            .dpadFocusable(focusedBorderColor = OpusCyan, onClick = onToggleLayout)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = "Klasik Düzene Geç",
                            tint = OpusCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ====================================================================
        // ARAMA ALANI (CANLI FİLTRELEME)
        // ====================================================================
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.live_shelf_search_hint),
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
            keyboardOptions = tvKeyboardOptions(KeyboardOptions(imeAction = ImeAction.Search)),
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
            modifier = Modifier.tvOpenKeyboardOnOk()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(48.dp)
        )

        // ====================================================================
        // ARAMA SONUÇLARI IZGARASI VEYA RAFLAR
        // ====================================================================
        if (isSearching) {
            // Arama yapılıyorsa: Raflar yerine tek sonuç ızgarası
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aramanızla eşleşen canlı kanal bulunamadı.",
                        color = TiviTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = searchResults,
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
        } else if (isLoading && allLiveStreams.isEmpty()) {
            // Yükleme durumu: Shimmer iskelet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OpusCyan, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Kanallar hazırlanıyor...",
                        color = TiviTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (allLiveStreams.isEmpty()) {
            // Kanal yok durumu
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = TiviTextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.live_shelf_empty),
                        color = TiviTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // ====================================================================
            // NORMAL RAF DÜZENİ: HERO KARTI + YATAY RAFLAR
            // ====================================================================
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. HERO / ÖNE ÇIKAN KANAL ALANI (~210-220dp)
                if (heroChannel != null) {
                    item(key = "hero_section") {
                        HeroCard(
                            channel = heroChannel,
                            epg = epgSummaryMap[heroChannel.streamId],
                            isFavorite = favoriteIds.contains(heroChannel.streamId.toString()),
                            onPlay = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = { onLoadEpg(heroChannel.streamId) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // 2. DEVAM ET RAFI (Son İzlenen Kanallar)
                if (recentChannels.isNotEmpty()) {
                    item(key = "shelf_recent") {
                        ShelfRow(
                            title = stringResource(R.string.live_shelf_continue),
                            channels = recentChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair("Devam Et (Son İzlenenler)", recentChannels)
                            }
                        )
                    }
                }

                // 3. FAVORİLER RAFI (Varsa)
                if (favoriteChannels.isNotEmpty()) {
                    item(key = "shelf_favorites") {
                        ShelfRow(
                            title = stringResource(R.string.live_shelf_favorites),
                            channels = favoriteChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair("Favori Kanallar", favoriteChannels)
                            }
                        )
                    }
                }

                // 4. ŞU AN CANLI RAFI (Program Akışı Olan Kanallar)
                if (nowPlayingChannels.isNotEmpty()) {
                    item(key = "shelf_now_playing") {
                        ShelfRow(
                            title = stringResource(R.string.live_shelf_now_playing),
                            channels = nowPlayingChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair("Şu An Canlı", nowPlayingChannels)
                            }
                        )
                    }
                }

                // 5. KATEGORİ RAFLARI (Haber, Spor, Sinema vb.)
                // "all", "favorites", "recents" hariç kategoriler
                val contentCategories = visibleCategories.filter {
                    it.categoryId != "all" && it.categoryId != "favorites" && it.categoryId != "recents"
                }

                items(
                    items = contentCategories,
                    key = { "shelf_cat_${it.categoryId}" }
                ) { cat ->
                    val catChannels = streamsByCategory[cat.categoryId] ?: emptyList()
                    if (catChannels.isNotEmpty()) {
                        ShelfRow(
                            title = cat.categoryName,
                            channels = catChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair(cat.categoryName, catChannels)
                            }
                        )
                    }
                }
            }
        }
    }
}
