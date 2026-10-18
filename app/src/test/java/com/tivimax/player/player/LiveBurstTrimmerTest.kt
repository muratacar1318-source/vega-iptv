package com.tivimax.player.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class LiveBurstTrimmerTest {
    fun packet(pcrMs: Long?, fill: Int): ByteArray {
        val p = ByteArray(188) { (fill and 0xFF).toByte() }
        p[0] = 0x47
        p[1] = 0x01; p[2] = 0x00
        if (pcrMs != null) {
            p[3] = 0x30 // afc=3
            p[4] = 7
            p[5] = 0x10
            val base = pcrMs * 90
            p[6] = ((base shr 25) and 0xFF).toByte()
            p[7] = ((base shr 17) and 0xFF).toByte()
            p[8] = ((base shr 9) and 0xFF).toByte()
            p[9] = ((base shr 1) and 0xFF).toByte()
            p[10] = (((base and 1) shl 7) or 0x7E).toByte()
            p[11] = 0
        } else p[3] = 0x10
        return p
    }

    fun stream(seconds: Int, msPerPacket: Long = 40, startPcr: Long = 100_000): ByteArray {
        val o = ByteArrayOutputStream()
        var t = startPcr
        var k = 0
        while (t < startPcr + seconds * 1000L) {
            o.write(packet(if (k % 2 == 0) t else null, k)); t += msPerPacket; k++
        }
        return o.toByteArray()
    }

    fun source(data: ByteArray, step: Int = 5000): (ByteArray, Int, Int) -> Int {
        var pos = 0
        return { b, off, len -> if (pos >= data.size) -1 else { val n = minOf(len, step, data.size - pos); System.arraycopy(data, pos, b, off, n); pos += n; n } }
    }

    fun drain(t: LiveBurstTrimmer, data: ByteArray): ByteArray {
        val src = source(data)
        val q = t.preRead(src)
        val o = ByteArrayOutputStream()
        q.forEach { o.write(it) }
        val b = ByteArray(4096)
        while (true) { val n = src(b, 0, b.size); if (n < 0) break; o.write(b, 0, n) }
        return o.toByteArray()
    }

    private fun check(name: String, ok: Boolean) = assertTrue(name, ok)

    @Test
    fun `canli burst kirpma senaryolari`() {
        // 1) 30 sn burst: ilk paket PCR >= son - 8000 - parça payı
        val s30 = stream(30)
        val t = LiveBurstTrimmer(chunkBytes = 188 * 10, clockMs = { 0L })
        val out = drain(t, s30)
        check("30s aligned", out.size % 188 == 0 && out[0] == 0x47.toByte())
        var firstPcr = -1L; var i = 0
        while (i < out.size) { val p = LiveBurstTrimmer.readPcrMs(out, i); if (p >= 0) { firstPcr = p; break }; i += 188 }
        val lastPcr = 100_000L + 30_000 - 40
        check("30s trimmed ~8s", lastPcr - firstPcr in 8000..8800)
        check("tail preserved", out.copyOfRange(out.size - 188, out.size).contentEquals(s30.copyOfRange(s30.size - 188, s30.size)))
        // 2) 5 sn: hiçbir şey atılmaz
        val s5 = stream(5)
        check("5s untouched", drain(LiveBurstTrimmer(chunkBytes = 1880, clockMs = { 0L }), s5).contentEquals(s5))
        // 3) TS değil
        val txt = ByteArray(20000) { (it % 50 + 65).toByte() }
        check("non-ts untouched", drain(LiveBurstTrimmer(clockMs = { 0L }), txt).contentEquals(txt))
        // 4) Başta çöp + 30s
        val junk = ByteArray(100) { 1 }
        val jd = junk + s30
        val o4 = drain(LiveBurstTrimmer(chunkBytes = 1880, clockMs = { 0L }), jd)
        check("junk+30s starts with sync", o4[0] == 0x47.toByte() && o4.size % 188 == 0)
        // 5) PCR yok → dokunma
        val np = ByteArrayOutputStream(); repeat(2000) { np.write(packet(null, it)) }
        val npb = np.toByteArray()
        check("no pcr untouched", drain(LiveBurstTrimmer(clockMs = { 0L }), npb).contentEquals(npb))
        // 6) Saat sıçraması → dokunma (bellek kırpması olmadıysa)
        val d1 = stream(3); val d2 = stream(3, startPcr = 10_000)
        val dj = d1 + d2
        check("discontinuity untouched", drain(LiveBurstTrimmer(chunkBytes = 1880, clockMs = { 0L }), dj).contentEquals(dj))
        // 7) Gerçek zamanlı (burst yok) — saat ilerliyor: akış bölünmeden tamamı
        var clock = 0L
        val s3 = stream(3)
        val src7 = source(s3, 1880) 
        val tt = LiveBurstTrimmer(chunkBytes = 1880, clockMs = { clock })
        val q = tt.preRead { b, o, l -> clock += 400; src7(b, o, l) }
        val o7 = ByteArrayOutputStream(); q.forEach { o7.write(it) }; val bb = ByteArray(4096)
        while (true) { val n = src7(bb, 0, bb.size); if (n < 0) break; o7.write(bb, 0, n) }
        check("realtime untouched", o7.toByteArray().contentEquals(s3))
    }
}
