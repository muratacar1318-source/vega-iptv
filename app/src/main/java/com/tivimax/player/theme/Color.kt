package com.tivimax.player.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Vega IPTV Renk Paleti (Sinematik derinlik ve anlık reaktif tema desteği)
 */
data class VegaPalette(
    val id: String,
    val canvas: Color,
    val canvasElevated: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val surfaceAccent: Color,
    val border: Color,
    val brand: Color,
    val brandStrong: Color,
    val onAccent: Color,
    val live: Color,
    val success: Color,
    val gold: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val overlayPanel: Color,
    val overlayPill: Color
)

object AppThemes {
    // 1. KLASİK MAVİ (Varsayılan — rafine sinematik obsidiyen arduvaz + buz mavisi vurgu)
    val ClassicBlue = VegaPalette(
        id = "MIDNIGHT",
        canvas = Color(0xFF090C11),
        canvasElevated = Color(0xFF0D1118),
        surface = Color(0xFF121720),
        surfaceElevated = Color(0xFF181F2B),
        surfaceVariant = Color(0xFF212B3B),
        surfaceAccent = Color(0xFF283447),
        border = Color(0x1FFFFFFF),
        brand = Color(0xFF4DA3FF),
        brandStrong = Color(0xFF8AC4FF),
        onAccent = Color(0xFF041120),
        live = Color(0xFFFF5C61),
        success = Color(0xFF4FD39A),
        gold = Color(0xFFFFC766),
        textPrimary = Color(0xFFF4F6FA),
        textSecondary = Color(0xFFA8B3C5),
        textMuted = Color(0xFF6B778C),
        overlayPanel = Color(0xEB0B0F16),
        overlayPill = Color(0xC20E141D)
    )

    // 2. AMOLED SİYAH (Saf OLED siyah + camgöbeği)
    val AmoledBlack = VegaPalette(
        id = "AMOLED",
        canvas = Color(0xFF000000),
        canvasElevated = Color(0xFF05080D),
        surface = Color(0xFF0B1018),
        surfaceElevated = Color(0xFF121924),
        surfaceVariant = Color(0xFF192231),
        surfaceAccent = Color(0xFF212C3E),
        border = Color(0x229BB2D1),
        brand = Color(0xFF00E5FF),
        brandStrong = Color(0xFF66F0FF),
        onAccent = Color(0xFF001418),
        live = Color(0xFFFF3B5C),
        success = Color(0xFF10B981),
        gold = Color(0xFFFFC766),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        overlayPanel = Color(0xEB080C12),
        overlayPill = Color(0xC2080C12)
    )

    // 3. OKYANUS (Derin petrol + turkuaz)
    val Ocean = VegaPalette(
        id = "OCEAN",
        canvas = Color(0xFF041014),
        canvasElevated = Color(0xFF07181E),
        surface = Color(0xFF0B2128),
        surfaceElevated = Color(0xFF122F38),
        surfaceVariant = Color(0xFF1A414C),
        surfaceAccent = Color(0xFF22525F),
        border = Color(0x246FA3AC),
        brand = Color(0xFF67D9D5),
        brandStrong = Color(0xFF9AF2E8),
        onAccent = Color(0xFF002B2A),
        live = Color(0xFFFF6868),
        success = Color(0xFF7BD8A8),
        gold = Color(0xFFFFC875),
        textPrimary = Color(0xFFF0FAFA),
        textSecondary = Color(0xFFB9D0D3),
        textMuted = Color(0xFF7F9CA1),
        overlayPanel = Color(0xEB081B22),
        overlayPill = Color(0xC2081B22)
    )

