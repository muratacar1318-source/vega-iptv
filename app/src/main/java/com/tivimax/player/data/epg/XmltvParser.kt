package com.tivimax.player.data.epg

import com.tivimax.player.data.local.entity.EpgProgramEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * G-060: XMLTV (toplu program rehberi) akış ayrıştırıcısı.
 *
 * Dosya `XmlPullParser` ile akış halinde okunur; tamamı belleğe alınmaz. Yalnızca verilen pencereyle çakışan ve
 * (verildiyse) izin verilen kanallara ait programlar üretilir; bozuk kayıtlar atlanır. Ayrıştırıcı örneği dışarıdan
 * verilir (uygulamada `Xml.newPullParser()`, JVM testlerinde kxml2).
 */
object XmltvParser {
    /** Açıklamalar bu uzunlukta kesilir (veritabanı şişmesin). */
    const val MAX_DESCRIPTION_CHARS = 500

    /**
     * `yyyyMMddHHmmss Z` biçimini (ör. "20261113180000 +0300") epoch milisaniyeye çevirir.
     * Bölge yoksa UTC sayılır. Saniye isteğe bağlıdır ("yyyyMMddHHmm"). Bozuk metin → null.
     */
    fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        val digitsEnd = s.indexOfFirst { !it.isDigit() }.let { if (it == -1) s.length else it }
        val digits = s.substring(0, digitsEnd)
        if (digits.length != 14 && digits.length != 12) return null
        val zone = s.substring(digitsEnd).trim().replace(":", "")
        val offsetSeconds = when {
            zone.isEmpty() || zone.equals("Z", ignoreCase = true) || zone.equals("UTC", ignoreCase = true) -> 0
            zone.length == 5 && (zone[0] == '+' || zone[0] == '-') && zone.substring(1).all { it.isDigit() } -> {
                val h = zone.substring(1, 3).toInt()
                val m = zone.substring(3, 5).toInt()
                if (h > 18 || m > 59) return null
                (h * 3600 + m * 60) * if (zone[0] == '-') -1 else 1
            }
            else -> return null
        }
        return try {
            val year = digits.substring(0, 4).toInt()
            val month = digits.substring(4, 6).toInt()
            val day = digits.substring(6, 8).toInt()
            val hour = digits.substring(8, 10).toInt()
            val minute = digits.substring(10, 12).toInt()
            val second = if (digits.length == 14) digits.substring(12, 14).toInt() else 0
            val utcSeconds = LocalDateTime.of(year, month, day, hour, minute, second).toEpochSecond(ZoneOffset.UTC) - offsetSeconds
            utcSeconds * 1000L
        } catch (_: Exception) {
            null // sessiz: bozuk tarih, kayıt atlanır
        }
    }

    /** Program [windowStartMs, windowEndMs] penceresiyle çakışıyor mu? (saf; testlenir) */
    fun overlapsWindow(startMs: Long, endMs: Long, windowStartMs: Long, windowEndMs: Long): Boolean =
        endMs > windowStartMs && startMs < windowEndMs

    /** Kanal kimliği her yerde aynı biçimde saklanır/aranır: kırpılmış ve küçük harf. */
    fun normalizeChannelId(id: String): String = id.trim().lowercase()

    /**
     * [parser]'ı baştan sona okur; geçerli her program için [onProgram]'ı çağırır. Üretilen program sayısını döndürür.
     * [channelIds] null değilse yalnızca bu (normalize edilmiş) kimliklerdeki kanallar alınır.
     * XML yapısı bozuksa `XmlPullParserException` fırlatılır (çağıran işlemi geri alır).
     */
    suspend fun parse(
        parser: XmlPullParser,
        windowStartMs: Long,
        windowEndMs: Long,
        channelIds: Set<String>?,
        onProgram: suspend (EpgProgramEntity) -> Unit
    ): Int {
        var count = 0
        var seen = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                if (++seen % 2000 == 0) currentCoroutineContext().ensureActive()
                val program = readProgramme(parser, windowStartMs, windowEndMs, channelIds)
                if (program != null) {
                    onProgram(program)
                    count++
                }
            }
            event = parser.next()
        }
        return count
    }

    /** `<programme>` etiketini okur; bitişinde ayrıştırıcı `</programme>` üzerindedir. Atlanacaksa null. */
    private fun readProgramme(
        parser: XmlPullParser,
        windowStartMs: Long,
        windowEndMs: Long,
        channelIds: Set<String>?
    ): EpgProgramEntity? {
        val channel = parser.getAttributeValue(null, "channel")?.let { normalizeChannelId(it) }
        val startMs = parseTime(parser.getAttributeValue(null, "start"))
        val endMs = parseTime(parser.getAttributeValue(null, "stop"))

        val skip = channel.isNullOrEmpty() || startMs == null || endMs == null || endMs <= startMs ||
            !overlapsWindow(startMs, endMs, windowStartMs, windowEndMs) ||
            (channelIds != null && channel !in channelIds)
        if (skip) {
            skipTag(parser)
            return null
        }

        var title: String? = null
        var description: String? = null
        loop@ while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "title" -> {
                        val text = parser.nextText().trim()
                        if (title == null) title = text
                    }
                    "desc" -> {
                        val text = parser.nextText().trim()
                        if (description == null) description = text
                    }
                    else -> skipTag(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme") break@loop
                XmlPullParser.END_DOCUMENT -> break@loop
            }
        }
        if (title.isNullOrBlank()) return null
        return EpgProgramEntity(
            channelEpgId = channel!!,
            startMs = startMs!!,
            endMs = endMs!!,
            title = title,
            description = description?.takeIf { it.isNotBlank() }?.take(MAX_DESCRIPTION_CHARS)
        )
    }

    /** Ayrıştırıcı bir START_TAG üzerindeyken o etiketin sonuna (END_TAG) kadar ilerler. */
    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
