# Vega IPTV - Yeni Nesil Mobil IPTV Oynatıcı

TiviMate'in Android TV'deki efsanevi hız ve akıcılık standartlarını **akıllı telefon ve tabletlere özel mobil dokunmatik deneyim (Touch UX)** ile birleştiren, kendi IPTV servisiniz için özelleştirilmiş (White-Label) yerel Android uygulaması.

---

## 🚀 TiviMate'den Daha İyi Yapan Mobil Özellikler

| Özellik | TiviMate | Vega IPTV (Bu Uygulama) |
|---|:---:|:---:|
| **Platform Odağı** | Android TV / Kumanda | **Telefon & Tablet Öncelikli Dokunmatik UX** |
| **Ekran Hareketleri** | ❌ (Kumanda odaklı) | ✅ **Sol taraf dikey kaydırma: Parlaklık**<br>✅ **Sağ taraf dikey kaydırma: Ses Seviyesi**<br>✅ **Çift tık: 10s İleri/Geri & En-Boy Oranı Değiştirme** |
| **Hızlı Zapping Çekmecesi** | Kısmi | ✅ **Yayın durmadan yandan açılan yarı saydam kanal listesiyle anında kanal geçişi** |
| **Netflix Tarzı VOD & Dizi** | Basit liste | ✅ **TMDb/IMDb puanları, afişler, konu özeti, oyuncu kadrosu, sezon/bölüm seçici** |
| **Kaldığın Yerden Devam Et** | Sınırlı | ✅ **Film ve dizi bölümlerinde saniyesine kadar otomatik kayıt ve tek tıkla devam etme** |
| **Dahili Sunucu Hız Testi** | ❌ Yok | ✅ **"Yayın donuyor" şikayetlerini çözmek için dahili ping ve sunucu bağlantı testi** |
| **Markalama (White-Label)** | ❌ Genel oynatıcı | ✅ **Sunucu/DNS adresi gömülü; müşteriler sadece Kullanıcı Adı ve Şifre girer** |
| **Ekran Dokunma Kilidi** | ❌ Yok | ✅ **Yatakta veya elde izlerken yanlışlıkla dokunmaları engelleyen kilit butonu** |
| **Resim İçinde Resim (PiP)** | Temel | ✅ **Arka planda veya WhatsApp'tayken yayını kayan pencerede izleme** |

---

## 🛠 Mimari ve Teknoloji Yığını

- **Dil & Arayüz:** %100 Modern Kotlin + Jetpack Compose + Material 3 (OLED Midnight Dark Theme)
- **Video Oynatma Motoru:** AndroidX Media3 (ExoPlayer)
  - Donanım hızlandırmalı video çözümleme (Hardware Acceleration)
  - HLS (`.m3u8`), TS (`.ts`), DASH (`.mpd`) ve Progressive MP4/MKV desteği
  - Hızlı kanal geçişi (fast zapping) için optimize edilmiş 800ms başlatma tamponu (`DefaultLoadControl`)
- **Ağ & API:** OkHttp 4 + Gson + Xtream Codes Player API (`player_api.php`)
- **Görsel Önbellekleme:** Coil Compose (kanal logoları ve film afişleri için yüksek performanslı önbellek)
- **Veri Depolama:** Jetpack DataStore Preferences (şifreler, favoriler, izleme süreleri)

---

## ⚙️ Kendi IPTV Servisinizi Ayarlama (White-Label)

Uygulamanın varsayılan sunucu adresini, marka ismini ve iletişim bilgilerini değiştirmek için:

Dosya: `app/src/main/java/com/tivimax/player/data/config/AppConfig.kt`

```kotlin
object AppConfig {
    const val APP_NAME = "Kendi Markanız"
    const val APP_TAGLINE = "Hızlı ve Kesintisiz IPTV Deneyimi"

    // Kendi IPTV Sunucunuzun Adresi ve Portu:
    const val DEFAULT_SERVER_URL = "http://kendi-iptv-sunucunuz.com:8080"

    // Yedek Sunucu Domainleri (ISP engellerine karşı otomatik denenir):
    val FALLBACK_DOMAINS = listOf(
        "http://yedek1.kendi-iptv-sunucunuz.com:8080",
        "http://yedek2.kendi-iptv-sunucunuz.com:8080"
    )

    // Destek Hatları (Ayarlar menüsünde görünür):
    const val SUPPORT_WHATSAPP = "+905xxxxxxxxx"
    const val SUPPORT_TELEGRAM = "t.me/kullaniciadiniz"
}
```

Bu ayarı yaptıktan sonra müşterileriniz sunucu adresi girmekle uğraşmaz; yalnızca **Kullanıcı Adı** ve **Şifre** yazarak tek tıkla bağlanırlar.

---

## 📱 Dokunmatik Oynatıcı Kontrolleri Rehberi

- **Sol Tarafta Yukarı/Aşağı Kaydırma:** Ekran parlaklığını anlık olarak ayarlar (%0 - %100).
- **Sağ Tarafta Yukarı/Aşağı Kaydırma:** Medya ses seviyesini ayarlar (%0 - %100).
- **Sol Tarafa Çift Tıklama:** 10 saniye geri sarar.
- **Sağ Tarafa Çift Tıklama:** 10 saniye ileri sarar.
- **Ekran Ortasına Çift Tıklama:** En-boy oranını döngüsel olarak değiştirir:
  - `FIT`: Orijinal yayın oranını korur.
  - `FILL`: Ekrandaki siyah boşlukları dolduracak şekilde kırpar.
  - `ZOOM`: Hafif yakınlaştırma uygular.
  - `16:9`: Standart geniş ekran moduna sabitler.
- **Hızlı Kanal Çekmecesi:** Canlı yayın sırasında sol üstteki liste simgesine tıklandığında yayın kesilmeden yarı saydam kanal listesi açılır. Buradan başka bir kanala tıklandığında yayın hemen o kanala geçer.
- **Kilit Simgesi:** Tüm dokunmatik hareketleri kilitler; istenmeyen dokunmaları engeller.
- **PiP Simgesi:** Uygulamayı arka plana atıp kayan pencerede yayını sürdürür.

---

## 📦 APK Derleme ve Dağıtım

### Debug APK (Test için):
```bash
./gradlew assembleDebug
```
Üretilen APK: `app/build/outputs/apk/debug/app-debug.apk`

### İmzalı Release APK (Müşterilere dağıtım için):
```bash
./gradlew assembleRelease
```
Üretilen APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
