package com.tivimax.player.ui.live.modern

import java.util.Locale

/**
 * Türkçe karakter duyarsız arama yardımcı fonksiyonları.
 * 'İ'/'ı', 'Ş'/'ş', 'Ğ'/'ğ', 'Ü'/'ü', 'Ö'/'ö', 'Ç'/'ç' harflerini
 * hem Türkçe locale hem de diyatrikten arındırılmış biçimde karşılaştırır.
 */
object TurkishStringNormalizer {

    private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input.lowercase(TR_LOCALE)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .trim()
    }

    /**
     * Hedef metin aranan sorguyu içeriyor mu?
     * Hem standart küçük harf hem de Türkçe normalize edilmiş haliyle kontrol eder.
     */
    fun matches(target: String?, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (target.isNullOrBlank()) return false

        val cleanQuery = query.trim()
        if (target.contains(cleanQuery, ignoreCase = true)) return true

        val targetLowerTr = target.lowercase(TR_LOCALE)
        val queryLowerTr = cleanQuery.lowercase(TR_LOCALE)
        if (targetLowerTr.contains(queryLowerTr)) return true

        val normTarget = normalize(target)
        val normQuery = normalize(cleanQuery)
        return normTarget.contains(normQuery)
    }
}
