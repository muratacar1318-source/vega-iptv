package com.tivimax.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLoggerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `sanitize adresi gizler`() {
        val out = CrashLogger.sanitize("Hata: http://a.b:8080/live/kullanici/sifre/123.ts acilamadi")
        assertTrue(out.contains("<adres>"))
        assertFalse(out.contains("a.b"))
        assertFalse(out.contains("kullanici"))
        assertFalse(out.contains("sifre"))
    }

    @Test
    fun `sanitize sorgu parametrelerindeki kullanici adi ve sifreyi gizler`() {
        val out = CrashLogger.sanitize("istek player_api.php?username=ali&password=123 basarisiz")
        assertFalse(out.contains("ali"))
        assertFalse(out.contains("123"))
        assertTrue(out.contains("username=<gizli>"))
        assertTrue(out.contains("password=<gizli>"))
    }

    @Test
    fun `sanitize adres olmadan gecen kullanici adi ve sifre alanlarini da gizler`() {
        val out = CrashLogger.sanitize("XtreamUserInfo(username=ali, password=123, status=Active)")
        assertFalse(out.contains("ali"))
        assertFalse(out.contains("123"))
        assertTrue(out.contains("status=Active"))
    }

    @Test
    fun `sanitize ag hatalarindaki sunucu adini ve ip adresini gizler`() {
        val out = CrashLogger.sanitize(
            "UnknownHostException: Unable to resolve host \"sunucu.example\"\n" +
                "ConnectException: failed to connect to sunucu.example/203.0.113.7 (port 8080)"
        )
        assertFalse(out.contains("sunucu.example"))
        assertFalse(out.contains("203.0.113.7"))
    }

    @Test
    fun `7 rapor yazilinca 5 dosya kalir ve en eskiler silinir`() {
        val dir = tmp.newFolder("crash")
        for (i in 1..7) {
            CrashLogger.writeReport(dir, "rapor $i", timeMillis = 1000L * i)
        }
        val kalan = CrashLogger.listReportFiles(dir)
        assertEquals(5, kalan.size)
        assertEquals(
            listOf("crash_7000.txt", "crash_6000.txt", "crash_5000.txt", "crash_4000.txt", "crash_3000.txt"),
            kalan.map { it.name }
        )
        assertFalse(java.io.File(dir, "crash_1000.txt").exists())
        assertFalse(java.io.File(dir, "crash_2000.txt").exists())
    }

    @Test
    fun `ayni milisaniyede yazilan raporlar birbirinin ustune yazilmaz`() {
        val dir = tmp.newFolder("crash2")
        CrashLogger.writeReport(dir, "a", timeMillis = 5000L)
        CrashLogger.writeReport(dir, "b", timeMillis = 5000L)
        assertEquals(2, CrashLogger.listReportFiles(dir).size)
    }

    @Test
    fun `paylasim metni gizli bilgi icermez ve sinirlidir`() {
        val dir = tmp.newFolder("crash3")
        // Eski/elle yazilmis bir dosyada ham bilgi olsa bile paylasim metni yeniden temizler.
        java.io.File(dir, "crash_1.txt").writeText("hata http://x.y/live/ali/123/9.ts username=ali")
        val text = CrashLogger.buildShareText(dir)
        assertFalse(text.contains("ali"))
        assertFalse(text.contains("123"))

        val buyuk = tmp.newFolder("crash4")
        for (i in 1..5) CrashLogger.writeReport(buyuk, "x".repeat(30_000), timeMillis = i.toLong())
        assertTrue(CrashLogger.buildShareText(buyuk).length <= CrashLogger.MAX_SHARE_CHARS)
    }
}
