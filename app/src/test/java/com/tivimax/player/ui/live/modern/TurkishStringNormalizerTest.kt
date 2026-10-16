package com.tivimax.player.ui.live.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishStringNormalizerTest {

    @Test
    fun testNormalizeTurkishCharacters() {
        val input = "Şemsiye İSTANBUL Ğalle Üzüm Ördek Çiçek"
        val normalized = TurkishStringNormalizer.normalize(input)
        assertEquals("semsiye istanbul galle uzum ordek cicek", normalized)
    }

    @Test
    fun testMatchesWithTurkishDotlessI() {
        assertTrue(TurkishStringNormalizer.matches("Isparta", "isparta"))
        assertTrue(TurkishStringNormalizer.matches("İstanbul", "istanbul"))
        assertTrue(TurkishStringNormalizer.matches("Süper Lig", "super lig"))
        assertTrue(TurkishStringNormalizer.matches("ÇARŞAMBA", "carsamba"))
    }

    @Test
    fun testMatchesNegativeCase() {
        assertFalse(TurkishStringNormalizer.matches("Fenerbahçe", "Galatasaray"))
    }

    @Test
    fun testMatchesBlankQuery() {
        assertTrue(TurkishStringNormalizer.matches("Herhangi bir metin", ""))
        assertTrue(TurkishStringNormalizer.matches("Herhangi bir metin", "   "))
    }
}
