# G-012 — "Hesap ve Liste" ekranını detaylandır (gerçek abonelik bilgileri, içerik özeti, güvenli çıkış)

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `ui/hesap-ekrani-detay`
- **Commit mesajı:** `ui(settings): hesap ekranında gerçek abonelik bilgileri, içerik özeti ve çıkış onayı`

## Sorun (kullanıcı ekran görüntüsüyle bildirdi)

Ayarlar → "Hesap ve Liste" (`ui/settings/SettingsAccountSection.kt`) çok yüzeysel:
- Sadece kullanıcı adı, "Abonelik Durumu: Aktif", "Bağlantı Limiti 0 / 1 Cihaz", "Son Senkronizasyon" var.
- **"Abonelik Durumu: Aktif" sabit yazı** — süresi dolmuş/askıya alınmış hesapta da "Aktif" yazar.
- `"${activeCons ?: "0"} / ${maxConnections ?: "1"}"` → bilgi yokken uydurma 0/1 gösterir.
- Bitiş tarihi, kalan gün, üyelik başlangıcı, deneme hesabı bilgisi **hiç gösterilmiyor** (sunucu gönderiyor:
  `XtreamUserInfo.expDate`, `createdAt`, `isTrial`, `status`).
- "Çıkış Yap / Profil Değiştir" **onaysız** çalışıyor ve `logout()` tüm veritabanını siliyor (yanlışlıkla basılırsa
  bütün listeler yeniden indirilir). Uygulamada profil özelliği yok; "Profil Değiştir" yanıltıcı.
- Uygulama açılışında önbellekten kurulan `userInfo` (`MainViewModel.kt` ~848) `status = "Active"` sabit yazıyor.

## Kesin kurallar

- Kullanıcıya görünen hiçbir metinde **sunucu adresi, port, URL, "GitHub"** geçmeyecek. Şifre gösterilmeyecek.
- Bilgi yoksa **uydurma değer yazma**: "—" ya da ilgili satırı gizle.
- Teknik terim yok; sade Türkçe.

## Yapılacaklar

### 1) Profil başlığı
- Avatar: kullanıcı adının ilk harfi (büyük harf, Türkçe uyumlu `uppercase(Locale("tr"))`), dairenin içinde.
- Kullanıcı adı.
- **Durum rozeti** (gerçek veriden; yardımcı fonksiyon `accountStatus(userInfo, nowSec)`):
  | Koşul | Rozet | Renk |
  |---|---|---|
  | `status` = "Active" ve (exp_date yok/0 ya da gelecekte) | Aktif | yeşil |
  | exp_date geçmişte veya `status` = "Expired" | Süresi doldu | kırmızı |
  | `status` = "Banned" / "Disabled" | Askıya alındı | kırmızı |
  | `status` boş/null | Bilinmiyor | gri |
  | diğer | `status` değeri olduğu gibi | gri |
- `isTrial == "1"` ise yanında ikinci rozet: **Deneme** (amber).
- Başlığın sağında küçük **yenile** ikonu (40dp+, contentDescription "Hesap bilgilerini yenile") → madde 6.

### 2) Abonelik bölümü
- **Bitiş tarihi:** `expDate` (unix saniye) → cihaz saat diliminde "12 Mart 2027" (Türkçe ay adı, `Locale("tr")`).
  `expDate` null/boş/"0"/"null" ise "Süresiz".
- **Kalan süre:** "145 gün kaldı". 30 günden az → amber, 7 günden az → kırmızı, geçmişse "Süresi doldu" kırmızı.
  1 günden az kaldıysa "Bugün sona eriyor".
- **İnce ilerleme çubuğu** (4dp): `createdAt` → `expDate` aralığında geçen süre oranı. İkisi de geçerli değilse çizme.
- **Üyelik başlangıcı:** `createdAt` → "3 Ocak 2026". Yoksa satırı gizle.
- Tarih/gün hesaplarını **saf (pure) bir yardımcıda** yaz (ör. `ui/settings/AccountInfoFormatter.kt`) ve
  `app/src/test` altına birim testi ekle: süresiz, geçmiş, 0 gün, 6 gün, 29 gün, 200 gün, geçersiz metin.

### 3) Bağlantı bölümü
- "Aynı anda izleme hakkı: **N cihaz**" (`maxConnections`; yoksa "—").
- "Şu an kullanımda: **X**" (`activeCons`; yoksa satırı gizle).
- `?: "0"` / `?: "1"` uydurma yedeklerini kaldır.

### 4) İçerik özeti
- Üç küçük kutucuk yan yana: **Canlı kanal** · **Film** · **Dizi** — sayılar Room'dan
  (`LiveStreamDao/VodStreamDao/SeriesStreamDao.getTotalStreamCount()`), binlik ayraçlı ("12.345").
