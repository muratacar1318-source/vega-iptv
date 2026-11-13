package com.tivimax.player.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * G-062: Son oynatma olaylarını bellekte tutan halka tampon (en fazla [MAX_EVENTS]).
 * Diske yazılmaz, uygulama kapanınca silinir. Kullanıcı "Sorun bildir" ile paylaşırsa rapora eklenir.
 *
 * Gizlilik: her satır [CrashLogger.sanitize]'dan geçer; adres, kullanıcı adı ve şifre yazılmaz.
 * Çağıranlar yine de ayrıntıya adres koymamalıdır (yalnızca hata türü, süre, sayaç gibi bilgiler).
 */
object PlaybackEventLog {
    const val MAX_EVENTS = 80

    private val events = ArrayDeque<String>()

    /** Bir olay ekler: "HH:mm:ss.SSS tür: ayrıntı". Dolunca en eski olay düşer. */
    @Synchronized
    fun add(kind: String, detail: String = "", nowMillis: Long = System.currentTimeMillis()) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(nowMillis))
        val line = if (detail.isBlank()) "$time $kind" else "$time $kind: $detail"
        events.addLast(CrashLogger.sanitize(line))
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    /** Eskiden yeniye sıralı olay satırları. */
    @Synchronized
    fun snapshot(): List<String> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }

    /** Paylaşım metni için "Son oynatma olayları" bölümü; günlük boşsa boş metin. */
    fun buildSection(): String {
        val lines = snapshot()
        if (lines.isEmpty()) return ""
        return "Son oynatma olayları\n" + lines.joinToString("\n")
    }
}
