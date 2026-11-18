package com.tivimax.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StallPolicyTest {
    @Test
    fun `canli ilk kare yokken 20 sn`() {
        assertEquals(20_000L, StallPolicy.timeoutMs(isLive = true, firstFrameShown = false))
    }

    @Test
    fun `canli ilk kare varken 10 sn`() {
        assertEquals(10_000L, StallPolicy.timeoutMs(isLive = true, firstFrameShown = true))
    }

    @Test
    fun `film ilk kare yokken bekci kurulmaz`() {
        assertNull(StallPolicy.timeoutMs(isLive = false, firstFrameShown = false))
    }

    @Test
    fun `film ilk kare varken 15 sn`() {
        assertEquals(15_000L, StallPolicy.timeoutMs(isLive = false, firstFrameShown = true))
    }

    @Test
    fun `film sarmadan 1 sn sonra takilirsa 5 sn`() {
        assertEquals(5_000L, StallPolicy.timeoutMs(isLive = false, firstFrameShown = true, msSinceSeek = 1_000L))
        assertEquals(5_000L, StallPolicy.timeoutMs(isLive = false, firstFrameShown = true, msSinceSeek = 3_000L))
    }

    @Test
    fun `film sarmadan 10 sn sonra takilirsa 15 sn`() {
        assertEquals(15_000L, StallPolicy.timeoutMs(isLive = false, firstFrameShown = true, msSinceSeek = 10_000L))
        assertEquals(15_000L, StallPolicy.timeoutMs(isLive = false, firstFrameShown = true, msSinceSeek = 3_001L))
    }

    @Test
    fun `film sarma hic olmadiysa 15 sn`() {
        assertEquals(15_000L, StallPolicy.timeoutMs(isLive = false, firstFrameShown = true, msSinceSeek = null))
    }

    @Test
    fun `film ilk kare yokken sarma olsa da bekci kurulmaz`() {
        assertNull(StallPolicy.timeoutMs(isLive = false, firstFrameShown = false, msSinceSeek = 500L))
    }

    @Test
    fun `canlida sarma suresi etkisiz`() {
        assertEquals(10_000L, StallPolicy.timeoutMs(isLive = true, firstFrameShown = true, msSinceSeek = 500L))
        assertEquals(20_000L, StallPolicy.timeoutMs(isLive = true, firstFrameShown = false, msSinceSeek = 500L))
    }

    @Test
    fun `sarma kaynakli takilma penceresi`() {
        assertEquals(true, StallPolicy.isSeekRelated(0L))
        assertEquals(true, StallPolicy.isSeekRelated(3_000L))
        assertEquals(false, StallPolicy.isSeekRelated(3_001L))
        assertEquals(false, StallPolicy.isSeekRelated(null))
    }
}