- Sayıları ViewModel'de `accountContentCounts: StateFlow<Triple<Int,Int,Int>?>` olarak hazırla; ekran açılınca ve
  senkronizasyon bitince güncellensin (IO dispatcher). Yüklenirken "—".

### 5) Senkronizasyon bölümü
- "Son güncelleme: 17 Ekim 2026, 23:56 · 2 saat önce" (göreli süre: "az önce", "N dk önce", "N saat önce",
  "N gün önce"). Hiç yapılmadıysa "Henüz yapılmadı".
- Mevcut **"Şimdi Senkronize Et"** butonu aynen kalsın (`viewModel.openPlaylistSync()`).

### 6) Hesap bilgilerini yenile
- `MainViewModel`'e `fun refreshAccountInfo()` ekle: kayıtlı kullanıcı adı/şifre/sunucu ile mevcut
  `repository.authenticate(...)`'i çağır; başarılıysa `userInfo.value` ve `preferences.saveAuth(...)` güncelle
  (açılıştaki mevcut kodla aynı desen, ~869-878). Yükleniyor durumu için `accountRefreshing: StateFlow<Boolean>`.
- Başarılı → kısa bilgi "Hesap bilgileri güncellendi"; hata → "Bilgiler alınamadı, bağlantınızı kontrol edin".
  (Uygulamadaki mevcut Snackbar/Toast yöntemini kullan.)
- Ekran ilk açıldığında da bir kez sessizce çağır (en fazla 5 dakikada bir — son yenileme zamanını bellekte tut).

### 7) Önbellekteki durum bilgisi
- `UserPreferences.saveAuth(...)`'a **varsayılanlı** yeni parametreler ekle: `status: String = ""`,
  `isTrial: String = ""`, `createdAt: String = ""` (yeni anahtarlar). Mevcut çağrılar bozulmasın.
- Giriş ve açılış yollarında (`login`, açılış ~869-878, madde 6) bu alanları da kaydet.
- Açılıştaki önbellek `userInfo` kurulumunda (~848) `status = "Active"` sabitini kaldır; kaydedilmiş değerleri kullan.
- `loginDemo()`'ya DOKUNMA.

### 8) Güvenli çıkış
- Buton metni: **"Çıkış Yap"** (kırmızı, aynı yerde).
- Basınca onay diyaloğu:
  - Başlık: "Çıkış yapılsın mı?"
  - Metin: "Kanal, film ve dizi listeleri bu cihazdan silinir. Tekrar giriş yaptığınızda yeniden indirilir."
  - Butonlar: **Vazgeç** (varsayılan odak) · **Çıkış Yap** (kırmızı).
- Onaylanınca mevcut `viewModel.logout()` — `logout()`'un içine DOKUNMA.

### 9) Yerleşim
- Telefon: tek sütun, bölümler ayrı kartlar (mevcut `TiviCard` + `TiviCardBorder` stili), 12dp aralık.
- Geniş ekran/TV (genişlik ≥ 840dp): iki sütun — solda Profil + Abonelik + Bağlantı, sağda İçerik özeti +
  Senkronizasyon + Çıkış.
- Tüm butonlar `dpadFocusable` ile kumandayla gezilebilir; dokunma alanı ≥ 44dp.
- `SettingsAccountSection`'ın imzasını gerektiği kadar değiştir; `SettingsScreen.kt`'deki iki çağrı yerini
  (~187 ve ~359 civarı, `RenderSelectedSection`) buna göre güncelle. Kullanılmayan `autoSyncLive` /
  `onAutoSyncChange` parametreleri bu ekrana ait değil (oynatıcı ayarı) — kaldırabilirsin, ayarın kendisine dokunma.

## DOKUNMA

`logout()` içi, senkronizasyon akışı (`startPlaylistSync`), giriş ekranı, `loginDemo()`, oynatıcı,
güncelleme sistemi (`AutoUpdateManager`), imza, CI. Sunucu adresini hiçbir yerde gösterme.

## Test

- Normal hesap: rozet "Aktif", bitiş tarihi + "N gün kaldı", başlangıç tarihi, ilerleme çubuğu görünüyor.
- Uçak modunda uygulamayı aç → Hesap ekranı: önbellekteki durum gösteriliyor (sabit "Aktif" değil), yenile → hata mesajı.
- İçerik özeti sayıları Canlı TV / Filmler / Diziler'deki toplamlarla uyumlu.
- Çıkış Yap → diyalog → Vazgeç: hiçbir şey silinmiyor. Çıkış Yap: giriş ekranına dönülüyor.
- TV/kumanda: tüm butonlara ulaşılıyor, diyalogda odak "Vazgeç"te.
- Birim testleri geçiyor (yerelde sadece `./gradlew testDebugUnitTest` çalıştırabilirsin, APK üretme).
- Test edemediğin maddeyi "test edilemedi" diye yaz.

---

## Agent raporu

_(Agent doldurur.)_