    // 4. ORMAN (Derin zümrüt yeşili)
    val Forest = VegaPalette(
        id = "FOREST",
        canvas = Color(0xFF050F0B),
        canvasElevated = Color(0xFF081711),
        surface = Color(0xFF0E2018),
        surfaceElevated = Color(0xFF162E23),
        surfaceVariant = Color(0xFF204031),
        surfaceAccent = Color(0xFF28503D),
        border = Color(0x2473A68A),
        brand = Color(0xFF79D6A5),
        brandStrong = Color(0xFFA2F0C5),
        onAccent = Color(0xFF052817),
        live = Color(0xFFFF6868),
        success = Color(0xFF79D6A5),
        gold = Color(0xFFFFC66D),
        textPrimary = Color(0xFFF1F8F3),
        textSecondary = Color(0xFFBDCEC2),
        textMuted = Color(0xFF83978A),
        overlayPanel = Color(0xEB0A1A13),
        overlayPill = Color(0xC20A1A13)
    )

    // 5. MOR (M3 Koyu Grafit + Lavanta)
    val Violet = VegaPalette(
        id = "VIOLET",
        canvas = Color(0xFF08080C),
        canvasElevated = Color(0xFF0D0D13),
        surface = Color(0xFF13131B),
        surfaceElevated = Color(0xFF1B1B26),
        surfaceVariant = Color(0xFF242433),
        surfaceAccent = Color(0xFF2E2E42),
        border = Color(0x22A898CF),
        brand = Color(0xFFD0BCFF),
        brandStrong = Color(0xFFEADDFF),
        onAccent = Color(0xFF2B1459),
        live = Color(0xFFFF5C61),
        success = Color(0xFF4FD39A),
        gold = Color(0xFFFFC766),
        textPrimary = Color(0xFFF5F7FB),
        textSecondary = Color(0xFFC2C2C8),
        textMuted = Color(0xFF8A8A92),
        overlayPanel = Color(0xEB101018),
        overlayPill = Color(0xC2101018)
    )

    // 6. GÜN BATIMI (Sıcak kestane + şeftali)
    val Sunset = VegaPalette(
        id = "SUNSET",
        canvas = Color(0xFF130B0A),
        canvasElevated = Color(0xFF1B100E),
        surface = Color(0xFF241613),
        surfaceElevated = Color(0xFF33201A),
        surfaceVariant = Color(0xFF432920),
        surfaceAccent = Color(0xFF543326),
        border = Color(0x24997160),
        brand = Color(0xFFFFB27F),
        brandStrong = Color(0xFFFFD0AD),
        onAccent = Color(0xFF3A1809),
        live = Color(0xFFFF6969),
        success = Color(0xFF8BD3A8),
        gold = Color(0xFFFFCA77),
        textPrimary = Color(0xFFFFF6F0),
        textSecondary = Color(0xFFD9C4B8),
        textMuted = Color(0xFFA88D80),
        overlayPanel = Color(0xEB1E120F),
        overlayPill = Color(0xC21E120F)
    )

    // 7. GÜL (Derin mürdüm + gül pembesi)
    val Rose = VegaPalette(
        id = "ROSE",
        canvas = Color(0xFF120A11),
        canvasElevated = Color(0xFF1A0F19),
        surface = Color(0xFF241522),
        surfaceElevated = Color(0xFF321E30),
        surfaceVariant = Color(0xFF432841),
        surfaceAccent = Color(0xFF53324F),
        border = Color(0x249B7290),
        brand = Color(0xFFF0A4CC),
        brandStrong = Color(0xFFFFC6E2),
        onAccent = Color(0xFF3E122B),
        live = Color(0xFFFF6C70),
        success = Color(0xFF83D2AA),
        gold = Color(0xFFFFC878),
        textPrimary = Color(0xFFFFF5FA),
        textSecondary = Color(0xFFD9C1D0),
        textMuted = Color(0xFFA88C9E),
        overlayPanel = Color(0xEB1C101B),
        overlayPill = Color(0xC21C101B)
    )

