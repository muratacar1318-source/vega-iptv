# G-054 — MainViewModel'i parçalara böl (davranış değişmeden)

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent. **Ön koşul:** G-052, G-053, G-056 main'e alınmış olmalı (`git log main` ile kontrol et;
  alınmamışsa başlama, raporla).
- **Branch:** `refactor/mainviewmodel-bolme` (main'den aç)
- **Commit mesajları:** her parça ayrı commit: `refactor(vm): <parça adı> ayrı sınıfa taşındı`
- **Değişecek dosyalar:** `ui/MainViewModel.kt` ve yeni oluşturulan `ui/vm/*.kt` dosyaları. UI dosyalarına
  (`ui/**/*Screen.kt` vb.) **dokunulmaz** (aşağıdaki "Cephe" kuralı sayesinde gerekmez). + rapor ve `YAPILACAKLAR.md`'de G-054 satırı.

## Sorun
`MainViewModel.kt` ~2.550 satır, 53'ten fazla durum akışı ve 116 fonksiyon; 26 dosya kullanıyor. Her yeni görev bu
dosyaya dokunuyor, çakışma ve gerileme riski yüksek.

## Yaklaşım: cephe (facade) — UI hiç değişmez
Her parça `ui/vm/` altında ayrı bir sınıfa taşınır. `MainViewModel` o sınıfın bir örneğini tutar ve **eski public
adları aynen** ileri yönlendirir:
```kotlin
// MainViewModel içinde
private val search = UniversalSearchController(viewModelScope, repository, ...)
val universalSearchQuery get() = search.query          // aynı ad, aynı tür
fun onUniversalSearchQueryChanged(q: String) = search.onQueryChanged(q)
```
Böylece `viewModel.universalSearchQuery` kullanan ekranlar değişmez. Parça sınıfları `ViewModel` DEĞİLDİR;
`CoroutineScope` (viewModelScope) ve ihtiyaç duydukları bağımlılıkları kurucudan alır.

## Parçalar (bu sırayla, her biri ayrı commit; her commit'ten sonra `:app:compileDebugKotlin` ve `:app:testDebugUnitTest`)
1. **Evrensel arama** (~282–415. satırlar, "EVRENSEL ARAMA" bölümü) → `ui/vm/UniversalSearchController.kt`
2. **Ebeveyn kilidi ve kategori yönetimi** (gizli/sabit kategoriler, `isParentalUnlocked`, PIN fonksiyonları,
   G-052'deki engel akışları, `visible*Categories`) → `ui/vm/CategoryAccessController.kt`
3. **Kısa EPG önbelleği** (~715. satırdan itibaren, `epgProgramsMap`, `loadEpgSummary`) → `ui/vm/EpgCache.kt`
4. **Hesap ve hız testi** (`speedTestResult`, hesap/abonelik bilgileri, içerik sayıları) → `ui/vm/AccountController.kt`
5. **Kanal geçişi (zapper)** (`zapperNumber`, `zapNextChannel`, `zapPreviousChannel`, `onNumericKeyPressed`, son kanal)
   → `ui/vm/ZapController.kt`

Hedef: MainViewModel ~1.500 satırın altına insin. Bir parçanın sınırı belirsizse (çok sayıda başka akışla iç içe)
o parçayı atla ve raporda gerekçesini yaz — zorlama.

## Kurallar
- **Davranış değişmez:** mantık satır satır taşınır; "iyileştirme", yeniden adlandırma, biçimlendirme yok.
- `stateIn(..., SharingStarted.Eagerly, ...)` olan akışlar aynı başlatma sırasıyla kurulmalı (init sırası önemli:
  bir parçanın akışı MainViewModel'deki başka bir akışa bağlıysa, parça o akışı kurucudan alsın).
- `private` olan yardımcılar parçaya taşınır; MainViewModel'de dışarıdan erişilmeyenler için yönlendirme yazma.
- `object ParentalPin` ve G-052'deki saf yardımcılar kendi dosyalarına taşınabilir (paket adı değişirse testlerin
  import'unu güncelle — testler bu görevde dokunulabilir tek dosya türü, sadece import satırı).
- Yeni bağımlılık / DI kütüphanesi ekleme.

## 🚫 DOKUNMA
- Ekran dosyaları, `TiviPlayerManager`, `XtreamRepository`, `UserPreferences`.
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- Her commit'te derleme + birim testleri geçer.
- Sonda: `wc -l app/src/main/java/com/tivimax/player/ui/MainViewModel.kt` değeri rapora.
- `git diff main --stat -- 'app/src/main/java/com/tivimax/player/ui/**/*Screen.kt'` → boş olmalı.

## Agent raporu
Beş parçanın beşi de taşındı (her biri ayrı commit; her commit'ten sonra `:app:compileDebugKotlin` + `:app:testDebugUnitTest` → BUILD SUCCESSFUL). Ön koşullar (G-052, G-053, G-056, G-057) main'deydi.

| # | Parça | Yeni dosya (ui/vm/) | Notlar |
|---|---|---|---|
| 1 | Evrensel arama | `UniversalSearchController.kt` (169 satır) | İzinli kategori kümeleri lambda ile verilir (arama anında okunur). Çakışmayı önlemek için iç adlar `searchQuery/searchFilter/liveResults/...`; MainViewModel'de eski adlar (`universalSearchQuery`, `universalLiveResults`, `recentSearches`, `setUniversalSearchQuery`…) aynen duruyor. `resetSessionState` içindeki `universalSearchJob?.cancel()` → `universalSearch.cancelSearch()`. |
| 2 | Ebeveyn kilidi ve kategori yönetimi | `CategoryAccessController.kt` (148) + `ParentalPolicy.kt` (40) | `hidden/pinnedCategories`, `isParentalUnlocked`, `parentalPin`, G-052 `blocked…CategoryIds`, `hasAdult…Category`, `visible…Categories`, `isAdultCategory`, PIN ve kategori gizle/sabitle fonksiyonları. `liveCategories/vodCategories/seriesCategories` MainViewModel'de kalır, kurucudan verilir. `ParentalPin` ve `AdultFilter` `ParentalPolicy.kt`'ye taşındı; **paket bilerek `com.tivimax.player.ui` bırakıldı** (dosya ui/vm'de) — `SettingsScreen`, `ParentalUnlockDialog` ve testler importlarını değiştirmeden çalışıyor, hiçbir test dosyasına dokunulmadı. |
| 3 | Kısa EPG önbelleği | `EpgCache.kt` (44) | `epgSummaryMap`, `epgProgramsMap`, `loadEpgSummary`. `loadEpgForChannel` (seçili kanalın `currentEpgList`'i, oynatma akışıyla iç içe) bilerek MainViewModel'de bırakıldı. |
| 4 | Hesap ve hız testi | `AccountController.kt` (177) | `accountContentCounts`, `accountRefreshing`, `loadAccountContentCounts`, `refreshAccountInfo`, `speedTestResult`, `isTestingSpeed`, `runSpeedTest`. `userInfo` MainViewModel'de kalır (başka yerler de yazıyor), kurucudan verilir. `switchPlaylist` içindeki `lastAccountRefreshTimeMs = 0L` → `account.resetRefreshThrottle()`. |
| 5 | Kanal geçişi (zapper) | `ZapController.kt` (128) | Numara ile geçiş, `executeZapperSwitch`, `dismissZapper`, `zapNext/Previous/Recall`. `playLiveChannel`, `scheduleTvZap` (TV debounce işi `tvZapJob` ile) ve ham/süzülmüş kanal listeleri MainViewModel'de kalır; parçaya lambda olarak verilir. G-052'nin "Bu kanal kilitli" mantığı aynen taşındı. |

**Cephe kuralı:** `git diff main --stat -- 'app/src/main/java/com/tivimax/player/ui/**/*Screen.kt'` → boş (hiçbir ekran dosyasına, `ui/tv`, `ui/components`'a dokunulmadı). MainViewModel'in eski public adları (özellik ve fonksiyonlar, varsayılan parametreler dahil) aynen ileri yönlendiriliyor.

**Başlatma sırası kontrolü (G-052/G-057 uyarısı):** MainViewModel'deki yeni sıra (satır no): `userInfo` 219 → `liveCategories/liveStreams/vodCategories/seriesCategories` 222–238 → `universalSearch` 287 (yalnız lambda, bağımlılığı sonradan okunur) → `lastPlayedLiveChannel` 318 → `categoryAccess` 322 (kategori akışlarını kurucudan alır) → `zapper` 355 (lambda; `liveStreams` ve `lastPlayedLiveChannel` doğrudan, ikisi de ondan önce) → `allLiveStreamsRaw` 397 ve `allLiveStreams` 399 (artık `categoryAccess` getter'ından `blockedLiveCategoryIds` okuyor; `categoryAccess` ondan ÖNCE kurulu) → `account` 447 → `epgCache` 531 → `init {}` 586 (G-057 `withTimeoutOrNull(2_000L)` beklemeleri 647/670/693, `liveCategories/vodCategories/seriesCategories` ve cephe getter'larını kullanıyor, hepsi init'ten önce kurulu). Facade özellikleri `get() =` ile tanımlı olduğundan sıra yalnız parça nesnelerinin (`categoryAccess` vb.) kurulum yerine bağlı; her parçanın kurucu bağımlılıkları kendisinden önce tanımlı. `SharingStarted.Eagerly` akışları (hidden/pinned/blocked/visible…) parça içinde aynı sırayla (hidden → pinned → unlocked → pin → blocked → hasAdult → visible) kuruluyor.

**Satır sayısı:** MainViewModel **2628 → 2185** (-443; yeni dosyalar toplam 706 satır, başlık/kurucu/yorumlar dahil). Hedef olan "~1.500 altı" bu beş parçayla TUTMADI: görev tarifindeki satır tahminleri (~2.550) beşinin ağırlığını olduğundan büyük gösteriyordu; kalan büyük bloklar görevde listelenmeyen alanlar: liste yönetimi (`switchPlaylist/addPlaylist/updatePlaylist/removePlaylist`, ~300 satır), içerik senkronizasyonu (`startPlaylistSync` ve adımları, ~300 satır), oynatma (`playVod/playSeriesEpisode/playLiveChannel`, bölüm geçişi, ~350 satır), içerik yükleme (`loadLive/Vod/Series…`, ~200 satır). Bunlar G-054 kapsamı dışında bırakıldı; ayrı bir "G-054b" olarak aynı cephe yöntemiyle taşınabilir (özellikle senkronizasyon ve liste yönetimi birbirine sıkı bağlı olduğu için tek parça).

**Atlanan parça:** yok. Sınırı belirsiz kalan tek şey `loadEpgForChannel` ve `scheduleTvZap`; ikisi de oynatma/önizleme akışlarına bağlı olduğundan MainViewModel'de bırakıldı (yukarıda).

**Test:** her commit'te 17 test sınıfı çalıştı, hepsi başarılı. Elle/cihaz denemesi yok; kritik yollar (arama, kilit aç/kapa, kanal numarası, hesap yenileme) davranış değiştirmeyecek şekilde satır satır taşındı ama uygulamada elle gözden geçirilmeli.
