package com.tivimax.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultFilterTest {
    @Test
    fun `yetiskin kategori adlari taninir`() {
        listOf("XXX Filmler", "Yetişkin", "YETİŞKİN", "+18 Kanallar", "18+", "Adult", "ADULT MOVIES", "Erotik", "For Adults", "+ 18 Film", "18 + Dizi")
            .forEach { assertTrue("$it yetişkin sayılmalı", AdultFilter.isAdult(it)) }
    }

    @Test
    fun `normal kategori adlari yetiskin sayilmaz`() {
        listOf("Belgesel", "Çocuk", "Spor 1", "Haber", "Aksiyon").forEach {
            assertFalse("$it yetişkin sayılmamalı", AdultFilter.isAdult(it))
        }
    }

    private val cats = listOf(
        "all" to "Tümü",
        "1" to "Belgesel",
        "2" to "+18 Filmler",
        "3" to "Spor",
        "4" to "Çocuk"
    )

    @Test
    fun `kilit kapaliyken yetiskin ve gizli kategoriler engellenir`() {
        val blocked = AdultFilter.blockedIds(cats, hidden = setOf("4"), unlocked = false)
        assertEquals(setOf("2", "4"), blocked)
    }

    @Test
    fun `kilit aciksa sadece gizli kategoriler engellenir`() {
        val blocked = AdultFilter.blockedIds(cats, hidden = setOf("4"), unlocked = true)
        assertEquals(setOf("4"), blocked)
    }

    private data class Item(val name: String, val categoryId: String)

    @Test
    fun `Tumu listesinde yetiskin icerik cikar normal icerik kalir`() {
        val items = listOf(Item("Belgesel A", "1"), Item("Gizli B", "2"), Item("Spor C", "3"), Item("Çocuk D", "4"))
        val blocked = AdultFilter.blockedIds(cats, hidden = emptySet(), unlocked = false)
        val result = AdultFilter.filterBlocked(items, blocked) { it.categoryId }
        assertEquals(listOf("Belgesel A", "Spor C", "Çocuk D"), result.map { it.name })
    }

    @Test
    fun `engel yoksa liste aynen doner`() {
        val items = listOf(Item("A", "1"))
        assertTrue(AdultFilter.filterBlocked(items, emptySet()) { it.categoryId } === items)
    }
}
