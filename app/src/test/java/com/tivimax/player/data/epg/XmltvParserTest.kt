package com.tivimax.player.data.epg

import com.tivimax.player.data.local.entity.EpgProgramEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneOffset

class XmltvParserTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).toEpochSecond(ZoneOffset.UTC) * 1000L

    // Pencere: 13 Kasım 2026 12:00 UTC'den 6 sa geri, 48 sa ileri
    private val now = utc(2026, 11, 13, 12, 0)
    private val windowStart = now - 6 * 3600_000L
    private val windowEnd = now + 48 * 3600_000L

    private val sample = """<?xml version="1.0" encoding="UTF-8"?>
<tv generator-info-name="test">
  <channel id="A.tr"><display-name>A</display-name></channel>
  <channel id="b.tr"><display-name>B</display-name></channel>
  <programme start="20261113180000 +0300" stop="20261113190000 +0300" channel="A.tr">
    <title lang="tr">Haberler</title>
    <desc>Günün özeti &amp; yorumlar</desc>
    <category>haber</category>
  </programme>
  <programme start="20261113120000 -0500" stop="20261113130000 -0500" channel=" B.TR ">
    <title>Film</title>
  </programme>
  <programme start="bozuk" stop="20261113190000 +0300" channel="A.tr">
    <title>Bozuk kayıt</title>
  </programme>
  <programme start="20261201000000 +0000" stop="20261201010000 +0000" channel="A.tr">
    <title>Pencere dışı</title>
  </programme>
  <programme start="20261113200000" stop="20261113210000" channel="A.tr">
    <title>Bölgesiz</title>
  </programme>
  <programme start="20261113200000 +0000" stop="20261113210000 +0000" channel="yok.tr">
    <title>İzinsiz kanal</title>
  </programme>
</tv>"""

    private fun parse(xml: String, channels: Set<String>?): List<EpgProgramEntity> {
        val parser = KXmlParser()
        parser.setInput(StringReader(xml))
        val out = ArrayList<EpgProgramEntity>()
        runBlocking { XmltvParser.parse(parser, windowStart, windowEnd, channels) { out.add(it) } }
        return out
    }

    @Test
    fun `gecerli programlar dogru sayida ve dogru milisaniyede okunur`() {
        val programs = parse(sample, setOf("a.tr", "b.tr"))
        // Haberler, Film, Bölgesiz = 3 (bozuk, pencere dışı ve izinsiz kanal atlanır)
        assertEquals(3, programs.size)

        val haberler = programs.first { it.title == "Haberler" }
        assertEquals("a.tr", haberler.channelEpgId)
        assertEquals(utc(2026, 11, 13, 15, 0), haberler.startMs) // 18:00 +0300 = 15:00 UTC
        assertEquals(utc(2026, 11, 13, 16, 0), haberler.endMs)
        assertEquals("Günün özeti & yorumlar", haberler.description)

        val film = programs.first { it.title == "Film" }
        assertEquals("b.tr", film.channelEpgId) // boşluk ve büyük harf normalize edilir
        assertEquals(utc(2026, 11, 13, 17, 0), film.startMs) // 12:00 -0500 = 17:00 UTC
        assertNull(film.description)
    }

    @Test
    fun `bolgesiz saat UTC sayilir`() {
        val p = parse(sample, setOf("a.tr")).first { it.title == "Bölgesiz" }
        assertEquals(utc(2026, 11, 13, 20, 0), p.startMs)
        assertEquals(utc(2026, 11, 13, 21, 0), p.endMs)
    }

    @Test
    fun `bozuk pencere disi ve izinsiz kanal kayitlari atlanir`() {
        val titles = parse(sample, setOf("a.tr", "b.tr")).map { it.title }
        assertFalse(titles.contains("Bozuk kayıt"))
        assertFalse(titles.contains("Pencere dışı"))
        assertFalse(titles.contains("İzinsiz kanal"))
    }

    @Test
    fun `kanal listesi verilmezse tum kanallar alinir`() {
        val titles = parse(sample, null).map { it.title }
        assertTrue(titles.contains("İzinsiz kanal"))
        assertEquals(4, titles.size)
    }

    @Test
    fun `saat cozumleme arti eksi ve bolgesiz`() {
        assertEquals(utc(2026, 11, 13, 15, 0), XmltvParser.parseTime("20261113180000 +0300"))
        assertEquals(utc(2026, 11, 13, 17, 0), XmltvParser.parseTime("20261113120000 -0500"))
        assertEquals(utc(2026, 11, 13, 12, 0), XmltvParser.parseTime("20261113120000"))
        assertEquals(utc(2026, 11, 13, 9, 30), XmltvParser.parseTime("20261113120000 +02:30"))
        assertEquals(utc(2026, 11, 13, 12, 5), XmltvParser.parseTime("202611131205 +0000"))
    }

    @Test
    fun `bozuk saat metinleri null doner`() {
        assertNull(XmltvParser.parseTime(null))
        assertNull(XmltvParser.parseTime(""))
        assertNull(XmltvParser.parseTime("bozuk"))
        assertNull(XmltvParser.parseTime("20261313120000"))   // 13. ay
        assertNull(XmltvParser.parseTime("20261113120000 +9999"))
        assertNull(XmltvParser.parseTime("2026111312"))
    }

    @Test
    fun `pencere suzgeci cakisma esasli`() {
        val ws = 1_000L
        val we = 2_000L
        assertTrue(XmltvParser.overlapsWindow(1_500L, 1_600L, ws, we))   // içinde
        assertTrue(XmltvParser.overlapsWindow(500L, 1_100L, ws, we))      // başı dışarıda, çakışıyor
        assertTrue(XmltvParser.overlapsWindow(1_900L, 2_500L, ws, we))    // sonu dışarıda, çakışıyor
        assertFalse(XmltvParser.overlapsWindow(100L, 1_000L, ws, we))     // tam önce biter
        assertFalse(XmltvParser.overlapsWindow(2_000L, 3_000L, ws, we))   // tam sonra başlar
        assertFalse(XmltvParser.overlapsWindow(5_000L, 6_000L, ws, we))
    }
}
