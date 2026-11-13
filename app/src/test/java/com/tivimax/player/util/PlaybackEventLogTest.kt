package com.tivimax.player.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackEventLogTest {
    @Before
    fun setUp() = PlaybackEventLog.clear()

    @After
    fun tearDown() = PlaybackEventLog.clear()

    @Test
    fun `100 olayda son 80 kalir ve sira korunur`() {
        for (i in 1..100) PlaybackEventLog.add("olay", "n=$i")
        val lines = PlaybackEventLog.snapshot()
        assertEquals(PlaybackEventLog.MAX_EVENTS, lines.size)
        assertTrue(lines.first().endsWith("olay: n=21"))
        assertTrue(lines.last().endsWith("olay: n=100"))
    }

    @Test
    fun `satir bicimi saat tur ve ayrinti icerir`() {
        PlaybackEventLog.add("sarma", "hedef=120 sn", nowMillis = 0L)
        val line = PlaybackEventLog.snapshot().single()
        assertTrue(line.matches(Regex("""\d{2}:\d{2}:\d{2}\.\d{3} sarma: hedef=120 sn""")))
    }

    @Test
    fun `ayrintisiz olay yalnizca tur yazar`() {
        PlaybackEventLog.add("ilk kare")
        assertTrue(PlaybackEventLog.snapshot().single().endsWith(" ilk kare"))
    }

    @Test
    fun `adres ve sifre metinde yer almaz`() {
        PlaybackEventLog.add("hata", "http://sunucu.example:8080/movie/ali/123/9.mp4 acilamadi password=gizli123 username=ali")
        val text = PlaybackEventLog.snapshot().joinToString("\n")
        assertFalse(text.contains("http://"))
        assertFalse(text.contains("sunucu.example"))
        assertFalse(text.contains("gizli123"))
        assertFalse(text.contains("ali"))
        assertTrue(text.contains("<adres>"))
    }

    @Test
    fun `bolum bos gunlukte bos metin verir`() {
        assertEquals("", PlaybackEventLog.buildSection())
        PlaybackEventLog.add("durum", "READY")
        assertTrue(PlaybackEventLog.buildSection().startsWith("Son oynatma olayları\n"))
    }
}
