package com.tivimax.player.player

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/**
 * G-040: Canlı MPEG-TS yayınında sunucunun bağlantı başında bir anda gönderdiği eski yayını (burst) atar.
 *
 * Bağlantı açılınca veriyi kısa bir süre (en fazla [maxPreReadMs]) önceden okur, TS paketlerindeki saat
 * bilgisinden (PCR) en güncel ana bakar ve en yeni [keepMs] dışındaki eski kısmı atar. Paket sınırında keser.
 * TS değilse, saat bilgisi yoksa, saat sıçrarsa veya beklenmeyen bir durum olursa HİÇBİR ŞEY ATMAZ:
 * okunan her bayt aynı sırayla geri verilir. Saf Kotlin — Android'e bağlı değildir (birim testi yapılabilir).
 */
class LiveBurstTrimmer(
    private val keepMs: Long = 8_000L,
    private val maxPreReadMs: Long = 3_000L,
    private val maxPreReadBytes: Long = 48L * 1024 * 1024,
    private val chunkBytes: Int = 64 * 1024,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private class Chunk(val bytes: ByteArray, val maxPcrMs: Long)

    /** Son çağrıda atılan bayt sayısı (teşhis için). */
    var lastDroppedBytes: Long = 0L
        private set

    /** G-041: Son ön okumanın teşhis özeti (geliştirici ekranı için). */
    var lastStats: String = ""
        private set

    /**
     * [read] upstream'den okur (DataSource.read gibi: okunan bayt sayısı ya da -1 = akış bitti).
     * Dönüş: çıkışa verilecek baytlar (sırayla). Sonrası doğrudan upstream'den okunur.
     * IOException'lar olduğu gibi yukarı fırlatılır.
     */
    fun preRead(read: (ByteArray, Int, Int) -> Int): ArrayDeque<ByteArray> {
        lastDroppedBytes = 0L
        val start = clockMs()
        val buf = ByteArray(64 * 1024)
        val raw = ByteArrayOutputStream()          // ilk senkrondan önceki baytlar
        val chunks = ArrayDeque<Chunk>()
        val packetBuf = ByteArrayOutputStream()
        var packetBufMaxPcr = -1L
        var leftover = ByteArray(0)
        var synced = false
        var notTs = false
        var discontinuity = false
        var firstPcr = -1L
        var lastPcr = -1L
        var maxLead = Long.MIN_VALUE
        var lastLeadGrowAt = start
        var total = 0L
        var droppedDuringRead = 0L
        var stopReason = "akış bitti"

        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) break
            if (n == 0) continue
            total += n
            val data = if (leftover.isEmpty()) buf.copyOf(n) else leftover + buf.copyOf(n)
            var i = 0
            if (!synced) {
                val s = findSync(data)
                if (s < 0) {
                    if (data.size >= 4096) {
                        stopReason = "TS değil"
                        notTs = true
                        leftover = data
                        break
                    }
                    leftover = data
                    continue
                }
                if (s > 0) raw.write(data, 0, s)
                i = s
                synced = true
            }
            while (i + 188 <= data.size) {
                if (data[i] != SYNC) {
                    discontinuity = true
                    break
                }
                val pcr = readPcrMs(data, i)
                if (pcr >= 0) {
                    if (lastPcr >= 0 && (pcr < lastPcr - 1_000L || pcr > lastPcr + 60_000L)) discontinuity = true
                    if (firstPcr < 0) firstPcr = pcr
                    if (pcr > lastPcr) lastPcr = pcr
                    if (pcr > packetBufMaxPcr) packetBufMaxPcr = pcr
                }
                packetBuf.write(data, i, 188)
                i += 188
                if (packetBuf.size() >= chunkBytes) {
                    chunks.addLast(Chunk(packetBuf.toByteArray(), packetBufMaxPcr))
                    packetBuf.reset()
                    packetBufMaxPcr = -1L
                    // Bellek sınırı: en yeni keepMs + 1 sn'den eski parçaları okurken at
                    if (!discontinuity && lastPcr >= 0) {
                        val cutoff = lastPcr - keepMs - 1_000L
                        while (chunks.size > 1 && chunks.peekFirst().maxPcrMs < cutoff) {
                            droppedDuringRead += chunks.pollFirst().bytes.size
                        }
                    }
                }
            }
            leftover = if (i < data.size) data.copyOfRange(i, data.size) else ByteArray(0)
            if (discontinuity) {
                stopReason = "saat sıçraması"
                break
            }

            val now = clockMs()
            val wall = now - start
            if (firstPcr >= 0) {
                val lead = (lastPcr - firstPcr) - wall
                if (lead > maxLead + 150L) {
                    maxLead = lead
                    lastLeadGrowAt = now
                }
                // Burst bitti: yayın artık gerçek zamanlı akıyor (öne geçiş 400 ms'dir büyümüyor)
                if (wall >= 300L && now - lastLeadGrowAt >= 400L) {
                    stopReason = "yığın bitti"
                    break
                }
            } else if (wall >= 1_500L || total >= 2L * 1024 * 1024) {
                stopReason = "saat bilgisi yok"
                break // saat bilgisi yok → kırpma yapılmaz
            }
            if (wall >= maxPreReadMs) {
                stopReason = "süre doldu"
                break
            }
            if (total >= maxPreReadBytes) {
                stopReason = "boyut sınırı"
                break
            }
        }

        val out = ArrayDeque<ByteArray>()
        val canTrim = synced && !notTs && !discontinuity && lastPcr >= 0
        if (canTrim) {
            val cutoff = lastPcr - keepMs
            if (packetBuf.size() > 0) {
                chunks.addLast(Chunk(packetBuf.toByteArray(), packetBufMaxPcr))
                packetBuf.reset()
            }
            while (chunks.size > 1 && chunks.peekFirst().maxPcrMs < cutoff) {
                droppedDuringRead += chunks.pollFirst().bytes.size
            }
            lastDroppedBytes = droppedDuringRead + if (droppedDuringRead > 0) raw.size().toLong() else 0L
            if (droppedDuringRead == 0L && raw.size() > 0) out.addLast(raw.toByteArray())
            for (c in chunks) out.addLast(c.bytes)
        } else {
            // Kırpma yok. Okurken bellek için atılmış parça varsa (çok nadir: sonradan saat sıçraması)
            // kalan veriyi yine sırayla ver; akış paket sınırında devam eder.
            lastDroppedBytes = droppedDuringRead
            if (droppedDuringRead == 0L && raw.size() > 0) out.addLast(raw.toByteArray())
            for (c in chunks) out.addLast(c.bytes)
            if (packetBuf.size() > 0) out.addLast(packetBuf.toByteArray())
        }
        if (leftover.isNotEmpty()) out.addLast(leftover)
        val spanSec = if (firstPcr >= 0 && lastPcr >= firstPcr) (lastPcr - firstPcr) / 1000 else 0
        lastStats = "kırpma: $stopReason, ön okuma ${clockMs() - start}ms, gelen ${spanSec}sn/${total / 1024}KB, atılan ${lastDroppedBytes / 1024}KB"
        return out
    }

    companion object {
        private const val SYNC: Byte = 0x47

        fun findSync(d: ByteArray): Int {
            var s = 0
            while (s + 376 < d.size) {
                if (d[s] == SYNC && d[s + 188] == SYNC && d[s + 376] == SYNC) return s
                s++
            }
            return -1
        }

        /** TS paketindeki PCR (milisaniye) ya da -1. */
        fun readPcrMs(d: ByteArray, o: Int): Long {
            val afc = (d[o + 3].toInt() shr 4) and 0x3
            if (afc != 2 && afc != 3) return -1L
            val afLen = d[o + 4].toInt() and 0xFF
            if (afLen < 7) return -1L
            val flags = d[o + 5].toInt() and 0xFF
            if (flags and 0x10 == 0) return -1L
            val b6 = d[o + 6].toLong() and 0xFF
            val b7 = d[o + 7].toLong() and 0xFF
            val b8 = d[o + 8].toLong() and 0xFF
            val b9 = d[o + 9].toLong() and 0xFF
            val b10 = d[o + 10].toLong() and 0xFF
            val base = (b6 shl 25) or (b7 shl 17) or (b8 shl 9) or (b9 shl 1) or (b10 shr 7)
            return base / 90L
        }
    }
}
