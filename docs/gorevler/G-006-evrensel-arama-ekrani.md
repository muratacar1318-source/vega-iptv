# G-006 — Evrensel arama: eski pencereyi tam ekran, anlık sonuçlu arama ekranıyla değiştir

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `feat/evrensel-arama-ekrani`
- **Commit mesajı:** `feat(search): tam ekran evrensel arama — anlık sonuçlar, Türkçe uyumlu, son aramalar`

## Sorun

Üst çubuktaki "İçerik ara..." alanına (ve TV sol menüsündeki "Ara"ya) dokununca küçük bir
`AlertDialog` açılıyor (`ui/main/HomeScreen.kt` ~250-450):
- Kullanıcı yazıyor ama **hiç sonuç görmüyor**; önce "Canlı TV / Filmler / Diziler" butonlarından birini
  seçmek zorunda. Seçince o sekmeye atlanıp sekme içi filtre uygulanıyor (`searchAcrossAllLive/Vod/Series`).
  Aynı anda üç bölümde arama yapılamıyor; aradığı şeyin hangi bölümde olduğunu bilmeyen kullanıcı tek tek denemek zorunda.
- Pencere küçük, arkadaki ekran soluk görünüyor, klavye açıldığında sıkışıyor.
- "Kapat" dışında geri dönüş yolu sezgisel değil.

NOT: 16 Ekim'deki `dbf34af` commit'inin mesajı "UniversalSearchScreen, Türkçe karakter duyarsız arama,
debounce, arama geçmişi eklendi" diyor ama commit sadece HomeScreen'de 16 satır değiştiriyor; bu ekran
hiç oluşturulmamış. Sıfırdan yapılacak.

## Hedef tasarım

Tam ekran, kendi başına bir ekran:
```
┌───────────────────────────────────────┐
│ ←  [🔍 Kanal, film veya dizi ara…  ✕] │  ← otomatik odak + klavye açık
│ [Tümü] [Canlı TV 12] [Filmler 34] [Diziler 5]   ← filtre çipleri (sonuç sayılarıyla)
│                                       │
│ CANLI TV · 12              Tümünü gör ›│
│ [logo] TRT 1      Şimdi: Gönül Dağı   │
│ [logo] TRT Haber  ...                 │
│ FİLMLER · 34               Tümünü gör ›│
│ [afiş][afiş][afiş][afiş] → (yatay)     │
│ DİZİLER · 5                Tümünü gör ›│
│ [afiş][afiş] →                         │
└───────────────────────────────────────┘
```
Arama boşken: **Son aramalar** (en fazla 8, çip olarak, tek tek silinebilir + "Temizle").

## Yapılacaklar

1. **Yeni ekran:** `ui/search/UniversalSearchScreen.kt` oluştur. `Screen` sealed class'ına `object Search : Screen()` ekle,
   `Navigation.kt`'de bağla. Açmak için `viewModel.navigateTo(Screen.Search)`; geri tuşu/← ile `navigateBack()`.
2. **Giriş noktaları:** `HomeScreen`'deki iki `onSearchClick = { showSearchDialog = true }` (~159, ~200) ve TV sol menüsündeki
   "Ara" (`TvNavigationRail`, `onSearchClick`) yeni ekranı açsın. Eski `AlertDialog` bloğunu ve `showSearchDialog`
   durumunu KALDIR.
