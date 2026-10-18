package com.tivimax.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceModeRecommendTest {

    @Test
    fun testOfficialAndroidTv() {
        // 1. Resmi Android TV (hasLeanback = true, diğerleri telefon gibi) -> DeviceMode.TV
        val mode = recommendDeviceMode(
            isTvUiMode = false,
            hasLeanback = true,
            hasTvFeature = false,
            hasTouchscreen = true,
            hasAccelerometer = true,
            hasTelephony = true,
            smallestWidthDp = 411
        )
        assertEquals(DeviceMode.TV, mode)
    }

    @Test
    fun testUncertifiedTvBox() {
        // 2. Sertifikasız kutu: hasTouchscreen = true, hasAccelerometer = false, hasTelephony = false, smallestWidthDp = 540 -> TV
        val mode = recommendDeviceMode(
            isTvUiMode = false,
            hasLeanback = false,
            hasTvFeature = false,
            hasTouchscreen = true,
            hasAccelerometer = false,
            hasTelephony = false,
            smallestWidthDp = 540
        )
        assertEquals(DeviceMode.TV, mode)
    }

    @Test
    fun testWifiTablet() {
        // 3. Wi-Fi tablet: hasTouchscreen = true, hasAccelerometer = true, hasTelephony = false, smallestWidthDp = 800 -> TABLET
        val mode = recommendDeviceMode(
            isTvUiMode = false,
            hasLeanback = false,
            hasTvFeature = false,
            hasTouchscreen = true,
            hasAccelerometer = true,
            hasTelephony = false,
            smallestWidthDp = 800
        )
        assertEquals(DeviceMode.TABLET, mode)
    }

    @Test
    fun testPhone() {
        // 4. Telefon: hasTouchscreen = true, hasAccelerometer = true, hasTelephony = true, smallestWidthDp = 411 -> PHONE
        val mode = recommendDeviceMode(
            isTvUiMode = false,
            hasLeanback = false,
            hasTvFeature = false,
            hasTouchscreen = true,
            hasAccelerometer = true,
            hasTelephony = true,
            smallestWidthDp = 411
        )
        assertEquals(DeviceMode.PHONE, mode)
    }

    @Test
    fun testNoTouchscreen() {
        // 5. Dokunmatik yok: hasTouchscreen = false (diğerleri telefon gibi) -> TV
        val mode = recommendDeviceMode(
            isTvUiMode = false,
            hasLeanback = false,
            hasTvFeature = false,
            hasTouchscreen = false,
            hasAccelerometer = true,
            hasTelephony = true,
            smallestWidthDp = 411
        )
        assertEquals(DeviceMode.TV, mode)
    }
}
