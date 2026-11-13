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
_(Agent doldurur: taşınan parçalar, atlananlar ve neden, satır sayısı önce/sonra, test sonuçları.)_
