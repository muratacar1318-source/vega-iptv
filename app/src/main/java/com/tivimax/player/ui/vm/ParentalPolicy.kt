// G-054: Saf yardımcılar MainViewModel.kt'den taşındı. Paket bilerek com.tivimax.player.ui bırakıldı
// (ekran dosyaları ve testler aynı import ile kullanmaya devam eder).
package com.tivimax.player.ui

/** Ebeveyn PIN'i kararları (saf fonksiyonlar; birim testle doğrulanır). */
object ParentalPin {
    /** Kayıtlı PIN boşsa (belirlenmemiş) hiçbir giriş kilidi açmaz. */
    fun canUnlock(stored: String, entered: String): Boolean = stored.isNotEmpty() && entered == stored

    /** Yeni PIN geçerliyse null, değilse kullanıcıya gösterilecek hata metni. */
    fun validateNewPin(pin: String, repeat: String): String? = when {
        pin.length != 4 || !pin.all { it in '0'..'9' } -> "4 rakam gir"
        pin != repeat -> "PIN'ler aynı değil"
        else -> null
    }
}

/** G-052: +18 / gizli kategori süzmesi (saf fonksiyonlar; birim testle doğrulanır). */
object AdultFilter {
    private val keywords = listOf(
        "+18", "18+", "+ 18", "18 +", "xxx", "adult", "for adults", "yetişkin", "erotik", "porn"
    )
    private val turkish = java.util.Locale.forLanguageTag("tr-TR")

    /** Kategori adı yetişkin içeriği işaret ediyor mu? ("YETİŞKİN" gibi Türkçe büyük harfler de yakalanır.) */
    fun isAdult(name: String): Boolean {
        val forms = listOf(name.lowercase(turkish), name.lowercase(java.util.Locale.ROOT))
        return forms.any { lower -> keywords.any { lower.contains(it) } }
    }

    /** Engellenen kategori kimlikleri: kullanıcının gizledikleri ∪ (kilit kapalıysa) yetişkin olanlar. */
    fun blockedIds(categories: List<Pair<String, String>>, hidden: Set<String>, unlocked: Boolean): Set<String> {
        val adult = if (unlocked) emptySet() else categories.filter { it.first != "all" && isAdult(it.second) }.map { it.first }
        return hidden + adult
    }

    /** Engelli kategorideki kayıtları çıkarır; engel yoksa listeyi aynen döner. */
    fun <T> filterBlocked(items: List<T>, blocked: Set<String>, categoryOf: (T) -> String): List<T> =
        if (blocked.isEmpty()) items else items.filter { categoryOf(it) !in blocked }
}
