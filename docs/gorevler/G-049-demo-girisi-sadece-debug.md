# G-049 — Demo girişi sadece geliştirici derlemesinde

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent.
- **Branch:** `fix/demo-sadece-debug` (main'den aç)
- **Commit mesajı:** `fix(giriş): demo girişi müşteri sürümünden kaldırıldı`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/ui/login/LoginScreen.kt`
  2. `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt` — sadece `loginDemo()` fonksiyonunun ilk satırı
  3. Bu görev dosyasının "Agent raporu" bölümü ve `YAPILACAKLAR.md` — sadece G-049 satırı

## Sorun
Giriş ekranındaki "Demo" butonu müşteriye dağıtılan sürümde de görünüyor. Bu buton `MainViewModel.loginDemo()`
ile TRT 1, beIN Sports 1, National Geographic, NASA TV adları ve logolarıyla üçüncü taraf test yayınlarını
listeliyor. Kendi markalı bir üründe başka kanalların marka ve logoları sahte içerikle gösterilmemeli;
müşteri için de kafa karıştırıcı.

## Çözüm
1. `LoginScreen.kt` içinde `// Demo Modu ile Test Et Butonu` yorumuyla başlayan `OutlinedButton`'ı ve
   hemen öncesindeki ona ait `Spacer`'ı `if (BuildConfig.DEBUG) { … }` içine al
   (`import com.tivimax.player.BuildConfig`). Release'de buton ve boşluk hiç çizilmez.
   TV yolunu da kontrol et: TV'de giriş ekranı aynı dosyadan geliyorsa ek iş yok; ayrı bir TV giriş ekranında
   demo düğmesi varsa onu da aynı şekilde sar.
2. `MainViewModel.loginDemo()` fonksiyonunun ilk satırı olarak `if (!BuildConfig.DEBUG) return` ekle
   (yanlışlıkla başka yerden çağrılırsa release'de hiçbir şey yapmasın). Fonksiyonun geri kalanına dokunma.
3. Düğme kaldırılınca giriş ekranında TV kumandası odak sırası bozulmamalı (Giriş yap düğmesinden aşağı
   basınca boşluğa düşmemeli). `focusRequester`/`focusProperties` ile demo düğmesine bağlanan bir şey varsa
   koşula göre düzelt.

## 🚫 DOKUNMA
- `loginDemo()` içindeki sahte veri listeleri (debug'da kalabilir).
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:compileReleaseKotlin` ve `:app:compileDebugKotlin` (düşük öncelikle, pencere açmadan) → hatasız.
- `grep -n "BuildConfig.DEBUG" app/src/main/java/com/tivimax/player/ui/login/LoginScreen.kt` → en az 1 satır.

## Agent raporu
- `LoginScreen.kt`: "Hemen dene (canlı demo)" düğmesi ve öncesindeki `Spacer` artık `if (BuildConfig.DEBUG) { … }` içinde (`BuildConfig` import edildi). Release'de ikisi de çizilmez.
- `MainViewModel.kt`: `loginDemo()` ilk satırı `if (!BuildConfig.DEBUG) return`. Fonksiyonun geri kalanına dokunulmadı.
- TV yolu: demo düğmesi yalnızca bu dosyada var (`grep` ile başka giriş ekranında demo yok); giriş ekranı TV'de de aynı dosyadan geliyor, ek iş yok.
- Odak sırası: `LoginScreen.kt`'de `focusRequester`/`focusProperties` yok, alanlar `LocalFocusManager.moveFocus` ile ilerliyor; demo düğmesine bağlı özel odak yok → "Giriş yap"tan aşağı basınca boşluğa düşme riski yok. TV'de elle denenmedi.
- Test: `:app:compileDebugKotlin` ve `:app:compileReleaseKotlin` → BUILD SUCCESSFUL. `grep "BuildConfig.DEBUG" LoginScreen.kt` → 389. satır.