    // 8. VİNTAGE (Koyu kahve + sıcak altın)
    val Vintage = VegaPalette(
        id = "VINTAGE",
        canvas = Color(0xFF100D0A),
        canvasElevated = Color(0xFF17130E),
        surface = Color(0xFF201A14),
        surfaceElevated = Color(0xFF2D251C),
        surfaceVariant = Color(0xFF3B3024),
        surfaceAccent = Color(0xFF4A3D2E),
        border = Color(0x249C886D),
        brand = Color(0xFFE5C07B),
        brandStrong = Color(0xFFF4D9A0),
        onAccent = Color(0xFF2B200B),
        live = Color(0xFFE86A5D),
        success = Color(0xFF8EC07C),
        gold = Color(0xFFE5C07B),
        textPrimary = Color(0xFFF8F2E8),
        textSecondary = Color(0xFFD1C4B2),
        textMuted = Color(0xFF9C8D79),
        overlayPanel = Color(0xEB1A1510),
        overlayPill = Color(0xC21A1510)
    )

    fun forName(themeName: String?): VegaPalette = when (themeName?.trim()?.uppercase()) {
        "AMOLED" -> AmoledBlack
        "OCEAN" -> Ocean
        "FOREST" -> Forest
        "VIOLET" -> Violet
        "SUNSET" -> Sunset
        "ROSE" -> Rose
        "VINTAGE" -> Vintage
        else -> ClassicBlue // "MIDNIGHT" / "CLASSIC_BLUE" varsayılan
    }

    // Eski referans uyumluluğu için
    val MidnightPrimary get() = ClassicBlue.brand
    val MidnightCyan get() = ClassicBlue.brand
    val MidnightBackground get() = ClassicBlue.canvas
    val MidnightSurface get() = ClassicBlue.surface
    val MidnightCard get() = ClassicBlue.surfaceElevated
    val MidnightCardDark get() = ClassicBlue.surfaceVariant
    val MidnightCardBorder get() = ClassicBlue.border
    val MidnightAccentLive get() = ClassicBlue.live
}

object ActivePaletteHolder {
    private val state: MutableState<VegaPalette> = mutableStateOf(AppThemes.ClassicBlue)
    var current: VegaPalette
        get() = state.value
        set(value) {
            state.value = value
        }
}

fun contentInkFor(color: Color): Color = if (color.luminance() > 0.179f) Color(0xFF041120) else Color.White

// Reaktif global renk referansları (tüm eski ekranlarla %100 uyumlu + tema değişince anında güncellenir)
val TiviGold: Color get() = ActivePaletteHolder.current.gold
val TiviGreen: Color get() = ActivePaletteHolder.current.success
val TiviTextPrimary: Color get() = ActivePaletteHolder.current.textPrimary
val TiviTextSecondary: Color get() = ActivePaletteHolder.current.textSecondary
val TiviTextMuted: Color get() = ActivePaletteHolder.current.textMuted

val TiviBackground: Color get() = ActivePaletteHolder.current.canvas
val TiviSurface: Color get() = ActivePaletteHolder.current.surface
val TiviSurfaceVariant: Color get() = ActivePaletteHolder.current.surfaceVariant
val TiviCard: Color get() = ActivePaletteHolder.current.surfaceElevated
val TiviCardBorder: Color get() = ActivePaletteHolder.current.border

val TiviPrimary: Color get() = ActivePaletteHolder.current.brand
val TiviPrimaryDark: Color get() = ActivePaletteHolder.current.brand
val TiviSecondary: Color get() = ActivePaletteHolder.current.brandStrong
val TiviAccentLive: Color get() = ActivePaletteHolder.current.live
val OpusCyan: Color get() = ActivePaletteHolder.current.brand
val OpusPurple: Color get() = Color(0xFF8B5CF6)
val TiviCardDark: Color get() = ActivePaletteHolder.current.surfaceVariant

val TiviOverlayDark: Color get() = ActivePaletteHolder.current.overlayPanel
val TiviGlassmorphism: Color get() = ActivePaletteHolder.current.overlayPill