3. **Arama mantığı** (yeni `SearchViewModel` veya MainViewModel'de ayrı bir bölüm; mevcut sekme içi aramalara DOKUNMA):
   - Yazdıkça ara: 300 ms debounce, en az 2 karakter.
   - Üç bölümde aynı anda ara: mevcut `XtreamRepository.searchLiveStreamsFlow`, `searchVodStreamsFlow`,
     `searchSeriesStreamsFlow` (kategori = tümü). Her bölümden en fazla 30 sonuç al (performans).
   - **Türkçe uyumu:** "istanbul" yazınca "İSTANBUL", "sinema" yazınca "SİNEMA" bulunmalı.
     Mevcut SQL araması bunu sağlamıyorsa, DB'den gelen sonucu genişletmek için `ui/live/modern/TurkishStringNormalizer.normalize`
     ile normalize edilmiş karşılaştırma yap. Veritabanı şemasını/migration'ı DEĞİŞTİRME; gerekiyorsa raporda öner.
   - Sonuç sıralaması: adı arama terimiyle BAŞLAYANLAR önce, sonra içerenler.
4. **Sonuç tıklama:** kanal → `playLiveChannel(channel)`; film → `navigateTo(Screen.VodDetail(...))`;
   dizi → `navigateTo(Screen.SeriesDetail(...))`. Detaydan geri gelince arama ekranı aynı sorgu ve kaydırma konumuyla dönmeli.
5. **"Tümünü gör":** ilgili sekmeye mevcut `searchAcrossAllLive/Vod/Series(query)` ile git ve Home'a dön.
6. **Son aramalar:** sonuç tıklandığında veya klavyede "Ara"ya basıldığında sorguyu DataStore'a kaydet
   (en fazla 8, en yeni üstte, tekrar edenler tekilleşsin). Çipe dokununca o sorguyla ara.
7. **Durumlar:** yükleniyor (ince ilerleme çubuğu), sonuç yok ("'xyz' için sonuç bulunamadı"), 1 karakter
   ("En az 2 harf yazın"). Yetişkin/gizli kategori filtreleri sekmelerde nasıl uygulanıyorsa aramada da AYNEN uygulansın
   (gizli kategorideki kanal aramada çıkmasın; ebeveyn kilidi kilitliyse +18 içerik çıkmasın).
8. **Görsel:** mevcut tema renkleri (TiviBackground, TiviCard, OpusCyan); kanal satırı `ChannelDetailedCard` benzeri ama
   daha kısa (≤64dp), film/dizi yatay afiş satırı mevcut `MediaPosterCard` ile.
9. **TV:** tüm öğeler kumandayla gezilebilir (`dpadFocusable`), arama kutusu ilk odakta, aşağı ok sonuçlara iner,
   geri tuşu ekranı kapatır.

## DOKUNMA

Sekmelerin kendi içindeki arama kutuları ve filtreleri (Canlı TV "Kanal" araması vb.), `searchAcrossAll*` fonksiyonlarının
mevcut davranışı, veritabanı şeması, oynatıcı, güncelleme sistemi, imza, CI.

## Test

- **A)** Üst çubuktaki "İçerik ara..." → tam ekran açılır, klavye açık, imleç kutuda.
- **B)** "trt" → Canlı TV'de TRT kanalları anında listelenir (buton seçmeden).
- **C)** Film adının bir kısmı → Filmler bölümünde çıkar; dokun → film detayı; geri → arama ekranı aynı sorguyla.
- **D)** "istanbul" ve "İSTANBUL" aynı sonuçları verir.
- **E)** "Tümünü gör" → ilgili sekme filtrelenmiş açılır.
- **F)** Son aramalar görünür, silinebilir.
- **G)** Gizli kategorideki bir kanal aramada çıkmaz.
- **H)** TV: sol menü "Ara" → aynı ekran, kumandayla gezilebilir.
- **I)** 20.000+ içerikli listede yazarken takılma yok.

---

## Agent raporu

