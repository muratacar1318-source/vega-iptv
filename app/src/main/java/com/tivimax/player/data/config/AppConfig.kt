package com.tivimax.player.data.config

object AppConfig {
    // Uygulama Marka İsmi
    const val APP_NAME = "Vega IPTV"
    const val APP_TAGLINE = "Hızlı, Akıcı ve Kesintisiz Yayın Deneyimi"

    // Gömülü Sunucu Adresleri
    const val SERVER_URL_HTTPS = "https://mntrdns.com"
    const val SERVER_URL_HTTP = "http://mntrdns.com"
    const val DEFAULT_SERVER_URL = SERVER_URL_HTTPS

    // Yedek Sunucu Domainleri (ISP engeli veya sunucu yoğunluğunda otomatik denenir)
    val FALLBACK_DOMAINS = listOf(
        "http://mntrdns.com",
        "https://mntrdns.com"
    )

    // Destek Hattı / İletişim (Ayarlar ekranında görünür)
    const val SUPPORT_WHATSAPP = "+905550000000"
    const val SUPPORT_TELEGRAM = "t.me/vegaiptv"
    const val SUPPORT_WEBSITE = "https://mntrdns.com"

    // Video Motoru İnce Ayarları (TiviMate Hızı İçin)
    const val MIN_BUFFER_MS = 2500            // Minimum tampon (hızlı kanal açılışı için)
    const val MAX_BUFFER_MS = 8000            // Maksimum tampon (mobil veri tasarrufu ve takılmama dengesi)
    const val BUFFER_FOR_PLAYBACK_MS = 800    // Oynatma başlangıcı tamponu (0.8 saniyede anında yayın başlar)
    const val BUFFER_FOR_REBUFFER_MS = 1500   // Yeniden tamponlamada bekleme süresi
}
