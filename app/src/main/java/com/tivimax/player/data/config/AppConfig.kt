package com.tivimax.player.data.config

import com.tivimax.player.data.security.SecurityUtils

object AppConfig {
    // Uygulama Marka İsmi
    const val APP_NAME = "Vega IPTV"
    const val APP_TAGLINE = "Hızlı, Akıcı ve Kesintisiz Yayın Deneyimi"

    // Gömülü Sunucu Adresleri (Tersine mühendisliğe karşı XOR/Base64 ile gizlenmiştir)
    private const val OBF_SERVER_URL_HTTPS = "Mi4uKilgdXU3NC4oPjQpdDk1Nw==" // https://mntrdns.com
    private const val OBF_SERVER_URL_HTTP = "Mi4uKmB1dTc0Lig+NCl0OTU3"     // http://mntrdns.com
    private const val OBF_SUPPORT_WHATSAPP = "cWNqb29vampqampqag=="        // +905550000000
    private const val OBF_SUPPORT_TELEGRAM = "LnQ3P3UsPz07MyouLA=="        // t.me/vegaiptv

    val SERVER_URL_HTTPS: String by lazy { SecurityUtils.deobfuscate(OBF_SERVER_URL_HTTPS) }
    val SERVER_URL_HTTP: String by lazy { SecurityUtils.deobfuscate(OBF_SERVER_URL_HTTP) }
    val DEFAULT_SERVER_URL: String by lazy { SERVER_URL_HTTPS }

    // Yedek Sunucu Domainleri (ISP engeli veya sunucu yoğunluğunda otomatik denenir)
    val FALLBACK_DOMAINS: List<String> by lazy {
        listOf(
            SERVER_URL_HTTP,
            SERVER_URL_HTTPS
        )
    }

    // Destek Hattı / İletişim (Ayarlar ekranında görünür)
    val SUPPORT_WHATSAPP: String by lazy { SecurityUtils.deobfuscate(OBF_SUPPORT_WHATSAPP) }
    val SUPPORT_TELEGRAM: String by lazy { SecurityUtils.deobfuscate(OBF_SUPPORT_TELEGRAM) }
    val SUPPORT_WEBSITE: String by lazy { SERVER_URL_HTTPS }

    // Video Motoru İnce Ayarları (TiviMate Hızı İçin)
    const val MIN_BUFFER_MS = 2500            // Minimum tampon (hızlı kanal açılışı için)
    const val MAX_BUFFER_MS = 8000            // Maksimum tampon (mobil veri tasarrufu ve takılmama dengesi)
    const val BUFFER_FOR_PLAYBACK_MS = 800    // Oynatma başlangıcı tamponu (0.8 saniyede anında yayın başlar)
    const val BUFFER_FOR_REBUFFER_MS = 1500   // Yeniden tamponlamada bekleme süresi
}
