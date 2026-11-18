package com.tivimax.player.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
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
 * TV kumandası uyumlu, tek odak hedefi (çift tıklama yok), en az 44dp dokunma alanı ve Medium (12dp) köşe yuvarlaklığı.
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
    var isFocused by remember { mutableStateOf(false) }

    val baseContainerColor = when (variant) {
        VegaButtonVariant.PRIMARY -> VegaColors.Accent
        VegaButtonVariant.SECONDARY -> if (isFocused) VegaColors.SurfaceElevated else VegaColors.SurfaceVariant
        VegaButtonVariant.DANGER -> VegaColors.Danger
        VegaButtonVariant.TEXT -> if (isFocused) VegaColors.Accent.copy(alpha = 0.14f) else Color.Transparent
    }

    val contentColor = when (variant) {
        VegaButtonVariant.PRIMARY -> VegaColors.OnAccent
        VegaButtonVariant.SECONDARY -> VegaColors.TextPrimary
        VegaButtonVariant.DANGER -> Color.White
        VegaButtonVariant.TEXT -> VegaColors.Accent
    }

    val border = when (variant) {
        VegaButtonVariant.SECONDARY -> if (!isFocused) BorderStroke(1.dp, VegaColors.Border) else null
        else -> null
    }

    val focusedBorder = when (variant) {
        VegaButtonVariant.PRIMARY, VegaButtonVariant.DANGER -> Color.White
        else -> VegaColors.FocusBorder
    }

    Surface(
        shape = VegaShapes.Medium,
        color = if (enabled) baseContainerColor else baseContainerColor.copy(alpha = 0.38f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        border = border,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .then(
                if (enabled) {
                    Modifier.dpadFocusable(
                        shape = VegaShapes.Medium,
                        focusedBorderColor = focusedBorder,
                        onClick = onClick,
                        onFocusStateChange = { isFocused = it }
                    )
                } else {
                    Modifier
                }
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
 * Tek odak hedefi, 44dp dokunma alanı, 20dp ikon. onOverlay = true olduğunda video üstü yarı şeffaf koyu zemin kullanır.
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
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isFocused && onOverlay -> VegaColors.Accent.copy(alpha = 0.28f)
        isFocused -> VegaColors.SurfaceElevated
        onOverlay -> VegaColors.OverlayPill
        else -> VegaColors.SurfaceVariant
    }
    val iconTint = when {
        isFocused -> VegaColors.Accent
        onOverlay -> Color.White
        else -> VegaColors.TextPrimary
    }

    Surface(
        shape = VegaShapes.Pill,
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.38f),
        border = if (!isFocused && !onOverlay) BorderStroke(1.dp, VegaColors.Border) else null,
        modifier = modifier
            .size(VegaSizes.IconButton)
            .then(
                if (enabled) {
                    Modifier.dpadFocusable(
                        shape = VegaShapes.Pill,
                        focusedBorderColor = VegaColors.FocusBorder,
                        onClick = onClick,
                        onFocusStateChange = { isFocused = it }
                    )
                } else {
                    Modifier
                }
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
 * Tek odak hedefi; seçili olduğunda %18 vurgulu zemin ve vurgulu çerçeve/yazı.
 */
@Composable
fun VegaChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        selected && isFocused -> VegaColors.Accent.copy(alpha = 0.26f)
        selected -> VegaColors.Accent.copy(alpha = 0.18f)
        isFocused -> VegaColors.SurfaceElevated
        else -> VegaColors.SurfaceVariant
    }
    val borderColor = if (selected) VegaColors.Accent else VegaColors.Border
    val textColor = when {
        selected -> VegaColors.Accent
        isFocused -> VegaColors.TextPrimary
        else -> VegaColors.TextSecondary
    }

    Surface(
        shape = VegaShapes.Small,
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.38f),
        border = if (!isFocused) BorderStroke(1.dp, borderColor) else null,
        modifier = modifier
            .defaultMinSize(minHeight = VegaSizes.MinTouch)
            .then(
                if (enabled) {
                    Modifier.dpadFocusable(
                        shape = VegaShapes.Small,
                        focusedBorderColor = VegaColors.FocusBorder,
                        onClick = onClick,
                        onFocusStateChange = { isFocused = it }
                    )
                } else {
                    Modifier
                }
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
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Vega Ayar / Anahtar Satırı
 * Başlık, açıklama ve Accent renkli Switch. Odak yalnızca satırdadır; tek OK basışında durum değişir.
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
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Medium,
        color = if (isFocused) VegaColors.SurfaceElevated else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.dpadFocusable(
                        shape = VegaShapes.Medium,
                        focusedBorderColor = VegaColors.FocusBorder,
                        onClick = { onCheckedChange(!checked) },
                        onFocusStateChange = { isFocused = it }
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    color = if (enabled) VegaColors.TextPrimary else VegaColors.TextMuted,
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
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.focusProperties { canFocus = false },
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
 * StreamVault yüzey derinliği + Large (16dp) köşe + 1dp Border + 16dp iç boşluk.
 */
@Composable
fun VegaSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .vegaPanel(VegaShapes.Large)
            .padding(16.dp),
        content = content
    )
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
