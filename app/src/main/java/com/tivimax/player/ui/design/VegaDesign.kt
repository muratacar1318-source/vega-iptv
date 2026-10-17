package com.tivimax.player.ui.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.theme.*

/**
 * Vega IPTV Tasarım Tokenları (Tek Kaynak)
 * docs/TASARIM-KURALLARI.md dosyasındaki kuralların kod karşılığıdır.
 */
object VegaColors {
    // Vurgu (tek) - Birincil buton, seçili durum, kaydırıcı, anahtar, odak çerçevesi, ilerleme
    val Accent: Color get() = OpusCyan
    // Vurgu üstü metin - Vurgu renkli buton/çip içindeki yazı/ikon
    val OnAccent: Color = Color.Black

    // Durum renkleri
    val Danger: Color = Color(0xFFEF5350)
    val Live: Color get() = TiviAccentLive
    val Gold: Color get() = TiviGold
    val Success: Color get() = TiviGreen

    // Arka plan / yüzey / kart / çerçeve
    val Background: Color get() = TiviBackground
    val Surface: Color get() = TiviSurface
    val SurfaceVariant: Color get() = TiviSurfaceVariant
    val Card: Color get() = TiviCard
    val Border: Color get() = TiviCardBorder

    // Metin renkleri
    val TextPrimary: Color get() = TiviTextPrimary
    val TextSecondary: Color get() = TiviTextSecondary
    val TextMuted: Color get() = TiviTextMuted

    // Video üstü katmanlar
    val OverlayPanel: Color = Color(0xF20F141C)  // %95 opak koyu
    val OverlayPill: Color = Color(0x8C0A0E17)   // %55 opak koyu
    val Scrim: Color = Color(0x73000000)         // %45 opak siyah
}

/**
 * Köşe yuvarlaklığı tokenları (3 seviye + hap)
 */
object VegaShapes {
    val Small = RoundedCornerShape(8.dp)   // Çip, küçük buton, liste satırı, rozet
    val Medium = RoundedCornerShape(12.dp) // Buton, giriş kutusu, kutucuk, afiş
    val Large = RoundedCornerShape(16.dp)  // Kart, panel, diyalog, alt sayfa
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
