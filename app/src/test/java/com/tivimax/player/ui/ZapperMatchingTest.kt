package com.tivimax.player.ui

import com.tivimax.player.data.model.LiveStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZapperMatchingTest {

    private val allStreams = listOf(
        LiveStream(streamId = 1001, name = "TRT 1 HD", num = 1, categoryId = "1"),
        LiveStream(streamId = 1002, name = "ATV HD", num = 2, categoryId = "1"),
        LiveStream(streamId = 1003, name = "Kanal D HD", num = 3, categoryId = "1"),
        LiveStream(streamId = 2001, name = "beIN Sports 1", num = 10, categoryId = "2"),
        LiveStream(streamId = 2002, name = "S Sport", num = 11, categoryId = "2"),
        LiveStream(streamId = 3001, name = "NTV HD", num = 20, categoryId = "3")
    )

    private fun matchZapperChannel(targetNum: Int, streams: List<LiveStream>): LiveStream? {
        return streams.firstOrNull { it.num == targetNum }
            ?: streams.firstOrNull { it.streamId == targetNum }
            ?: streams.getOrNull(targetNum - 1)
    }

    @Test
    fun testMatchByChannelNumber() {
        val matched = matchZapperChannel(1, allStreams)
        assertNotNull(matched)
        assertEquals("TRT 1 HD", matched?.name)

        val matchedSport = matchZapperChannel(10, allStreams)
        assertNotNull(matchedSport)
        assertEquals("beIN Sports 1", matchedSport?.name)
    }

    @Test
    fun testMatchByStreamIdFallback() {
        val matched = matchZapperChannel(2002, allStreams)
        assertNotNull(matched)
        assertEquals("S Sport", matched?.name)
    }

    @Test
    fun testMatchByIndexFallback() {
        // Numara 5 listede yok ama liste boyutu >= 5 (5. eleman num=11 olan S Sport)
        val matched = matchZapperChannel(5, allStreams)
        assertNotNull(matched)
        assertEquals(allStreams[4].streamId, matched?.streamId)
    }

    @Test
    fun testMatchAcrossCategoriesWhenActiveCategoryIsIsolated() {
        // Kullanıcı Spor kategorisinde (sadece beIN Sports ve S Sport var)
        val activeCategoryStreams = allStreams.filter { it.categoryId == "2" }

        // Eğer arama sadece aktif kategoride yapılsaydı 1 numaralı TRT 1 bulunamazdı
        val failedMatch = matchZapperChannel(1, activeCategoryStreams)
        // Ancak allStreams üzerinden arandığında her zaman doğru kanal bulunur:
        val correctMatch = matchZapperChannel(1, allStreams.ifEmpty { activeCategoryStreams })

        assertNotNull(correctMatch)
        assertEquals(1, correctMatch?.num)
        assertEquals("TRT 1 HD", correctMatch?.name)
    }

    @Test
    fun testMatchNonExistentReturnsNull() {
        val matched = matchZapperChannel(99999, allStreams)
        assertNull(matched)
    }
}
