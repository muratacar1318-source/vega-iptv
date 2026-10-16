package com.tivimax.player.ui

import com.tivimax.player.data.model.LiveCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryFilteringTest {

    private val sampleCategories = listOf(
        LiveCategory(categoryId = "all", categoryName = "Tüm Kanallar", itemCount = 150),
        LiveCategory(categoryId = "1", categoryName = "Ulusal", itemCount = 40),
        LiveCategory(categoryId = "2", categoryName = "Spor", itemCount = 30),
        LiveCategory(categoryId = "3", categoryName = "Haber", itemCount = 20),
        LiveCategory(categoryId = "4", categoryName = "Belgesel", itemCount = 25),
        LiveCategory(categoryId = "99", categoryName = "Adult 18+", itemCount = 35)
    )

    @Test
    fun testFilterOutHiddenCategories() {
        val hiddenCategoryIds = setOf("99", "3")
        val filtered = sampleCategories.filter { it.categoryId !in hiddenCategoryIds }

        assertEquals(4, filtered.size)
        assertFalse(filtered.any { it.categoryId == "99" })
        assertFalse(filtered.any { it.categoryId == "3" })
        assertTrue(filtered.any { it.categoryId == "1" })
    }

    @Test
    fun testPinnedCategoriesSorting() {
        val pinnedCategoryIds = setOf("2", "4") // Spor ve Belgesel sabitlenmiş
        val sorted = sampleCategories.sortedWith(
            compareByDescending<LiveCategory> { it.categoryId in pinnedCategoryIds }
                .thenBy { it.categoryName }
        )

        // Sabitlenenler en başa gelmeli
        assertTrue(sorted[0].categoryId in pinnedCategoryIds)
        assertTrue(sorted[1].categoryId in pinnedCategoryIds)
        // Sabitlenmeyenler arkadan alfabetik gelmeli
        assertFalse(sorted[2].categoryId in pinnedCategoryIds)
    }

    @Test
    fun testAdultContentDetection() {
        fun isAdultCategory(name: String): Boolean {
            val lower = name.lowercase()
            return lower.contains("adult") || lower.contains("+18") || lower.contains("18+") || lower.contains("xxx")
        }

        assertTrue(isAdultCategory("Adult 18+"))
        assertTrue(isAdultCategory("VOD +18 Kanallar"))
        assertTrue(isAdultCategory("XXX Özel"))
        assertFalse(isAdultCategory("Ulusal HD"))
        assertFalse(isAdultCategory("Çocuk ve Animasyon"))
    }
}
