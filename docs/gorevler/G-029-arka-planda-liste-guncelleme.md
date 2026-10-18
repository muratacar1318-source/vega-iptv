# G-029 — Kurulu listede "İçerikleri güncelle" arka planda çalışsın

- **Öncelik:** 🟡
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım yazıldı — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `feat/arka-plan-guncelleme` (main'den aç)
- **Commit mesajı:** `feat(sync): kurulu listede içerik güncellemesi arka planda, ana ekranda şerit`
- **Değişecek dosyalar (sadece bunlar):**
  1. `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`
  2. Yeni: `app/src/main/java/com/tivimax/player/ui/components/BackgroundSyncBanner.kt`
  3. `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt`
  4. `app/src/main/java/com/tivimax/player/ui/settings/SettingsScreen.kt` (tek satır)
  5. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Ne istiyoruz (kullanıcı gözünden)
- Liste **daha önce kurulmuşsa** (`isSynced == true`), "İçerikleri güncelle" denince tam ekran kurulum ekranı **açılmasın**.
  Güncelleme arka planda çalışsın, kullanıcı uygulamayı kullanmaya devam etsin.
- Ana ekranda küçük bir şerit: `Liste güncelleniyor… %40` → bitince `Liste güncellendi` (3 sn sonra kaybolur).
  Hata olursa: `Liste güncellenemedi. Tekrar dene` (telefonda dokununca yeniden dener).
- Güncelleme sürerken kullanıcı **kanal / film / bölüm açarsa** güncelleme **durur** (oynatma her zaman önceliklidir):
  şerit `Güncelleme durduruldu. Tekrar dene` gösterir. (Yarım kalan güncelleme hiçbir şey silmez — silme sadece bir
  listenin tamamı başarıyla indiğinde yapılıyor. Bu güvenli.)
- Liste **hiç kurulmamışsa** (ilk kurulum, `isSynced == false`): bugünkü tam ekran akış **aynen** kalır.

## Adım 1 — Durum sınıfı (MainViewModel.kt, `data class PlaylistSyncState` tanımının hemen ALTINA, sınıf dışına)

```kotlin
/** G-029: Ana ekrandaki arka plan güncelleme şeridinin durumu. */
enum class BackgroundSyncStatus { HIDDEN, RUNNING, DONE, FAILED, STOPPED }
```

## Adım 2 — Yeni alanlar (MainViewModel sınıfının içinde, `private var syncJob: Job? = null` satırının hemen ALTINA)

```kotlin
    /** G-029: Şu anki senkron arka planda mı çalışıyor? */
    private var isBackgroundSyncRun = false

    /** G-029: Ana ekran şeridi için durum. */
    val backgroundSyncStatus = MutableStateFlow(BackgroundSyncStatus.HIDDEN)
```

## Adım 3 — Yeni fonksiyonlar (MainViewModel, `openPlaylistSync()` fonksiyonunun hemen ALTINA)

```kotlin
    /**
     * G-029: "İçerikleri güncelle" düğmesi bunu çağırır.
     * Liste daha önce kurulduysa arka planda günceller; kurulmadıysa bugünkü tam ekran akışı açar.
     */
    fun refreshPlaylistContent() {
        viewModelScope.launch {
            val active = playlistRepository.getActivePlaylist()
            if (active != null && active.isSynced) {
                startPlaylistSync(fromStep = 1, background = true)
            } else {
                openPlaylistSync()
            }
        }
    }

    /** G-029: Oynatma başlarken çağrılır. Arka plan güncellemesi sürüyorsa durdurur. */
    private fun stopBackgroundSyncForPlayback() {
        if (isBackgroundSyncRun && syncJob?.isActive == true) {
            syncJob?.cancel()
            syncJob = null
            isBackgroundSyncRun = false
            playlistSyncState.value = playlistSyncState.value.copy(isSyncing = false)
            backgroundSyncStatus.value = BackgroundSyncStatus.STOPPED
        }
    }

    /** G-029: Şeritteki "Tekrar dene" / kapatma. */
    fun dismissBackgroundSyncBanner() {
        backgroundSyncStatus.value = BackgroundSyncStatus.HIDDEN
    }
```

## Adım 4 — `startPlaylistSync` içinde KÜÇÜK değişiklikler (başka hiçbir satıra dokunma)

4a. İmza:
```kotlin
// ÖNCE
fun startPlaylistSync(fromStep: Int = 1) {
// SONRA
fun startPlaylistSync(fromStep: Int = 1, background: Boolean = false) {
```

4b. Fonksiyonun ilk satırı `syncJob?.cancel()`. **Hemen altına**:
```kotlin
        isBackgroundSyncRun = background
        backgroundSyncStatus.value = if (background) BackgroundSyncStatus.RUNNING else BackgroundSyncStatus.HIDDEN
```
(`closeMiniPlayer()`, `playerManager.release()`, `vodPrewarmer.cancelAll(...)` satırları **aynen kalsın**.)

4c. 2. adımın sonunda şu iki satır var:
```kotlin
                loadLiveCategories()
                loadLiveStreams("all")
```
Bunları şöyle sar (arka planda kullanıcının seçtiği kategori sıfırlanmasın diye):
```kotlin
                if (!background) {
                    loadLiveCategories()
                    loadLiveStreams("all")
                }
```

4d. En sondaki başarı bölümünde şu satır var:
```kotlin
            loadInitialData()
```
Bunu şöyle değiştir (arka planda kategori seçimleri sıfırlanmasın ve son kanal kendiliğinden açılmasın diye):
```kotlin
            if (background) {
                loadAccountContentCounts()
            } else {
                loadInitialData()
            }
```
(Listeler veritabanından canlı okunduğu için ekranlar kendiliğinden güncellenir; ek kod yazma.)

4e. Hemen altındaki `playlistSyncState.value = playlistSyncState.value.copy(isSyncing = false, isCompleted = true, ...)`
bloğundan **sonra**, `kotlinx.coroutines.delay(1500)` satırından **önce** ekle:
```kotlin
            if (background) {
                isBackgroundSyncRun = false
                backgroundSyncStatus.value = BackgroundSyncStatus.DONE
                kotlinx.coroutines.delay(3000)
                if (backgroundSyncStatus.value == BackgroundSyncStatus.DONE) {
                    backgroundSyncStatus.value = BackgroundSyncStatus.HIDDEN
                }
                return@launch
            }
```

## Adım 5 — `failStep` fonksiyonu (tek ekleme)
`failStep` fonksiyonunun **en sonuna** (kapanış `}` öncesine) ekle:
```kotlin
        if (isBackgroundSyncRun) {
            isBackgroundSyncRun = false
            backgroundSyncStatus.value = BackgroundSyncStatus.FAILED
        }
```

## Adım 6 — `cancelPlaylistSync` fonksiyonu (tek ekleme)
`cancelPlaylistSync()` fonksiyonunun **en sonuna** ekle (liste değiştirilince şerit kaybolsun):
```kotlin
        isBackgroundSyncRun = false
        backgroundSyncStatus.value = BackgroundSyncStatus.HIDDEN
```

## Adım 7 — Oynatma önceliği (MainViewModel, 4 fonksiyonun İLK satırı)
Şu dört fonksiyonun gövdesindeki **ilk satırın üstüne** `stopBackgroundSyncForPlayback()` ekle:
- `fun playLiveChannel(channel: LiveStream)`
- `fun startDualView(channel: LiveStream)`
- `fun playVod(vod: VodStream, resume: Boolean = false)`
- `fun playSeriesEpisode(episode: SeriesEpisode, seriesTitle: String, resume: Boolean = true)`

Başka oynatma fonksiyonuna dokunma (`playNextEpisode` zaten `playSeriesEpisode`'u çağırıyor).

## Adım 8 — Şerit bileşeni (YENİ dosya `ui/components/BackgroundSyncBanner.kt`)

```kotlin
package com.tivimax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tivimax.player.ui.BackgroundSyncStatus
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/** G-029: Arka plan liste güncellemesi şeridi. HIDDEN iken hiçbir şey çizmez. */
@Composable
fun BackgroundSyncBanner(
    status: BackgroundSyncStatus,
    percentage: Int,
    isTv: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (status == BackgroundSyncStatus.HIDDEN) return

    val text = when (status) {
        BackgroundSyncStatus.RUNNING -> "Liste güncelleniyor… %$percentage"
        BackgroundSyncStatus.DONE -> "Liste güncellendi"
        BackgroundSyncStatus.FAILED ->
            if (isTv) "Liste güncellenemedi. Ayarlar'dan tekrar dene." else "Liste güncellenemedi. Tekrar dene"
        BackgroundSyncStatus.STOPPED ->
            if (isTv) "Güncelleme durduruldu. Ayarlar'dan tekrar dene." else "Güncelleme durduruldu. Tekrar dene"
        BackgroundSyncStatus.HIDDEN -> ""
    }
    val canRetry = !isTv && (status == BackgroundSyncStatus.FAILED || status == BackgroundSyncStatus.STOPPED)

    Row(
        modifier = modifier
            .background(VegaColors.Card, VegaShapes.Medium)
            .then(if (canRetry) Modifier.clickable { onRetry() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == BackgroundSyncStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VegaColors.Accent
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, fontSize = VegaText.Body, color = VegaColors.TextPrimary)
    }
}
```
> `VegaColors.Card`, `VegaColors.Accent`, `VegaColors.TextPrimary`, `VegaShapes.Medium`, `VegaText.Body` (bu bir yazı BOYUTU, `fontSize =` ile kullanılır) projede **var**.
> Başka isim uydurma. Derleme hatası çıkarsa `ui/design/VegaDesign.kt` dosyasına bakıp **var olan** adı kullan.
> **TV'de şerit odak almaz** (clickable yok) — kumanda gezinmesi bozulmasın diye. Bunu değiştirme.

## Adım 9 — Ana ekrana yerleştir (HomeScreen.kt)

9a. `HomeScreen` fonksiyonunun başında diğer `collectAsState()` satırlarının yanına ekle:
```kotlin
    val backgroundSyncStatus by viewModel.backgroundSyncStatus.collectAsState()
```
(`playlistSyncState` zaten okunuyor; yüzde için `playlistSyncState.overallPercentage` kullan.)

9b. **Telefon/tablet:** `Scaffold(` çağrısına yeni bir parametre ekle (`topBar`, `bottomBar` gibi; içeriğe dokunma):
```kotlin
        snackbarHost = {
            BackgroundSyncBanner(
                status = backgroundSyncStatus,
                percentage = playlistSyncState.overallPercentage,
                isTv = false,
                onRetry = { viewModel.refreshPlaylistContent() },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
```

9c. **TV:** `if (isTvDevice)` dalındaki sağdaki `Box(modifier = Modifier.weight(1f).fillMaxHeight())` bloğunun içinde,
mevcut `if (selectedTab == HomeTab.LIVE_TV) { ... } else { ... }` bloğunun **ALTINA** (aynı Box içinde) ekle:
```kotlin
                if (!tvLiveFullScreen) {
                    BackgroundSyncBanner(
                        status = backgroundSyncStatus,
                        percentage = playlistSyncState.overallPercentage,
                        isTv = true,
                        onRetry = { },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    )
                }
```

9d. HomeScreen.kt içinde **onay diyaloğundaki** "Güncelle" düğmesinde:
```kotlin
// ÖNCE
viewModel.openPlaylistSync()
// SONRA
viewModel.refreshPlaylistContent()
```
(Sadece `showSyncConfirmationDialog = false` satırının altındaki bu çağrı. Dosyada başka `openPlaylistSync` varsa dokunma.)

## Adım 10 — Ayarlar (SettingsScreen.kt, tek satır)
```kotlin
// ÖNCE
onSyncClick = { viewModel.openPlaylistSync() },
// SONRA
onSyncClick = { viewModel.refreshPlaylistContent() },
```

## Adım 11 — Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `feat/arka-plan-guncelleme` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-029 durumunu `🔍 İncelemede (branch: feat/arka-plan-guncelleme)` yap.

## DOKUNMA
`XtreamRepository` (hiç değişmeyecek), silme kuralları, senkronun adımları/sırası, `openPlaylistSync`, `retryFailedSyncStep`,
`PlaylistSyncScreen`, oynatıcı (`TiviPlayerManager`), oynatma listesi geçiş mantığı, güncelleme sistemi (AutoUpdateManager),
imza, CI, sürüm numarası, tasarım dosyaları.

## Test (cihaz varsa — yoksa "test edilemedi" yaz)
- Kurulu listede Ayarlar → İçerikleri güncelle: ekran değişmiyor, altta şerit `Liste güncelleniyor… %…`, bitince `Liste güncellendi`.
- Güncelleme sürerken bir film aç: film açılıyor, şerit `Güncelleme durduruldu. Tekrar dene`. Dokununca yeniden başlıyor.
- Uçak modunda güncelle: `Liste güncellenemedi. Tekrar dene`, mevcut liste aynen duruyor.
- Yeni eklenen (hiç kurulmamış) listede: eski tam ekran kurulum ekranı açılıyor.
- Güncelleme sürerken başka listeye geç: şerit kayboluyor.

---

## Agent raporu

_(Agent doldurur: her adım için ne yaptın, test sonucu, test edemediklerin.)_
