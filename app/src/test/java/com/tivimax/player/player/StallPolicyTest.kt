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
}
