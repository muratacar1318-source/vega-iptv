package com.tivimax.player.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleIdFilterTest {

    @Test
    fun `seen is empty returns empty list`() {
        val existing = listOf(1, 2, 3, 4, 5)
        val seen = emptySet<Int>()
        val result = XtreamRepository.safeStaleIds(existing, seen, "Test")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `seen size less than half of existing returns empty list`() {
        val existing = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val seen = setOf(1, 2, 3, 4) // 4 < 10 / 2 (5)
        val result = XtreamRepository.safeStaleIds(existing, seen, "Test")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `normal case returns difference`() {
        val existing = listOf(1, 2, 3, 4, 5)
        val seen = setOf(1, 2, 3) // 3 >= 5 / 2 (2)
        val result = XtreamRepository.safeStaleIds(existing, seen, "Test")
        assertEquals(listOf(4, 5), result)
    }

    @Test
    fun `both existing and seen empty returns empty list`() {
        val existing = emptyList<Int>()
        val seen = emptySet<Int>()
        val result = XtreamRepository.safeStaleIds(existing, seen, "Test")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `existing empty and seen not empty returns empty list`() {
        val existing = emptyList<Int>()
        val seen = setOf(1, 2)
        val result = XtreamRepository.safeStaleIds(existing, seen, "Test")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `seen contains all items returns empty list`() {
        val existing = listOf(1, 2, 3)
        val seen = setOf(1, 2, 3)
        val result = XtreamRepository.safeStaleIds(existing, seen, "Test")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `works with string category ids`() {
        val existing = listOf("cat_1", "cat_2", "cat_3", "cat_4")
        val seen = setOf("cat_1", "cat_2") // 2 >= 4 / 2 (2)
        val result = XtreamRepository.safeStaleIds(existing, seen, "Categories")
        assertEquals(listOf("cat_3", "cat_4"), result)
    }
}
