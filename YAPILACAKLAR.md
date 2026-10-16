# Vega IPTV — Yapılacaklar Listesi

_Son güncelleme: 17 Ekim 2026 · Sürüm: v1.2.7 (versionCode 49) · Dal: `analiz/oynatici`_

Kodun güncel hali yeniden incelenerek hazırlandı. Derleme ve birim testleri şu an başarılı (`./gradlew assembleRelease testDebugUnitTest`).

Öncelik: 🔴 acil · 🟠 önemli · 🟢 iyileştirme. Tamamlanan maddeleri `[x]` olarak işaretleyin.

---

## 🔴 1. Acil hatalar

- [x] **Rakam tuşları her ekranda kanal değiştiriyor ve yazı alanlarına rakam girilemiyor**
  `MainActivity.dispatchKeyEvent` içindeki genel 0-9 yakalama kaldırıldı, sadece mobil `PlayerScreen` için sınırlandırıldı.
  Giriş ekranı, Arama kutusu ve Ayarlar PIN alanına artık klavye ve kumandayla normal rakam yazılabilir. TV rehberinde ise `TvTiviMateScreen` ve `TvPlayerOverlay` kendi `onPreviewKeyEvent` dinleyicileriyle numaratörü hatasız açar.
- [x] **TV'de yayınlar arka planda sesli başlıyor**
  `checkAndAutoStartLastChannel` ve `executeZapperSwitch` TV modunda tetiklendiğinde `selectedHomeTab` otomatik olarak `HomeTab.LIVE_TV` sekmesine geçirilir. Ayrıca `MainActivity` içindeki CH+/CH- tuşları TV'de yalnızca Canlı TV sekmesi açıkken çalışacak şekilde kısıtlandı.
- [x] **TV'de program bilgisi (EPG) görünmüyor**
  `TvTiviMateScreen` ve `TvEpgGridScreen` listelerindeki kanallar için `LaunchedEffect(channel.streamId) { viewModel.loadEpgSummary(channel.streamId) }` eklendi. Görünen her kanalın o anki program bilgisi ve ilerleme yüzdesi otomatik olarak yüklenir.

## 🟠 2. Yarım kalan işler (önceki listede tamamlandı olarak işaretlenmişti)

- [ ] **EPG ızgarası gerçek bir saat çizelgesi değil.** `TvEpgGridScreen` üstte 30 dakikalık saat dilimleri gösteriyor, ama program kutuları sabit genişlikte (240dp ve 200dp) ve saatlerle hizalı değil. Ayrıca sadece "şu an" ve "sıradaki" program gösteriliyor.
  Program kutularının genişliği başlangıç-bitiş saatine göre hesaplanmalı ve kanal başına birkaç saatlik EPG yüklenmeli.
- [ ] **TMDb anahtarı hâlâ repoda.** `app/build.gradle.kts` içinde eski anahtar yedek değer olarak yazılı duruyor. Ayrıca anahtar git geçmişinde de var.
  **Yapılacaklar:** Yeni bir TMDb anahtarı al ve `local.properties` dosyasına yaz. Yedek değeri boş yap. Eski anahtarı TMDb hesabından iptal et.
- [ ] **Büyük dosyalar bölünmedi.** `SettingsAccountSection.kt` oluşturulmuş ama hiçbir yerde kullanılmıyor. `SettingsScreen.kt` değişmedi (~2.100 satır).
  `PlayerScreen.kt` (~3.700 satır) ve `MainViewModel.kt` (~1.600 satır) da olduğu gibi duruyor.
- [ ] **Kullanılmayan ve tekrarlanan kod:**
  - `LiveChannelCard.kt` hiçbir yerde kullanılmıyor.
  - `ActionChipButton` iki ayrı dosyada farklı parametrelerle tanımlı (`ActionChipButton.kt` ve `CategorySidebar.kt`). Tek bir tanımda birleştirilmeli.
- [ ] `local.properties.example` dosyası kişisel bilgisayar yolunu içeriyor (`C:\Users\murat\...`). Genel bir örnek değerle değiştirilmeli.

## 🟠 3. Performans

