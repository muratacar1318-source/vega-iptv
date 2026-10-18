# G-024 — Sabit sunucu (http), "Şifreli bağlantı" kaldırıldı, liste güncelleme ekranı düzeltmeleri

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/sunucu-ve-senkron`
- **Commit mesajı:** `fix(sync): sabit http sunucu, şifreli bağlantı ayarı kaldırıldı, güncelleme ekranı geri tuşu ve 429 yeniden deneme, eski içerik temizliği`

## Kullanıcının bildirdikleri

1. Gömülü sunucu **http** olsun; "Şifreli bağlantı" seçeneği kapatılsın ve **ayarlarda hiç olmasın**.
2. "İçerikleri güncelle"de hata alınca (ekranda `HTTP Hatası: 429`) **geri tuşu çalışmıyor**, uygulama arka plana atılıyor.
3. Ekranda "1. Kategoriler… **0 Kategori hazır**" yeşil tik — aslında kategoriler de alınamamış.
4. Sunucu değiştirdikten sonra aramada **eski sunucunun sonuçları** çıkıyor (ör. "matrix"); tekrar aramak düzeltmiyor.

## Kök nedenler (Claude incelemesi)

- `data/config/AppConfig.kt`: `DEFAULT_SERVER_URL = SERVER_URL_HTTPS`; `FALLBACK_DOMAINS` https de içeriyor.
  Ayarlar → Güncelleme ve Bakım'daki "Şifreli bağlantı" anahtarı `serverUrl`'i http/https arasında değiştiriyor.
- `PlaylistSyncScreen` için `BackHandler` yok → sistem geri tuşu Activity'yi kapatıp uygulamayı arka plana atıyor.
- `XtreamRepository.syncAllCategories()` üç kategori isteğinin hatalarını yutuyor (`getOrNull() ?: 0`) ve hep
  **başarı** dönüyor → "0 Kategori hazır".
- **429 = sunucu "çok fazla istek / bağlantı" diyor.** Sağlayıcı tek bağlantıya izin veriyor; güncelleme sırasında
  oynatıcı (önizleme/mini oynatıcı) ya da `VodPrewarmer` bağlantıyı tutuyor olabilir; ayrıca istekler arka arkaya atılıyor.
  Yeniden deneme / bekleme yok; hata metni teknik.
- **Eski içerik kalıyor:** `syncLiveStreams/syncVodStreams/syncSeriesStreams` sadece `insert(REPLACE)` yapıyor,
  sunucuda artık olmayan satırları **hiç silmiyor**. Sunucu değişince eski sunucunun tüm kanal/film/dizileri
  veritabanında kalıyor → arama onları buluyor. (Sunucudan kalkan içerikler de sonsuza kadar listede kalıyor.)

## Yapılacaklar

### 1) Sabit http sunucu, şifreli bağlantı ayarı yok
- `AppConfig`: `DEFAULT_SERVER_URL` → `SERVER_URL_HTTP`; `FALLBACK_DOMAINS` → sadece `SERVER_URL_HTTP`.
  (`SERVER_URL_HTTPS` sabiti kalabilir, kullanılmasın.)
- **Mevcut kullanıcılar için tek seferlik geçiş:** uygulama açılışında (`MainViewModel` açılış akışı, `saveAuth`'tan önce)
  kayıtlı `serverUrl` `AppConfig.SERVER_URL_HTTPS` ile aynıysa `SERVER_URL_HTTP` yap ve `repository.serverUrl`'i güncelle.
  Kullanıcının elle girdiği **başka** sunucu adreslerine dokunma.
- `SettingsScreen.kt`: "Şifreli bağlantı" satırını ve ona ait state/import'ları tamamen kaldır.
  (`UserPreferences` anahtarlarını silme.)

### 2) Güncelleme ekranında geri tuşu
`ui/sync/PlaylistSyncScreen.kt`:
- `BackHandler(enabled = true)`:
  - Senkron **devam ediyorsa**: önce iptal etmeyi sor — diyalog "Güncelleme durdurulsun mu?" · "Mevcut listen korunur."
    · **Devam et** (varsayılan odak) / **Durdur**. Durdur → senkron işini iptal et (`Job.cancel()`), aşağıdaki gibi çık.
  - Senkron **bitti / hata verdi**: çık.
  - **Çıkış hedefi:** daha önce tamamlanmış bir liste varsa (`isPlaylistSyncedFlow == true`) → geri yığınından önceki ekrana
    (`navigateBack()`, yoksa `Screen.Home`). Hiç tamamlanmış liste yoksa (ilk kurulum) → `Screen.Login`.
- Hata durumunda "Kaldığı yerden yeniden dene" butonunun yanına **"Geri dön"** (ikincil buton) ekle, aynı çıkışı yapsın.

### 3) Kategoriler hatası gizlenmesin
`syncAllCategories()`: üç istekten **hepsi** başarısızsa `Result.failure` dön (ilk hatayı taşı). Biri bile başarılıysa
başarı dön. Ekranda başarılı adım metni gerçek sayıyı göstersin.

### 4) 429 / geçici hatalarda bekleyip tekrar dene
- `XtreamRepository`'de ortak bir yardımcı: `executeWithRetry(request)` — HTTP **429, 503, 502** ve bağlantı zaman
  aşımında en fazla **3 deneme**; bekleme: yanıtta `Retry-After` (saniye) varsa o, yoksa 2 sn, 5 sn, 10 sn.
  Tüm `sync*` ve kategori isteklerinde kullan (`authenticate` dahil değil).
- Senkron başlarken (`startPlaylistSync`) bağlantı tutan oynatıcıları durdur: mini oynatıcı/önizleme varsa
  `playerManager.release()` (mevcut `closeMiniPlayer()` benzeri) ve `VodPrewarmer`'ı iptal et. Oynatıcı ekranı açıkken
  zaten güncelleme başlatılamıyor; başka bir yoldan çağrılıyorsa raporla.
- Kullanıcıya görünen hata metinleri (adım altında):
  - 429/503 (denemeler bitti): **"Sunucu şu an yoğun. Birkaç dakika sonra tekrar dene."**
  - 401/403: **"Hesap bilgileri reddedildi. Aboneliğini kontrol et."**
  - Ağ yok / zaman aşımı: **"Bağlantı kurulamadı. İnternetini kontrol et."**
  - Diğer: **"İçerik alınamadı. Tekrar dene."**
  `"HTTP Hatası: …"` metni ekranda görünmesin (Log.w ile logla).
- Yeniden deneme beklerken adımın alt satırında: "Sunucu yoğun, tekrar deneniyor… (2/3)".

### 5) Sunucuda olmayan eski içerik silinsin (arama sorununun kök çözümü)
Her tür için (canlı / film / dizi) senkron **tamamen başarılı** bittiğinde:
- Akış sırasında kaydedilen `streamId`/`seriesId`'leri bir `HashSet<Int>`'te topla.
- Başarıyla bitince DAO'ya `getAllIds()` sorgusu ekle; `stale = tüm id'ler − görülenler`; `stale`'i 900'lük parçalar halinde
  `DELETE ... WHERE streamId IN (:ids)` ile sil (yeni DAO fonksiyonları).
