package com.tivimax.player.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// VARSAYILAN KULLANILAN RENKLER (DEĞİŞKENLER)
// Theme.kt içinden dinamik olarak atanacak ancak 
// eski kodları bozmamak adına ColorSaver statik yapısı kullanılabilir.
// ==========================================

// Ortak / Sabit Renkler
val TiviGold = Color(0xFFFFB300)            // IMDb/Puan Yıldızı
val TiviGreen = Color(0xFF10B981)           // Aktif durum/yeşil
val TiviTextPrimary = Color(0xFFF8FAFC)
val TiviTextSecondary = Color(0xFF94A3B8)
val TiviTextMuted = Color(0xFF64748B)

// Tema Değişkenleri (Default Midnight Değerleri)
// Bunları dinamik olarak değiştirmek compose recomposition gerektirir, 
// o yüzden MaterialTheme.colorScheme.* kullanılması gerekir, ama var olan 
// static referansları kırmamak için static bırakıp Theme'de custom LocalColor sağlayabiliriz.
// En garantisi Tivi* değişkenlerini dinamik "ThemeColor" objesine çevirmektir.

// Eski yapıyı kırmamak için var olan statik referanslar:
var TiviBackground = Color(0xFF000000)      // Saf OLED Midnight Siyahı
var TiviSurface = Color(0xFF0F141C)         // Sinematik Gece Yüzeyi
var TiviSurfaceVariant = Color(0xFF161D28)  // Yarı Vurgulu Yüzey
var TiviCard = Color(0xFF0F141C)            // Standart Kart Rengi
var TiviCardBorder = Color(0xFF1E2638)      // İnce Zarif Kart Çerçevesi

var TiviPrimary = Color(0xFF6366F1)         // İndigo / Elektrik Mavisi Vurgu
var TiviPrimaryDark = Color(0xFF4F46E5)     // Koyu İndigo
var TiviSecondary = Color(0xFF6366F1)       // Standart İndigo Vurgu
var TiviAccentLive = Color(0xFFFF2A55)      // Canlı Yayın Kırmızısı
var OpusCyan = Color(0xFF00E5FF)            // Opus IPTV Neon Camgöbeği
var OpusPurple = Color(0xFF8B5CF6)          // Opus IPTV Neon Mor
var TiviCardDark = Color(0xFF121824)        // Koyu Kart Yüzeyi

val TiviOverlayDark = Color(0xCC0F141C)     // %80 Opak Buzlu Cam Paneli
val TiviGlassmorphism = Color(0x990F141C)

// ==========================================
// TEMA PALETLERİ
// ==========================================

object AppThemes {
    // 1. MIDNIGHT (Mevcut Orijinal OLED Tema)
    val MidnightPrimary = Color(0xFF6366F1)
    val MidnightCyan = Color(0xFF00E5FF)
    val MidnightBackground = Color(0xFF000000)
    val MidnightSurface = Color(0xFF0F141C)
    val MidnightCard = Color(0xFF0F141C)
    val MidnightCardDark = Color(0xFF121824)
    val MidnightCardBorder = Color(0xFF1E2638)
    val MidnightAccentLive = Color(0xFFFF2A55)

    // 2. OCEAN (Koyu Lacivert ve Turkuaz Tema)
    val OceanPrimary = Color(0xFF0EA5E9)
    val OceanCyan = Color(0xFF38BDF8)
    val OceanBackground = Color(0xFF020617)
    val OceanSurface = Color(0xFF0F172A)
    val OceanCard = Color(0xFF0F172A)
    val OceanCardDark = Color(0xFF1E293B)
    val OceanCardBorder = Color(0xFF334155)
    val OceanAccentLive = Color(0xFFF43F5E)

    // 3. FOREST (Koyu Zümrüt Yeşili Tema)
    val ForestPrimary = Color(0xFF10B981)
    val ForestCyan = Color(0xFF34D399)
    val ForestBackground = Color(0xFF022C22)
    val ForestSurface = Color(0xFF064E3B)
    val ForestCard = Color(0xFF064E3B)
    val ForestCardDark = Color(0xFF065F46)
    val ForestCardBorder = Color(0xFF047857)
    val ForestAccentLive = Color(0xFFEF4444)

    // 4. VIOLET (Ametist / Koyu Mor Tema)
    val VioletPrimary = Color(0xFF8B5CF6)
    val VioletCyan = Color(0xFFA78BFA)
    val VioletBackground = Color(0xFF2E1065)
    val VioletSurface = Color(0xFF4C1D95)
    val VioletCard = Color(0xFF4C1D95)
    val VioletCardDark = Color(0xFF5B21B6)
    val VioletCardBorder = Color(0xFF7C3AED)
    val VioletAccentLive = Color(0xFFF97316)
}
