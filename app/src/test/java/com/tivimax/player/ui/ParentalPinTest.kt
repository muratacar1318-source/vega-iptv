package com.tivimax.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalPinTest {
    @Test
    fun `kayitli 1234 iken 0000 kilidi acmaz`() {
        assertFalse(ParentalPin.canUnlock("1234", "0000"))
    }

    @Test
    fun `kayitli 1234 iken 1234 kilidi acar`() {
        assertTrue(ParentalPin.canUnlock("1234", "1234"))
    }

    @Test
    fun `kayit yoksa hicbir giris kilidi acmaz`() {
        assertFalse(ParentalPin.canUnlock("", ""))
        assertFalse(ParentalPin.canUnlock("", "0000"))
        assertFalse(ParentalPin.canUnlock("", "1234"))
    }

    @Test
    fun `gecerli yeni pin hata vermez`() {
        assertNull(ParentalPin.validateNewPin("1234", "1234"))
    }

    @Test
    fun `rakam olmayan ya da 4 haneli olmayan pin hata verir`() {
        assertEquals("4 rakam gir", ParentalPin.validateNewPin("12a4", "12a4"))
        assertEquals("4 rakam gir", ParentalPin.validateNewPin("123", "123"))
        assertEquals("4 rakam gir", ParentalPin.validateNewPin("12345", "12345"))
        assertEquals("4 rakam gir", ParentalPin.validateNewPin("", ""))
    }

    @Test
    fun `eslesmeyen tekrar hata verir`() {
        assertEquals("PIN'ler aynı değil", ParentalPin.validateNewPin("1234", "1235"))
    }
}
