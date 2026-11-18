# G-052 — +18 kilidi her yerde geçerli olsun (Tümü, arama, ana sayfa, Film, Dizi, TV)

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent.
- **Branch:** `fix/yetiskin-kilidi-her-yerde` (main'den aç)
- **Commit mesajı:** `fix(ebeveyn): +18 içerik Tümü listesinde ve aramada gizli, Film/Dizi ve TV'de kilit açılabiliyor`
- **Değişecek dosyalar (SADECE bunlar; yollar `app/src/main/java/com/tivimax/player/` altına göre):**
  1. `ui/MainViewModel.kt` — kategori/içerik süzme akışları ve ebeveyn fonksiyonları
  2. `ui/components/ParentalUnlockDialog.kt` (yeni) — ortak PIN diyaloğu
  3. `ui/live/LiveTvScreen.kt` — mevcut diyaloğu ortak bileşenle değiştir
  4. `ui/vod/VodScreen.kt`, `ui/catalog/VegaMediaCatalogScreen.kt` — kilit düğmesi + diyalog
  5. `ui/tv/TvTiviMateScreen.kt` ve TV'de film/dizi listesini çizen ekran(lar) — kilit düğmesi + diyalog
  6. `MainActivity.kt` — sadece `onStop` içine tek satır
  7. `app/src/test/java/com/tivimax/player/ui/AdultFilterTest.kt` (yeni)
  8. Bu görev dosyasının "Agent raporu" bölümü ve `YAPILACAKLAR.md` — sadece G-052 satırı

## Sorun (Claude'un kontrolünde bulunanlar)
1. **"Tümü" listesinde ve ekran içi aramada +18 süzülmüyor.** `MainViewModel`'deki içerik akışları
   (~850–900. satırlar) kategori `"all"` ya da arama doluyken `filterCat = null` ile **tüm** kayıtları çekiyor.
   Kategori süzmesi (`visibleLiveCategories` / `visibleVodCategories` / `visibleSeriesCategories`) sadece
   kategori listesini gizliyor; "Tüm Kanallar / Tüm Filmler / Tüm Diziler" ve ekran içi aramada +18 içerik
   (ve kullanıcının gizlediği kategorilerin içeriği) görünüyor.
2. **Ana sayfa da etkileniyor:** ana sayfa şeritleri/vitrin `vodStreams` ve `seriesStreams` listelerinden
   besleniyor (bunlar "Tümü" listesi), yani +18 afişler ana sayfaya düşebilir.
3. **Film ve Dizi ekranlarında kilidi açma yolu yok.** PIN diyaloğu ve kilit düğmesi sadece telefondaki
   `LiveTvScreen`'de. Film/dizideki +18 kategoriler ancak Canlı TV'den kilit açılırsa görünüyor.
4. **TV'de hiçbir yerde kilit düğmesi yok** (`ui/tv/` altında `unlockParental` çağrısı yok). TV kullanıcısı
   +18 içeriği hiç açamıyor.
5. Kilit uygulama arka plana gidince açık kalıyor (sadece uygulama kapanınca sıfırlanıyor).

## Çözüm

### A. Süzmeyi içerik düzeyine taşı (MainViewModel)
1. Engellenen kategori kimlikleri için tek akış ekle:
   `blockedLiveCategoryIds` / `blockedVodCategoryIds` / `blockedSeriesCategoryIds: StateFlow<Set<String>>`
   = gizli kategoriler (`hiddenCategories`) ∪ (kilit kapalıysa) `isAdultCategory(name)` olan kategoriler.
   `liveCategories`/`vodCategories`/`seriesCategories` + `hiddenCategories` + `isParentalUnlocked` birleşiminden üret.
2. Üç içerik akışında (`getLiveStreamsFilteredFlow`, `getVodStreamsFilteredFlow`, `getSeriesStreamsFilteredFlow`
   toplayıcıları) Room'dan gelen listeyi atamadan önce `filter { it.categoryId !in blocked }` uygula. Engel kümesi
   değişince liste yeniden süzülsün (combine'a ekle). Canlıda `favorites` / `recents` kategorileri de aynı süzmeden
   geçsin (kullanıcı favorisine +18 eklediyse kilit kapalıyken görünmesin).
3. Süzme kuralını saf bir yardımcıya koy ve test et (ör. `AdultFilter.blockedIds(categories, hidden, unlocked)`,
   `AdultFilter.isAdult(name)`). `isAdultCategory` mevcut kelimeleri korur; Türkçe büyük/küçük harf için
   `lowercase(Locale("tr"))` kullan ("YETİŞKİN" doğru yakalansın). Şu kelimeleri de ekle: `"+ 18"`, `"18 +"`, `"for adults"`.
4. Evrensel aramanın mevcut `allowedLiveCats/allowedVodCats/allowedSeriesCats` süzmesine dokunma (zaten doğru).
5. Kanal numarasıyla geçiş ve CH+/CH− (`zapNextChannel`, `onNumericKeyPressed` — `allLiveStreams` kullanıyor):
   kilit kapalıyken +18 kanala geçilmesin. Engellenen kanal atlanır, numarayla istenirse "Bu kanal kilitli"
   mesajı gösterilir (mevcut zapper mesaj alanı neyse onu kullan).

### B. Ortak kilit diyaloğu
1. `LiveTvScreen`'deki PIN diyaloğunu (G-050'deki "PIN belirle" modu dahil) davranışı değişmeden
   `ui/components/ParentalUnlockDialog.kt` içine taşı: `ParentalUnlockDialog(viewModel, onDismiss)`.
   `Color.Red` yerine `VegaColors.Danger`, sabit renkler yerine `VegaColors` kullan; buton metinleri cümle
   düzeninde ("Kilidi aç", "PIN'i kaydet").
2. `LiveTvScreen` bu bileşeni kullansın (mevcut kilit düğmeleri aynen kalır).

### C. Film, Dizi ve TV'de kilit düğmesi
1. Telefon/tablet: `VodScreen` ve dizi kataloğu (`VegaMediaCatalogScreen`) üst çubuğuna, arama ikonunun
   yanına `VegaIconButton` (kilit kapalı: `Icons.Default.Lock`, `TextSecondary`; açık: `Icons.Default.LockOpen`,
   `Danger`). Basınca: kilit kapalıysa `ParentalUnlockDialog`, açıksa `lockParental()`.
   **Sadece listede +18 kategori varsa göster** (kilit kapalıyken `blocked…` içinde yetişkin kategori var mı, ya da
   `vodCategories`'te `isAdultCategory` olan var mı). Hiç yetişkin kategorisi olmayan sağlayıcıda düğme görünmesin.
2. TV: `TvTiviMateScreen`'in kategori listesinin en altına ve TV'de film/dizi kategori listesinin en altına odaklanabilir
   bir satır: "+18 kilitli" / "+18 açık" (kilit ikonu). OK → aynı davranış. Yalnızca yetişkin kategori varsa görünür.
   Diyalogdaki rakam alanları kumandayla açılmalı (`tvOpenKeyboardOnOk`, G-050'deki gibi). TV odak hafızası
   (G-033/G-045) bozulmamalı: yeni satır listenin sonunda olsun, mevcut `focusRequester`'lara dokunma.
3. `docs/TASARIM-KURALLARI.md`'ye uy.

### D. Arka planda kilitlen
`MainActivity.onStop()` içinde, küçük pencere (PiP) değilse `viewModel.lockParental()` çağır (mevcut `inPip`
hesaplaması zaten orada). Başka satıra dokunma.

## Test (`AdultFilterTest.kt`)
- `isAdult`: "XXX Filmler", "Yetişkin", "YETİŞKİN", "+18 Kanallar", "18+", "Adult" → true; "Belgesel", "Çocuk",
  "Spor 1" → false.
- `blockedIds`: kilit kapalı → yetişkin + gizli kategoriler; kilit açık → sadece gizli kategoriler.
- Süzme: "Tümü" listesinde yetişkin kategorideki içerik çıkar, normal içerik kalır.

## 🚫 DOKUNMA
- Room sorguları ve DAO'lar (süzme bellekte yapılır; sorgu değişikliği gerekmez).
- G-050'deki PIN mantığı (`ParentalPin`, `createParentalPin`, `unlockParental`) — sadece taşınan diyalog kullanır.
- Oynatıcı dosyaları (`PlayerScreen`, `TiviPlayerManager`, `TvPlayerOverlay`).
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.
- `grep -rn "unlockParental\|ParentalUnlockDialog" app/src/main/java --include=*.kt` → TV dosyalarında da çıkmalı.

## Agent raporu
**A. Süzme içerik düzeyinde (MainViewModel)**
- Yeni saf yardımcı `object AdultFilter` (`isAdult`, `blockedIds`, `filterBlocked`); `isAdultCategory` artık buna yönlendiriyor. Kelimeler: mevcutlar + `"+ 18"`, `"18 +"`, `"for adults"`; küçük harfe çevirme hem `tr-TR` hem `ROOT` ile ("YETİŞKİN" yakalanır).
- `blockedLive/Vod/SeriesCategoryIds` (= gizli kategoriler ∪ kilit kapalıysa yetişkin kategoriler) üç içerik toplayıcısına (`getLive/Vod/SeriesStreamsFilteredFlow`) `combine` ile bağlandı; kilit açılıp kapanınca ya da kategori gizlenince liste yeniden süzülür. Canlıdaki favoriler/son izlenenler bu listeden türediği için aynı süzmeden geçiyor. Ana sayfa `vodStreams`/`seriesStreams`'ten beslendiği için ayrıca kod gerekmedi.
- `allLiveStreams` süzülmüş liste oldu (CH+/CH−, raf düzeni); ham liste özel `allLiveStreamsRaw`'da tutuluyor. Numarayla geçişte (`onNumericKeyPressed`) numara ham listede eşlenir; engelli kanal ise geçilmez ve "Bu kanal kilitli" Toast'ı çıkar (zapper'da hazır bir mesaj alanı yoktu; `PlayerScreen`/`TvPlayerOverlay` yasak olduğu için Toast kullanıldı).
- Evrensel aramanın `allowed…Cats` süzmesine dokunulmadı. Room/DAO değişmedi (süzme bellekte).
- Yeni `hasAdultLive/Vod/SeriesCategory` akışları: kilit düğmesi yalnızca listede yetişkin kategori varsa görünür (kilit açıkken de görünür kalır ki geri kilitlenebilsin).

**B. Ortak diyalog:** `ui/components/ParentalUnlockDialog.kt` (yeni): `ParentalUnlockDialog(viewModel, onDismiss)` (G-050 davranışı aynı, "PIN belirle" modu dahil; `VegaColors`/`VegaButton`, metinler cümle düzeninde "Kilidi aç"/"PIN'i kaydet"), `ParentalLockChip` (Film/Dizi üst çubuğu çipi) ve `ParentalLockRow` (TV satırı). `LiveTvScreen` artık bu diyaloğu kullanıyor; eski satır içi diyalog ve onun durum değişkenleri silindi, kilit düğmeleri aynen duruyor.

**C. Film/Dizi/TV kilit düğmeleri — TV'de kategori listesini hangi ekran çiziyor (istenen bulgu):**
- TV'de Canlı TV sekmesi → `TvTiviMateScreen` (kategori `LazyColumn`'unun SONUNA `item(key="parental_lock_row")` olarak "+18 kilitli/açık" satırı eklendi; mevcut `focusRequester`/`focusRestorer`'a dokunulmadı).
- TV'de Filmler ve Diziler sekmeleri ayrı bir TV ekranı DEĞİL: `HomeScreen` → `HomeContentBody` → telefondaki `VodScreen` ve `VegaMediaCatalogScreen` aynen çiziliyor; kategori listesi ortak `CategorySidebar` (`tvFocusBehavior = isTvDevice`). Kilit satırı için `CategorySidebar.kt`'ye (görev listesinde adı geçmiyordu, "TV'de film/dizi listesini çizen ekran" kapsamında sayıldı) isteğe bağlı `footer` parametresi eklendi; listenin sonunda ek öğe olarak çizilir, G-033/G-045 odak mantığı ve requester'lar değişmedi. TV'de footer'a `ParentalLockRow` veriliyor, telefonda üst çubuğa `ParentalLockChip` (sıralama düğmesinin yanı).
- Telefon/tablet: `VodScreen` ve `VegaMediaCatalogScreen` (grid başlık satırına `lockChip` yuvası). Canlı TV telefonda mevcut kilit düğmelerini koruyor.

**D.** `MainActivity.onStop()` içine tek satır: `if (!inPip) viewModel.lockParental()`.

**Test:** `AdultFilterTest` 6/6; `:app:testDebugUnitTest` ve `:app:compileReleaseKotlin` → BUILD SUCCESSFUL. `grep unlockParental|ParentalUnlockDialog|ParentalLockRow` TV dosyasında (`TvTiviMateScreen`) ve `CategorySidebar` üzerinden TV Film/Dizi yolunda çıkıyor.

**Elle denenmedi / açık noktalar:**
- Cihaz/emülatör yok: TV'de odak sırası (kilit satırına inme, diyalogda rakam girişi), telefonda çip yerleşimi görsel olarak doğrulanmadı.
- Açılışta kategoriler yüklenmeden içerik akışı ilk kez gelirse engel kümesi boştur ve çok kısa süre +18 içerik listede görünebilir, kategoriler yükleyince süzülür (kategori listesi boşken karar verilemiyor).
- Kilit kapalıyken kullanıcının Kategori Yönetimi'nde gizlediği kategorilerin içeriği de artık "Tümü"/aramada çıkmıyor (görev tarifi böyle istiyor).
- `LiveTvScreen`'de artık kullanılmayan `ParentalPin` importu silindi; başka satıra dokunulmadı.