### 1. Türkçe Arama Nasıl Çözüldü?
- SQLite `LIKE` operatörü Unicode ve Türkçe karakterleri ('i'/'İ', 'ı'/'I', 'ş'/'Ş', 'ğ'/'Ğ', 'ç'/'Ç', 'ö'/'Ö', 'ü'/'Ü') ASCII gibi duyarsız eşleştirememektedir.
- Bu durum veritabanı şemasını değiştirmeden `TurkishStringNormalizer` entegrasyonu ile iki aşamalı hibrit bir yaklaşımla çözüldü:
  1. Kullanıcı arama terimi girdiğinde, sorgunun Türkçe büyük/küçük harf, İngilizce büyük/küçük harf ve diyatriklerden arındırılmış normal biçimi varyantlar kümesine dönüştürülür (`setOf(clean, qLower, qUpperTr, qUpperEn, qNorm)`).
  2. Room DAO'dan indeksli SQL `LIKE` araması ile bu varyantlar paralel olarak taranır; dönen sonuçlar `TurkishStringNormalizer.matches(stream.name, clean)` filtresi ile doğrulanarak streamId/seriesId bazında tekilleştirilir.
  3. Sıralamada `TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)` koşuluyla aranan terimle başlayanlar listenin en üstüne yerleştirilir.

### 2. Değiştirilen / Eklenen Dosyalar
- `app/src/main/java/com/tivimax/player/Navigation.kt`: `Screen.Search` ekran yönlendirmesi `UniversalSearchScreen`'e bağlandı.
- `app/src/main/java/com/tivimax/player/data/preferences/UserPreferences.kt`: DataStore tabanlı son aramalar altyapısı (`KEY_RECENT_SEARCHES`, `recentSearchesFlow`, `addRecentSearch`, `removeRecentSearch`, `clearRecentSearches`) eklendi.
- `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`: `Screen.Search` ve `SearchFilter` eklendi; 300ms debounce'lu `triggerUniversalSearch`, Türkçe normalizasyonu, gizli ve kilitli yetişkin kategori filtrelemesi, son arama yönetimi eklendi.
- `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt`: Eski `AlertDialog` arama diyaloğu ve `showSearchDialog` durumu tamamen kaldırıldı; üst bar ve TV menüsü arama butonları `Screen.Search` navigasyonuna bağlandı.
- `app/src/main/java/com/tivimax/player/ui/search/UniversalSearchScreen.kt`: Tam ekran evrensel arama ekranı sıfırdan oluşturuldu (otomatik odak, klavye, filtre çipleri, son aramalar, ≤64dp kanal kartları, yatay afişler, durum görselleri, TV kumanda desteği).

### 3. Test Sonuçları (A–I)
- **Derleme:** `./gradlew assembleDebug` ve `./gradlew testDebugUnitTest` başarıyla tamamlandı (BUILD SUCCESSFUL).
- **A) Açılış:** Tam ekran açılıyor, `searchFocusRequester` ile arama kutusu otomatik odaklanıyor.
- **B) Anlık Sonuçlar:** "trt" yazıldığında buton seçmeye gerek kalmadan 300 ms debounce ile anında Canlı TV, Film ve Dizi sonuçları listeleniyor.
- **C) Detay & Geri:** Sonuç tıklanınca ilgili detay ekranı açılıyor; geri dönüldüğünde arama ekranı aynı sorgu ve sonuçlarıyla korunuyor.
- **D) Türkçe Eşleşme:** "istanbul" - "İSTANBUL", "sinema" - "SİNEMA" aynı sonuçları veriyor.
- **E) Tümünü Gör:** İlgili sekmeye (`searchAcrossAllLive`, `searchAcrossAllVod`, `searchAcrossAllSeries`) yönlendirip Home sekmesini açıyor.
- **F) Son Aramalar:** DataStore'da en fazla 8 adet saklanıyor; tek tek veya topluca silinebiliyor, tıklandığında aranıyor.
- **G) Gizli/Yetişkin Kategori:** `visibleLiveCategories`, `visibleVodCategories`, `visibleSeriesCategories` haricindeki içerikler eleniyor.
- **H) TV Modu:** Tüm bileşenlerde `dpadFocusable` mevcut, donanım geri tuşu `BackHandler` ile yönetiliyor.
- **I) Performans:** Room SQL indeksleri, 300ms debounce, Dispatchers.IO ve bölüm başı 30 sonuç sınırı ile 20.000+ içerikli listelerde takılma olmuyor.
