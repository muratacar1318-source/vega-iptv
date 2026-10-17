package com.tivimax.player.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.util.LocalIsTvDevice

/**
 * Buton Görsel Çeşitleri
 */
enum class VegaButtonVariant {
    PRIMARY,
    SECONDARY,
    DANGER,
    TEXT
}

/**
 * Vega Standart Buton Bileşeni
 * TV kumandası uyumlu, en az 44dp dokunma alanı ve Medium (12dp) köşe yuvarlaklığı.
 */
@Composable
fun VegaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VegaButtonVariant = VegaButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val isTv = LocalIsTvDevice.current
    val minHeight = if (isTv) VegaSizes.ButtonHeightTv else VegaSizes.ButtonHeight

    val containerColor = when (variant) {
        VegaButtonVariant.PRIMARY -> VegaColors.Accent
        VegaButtonVariant.SECONDARY -> VegaColors.SurfaceVariant
        VegaButtonVariant.DANGER -> VegaColors.Danger
        VegaButtonVariant.TEXT -> Color.Transparent
    }

    val contentColor = when (variant) {
        VegaButtonVariant.PRIMARY -> VegaColors.OnAccent
        VegaButtonVariant.SECONDARY -> VegaColors.TextPrimary
        VegaButtonVariant.DANGER -> Color.White
        VegaButtonVariant.TEXT -> VegaColors.Accent
    }

    val border = when (variant) {
        VegaButtonVariant.SECONDARY -> BorderStroke(1.dp, VegaColors.Border)
        else -> null
    }

    val focusedBorder = when (variant) {
        VegaButtonVariant.PRIMARY, VegaButtonVariant.DANGER -> Color.White
        else -> VegaColors.Accent
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = VegaShapes.Medium,
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        border = border,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = focusedBorder,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = VegaText.Body,
                fontWeight = if (variant == VegaButtonVariant.PRIMARY || variant == VegaButtonVariant.DANGER) FontWeight.Bold else FontWeight.SemiBold,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Vega Yuvarlak İkon Butonu
 * 44dp dokunma alanı, 20dp ikon. onOverlay = true olduğunda video üstü yarı şeffaf koyu zemin kullanır.
 */
@Composable
fun VegaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOverlay: Boolean = false,
    enabled: Boolean = true
) {
    val backgroundColor = if (onOverlay) VegaColors.OverlayPill else VegaColors.SurfaceVariant
    val iconTint = if (onOverlay) Color.White else VegaColors.TextPrimary

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = VegaShapes.Pill,
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.38f),
        modifier = modifier
            .size(VegaSizes.IconButton)
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = VegaColors.Accent,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Vega Seçim Çipi (Kategori, sekme ve filtreler için)
 * Seçili olduğunda %18 vurgulu zemin ve vurgulu çerçeve/yazı.
 */
@Composable
fun VegaChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val backgroundColor = if (selected) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant
    val borderColor = if (selected) VegaColors.Accent else VegaColors.Border
    val textColor = if (selected) VegaColors.Accent else VegaColors.TextSecondary

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = VegaShapes.Small,
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .defaultMinSize(minHeight = VegaSizes.MinTouch)
            .dpadFocusable(
                shape = VegaShapes.Small,
                focusedBorderColor = VegaColors.Accent,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (enabled) textColor else textColor.copy(alpha = 0.38f),
                fontSize = VegaText.Secondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

/**
 * Vega Ayar / Anahtar Satırı
 * Başlık, açıklama ve Accent renkli Switch. Kumandayla satıra basıldığında durum değişir.
 */
@Composable
fun VegaSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = VegaShapes.Medium,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.Accent,
                onClick = { if (enabled) onCheckedChange(!checked) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.SemiBold
                )
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VegaColors.OnAccent,
                    checkedTrackColor = VegaColors.Accent,
                    uncheckedThumbColor = VegaColors.TextSecondary,
                    uncheckedTrackColor = VegaColors.SurfaceVariant
                )
            )
        }
    }
}

/**
 * Vega Standart Bölüm Kartı
 * Card + Large (16dp) köşe + 1dp Border + 16dp iç boşluk.
 */
@Composable
fun VegaSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VegaComponentsPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VegaButton(
            text = "Birincil Buton",
            icon = Icons.Default.PlayArrow,
            onClick = {}
        )
        VegaButton(
            text = "İkincil Buton",
            variant = VegaButtonVariant.SECONDARY,
            onClick = {}
        )
        VegaButton(
            text = "Tehlike Butonu",
            variant = VegaButtonVariant.DANGER,
            onClick = {}
        )
        VegaButton(
            text = "Metin Butonu",
            variant = VegaButtonVariant.TEXT,
            onClick = {}
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VegaChoiceChip(label = "Seçili Çip", selected = true, onClick = {})
            VegaChoiceChip(label = "Normal Çip", selected = false, onClick = {})
            VegaIconButton(icon = Icons.Default.PlayArrow, contentDescription = null, onClick = {})
            VegaIconButton(icon = Icons.Default.PlayArrow, contentDescription = null, onClick = {}, onOverlay = true)
        }
        VegaSectionCard {
            VegaSwitchRow(
                title = "Şifreli bağlantı",
                description = "Açıkken bağlantın şifrelenir.",
                checked = true,
                onCheckedChange = {}
            )
        }
    }
}