- [ ] **Favoriler için tüm katalog belleğe yükleniyor.** `MainViewModel` içindeki `favoriteLiveStreams`, `favoriteVodStreams` ve `favoriteSeriesStreams` açılışta tüm kanalları, ~13.000 filmi ve ~2.500 diziyi belleğe alıp sürekli tutuyor (`SharingStarted.Eagerly`). Veritabanındaki her değişiklikte de hepsini yeniden süzüyor.
  **Çözüm:** DAO'ya `WHERE streamId IN (:ids)` sorgusu ekle ve listeyi sadece Favoriler ekranı açıkken topla (`WhileSubscribed`).
- [ ] **Kanal geçişi yüklü kategoriye bağlı.** `zapNextChannel`, `zapPreviousChannel` ve rakamla kanal seçme `liveStreams` listesini kullanıyor. Bu liste o an açık kategoriyi içerdiği için kanal numarası başka kategorideki bir kanala denk gelirse yanlış kanal açılıyor (`getOrNull(targetNum - 1)` yedeği).
  Rakamla seçim tüm kanal listesinde `num` ile yapılmalı.

## 🟠 4. Birleştirme ve yayın (bunları sizin yapmanız gerekiyor)

- [ ] **Dalları birleştirin.** Bu klasördeki `analiz/oynatici` dalında GitHub'a gönderilmemiş 16 commit var. TV menüsü ayrıca GitHub'daki PR #3'te (`feat/tv-full-navigation`) duruyor.
  Önerilen yol: `analiz/oynatici` dalını push edip PR #3 yerine bunu `main`'e birleştirin.
- [ ] **Release anahtarı (keystore):** Bu bilgisayarda yok. Release APK debug anahtarıyla imzalanıyor ve müşteri cihazındaki sürümün üzerine kurulamıyor. Gerçek `.jks` dosyası gerekiyor.
- [ ] **Sürüm numarası:** Yayından önce `versionCode` 49, `versionName` 1.2.7 olarak artırılmalı.
- [ ] **Gerçek TV cihazında test:** Favoriler, Ayarlar, film/dizi detayları, EPG ızgarası ve kumandayla rakam girişi denenmeli.
- [ ] Repo herkese açık. Sunucu adresleri (`AppConfig.kt`) sadece kolayca çözülebilen bir şekilde gizlenmiş. Repoyu **private** yapmayı düşünün.

## 🟢 5. Test ve kod kalitesi

- [ ] Birim testi sadece `TurkishStringNormalizer` için var. Sıradaki testler:
  - kategori filtreleme (gizli, sabitlenmiş, yetişkin içerik)
  - rakamla kanal seçme
  - `FailoverInterceptor`
- [ ] `MainActivity` içinde OK tuşuna uzun basma için kullanılan `startTracking()`, `dispatchKeyEvent` içinde çağrılıyor. Bu durumda `onKeyLongPress` güvenilir şekilde tetiklenmeyebilir. Gerçek cihazda doğrulanmalı.

## 🟢 6. Özellik fikirleri

- [ ] Ebeveyn kilidi PIN'ini TV'de kumandayla girme (önce 1. maddedeki rakam sorunu çözülmeli)
- [ ] Çoklu profil (aynı hesapta farklı favoriler ve izleme geçmişi)

---

### ✅ Tamamlananlar (16-17 Ekim)

- Mobilde kanal değiştirirken siyah ekran sorunu (`PlayerScreen` kapanırken yeni yayını durdurmuyor)
- Favoriler artık tüm veritabanından okunuyor (performans notu için 3. bölüme bakın)
- Sahte "7.8" puanı kaldırıldı
- TV sol menüsü: Ana Sayfa, Canlı TV, Filmler, Diziler, Favoriler, Ara, Ayarlar
- Diziler: TV'de 5-7 sütun, sonsuz kaydırma, Filtrele/Sırala menüsü
- Çift odak sorunu giderildi
- APK git'ten çıkarıldı, sürüm notları `docs/release-notes/` altına taşındı
- `TurkishStringNormalizer` birim testleri eklendi
- TV'de kumandayla rakam girerek kanal seçme ve CH+/CH− desteği (1. ve 3. bölümlerdeki sorunlara bakın)
