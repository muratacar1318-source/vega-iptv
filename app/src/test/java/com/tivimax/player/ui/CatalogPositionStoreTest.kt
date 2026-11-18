package com.tivimax.player.ui

import com.tivimax.player.ui.vm.CatalogPositionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogPositionStoreTest {
    @Test
    fun `kaydedilen konum okunur`() {
        val store = CatalogPositionStore()
        store.save("vod:12", firstIndex = 87, offset = 40, visibleLimit = 220)
        val saved = store.get("vod:12")!!
        assertEquals(87, saved.firstIndex)
        assertEquals(40, saved.offset)
        assertEquals(220, saved.visibleLimit)
    }

    @Test
    fun `farkli anahtarlar karismaz`() {
        val store = CatalogPositionStore()
        store.save("vod:1", 10, 0, 40)
        store.save("series:1", 20, 5, 70)
        assertEquals(10, store.get("vod:1")!!.firstIndex)
        assertEquals(20, store.get("series:1")!!.firstIndex)
        assertNull(store.get("vod:2"))
    }

    @Test
    fun `ayni anahtara yeniden kaydetmek eskisini degistirir`() {
        val store = CatalogPositionStore()
        store.save("vod:1", 10, 0, 40)
        store.save("vod:1", 30, 2, 100)
        assertEquals(30, store.get("vod:1")!!.firstIndex)
        assertEquals(100, store.get("vod:1")!!.visibleLimit)
    }

    @Test
    fun `clear konumlari ve son acilan ogeyi siler`() {
        val store = CatalogPositionStore()
        store.save("vod:1", 10, 0, 40)
        store.lastOpenedItemKey = "5_5"
        store.clear()
        assertNull(store.get("vod:1"))
        assertNull(store.lastOpenedItemKey)
    }

    @Test
    fun `son acilan oge anahtari saklanir`() {
        val store = CatalogPositionStore()
        store.lastOpenedItemKey = "42_7"
        assertEquals("42_7", store.lastOpenedItemKey)
    }
}
