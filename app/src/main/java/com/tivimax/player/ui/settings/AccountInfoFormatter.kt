package com.tivimax.player.ui.settings

import com.tivimax.player.data.model.XtreamUserInfo
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

enum class AccountBadgeColor {
    GREEN,
    RED,
    AMBER,
    GRAY
}

data class AccountStatusResult(
    val label: String,
    val colorType: AccountBadgeColor
)

data class RemainingDaysResult(
    val label: String,
    val colorType: AccountBadgeColor
)

object AccountInfoFormatter {

    private val turkishLocale = Locale.forLanguageTag("tr")
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", turkishLocale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", turkishLocale)

    fun getUserAvatarLetter(username: String?): String {
        val trimmed = username?.trim()
        return if (trimmed.isNullOrEmpty()) {
            "K"
        } else {
            trimmed.take(1).uppercase(turkishLocale)
        }
    }

    fun accountStatus(
        userInfo: XtreamUserInfo?,
        nowSec: Long = System.currentTimeMillis() / 1000L
    ): AccountStatusResult {
        if (userInfo == null) {
            return AccountStatusResult("Bilinmiyor", AccountBadgeColor.GRAY)
        }

        val rawStatus = userInfo.status?.trim()
        val expSec = userInfo.expDate?.trim()?.toLongOrNull()
        val isExpInPast = expSec != null && expSec > 0 && expSec <= nowSec

        if (rawStatus.isNullOrEmpty()) {
            return if (isExpInPast) {
                AccountStatusResult("Süresi doldu", AccountBadgeColor.RED)
            } else {
                AccountStatusResult("Bilinmiyor", AccountBadgeColor.GRAY)
            }
        }

        if (rawStatus.equals("Banned", ignoreCase = true) || rawStatus.equals("Disabled", ignoreCase = true)) {
            return AccountStatusResult("Askıya alındı", AccountBadgeColor.RED)
        }

        if (rawStatus.equals("Expired", ignoreCase = true) || isExpInPast) {
            return AccountStatusResult("Süresi doldu", AccountBadgeColor.RED)
        }

        if (rawStatus.equals("Active", ignoreCase = true)) {
            return AccountStatusResult("Aktif", AccountBadgeColor.GREEN)
        }

        return AccountStatusResult(rawStatus, AccountBadgeColor.GRAY)
    }

    fun formatExpDate(
        expDateStr: String?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (expDateStr.isNullOrBlank() || expDateStr == "0" || expDateStr.equals("null", ignoreCase = true)) {
            return "Süresiz"
        }
        val sec = expDateStr.trim().toLongOrNull() ?: return "Süresiz"
        if (sec <= 0) return "Süresiz"

        return try {
            Instant.ofEpochSecond(sec).atZone(zoneId).format(dateFormatter)
        } catch (_: Exception) {
            "Süresiz"
        }
    }

    fun formatRemainingDays(
        expDateStr: String?,
        nowSec: Long = System.currentTimeMillis() / 1000L
    ): RemainingDaysResult? {
        if (expDateStr.isNullOrBlank() || expDateStr == "0" || expDateStr.equals("null", ignoreCase = true)) {
            return null
        }
        val sec = expDateStr.trim().toLongOrNull() ?: return null
        if (sec <= 0) return null

        val diffSec = sec - nowSec
        if (diffSec <= 0) {
            return RemainingDaysResult("Süresi doldu", AccountBadgeColor.RED)
        }

        val days = diffSec / 86400L
        return when {
            days == 0L -> RemainingDaysResult("Bugün sona eriyor", AccountBadgeColor.RED)
            days < 7L -> RemainingDaysResult("$days gün kaldı", AccountBadgeColor.RED)
            days < 30L -> RemainingDaysResult("$days gün kaldı", AccountBadgeColor.AMBER)
            else -> RemainingDaysResult("$days gün kaldı", AccountBadgeColor.GREEN)
        }
    }

    fun calculateSubscriptionProgress(
        createdAtStr: String?,
        expDateStr: String?,
        nowSec: Long = System.currentTimeMillis() / 1000L
    ): Float? {
        val createdSec = createdAtStr?.trim()?.toLongOrNull() ?: return null
        val expSec = expDateStr?.trim()?.toLongOrNull() ?: return null
        if (createdSec <= 0 || expSec <= 0 || expSec <= createdSec) return null

        val total = (expSec - createdSec).toFloat()
        val elapsed = (nowSec - createdSec).toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    fun formatCreatedAt(
        createdAtStr: String?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String? {
        if (createdAtStr.isNullOrBlank() || createdAtStr == "0" || createdAtStr.equals("null", ignoreCase = true)) {
            return null
        }
        val sec = createdAtStr.trim().toLongOrNull() ?: return null
        if (sec <= 0) return null

        return try {
            Instant.ofEpochSecond(sec).atZone(zoneId).format(dateFormatter)
        } catch (_: Exception) {
            null
        }
    }

    fun formatContentCount(count: Int?): String {
        if (count == null) return "—"
        return NumberFormat.getInstance(turkishLocale).format(count)
    }

    fun formatLastSync(
        lastSyncStr: String?,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (lastSyncStr.isNullOrBlank()) {
            return "Henüz yapılmadı"
        }

        val parsedDate = try {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            sdf.parse(lastSyncStr)
        } catch (_: Exception) {
            null
        }

        if (parsedDate != null) {
            val syncMs = parsedDate.time
            val formattedDate = try {
                Instant.ofEpochMilli(syncMs).atZone(zoneId).format(dateTimeFormatter)
            } catch (_: Exception) {
                lastSyncStr
            }

            val diffMs = (nowMs - syncMs).coerceAtLeast(0L)
            val diffMins = diffMs / (60 * 1000L)
            val diffHours = diffMins / 60L
            val diffDays = diffHours / 24L

            val relativeStr = when {
                diffMins < 1L -> "az önce"
                diffMins < 60L -> "$diffMins dk önce"
                diffHours < 24L -> "$diffHours saat önce"
                else -> "$diffDays gün önce"
            }

            return "$formattedDate · $relativeStr"
        }

        return lastSyncStr
    }
}
