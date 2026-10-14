package com.tivimax.player.data.security

import android.util.Base64

/**
 * Güvenlik ve Gizleme Yardımcısı:
 * APK tersine mühendislikle (decompile) incelendiğinde sunucu adresleri ve hassas dizgilerin
 * düz metin (plain-text) olarak okunmasını engellemek için XOR + Base64 gizleme mekanizması sağlar.
 */
object SecurityUtils {

    // Karıştırma anahtarı
    private const val XOR_KEY = 0x5A

    /**
     * Düz metin bir veriyi XOR + Base64 ile gizler.
     */
    fun obfuscate(input: String): String {
        if (input.isEmpty()) return ""
        val bytes = input.toByteArray(Charsets.UTF_8)
        val xorBytes = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor XOR_KEY).toByte()
        }
        return Base64.encodeToString(xorBytes, Base64.NO_WRAP)
    }

    /**
     * XOR + Base64 ile gizlenmiş veriyi çalışma zamanında çözer.
     */
    fun deobfuscate(obfuscated: String): String {
        if (obfuscated.isEmpty()) return ""
        return try {
            val decoded = Base64.decode(obfuscated, Base64.DEFAULT)
            val result = ByteArray(decoded.size) { i ->
                (decoded[i].toInt() xor XOR_KEY).toByte()
            }
            String(result, Charsets.UTF_8)
        } catch (e: Throwable) {
            ""
        }
    }
}
