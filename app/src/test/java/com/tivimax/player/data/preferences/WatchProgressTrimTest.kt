package com.tivimax.player.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTrimTest {
    private fun entries(count: Int): Map<String, WatchProgress> =
        (1..count).associate { i -> i.toString() to WatchProgress(i, 1000L, 5000L, timestamp = i.toLong()) }

    @Test
    fun `499 kayit aynen kalir`() {
        val map = entries(499)
        assertSame(map, trimProgress(map))
        assertEquals(499, trimProgress(map).size)
    }

    @Test
    fun `500 kayit aynen kalir`() {
        assertEquals(500, trimProgress(entries(500)).size)
    }

    @Test
    fun `501 kayitta 500 kalir ve en eski zaman damgali gider`() {
        val trimmed = trimProgress(entries(501))
        assertEquals(500, trimmed.size)
        assertFalse(trimmed.containsKey("1")) // timestamp=1 en eski
        assertTrue(trimmed.containsKey("2"))
        assertTrue(trimmed.containsKey("501"))
    }

    @Test
    fun `cok fazla kayitta en yeniler kalir`() {
        val trimmed = trimProgress(entries(1200))
        assertEquals(500, trimmed.size)
        assertTrue((701..1200).all { trimmed.containsKey(it.toString()) })
    }

    @Test
    fun `yeni eklenen kayit hicbir zaman atilmaz`() {
        val map = entries(500).toMutableMap()
        // Yeni kayit en eski zaman damgasina sahip olsa bile korunur
        map["yeni"] = WatchProgress(9999, 10L, 100L, timestamp = 0L)
        val trimmed = trimProgress(map, keepKey = "yeni")
        assertEquals(500, trimmed.size)
        assertTrue(trimmed.containsKey("yeni"))
        assertFalse(trimmed.containsKey("1"))
    }

    @Test
    fun `ozel sinir kullanilabilir`() {
        assertEquals(10, trimProgress(entries(50), max = 10).size)
    }
}
