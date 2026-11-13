package com.tivimax.player.ui.vm

/**
 * G-034: Film/dizi ızgaralarının ve ana sayfa şeritlerinin kaydırma konumunu bellekte tutar.
 * Detay/oynatıcı ekranı açılınca ana ekran yeniden çizilir; geri dönüşte aynı konum ve (TV'de) aynı afiş odağı
 * buradan geri yüklenir. DataStore'a yazılmaz; uygulama kapanınca ya da liste değişince sıfırlanır.
 *
 * Anahtar biçimi: "vod:<kategoriId>", "series:<kategoriId>", "home:<şerit adı>", "fav:<sekme>".
 */
class CatalogPositionStore {
    data class Saved(val firstIndex: Int, val offset: Int, val visibleLimit: Int)

    private val positions = HashMap<String, Saved>()

    /** Detayı açılan öğenin ızgaradaki/şeritteki anahtarı (TV'de dönüşte odak ona verilir). */
    var lastOpenedItemKey: String? = null

    fun save(key: String, firstIndex: Int, offset: Int, visibleLimit: Int) {
        positions[key] = Saved(firstIndex, offset, visibleLimit)
    }

    fun get(key: String): Saved? = positions[key]

    /** Aktif liste değişince ya da oturum sıfırlanınca çağrılır. */
    fun clear() {
        positions.clear()
        lastOpenedItemKey = null
    }
}
