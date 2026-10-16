package com.tivimax.player.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cihaz Kullanım Modları:
 * - PHONE: Dikey kilitli genel kullanım, video oynatıcıda otomatik yatay tam ekran.
 * - TABLET: Serbest sensör rotasyonu (360°), 600dp+ geniş ekran çok sütunlu master-detail düzen.
 * - TV: Yatay kilitli tam ekran (10-foot UI), D-pad kumanda odaklı gezinme.
 */
enum class DeviceMode(val title: String, val description: String, val badgeText: String) {
    PHONE("Telefon", "Dikey kullanım, içerik açılınca otomatik yatay", "Telefon (Dikey Öncelikli)"),
    TABLET("Tablet", "Geniş ekran düzeni, serbest döndürme", "Tablet (Serbest Döndürme)"),
    TV("TV (Televizyon)", "Kumanda ile kullanım, tam ekran düzen", "TV (10-Foot UI)")
}

val LocalDeviceMode = staticCompositionLocalOf { DeviceMode.PHONE }
val LocalIsTvDevice = staticCompositionLocalOf { false }
val LocalOrientationManager = staticCompositionLocalOf<OrientationManager?> { null }

fun Context.isTvDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    @Suppress("DEPRECATION")
    val hasTvFeature = packageManager.hasSystemFeature("android.hardware.type.television") || 
                       packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    val hasNoTouch = !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    return isTvMode || hasLeanback || hasTvFeature || hasNoTouch
}

/**
 * Cihazın donanım ve ekran özelliklerini analiz ederek önerilen DeviceMode'u tespit eder:
 * 1. Android TV / Leanback / No Touchscreen -> TV
 * 2. Smallest Screen Width >= 600dp -> TABLET
 * 3. Diğer standart mobil cihazlar -> PHONE
 */
fun Context.detectRecommendedDeviceMode(): DeviceMode {
    if (isTvDevice()) {
        return DeviceMode.TV
    }
    val swDp = resources.configuration.smallestScreenWidthDp
    if (swDp >= 600) {
        return DeviceMode.TABLET
    }
    return DeviceMode.PHONE
}
