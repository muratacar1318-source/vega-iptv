package com.tivimax.player.ui.series

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.catalog.VegaMediaCatalogScreen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

@Composable
fun SeriesScreen(viewModel: MainViewModel) {
    VegaMediaCatalogScreen(viewModel = viewModel)
}

@Composable
fun SeriesPosterCard(
    series: SeriesStream,
    onClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(VegaShapes.Small)
            ) {
                AsyncShimmerImage(
                    model = series.cover,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = series.name,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                lineHeight = 14.sp,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val releaseYear = series.releaseDate?.take(4)?.ifBlank { "" } ?: ""
            val genreText = series.genre?.split(",")?.firstOrNull()?.trim()?.ifBlank { "Dizi" } ?: "Dizi"
            val subText = if (releaseYear.isNotBlank()) "$releaseYear • $genreText" else genreText
            Text(
                text = subText,
                color = VegaColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
