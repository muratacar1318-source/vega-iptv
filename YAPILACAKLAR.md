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

- [x] **EPG ızgarası gerçek bir saat çizelgesi değil.**
  `epgProgramsMap` StateFlow'u eklendi ve `loadEpgSummary` ile kanalın tüm yayın akışı yüklendi. `TvEpgGridScreen` içinde program kutuları yayın süresine göre (`(durationMinutes * 3.66f).dp`) dinamik genişlik alacak şekilde saat dilimleriyle hizalandı. Canlı yayınlanan program vurgulandı, oynatma simgesi eklendi ve kumanda odağında üst banner'da tam detaylar gösterildi.
- [x] **TMDb anahtarı hâlâ repoda.**
  `app/build.gradle.kts` içindeki sabit yedek anahtar kaldırıldı (`localProperties.getProperty("TMDB_API_KEY") ?: ""`). Geliştirici anahtarı `.gitignore` kapsamındaki `local.properties` içine alındı.
- [x] **Büyük dosyalar bölünmedi.**
  `SettingsAccountSection.kt` modülü `SettingsScreen.kt` içine bağlandı, ~160 satır mükerrer arayüz kodu temizlendi.
- [x] **Kullanılmayan ve tekrarlanan kod:**
  - `LiveChannelCard.kt` repodan silindi.
  - `ActionChipButton` tek bir ortak bileşende birleştirildi, `CategorySidebar.kt` içindeki mükerrer tanım kaldırıldı.
- [x] `local.properties.example` dosyası kişisel bilgisayar yolu temizlendi, genel `PATH_TO_ANDROID_SDK` şablonu yerleştirildi.

## 🟠 3. Performans

- [x] **Favoriler için tüm katalog belleğe yükleniyor.**
  `LiveDao`, `VodDao` ve `SeriesDao` içine `WHERE streamId IN (:ids)` Room sorguları eklendi. `MainViewModel` akışları `flatMapLatest` ve `SharingStarted.WhileSubscribed(5000)` ile sadece Favoriler açıkken ve doğrudan ID listesiyle çalışacak şekilde optimize edildi; on binlerce içerik gereksiz yere bellekte tutulmuyor.
- [x] **Kanal geçişi yüklü kategoriye bağlı.**
  `zapNextChannel`, `zapPreviousChannel` ve `onNumericKeyPressed` fonksiyonları kategori dışına çıkıldığında tüm kanal listesini (`allLiveStreams`) ve `num` eşleşmesini kullanacak şekilde güncellendi.

## 🟠 4. Birleştirme ve yayın (bunları sizin yapmanız gerekiyor)

- [ ] **Dalları birleştirin.** Bu klasördeki `analiz/oynatici` dalında GitHub'a gönderilmemiş commit'ler var. TV menüsü ayrıca GitHub'daki PR #3'te (`feat/tv-full-navigation`) duruyor.
  Önerilen yol: `analiz/oynatici` dalını push edip PR #3 yerine bunu `main`'e birleştirin.
- [ ] **Release anahtarı (keystore):** Bu bilgisayarda yok. Dynamic keystore desteği eklendi (`keystore.properties.example`). Gerçek `.jks` dosyası eklendiğinde doğrudan gerçek anahtarla imzalanır.
- [x] **Sürüm numarası:** `versionCode` 49, `versionName` 1.2.7 olarak güncellendi ve R8 ile optimize edilen release APK üretildi (`app-release.apk`, ~16.4 MB).
- [ ] **Gerçek TV cihazında test:** Favoriler, Ayarlar, film/dizi detayları, EPG ızgarası ve kumandayla rakam girişi denenmeli.
- [ ] Repo herkese açık. Sunucu adresleri (`AppConfig.kt`) sadece kolayca çözülebilen bir şekilde gizlenmiş. Repoyu **private** yapmayı düşünün.

## 🟢 5. Test ve kod kalitesi

- [x] Kapsamlı birim testleri eklendi (`./gradlew testDebugUnitTest` başarılı):
  - Kategori filtreleme: gizli kategoriler, sabitlenmiş kategoriler, yetişkin (+18) içerik kilidi (`CategoryFilteringTest.kt`)
  - Rakamla kanal seçme & zapping: numara eşleşmesi, kategori yalıtımı çözümü, fallback (`ZapperMatchingTest.kt`)
  - `FailoverInterceptor`: sunucu fallback URL sırası ve URL temizleme (`FailoverInterceptorTest.kt`)
- [x] `MainActivity` içinde OK tuşuna uzun basma için `onKeyDown` override edilerek `startTracking()` garantiye alındı ve TV modunda `zapRecall` tetiklemesi yalnızca Canlı TV açıkken çalışacak şekilde güvene alındı.

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
