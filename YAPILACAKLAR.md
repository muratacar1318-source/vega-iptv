# Vega IPTV — Yapılacaklar Listesi

_Son güncelleme: 16 Ekim 2026 · Sürüm: v1.2.6 (versionCode 48)_

Tamamlanan maddeleri `[x]` olarak işaretleyin. Öncelik: 🔴 acil · 🟠 önemli · 🟢 iyileştirme

---

## 🔴 1. Acil — Kullanıcıyı doğrudan etkileyen hatalar

- [x] **Mobilde kanal değiştirirken siyah ekran** _(Gemini üzerinde çalışıyor)_
  Kanal değişince eski `PlayerScreen` kapanırken `stopAndClear()` çağrılıyor ve yeni yayını durduruyor.
  Ayrıntı: `docs/analiz-oynatici.md` · Dosyalar: `PlayerScreen.kt`, `MainViewModel.playLiveChannel`
- [x] **Favoriler eksik görünüyor**
  `FavoritesScreen` favorileri sadece o an yüklü kategorinin listesinden (`liveStreams` / `vodStreams` / `seriesStreams`) süzüyor.
  Başka bir kategori açıksa favori kanallar, filmler ve diziler görünmüyor. Favoriler tüm veritabanından okunmalı.
- [x] **Sahte puan gösterimi**
  Puanı olmayan dizilerde "7.8" yazıyor (`VegaMediaCatalogScreen.kt:204`, `MainViewModel.kt:1126`).
  Puan yoksa rozet hiç gösterilmemeli.

## 🟠 2. Android TV

- [x] Sol menü: Ana Sayfa, Canlı TV, Filmler, Diziler, Favoriler, Ara, Ayarlar _(PR #3)_
- [x] Diziler: TV'de 5-7 sütun ve sonsuz kaydırma (önceden 60 diziyle sınırlıydı) _(PR #3)_
- [ ] **PR #3'ü birleştir.** TV değişiklikleri hem GitHub'daki `feat/tv-full-navigation` dalında hem de buradaki `analiz/oynatici` dalında (yerel commit) duruyor. Tek yerde toplanmalı.
- [ ] **Gerçek TV cihazında test:** Favoriler, Ayarlar, film/dizi detay sayfaları ve oynatıcı kumandayla denenmeli. Şimdiye kadar sadece emülatörde denendi.
- [x] **Çift odak sorunu:** Bazı öğelerde `Surface(onClick)` ile `dpadFocusable(onClick)` birlikte kullanılıyordu. `Surface(onClick)` kaldırılarak çift odak sorunu giderildi.
- [x] **Diziler ekranındaki "Filtrele" butonu çalışmıyor** (`onClick = {}`, `VegaMediaCatalogScreen.kt:104`). Filmlerdeki sıralama ve filtre menüsü buraya da eklendi.
- [x] TV Ana Sayfa'da kumandayla aşağı inince "Kaldığın Yerden Devam Et" ve "Son Eklenenler" satırlarına odak geçişi kontrol edildi (`dpadFocusable` desteği doğrulandı).

## 🟠 3. Derleme ve yayınlama

- [ ] **Release anahtarı (keystore):** Bu bilgisayarda yok. Release APK şu an debug anahtarıyla imzalanıyor ve müşteri cihazındaki sürümün üzerine güncelleme olarak kurulamıyor.
  Gerçek `.jks` dosyası bulunup `keystore.properties` ayarlanmalı (bkz. `keystore.properties.example`).
- [ ] Her yayında `versionCode` ve `versionName` değerlerini artır (`app/build.gradle.kts`).
- [ ] GitHub Actions derlemesi kapatıldı, derleme yerelde yapılıyor (`./gradlew assembleRelease`). İleride gerekirse Actions → Android Build → **Enable workflow** ile yeniden açılabilir.
- [x] `VegaIPTV-v1.1.5.apk` git'ten çıkarıldı (`.gitignore` `*.apk` dosyalarını yok sayıyor).
- [x] Kök klasördeki `release_notes_v1.0.x.txt` dosyaları `docs/release-notes/` altına taşındı.

## 🟠 4. Güvenlik

- [x] **TMDb API anahtarı** koda doğrudan yazılmak yerine `BuildConfig` ve `local.properties` üzerinden okunacak şekilde taşındı (`TmdbScraper.kt:15`). Kendi anahtarınızı eklemek için `local.properties` dosyasına `TMDB_API_KEY=anahtarınız` yazabilirsiniz.
- [ ] `AppConfig.kt` içindeki sunucu adresleri sadece Base64/XOR ile gizlenmiş, yani kolayca çözülebilir. Bu bilgiyi gerçek bir sır olarak düşünmeyin.
- [ ] Repo herkese açık (public). Sunucu bilgisi ve marka içeriği için **private** yapmayı düşünün.

## 🟢 5. Kod kalitesi

- [ ] Çok büyük dosyaları böl:
  - `PlayerScreen.kt` (~3.700 satır) → mobil oynatıcı, hareket kontrolleri, kanal çekmecesi, ayar menüleri
  - `SettingsScreen.kt` (~2.100 satır) → her ayar grubu ayrı dosyaya
  - `MainViewModel.kt` (~1.600 satır) → Canlı, VOD, Dizi ve Oynatıcı ViewModel'leri
- [ ] **Hiç test yok.** Önce şunlara birim testi yazılmalı: `TurkishStringNormalizer`, kategori filtreleme (yetişkin içerik, gizli, sabitlenmiş), `FailoverInterceptor`.
- [ ] Kullanılmayan import ve parametreleri temizle (ör. `VegaMediaCatalogScreen.kt` içindeki `clickable`).

## 🟢 6. Özellik fikirleri

- [ ] TV için tam EPG ızgarası (saat çizelgeli program rehberi, TiviMate tarzı)
- [ ] Kumandayla kanal numarası girerek doğrudan kanala geçiş (ör. "1-2-3")
- [ ] Ebeveyn kilidini TV'de kumandayla PIN girilebilir hale getirme
- [ ] Çoklu profil (aynı hesapta farklı favoriler ve izleme geçmişi)
