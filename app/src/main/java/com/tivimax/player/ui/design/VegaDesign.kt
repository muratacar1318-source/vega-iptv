package com.tivimax.player.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.theme.ActivePaletteHolder

/**
 * Vega IPTV Tasarım Tokenları (Tek Kaynak — StreamVault görsel derinliğine uyarlı)
 */
object VegaColors {
    // Vurgu (tek) - Birincil buton, seçili durum, kaydırıcı, anahtar, odak çerçevesi, ilerleme
    val Accent: Color get() = ActivePaletteHolder.current.brand
    val FocusBorder: Color get() = ActivePaletteHolder.current.brandStrong
    // Vurgu üstü metin - Vurgu renkli buton/çip içindeki yazı/ikon
    val OnAccent: Color get() = ActivePaletteHolder.current.onAccent

    // Durum renkleri
    val Danger: Color = Color(0xFFEF5350)
    val Live: Color get() = ActivePaletteHolder.current.live
    val Gold: Color get() = ActivePaletteHolder.current.gold
    val Success: Color get() = ActivePaletteHolder.current.success

    // Arka plan / yüzey / kart / çerçeve
    val Background: Color get() = ActivePaletteHolder.current.canvas
    val BackgroundElevated: Color get() = ActivePaletteHolder.current.canvasElevated
    val Surface: Color get() = ActivePaletteHolder.current.surface
    val SurfaceElevated: Color get() = ActivePaletteHolder.current.surfaceElevated
    val SurfaceVariant: Color get() = ActivePaletteHolder.current.surfaceVariant
    val SurfaceAccent: Color get() = ActivePaletteHolder.current.surfaceAccent
    val Card: Color get() = ActivePaletteHolder.current.surfaceElevated
    val Border: Color get() = ActivePaletteHolder.current.border

    // Metin renkleri
    val TextPrimary: Color get() = ActivePaletteHolder.current.textPrimary
    val TextSecondary: Color get() = ActivePaletteHolder.current.textSecondary
    val TextMuted: Color get() = ActivePaletteHolder.current.textMuted

    // Video üstü katmanlar (StreamVault yarı saydam gece camı)
    val OverlayPanel: Color get() = ActivePaletteHolder.current.overlayPanel
    val OverlayPill: Color get() = ActivePaletteHolder.current.overlayPill
    val Scrim: Color = Color(0x73000000)
}

/**
 * Köşe yuvarlaklığı tokenları (3 seviye + hap)
 */
object VegaShapes {
    val Small = RoundedCornerShape(8.dp)   // Çip, küçük buton, liste satırı, rozet
    val Medium = RoundedCornerShape(12.dp) // Buton, giriş kutusu, kutucuk, afiş
    val Large = RoundedCornerShape(16.dp)  // Kart, panel, diyalog, alt sayfa
    val Hero = RoundedCornerShape(20.dp)   // Vitrin / büyük bilgi panelleri
    val Pill = CircleShape                 // Hap düğme grupları, yuvarlak ikon düğmesi
}

/**
 * Boyut tokenları (minimum dokunma alanları ve standart yükseklikler)
 */
object VegaSizes {
    val ButtonHeight = 44.dp
    val ButtonHeightTv = 48.dp
    val MinTouch = 44.dp
    val IconButton = 44.dp
}

/**
 * Yazı ölçeği tokenları (sp)
 */
object VegaText {
    val ScreenTitle = 22.sp   // Ekran başlığı
    val SectionTitle = 17.sp  // Bölüm başlığı
    val BodyProminent = 15.sp // Vurgulu gövde
    val Body = 14.sp          // Gövde
    val Secondary = 13.sp     // İkincil
    val Caption = 11.sp       // Açıklama / rozet
}

/**
 * StreamVault imzası: Düşük maliyetli statik derin gece arka planı + sağ üst hafif ortam parıltısı.
 * TV cihazlarda blur maliyeti olmadan zengin derinlik sağlar.
 */
fun Modifier.vegaBackdrop(): Modifier = drawWithCache {
    val palette = ActivePaletteHolder.current
    val base = Brush.verticalGradient(
        colors = listOf(palette.canvasElevated, palette.canvas)
    )
    val ambient = Brush.radialGradient(
        colors = listOf(palette.brand.copy(alpha = 0.11f), Color.Transparent),
        center = Offset(size.width * 0.82f, -size.height * 0.18f),
        radius = size.width.coerceAtLeast(size.height) * 0.85f
    )
    onDrawBehind {
        drawRect(base)
        drawRect(ambient)
    }
}

/**
 * StreamVault imzası: Yuvarlatılmış hafif gradyanlı yüzey paneli + ince çerçeve.
 */
fun Modifier.vegaPanel(shape: Shape = VegaShapes.Large): Modifier =
    clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    ActivePaletteHolder.current.surfaceElevated.copy(alpha = 0.94f),
                    ActivePaletteHolder.current.surface.copy(alpha = 0.96f)
                )
            )
        )
        .border(1.dp, ActivePaletteHolder.current.border, shape)
