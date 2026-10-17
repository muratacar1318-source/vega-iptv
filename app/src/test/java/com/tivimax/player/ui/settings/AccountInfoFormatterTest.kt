package com.tivimax.player.ui.settings

import com.tivimax.player.data.model.XtreamUserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AccountInfoFormatterTest {

    private val fixedZone = ZoneId.of("Europe/Istanbul")
    private val baseNowSec = 1773000000L // 18 March 2026

    @Test
    fun testFormatRemainingDays_Suresiz() {
        assertNull(AccountInfoFormatter.formatRemainingDays(null, baseNowSec))
        assertNull(AccountInfoFormatter.formatRemainingDays("", baseNowSec))
        assertNull(AccountInfoFormatter.formatRemainingDays("0", baseNowSec))
        assertNull(AccountInfoFormatter.formatRemainingDays("null", baseNowSec))
    }

    @Test
    fun testFormatRemainingDays_GecersizMetin() {
        assertNull(AccountInfoFormatter.formatRemainingDays("abc", baseNowSec))
        assertNull(AccountInfoFormatter.formatRemainingDays("-123", baseNowSec))
        assertNull(AccountInfoFormatter.formatRemainingDays("invalid_date", baseNowSec))
    }

    @Test
    fun testFormatRemainingDays_Gecmis() {
        val pastExp = (baseNowSec - 3600).toString()
        val result = AccountInfoFormatter.formatRemainingDays(pastExp, baseNowSec)
        assertNotNull(result)
        assertEquals("Süresi doldu", result?.label)
        assertEquals(AccountBadgeColor.RED, result?.colorType)
    }

    @Test
    fun testFormatRemainingDays_0Gun_BugunSonaEriyor() {
        val todayExp = (baseNowSec + 3600).toString() // 1 hour left (< 1 day)
        val result = AccountInfoFormatter.formatRemainingDays(todayExp, baseNowSec)
        assertNotNull(result)
        assertEquals("Bugün sona eriyor", result?.label)
        assertEquals(AccountBadgeColor.RED, result?.colorType)
    }

    @Test
    fun testFormatRemainingDays_6Gun() {
        val exp = (baseNowSec + 6 * 86400 + 100).toString() // 6 days left
        val result = AccountInfoFormatter.formatRemainingDays(exp, baseNowSec)
        assertNotNull(result)
        assertEquals("6 gün kaldı", result?.label)
        assertEquals(AccountBadgeColor.RED, result?.colorType)
    }

    @Test
    fun testFormatRemainingDays_29Gun() {
        val exp = (baseNowSec + 29 * 86400 + 100).toString() // 29 days left
        val result = AccountInfoFormatter.formatRemainingDays(exp, baseNowSec)
        assertNotNull(result)
        assertEquals("29 gün kaldı", result?.label)
        assertEquals(AccountBadgeColor.AMBER, result?.colorType)
    }

    @Test
    fun testFormatRemainingDays_200Gun() {
        val exp = (baseNowSec + 200 * 86400 + 100).toString() // 200 days left
        val result = AccountInfoFormatter.formatRemainingDays(exp, baseNowSec)
        assertNotNull(result)
        assertEquals("200 gün kaldı", result?.label)
        assertEquals(AccountBadgeColor.GREEN, result?.colorType)
    }

    @Test
    fun testFormatExpDate() {
        assertEquals("Süresiz", AccountInfoFormatter.formatExpDate(null))
        assertEquals("Süresiz", AccountInfoFormatter.formatExpDate("0"))
        assertEquals("Süresiz", AccountInfoFormatter.formatExpDate("null"))
        assertEquals("Süresiz", AccountInfoFormatter.formatExpDate("invalid"))

        // 1710240000 = 12 Mart 2024 (UTC) -> in Europe/Istanbul
        val dateStr = AccountInfoFormatter.formatExpDate("1710240000", fixedZone)
        assertTrue(dateStr.contains("Mart 2024"))
    }

    @Test
    fun testAccountStatus() {
        // Active and future
        val activeUser = XtreamUserInfo(status = "Active", expDate = (baseNowSec + 86400).toString())
        val resActive = AccountInfoFormatter.accountStatus(activeUser, baseNowSec)
        assertEquals("Aktif", resActive.label)
        assertEquals(AccountBadgeColor.GREEN, resActive.colorType)

        // Active but expired in past
        val expiredByDate = XtreamUserInfo(status = "Active", expDate = (baseNowSec - 100).toString())
        val resExpiredByDate = AccountInfoFormatter.accountStatus(expiredByDate, baseNowSec)
        assertEquals("Süresi doldu", resExpiredByDate.label)
        assertEquals(AccountBadgeColor.RED, resExpiredByDate.colorType)

        // Expired status
        val expiredStatus = XtreamUserInfo(status = "Expired")
        val resExpired = AccountInfoFormatter.accountStatus(expiredStatus, baseNowSec)
        assertEquals("Süresi doldu", resExpired.label)
        assertEquals(AccountBadgeColor.RED, resExpired.colorType)

        // Banned / Disabled status
        val bannedUser = XtreamUserInfo(status = "Banned")
        val resBanned = AccountInfoFormatter.accountStatus(bannedUser, baseNowSec)
        assertEquals("Askıya alındı", resBanned.label)
        assertEquals(AccountBadgeColor.RED, resBanned.colorType)

        val disabledUser = XtreamUserInfo(status = "Disabled")
        val resDisabled = AccountInfoFormatter.accountStatus(disabledUser, baseNowSec)
        assertEquals("Askıya alındı", resDisabled.label)
        assertEquals(AccountBadgeColor.RED, resDisabled.colorType)

        // Unknown / null
        val nullStatusUser = XtreamUserInfo(status = null, expDate = null)
        val resNull = AccountInfoFormatter.accountStatus(nullStatusUser, baseNowSec)
        assertEquals("Bilinmiyor", resNull.label)
        assertEquals(AccountBadgeColor.GRAY, resNull.colorType)

        // Other custom status
        val trialUser = XtreamUserInfo(status = "Pending")
        val resOther = AccountInfoFormatter.accountStatus(trialUser, baseNowSec)
        assertEquals("Pending", resOther.label)
        assertEquals(AccountBadgeColor.GRAY, resOther.colorType)
    }

    @Test
    fun testSubscriptionProgress() {
        val created = (baseNowSec - 500).toString()
        val exp = (baseNowSec + 500).toString()
        val progress = AccountInfoFormatter.calculateSubscriptionProgress(created, exp, baseNowSec)
        assertNotNull(progress)
        assertEquals(0.5f, progress ?: 0f, 0.01f)

        assertNull(AccountInfoFormatter.calculateSubscriptionProgress(null, exp, baseNowSec))
        assertNull(AccountInfoFormatter.calculateSubscriptionProgress(created, null, baseNowSec))
        assertNull(AccountInfoFormatter.calculateSubscriptionProgress("0", "0", baseNowSec))
    }

    @Test
    fun testFormatCreatedAt() {
        assertNull(AccountInfoFormatter.formatCreatedAt(null))
        assertNull(AccountInfoFormatter.formatCreatedAt("0"))
        assertNull(AccountInfoFormatter.formatCreatedAt("invalid"))

        val createdStr = AccountInfoFormatter.formatCreatedAt("1704240000", fixedZone)
        assertNotNull(createdStr)
        assertTrue(createdStr!!.contains("Ocak 2024"))
    }

    @Test
    fun testFormatContentCount() {
        assertEquals("—", AccountInfoFormatter.formatContentCount(null))
        assertEquals("0", AccountInfoFormatter.formatContentCount(0))
        val formatted = AccountInfoFormatter.formatContentCount(12345)
        assertTrue(formatted.contains("12") && formatted.contains("345"))
    }

    @Test
    fun testFormatLastSync() {
        assertEquals("Henüz yapılmadı", AccountInfoFormatter.formatLastSync(null))
        assertEquals("Henüz yapılmadı", AccountInfoFormatter.formatLastSync(""))

        // Format is dd.MM.yyyy HH:mm
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.OCTOBER, 17, 23, 56, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val syncDateStr = "17.10.2026 23:56"
        val nowMs = cal.timeInMillis + (2 * 60 * 60 * 1000L) // 2 hours later
        val formatted = AccountInfoFormatter.formatLastSync(syncDateStr, nowMs, fixedZone)
        assertTrue(formatted.contains("23:56"))
        assertTrue(formatted.contains("2 saat önce"))
    }

    @Test
    fun testGetUserAvatarLetter() {
        assertEquals("K", AccountInfoFormatter.getUserAvatarLetter(null))
        assertEquals("K", AccountInfoFormatter.getUserAvatarLetter(""))
        assertEquals("A", AccountInfoFormatter.getUserAvatarLetter("ahmet"))
        assertEquals("İ", AccountInfoFormatter.getUserAvatarLetter("istanbul"))
    }
}
