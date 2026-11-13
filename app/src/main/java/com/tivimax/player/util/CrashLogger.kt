package com.tivimax.player.util

import android.content.Context
import android.os.Build
import com.tivimax.player.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * G-051: Uygulama çökmelerini cihazda saklar. Dışarıya hiçbir şey göndermez; kullanıcı isterse
 * Ayarlar'dan kendisi paylaşır.
 *
 * Gizlilik: Xtream adresleri kullanıcı adı ve şifreyi URL içinde taşır. Dosyaya yazmadan önce ve
 * paylaşım metni hazırlanırken metin [sanitize] ile temizlenir. Çökme işleyicisi içinde DataStore
 * okunmaz (bloklamamak için); bu yüzden aktif listenin kimlik bilgileri adres ve parametre
 * kurallarıyla yakalanır.
 */
object CrashLogger {
    private const val DIR_NAME = "crash"
    private const val FILE_PREFIX = "crash_"
    private const val FILE_SUFFIX = ".txt"
    internal const val MAX_FILES = 5
    internal const val MAX_SHARE_CHARS = 50_000

    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    // Adres içeren tipik ağ hata metinleri: Unable to resolve host "x", failed to connect to x/1.2.3.4
    private val hostRegex = Regex("""(host\s+)"[^"]*"""", RegexOption.IGNORE_CASE)
    private val connectRegex = Regex("""(connect to\s+)\S+""", RegexOption.IGNORE_CASE)
    private val ipRegex = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b""")
    private val credentialRegex = Regex(
        """(username|password)(["']?\s*[=:]\s*["']?)[^\s&"',;]+""",
        RegexOption.IGNORE_CASE
    )

    /** Mevcut varsayılan işleyiciyi saklar, çökmeleri dosyaya yazan yenisini kurar. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val text = buildReport(appContext, thread, throwable)
                writeReport(crashDir(appContext), text, System.currentTimeMillis())
            } catch (_: Throwable) {
                // Kayıt başarısız olsa bile sistemin normal çökme akışı sürmeli.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Kayıtlı raporlar, en yeni başta. */
    fun listReports(context: Context): List<File> = listReportFiles(crashDir(context))

    /** Son raporları birleştirip (en fazla ~50 KB) paylaşılabilir metin üretir. */
    fun buildShareText(context: Context): String {
        val crashes = buildShareText(crashDir(context))
        // G-062: Son oynatma olayları (varsa) metnin başına eklenir
        val events = PlaybackEventLog.buildSection()
        return when {
            events.isEmpty() -> crashes
            crashes.isEmpty() -> events
            else -> events + "\n\n----------------------------------------\n\n" + crashes
        }
    }

    /** Tüm kayıtları siler. */
    fun clear(context: Context) {
        listReportFiles(crashDir(context)).forEach { it.delete() }
    }

    /** Adres, kullanıcı adı ve şifre gibi gizli bilgileri metinden çıkarır. */
    fun sanitize(text: String): String {
        return text
            .replace(urlRegex, "<adres>")
            .replace(hostRegex) { "${it.groupValues[1]}\"<adres>\"" }
            .replace(connectRegex) { "${it.groupValues[1]}<adres>" }
            .replace(ipRegex, "<adres>")
            .replace(credentialRegex) { "${it.groupValues[1]}${it.groupValues[2]}<gizli>" }
    }

    // --- Dosya işlemleri (Context gerektirmez; birim testler bunları kullanır) ---

    internal fun writeReport(dir: File, text: String, timeMillis: Long, maxFiles: Int = MAX_FILES): File {
        dir.mkdirs()
        var stamp = timeMillis
        var file = File(dir, "$FILE_PREFIX$stamp$FILE_SUFFIX")
        while (file.exists()) {
            stamp++
            file = File(dir, "$FILE_PREFIX$stamp$FILE_SUFFIX")
        }
        file.writeText(sanitize(text), Charsets.UTF_8)
        listReportFiles(dir).drop(maxFiles).forEach { it.delete() }
        return file
    }

    internal fun listReportFiles(dir: File): List<File> {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX) }
            ?: return emptyList()
        return files.sortedByDescending { stampOf(it) }
    }

    internal fun buildShareText(dir: File, maxChars: Int = MAX_SHARE_CHARS): String {
        val builder = StringBuilder()
        for (file in listReportFiles(dir)) {
            val body = try {
                sanitize(file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                continue
            }
            if (builder.isNotEmpty()) builder.append("\n\n----------------------------------------\n\n")
            builder.append(body)
            if (builder.length >= maxChars) break
        }
        return if (builder.length > maxChars) builder.substring(0, maxChars) else builder.toString()
    }

    private fun stampOf(file: File): Long =
        file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX).toLongOrNull() ?: 0L

    private fun crashDir(context: Context): File = File(context.filesDir, DIR_NAME)

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val time = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        } catch (_: Throwable) {
            "?"
        }
        val deviceKind = try {
            if (context.isTvDevice()) "TV" else "Telefon/Tablet"
        } catch (_: Throwable) {
            "?"
        }
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("Tarih: $time")
            appendLine("Sürüm: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}, Android API ${Build.VERSION.SDK_INT}")
            appendLine("Cihaz türü: $deviceKind")
            appendLine("İş parçacığı: ${thread.name}")
            appendLine()
            append(stack)
        }
    }
}
