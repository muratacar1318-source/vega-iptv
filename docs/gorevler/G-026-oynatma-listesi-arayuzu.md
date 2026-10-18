# G-026 — Oynatma listeleri arayüzü: listelerim ekranı, ekle/düzenle/sil, hızlı geçiş

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `feat/oynatma-listesi-arayuz`
- **Commit mesajı:** `feat(playlist): listelerim ekranı, liste ekleme/düzenleme ve hızlı geçiş`
- **Ön koşul:** G-025 main'de olmalı. Değilse YAPMA, raporla.
- **Önce oku:** `docs/TASARIM-KURALLARI.md` (VegaButton, VegaSectionCard, VegaChoiceChip…)

## Yapılacaklar

### 1) Ayarlar → yeni ilk kategori: **Listelerim**
(`SettingsSection` en üste: başlık "Listelerim", alt başlık "Oynatma listelerini ekle ve aralarında geç".)
- Her liste bir kart (`VegaSectionCard`): **liste adı** (kalın), kullanıcı adı, "Son güncelleme: …", durum rozeti
  (Aktif / Süresi doldu… — G-012'deki `AccountInfoFormatter`), aktif listede "Şu an kullanılıyor" işareti.
- Kart işlemleri: **Geç** (aktif olmayanlarda, birincil), **Düzenle**, **Sil** (onaylı; "Bu listenin kanalları ve favorileri bu
  cihazdan silinir.").
- Altta **"+ Yeni liste ekle"** (birincil buton).

### 2) Liste ekle / düzenle formu (yan panel ya da tam ekran form; TV'de kumandayla doldurulabilir)
- **Liste adı** (zorunlu, ör. "Evdeki liste")
- **Sunucu:** iki seçenek (`VegaChoiceChip`): **Vega sunucusu** (varsayılan — `AppConfig.DEFAULT_SERVER_URL` kullanılır,
  adres EKRANDA GÖSTERİLMEZ) / **Başka sunucu** (seçilirse "Sunucu adresi" alanı açılır; kullanıcı kendi girdiği adresi görür).
- **Kullanıcı adı**, **Şifre** (göster/gizle ikonlu).
- **Kaydet**: `addPlaylist`/`updatePlaylist` — doğrularken buton "Kontrol ediliyor…"; hata sade Türkçe metin.
- Düzenlemede kimlik/sunucu değiştiyse listenin içeriği yeniden indirilsin (G-025 akışı).

### 3) Hızlı geçiş
- **Telefon ana ekranı üst çubuğu:** uygulama adının yanına aktif liste adını gösteren küçük bir hap
  ("Ana liste ▾"). Tek liste varsa hap yine görünür ama dokununca doğrudan "Listelerim"e gider.
  Birden fazla liste varsa dokununca küçük bir seçim paneli (aktif olan işaretli) + altta "Listeleri yönet".
- **TV:** yan menüye "Listeler" öğesi (aynı seçim paneli).
- Geçiş sırasında tam ekran kısa yükleme katmanı: "Liste değiştiriliyor…". Hata olursa önceki listeye dönülür ve mesaj gösterilir.

### 4) Giriş ekranı ve Hesabım
- Giriş ekranı = ilk listeyi ekleme ekranı: başlık altına "İlk oynatma listeni ekle"; alanlar madde 2 ile aynı
  (Liste adı varsayılan "Ana liste").
- Ayarlar → **Hesabım**: başlığa aktif liste adını ekle ("Hesabım · Ana liste"); "Çıkış yap" butonu → **"Bu listeyi kaldır"**,
  onay metni: "Bu listenin kanalları ve favorileri bu cihazdan silinir. Diğer listelerin etkilenmez."

## Kurallar
Sunucu adresi (Vega sunucusu) hiçbir metinde görünmez; teknik terim yok; "sen" dili; TV'de her öğe kumandayla
erişilebilir; tasarım bileşenleri kullanılır.

## DOKUNMA
G-025 altyapısının mantığı (eksik görürsen raporla), oynatıcı, senkron, `AutoUpdateManager`, imza, CI, sürüm numarası.

## Test
- Yeni liste ekle (Vega sunucusu, başka kullanıcı) → doğrulanıyor → içerik indiriliyor → ana ekran yeni listeyle açılıyor.
- Üst çubuktaki hapla eski listeye geç → kanallar, favoriler, aramalar eski listeninki; "matrix" araması karışmıyor.
- Listeyi düzenle (ad değiştir) → kartta ve hapta yeni ad.
- Aktif olmayan listeyi sil → kart kalkıyor; aktif listeyi kaldır → diğer listeye geçiyor; son liste kaldırılırsa giriş ekranı.
- TV'de liste geçişi kumandayla yapılabiliyor.
- Yerelde sadece `./gradlew compileDebugKotlin`; APK üretme. Test edemediğini "test edilemedi" yaz.

---

## Agent raporu

_(Agent doldurur.)_

---

## 🔁 Düzeltme turu 1 (Claude incelemesi — 18 Ekim)

Genel yapı iyi; Listelerim, form, hızlı geçiş, TV "Listeler", geri dönüş güvenlik ağı doğru. Aşağıdaki 3 madde düzeltilmeden main'e alınmayacak. **Aynı dalda (`feat/oynatma-listesi-arayuz`) devam et**, önce `git pull`.

### 1. Hata mesajında sunucu adresi görünebiliyor (kural ihlali — zorunlu)
`XtreamRepository.authenticate` catch bloğu `"Bağlantı hatası: ${e.localizedMessage}"` döndürüyor. `localizedMessage` örneğin `Unable to resolve host "…"` gibi **sunucu adresini** içerir. Bu metin hem yeni `PlaylistFormDialog` (`errorMessage = err.message`) hem giriş ekranında kullanıcıya gösteriliyor.
- Kaynağında düzelt: catch bloğu kullanıcıya sabit Türkçe metin döndürsün, ayrıntıyı yalnızca `Log.w` ile logla. Öneri:
  - `UnknownHostException` / `ConnectException` / `SocketTimeoutException` / `IOException` → `"Sunucuya ulaşılamadı. İnternetini kontrol et."`
  - diğerleri → `"Bağlanılamadı. Bilgilerini kontrol edip tekrar dene."`
- `"Sunucu hatası: Kod ${resp.code}"` → `"Sunucu şu an yanıt vermiyor. Biraz sonra tekrar dene."` (kod sadece logda).
- Hiçbir kullanıcı metninde `e.message` / `e.localizedMessage` / URL / sunucu adı geçmesin.
- Saf bir `toUserMessage(Throwable)` yardımcısı yazıp birim testi ekle (host adı içeren bir exception verildiğinde dönen metinde host adı geçmediğini doğrula).

### 2. Ayarlar ilk açılışta doğrudan Listelerim alt sayfasına atlıyor
`MainViewModel.targetSettingsSection` başlangıç değeri `"PLAYLISTS"`. Bu yüzden telefonda Ayarlar'a ilk girişte menü yerine doğrudan Listelerim sayfası açılıyor (geri tuşu bekleneni yapmıyor).
- Başlangıç değeri `null` olsun. Yalnızca `openPlaylistsSettings()` çağrılınca `"PLAYLISTS"` olsun.

### 3. Düzenle formunda Vega adresi "Başka sunucu" alanına düşebilir
`PlaylistFormDialog` Vega listesini `playlist.serverUrl != AppConfig.DEFAULT_SERVER_URL` ile ayırıyor. Adres sonda `/` içerirse veya büyük/küçük harf farkı / eski https adresi varsa Vega listesi "Başka sunucu" sayılır ve **Vega adresi kutuda görünür**.
- Saf yardımcı ekle: `isVegaServer(url)` → `url.trim().trimEnd('/')` değerini `SERVER_URL_HTTP` ve `SERVER_URL_HTTPS` ile `ignoreCase` karşılaştırır. Formda ve gereken her yerde bunu kullan. Vega listesinde adres kutusu her zaman boş kalsın.
- Birim testi ekle (sonda `/`, büyük harf, https varyantları).

### Dokunma
Güncelleme sistemi (AutoUpdateManager), imza/sürüm numarası, oynatıcı, senkron mantığı. APK üretme. Main'e birleştirme.

### Bitince
`./gradlew compileDebugKotlin testDebugUnitTest` → commit → aynı dala push. Agent raporuna "Düzeltme turu 1" başlığıyla ne yaptığını ekle.
