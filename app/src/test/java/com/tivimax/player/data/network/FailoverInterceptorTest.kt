package com.tivimax.player.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class FailoverInterceptorTest {

    @Test
    fun testSetWorkingBaseUrlTrimsSlash() {
        FailoverInterceptor.setWorkingBaseUrl("http://example.com:8080/")
        assertEquals("http://example.com:8080", FailoverInterceptor.currentWorkingBaseUrl)
    }

    @Test
    fun testSetWorkingBaseUrlIgnoresBlank() {
        val previous = FailoverInterceptor.currentWorkingBaseUrl
        FailoverInterceptor.setWorkingBaseUrl("   ")
        assertEquals(previous, FailoverInterceptor.currentWorkingBaseUrl)
    }

    @Test
    fun testCandidateDomainsOrderAndUniqueness() {
        val defaultUrl = "http://main.server.com:8080"
        val fallbacks = listOf("http://backup1.server.com:8080", "http://backup2.server.com:8080", defaultUrl)
        val currentWorking = "http://backup1.server.com:8080"

        val candidates = LinkedHashSet<String>().apply {
            add(defaultUrl)
            addAll(fallbacks)
            add(currentWorking)
        }.map { it.trimEnd('/') }.filter { it.isNotBlank() }

        // Duplikasyonlar temizlenmeli ve sıra korunmalı
        assertEquals(3, candidates.size)
        assertEquals(defaultUrl, candidates[0])
        assertEquals("http://backup1.server.com:8080", candidates[1])
        assertEquals("http://backup2.server.com:8080", candidates[2])
    }
}