- Adım **hata ile biterse silme yapma** (mevcut liste korunur — kullanıcı boş listeyle kalmasın).
- Kategoriler için aynı mantık: kategori isteği başarılıysa sunucuda olmayan kategorileri sil
  (kategori gizleme/sabitleme bilgisi aynı `categoryId` için korunmalı — REPLACE gizleme alanını sıfırlıyorsa raporla).
- `XtreamRepository`'deki bellek önbellekleri (`liveStreamsCache`, `vodStreamsCache`, `seriesStreamsCache`,
  `*CategoriesCache`) senkron bitince temizlensin.
- Senkron bitince `MainViewModel`'deki evrensel arama sonuçlarını (`universalLiveResults/VodResults/SeriesResults`)
  boşalt ve sorgu doluysa aramayı yeniden çalıştır.

## DOKUNMA
Giriş ekranının tasarımı, oynatıcı, medya oturumu, `AutoUpdateManager`, imza, CI, sürüm numarası.
Kullanıcının kayıtlı favorileri / izleme geçmişi.

## Test
- Ayarlar → Güncelleme ve Bakım'da "Şifreli bağlantı" yok.
- Eski kurulumda (https kayıtlıysa) uygulama açılınca http'ye geçiyor, içerikler açılıyor.
- İçerikleri güncelle → işlem sürerken geri: "Güncelleme durdurulsun mu?" → Durdur → ana ekrana dönüyor, liste duruyor.
- Hata ekranında geri tuşu ve "Geri dön" ana ekrana döndürüyor; uygulama arka plana atılmıyor.
- 429 alınca ekranda "Sunucu yoğun, tekrar deneniyor… (1/3)" görünüyor; denemeler bitince sade Türkçe mesaj.
- Güncelleme bitince sunucuda olmayan kanallar listeden ve aramadan kalkıyor.
- Yerelde sadece `./gradlew compileDebugKotlin`; APK üretme. Test edemediğini "test edilemedi" yaz.

---

## Agent raporu

_(Agent doldurur.)_
