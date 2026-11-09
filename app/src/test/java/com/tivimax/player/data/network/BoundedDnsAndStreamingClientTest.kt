package com.tivimax.player.data.network

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BoundedDnsAndStreamingClientTest {

    @Test
    fun testSuccessfulLookupCachesStaleAddress() {
        val mockAddress = InetAddress.getByName("192.168.1.100")
        val mockDns = Dns { hostname ->
            if (hostname == "iptv.provider.com") listOf(mockAddress) else emptyList()
        }

        val boundedDns = BoundedDns(
            timeoutMs = 1000L,
            staleTimeoutMs = 500L,
            delegate = mockDns
        )

        val result = boundedDns.lookup("iptv.provider.com")
        assertEquals(1, result.size)
        assertEquals(mockAddress, result[0])

        // Verify it was stored in stale cache
        val cached = boundedDns.getStale("iptv.provider.com")
        assertNotNull(cached)
        assertEquals(1, cached!!.size)
        assertEquals(mockAddress, cached[0])
    }

    @Test
    fun testFailedLookupFallsBackToStaleAddress() {
        val workingAddress = InetAddress.getByName("10.0.0.1")
        var shouldFail = false

        val mockDns = Dns { hostname ->
            if (shouldFail) {
                throw UnknownHostException("DNS unreachable / ISP blocked")
            } else {
                listOf(workingAddress)
            }
        }

        val boundedDns = BoundedDns(
            timeoutMs = 1000L,
            staleTimeoutMs = 500L,
            delegate = mockDns
        )

        // 1. Initial successful lookup
        val firstResult = boundedDns.lookup("live.iptv.com")
        assertEquals(listOf(workingAddress), firstResult)

        // 2. DNS fails (ISP block or timeout)
        shouldFail = true
        val fallbackResult = boundedDns.lookup("live.iptv.com")

        // Should return stale address instead of throwing
        assertEquals(listOf(workingAddress), fallbackResult)
    }

    @Test
    fun testFailedLookupWithoutStaleThrowsUnknownHostException() {
        val mockDns = Dns { hostname ->
            throw UnknownHostException("Host not found: $hostname")
        }

        val boundedDns = BoundedDns(
            timeoutMs = 500L,
            delegate = mockDns
        )

        try {
            boundedDns.lookup("unknown.nonexistent.tv")
            fail("Beklenen UnknownHostException fırlatılmadı")
        } catch (e: UnknownHostException) {
            assertTrue(e.message?.contains("unknown.nonexistent.tv") == true)
        }
    }

    @Test
    fun testTimeoutFallsBackToStaleAddress() {
        val cachedAddress = InetAddress.getByName("172.16.0.5")

        // Slow delegate that exceeds timeout
        val slowDns = Dns {
            Thread.sleep(800)
            listOf(InetAddress.getByName("172.16.0.99"))
        }

        val boundedDns = BoundedDns(
            timeoutMs = 200L,
            staleTimeoutMs = 100L,
            delegate = slowDns
        )

        // Pre-populate stale cache
        boundedDns.putStale("stream.server.net", listOf(cachedAddress))

        // Should timeout quickly and return cachedAddress
        val startTime = System.currentTimeMillis()
        val result = boundedDns.lookup("stream.server.net")
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(listOf(cachedAddress), result)
        assertTrue("Lookup should return within ~500ms, elapsed: ${elapsed}ms", elapsed < 700)
    }

    @Test
    fun testConcurrentLookupsDeduplicateInFlight() {
        val callCount = AtomicInteger(0)
        val testAddress = InetAddress.getByName("192.168.1.1")

        val latch = CountDownLatch(1)
        val mockDns = Dns {
            callCount.incrementAndGet()
            latch.await(2, TimeUnit.SECONDS)
            listOf(testAddress)
        }

        val boundedDns = BoundedDns(
            timeoutMs = 1500L,
            delegate = mockDns
        )

        val threadCount = 4
        val doneLatch = CountDownLatch(threadCount)
        val results = java.util.Collections.synchronizedList(mutableListOf<List<InetAddress>>())

        for (i in 0 until threadCount) {
            Thread {
                try {
                    val r = boundedDns.lookup("concurrent.server.com")
                    results.add(r)
                } finally {
                    doneLatch.countDown()
                }
            }.start()
        }

        // Release the DNS resolver
        Thread.sleep(50)
        latch.countDown()
        doneLatch.await(3, TimeUnit.SECONDS)

        assertEquals(threadCount, results.size)
        // All threads got the result
        results.forEach { res ->
            assertEquals(listOf(testAddress), res)
        }
        // In-flight deduplication means delegate was called at most once or twice, not 4 times
        assertTrue("Call count should be <= 2 due to deduplication, actual: ${callCount.get()}", callCount.get() <= 2)
    }

    @Test
    fun testStreamingHttpClientConfigurationAndEvict() {
        val streamingClient = StreamingHttpClient()

        assertNotNull(streamingClient.client)
        assertNotNull(streamingClient.client.connectionPool)
        assertTrue(streamingClient.client.retryOnConnectionFailure)

        // Calling evictAll shouldn't throw and clears idle connections
        streamingClient.evictAll()
        assertEquals(0, streamingClient.idleConnectionCount())
    }
}
