package com.tivimax.player.ui.player.components

import com.tivimax.player.data.model.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tam ekran Canlı TV oynatıcısı için tarih, saat ve kalan süre biçimlendirici.
 * Teknik tarih formatlarını ("2026-10-16 10:00:00") tamamen ayıklar;
 * kullanıcı dostu "10:00 – 11:00 • kalan 23 dk" formatı üretir.
 */
object LiveTimeFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    )

    /**
     * Program saat aralığını ve kalan süreyi döner:
     * Örnek: "10:00 – 11:00 • kalan 23 dk" veya "10:00 – 11:00"
     */
    fun formatProgramTime(epg: EpgProgram?): String {
        if (epg == null) return ""

        val (startStr, endStr, stopTimeMs) = parseTimes(epg)
        if (startStr.isBlank() && endStr.isBlank()) return ""

        val timeRange = when {
            startStr.isNotBlank() && endStr.isNotBlank() -> "$startStr – $endStr"
            startStr.isNotBlank() -> startStr
            else -> endStr
        }

        val nowMs = System.currentTimeMillis()
        val remainingPart = if (stopTimeMs > nowMs) {
            val remainingMins = (stopTimeMs - nowMs) / 60_000L
            if (remainingMins > 0) " • kalan $remainingMins dk" else " • son dakikalar"
        } else ""

        return "$timeRange$remainingPart"
    }

    /**
     * Sadece "10:00 – 11:00" saat aralığını döner.
     */
    fun formatTimeRange(epg: EpgProgram?): String {
        if (epg == null) return ""
        val (startStr, endStr, _) = parseTimes(epg)
        return when {
            startStr.isNotBlank() && endStr.isNotBlank() -> "$startStr – $endStr"
            startStr.isNotBlank() -> startStr
            else -> endStr
        }
    }

    /**
     * Programın yayınlanma ilerleme oranını 0.0f ile 1.0f arasında döner.
     * EPG yoksa veya zaman aralığı geçersizse null döner.
     */
    fun calculateProgress(epg: EpgProgram?, fallbackProgress: Float? = null): Float? {
        if (epg == null) return fallbackProgress
        val nowMs = System.currentTimeMillis()
        val startMs = getStartTimeMs(epg)
        val stopMs = getStopTimeMs(epg)

        return if (startMs > 0 && stopMs > startMs && nowMs in startMs..stopMs) {
            ((nowMs - startMs).toFloat() / (stopMs - startMs).toFloat()).coerceIn(0f, 1f)
        } else fallbackProgress
    }

    private fun parseTimes(epg: EpgProgram): Triple<String, String, Long> {
        var startStr = ""
        var endStr = ""
        var stopTimeMs = 0L

        // 1. Unix Timestamp'ler (start_timestamp / stop_timestamp)
        if (epg.startTimestamp > 0 && epg.stopTimestamp > 0) {
            startStr = timeFormat.format(Date(epg.startTimestamp * 1000L))
            endStr = timeFormat.format(Date(epg.stopTimestamp * 1000L))
            stopTimeMs = epg.stopTimestamp * 1000L
            return Triple(startStr, endStr, stopTimeMs)
        }

        // 2. String Tarih Alanları
        if (!epg.start.isNullOrBlank()) {
            startStr = extractOrParseTime(epg.start)
        }
        if (!epg.end.isNullOrBlank()) {
            endStr = extractOrParseTime(epg.end)
            stopTimeMs = parseToMillis(epg.end)
        }

        return Triple(startStr, endStr, stopTimeMs)
    }

    private fun extractOrParseTime(dateStr: String): String {
        val trimmed = dateStr.trim()
        if (trimmed.length >= 16 && trimmed.contains("-") && trimmed.contains(":")) {
            return trimmed.substring(11, 16)
        }
        if (trimmed.length == 5 && trimmed.contains(":")) {
            return trimmed
        }
        for (sdf in fullDateFormats) {
            try {
                val d = sdf.parse(trimmed)
                if (d != null) return timeFormat.format(d)
            } catch (_: Exception) {}
        }
        return ""
    }

    private fun parseToMillis(dateStr: String): Long {
        val trimmed = dateStr.trim()
        for (sdf in fullDateFormats) {
            try {
                val d = sdf.parse(trimmed)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return 0L
    }

    private fun getStartTimeMs(epg: EpgProgram): Long {
        if (epg.startTimestamp > 0) return epg.startTimestamp * 1000L
        if (!epg.start.isNullOrBlank()) return parseToMillis(epg.start)
        return 0L
    }

    private fun getStopTimeMs(epg: EpgProgram): Long {
        if (epg.stopTimestamp > 0) return epg.stopTimestamp * 1000L
        if (!epg.end.isNullOrBlank()) return parseToMillis(epg.end)
        return 0L
    }
}
