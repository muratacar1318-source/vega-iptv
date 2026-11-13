# Vega IPTV — Eksikler ve geliştirme planı

_Hazırlanma: 13 Kasım 2026 · İncelenen sürüm: v1.5.1 (build 65), `main` @ e0b7f46_

Bu belge kodun güncel hâli taranarak hazırlandı. Onaylanan maddeler `YAPILACAKLAR.md`'ye
G-048'den başlayan numaralarla, tarifleri `docs/gorevler/` altına yazılarak aktarılır.

Öncelik: 🔴 acil / risk · 🟠 önemli · 🟡 iyileştirme · 🟢 yeni özellik

---

## 1. Mevcut durum özeti

| Alan | Durum |
|---|---|
| Kod | ~54 bin satır Kotlin + Compose, tek modül, DI yok |
| Kaynak türü | Yalnızca Xtream Codes (çoklu liste var, M3U/XMLTV yok) |
| Oynatıcı | Media3 1.5.1 + Jellyfin FFmpeg, canlı takılma bekçisi, PiP, altyazı gecikmesi |
| TV | Ayrı TV ekranları (TiviMate düzeni, EPG ızgarası, kumanda) |
| Test | 11 birim testi, arayüz testi yok; CI sadece `compileDebugKotlin` |
| Bekleyen | PR #63 (ana sayfa/buton taşmaları) inceleme bekliyor; G-034, G-035 detaylandırılmadı |
| Açık kararlar | `feat/mobil-tablet-mini-oynatici` branch'i, release anahtarı, uygulama adı/simgesi |

---

## 2. Tespit edilen eksikler

### 2.1 Risk ve güvenlik 🔴

1. **Kayıtlı listeler ve izleme ilerlemesi güncellemede kaybolabilir (doğrulanmalı).**
   `Playlist` (`data/playlist`) ve `WatchProgress` (`data/preferences`) Gson ile JSON olarak
   saklanıyor, ama `proguard-rules.pro` bu paketleri korumuyor ve alanlarda `@SerializedName` yok.
   R8 alan adlarını kısaltırsa (a, b, c…) bir sonraki sürümde ad değişince liste/şifre ve
   "kaldığın yer" bilgisi okunamaz; Kotlin'de boş olmaması gereken alan null gelir → çökme.
   *Yapılacak:* son release derlemesinin `mapping.txt`'sine bak. Adlar kısaltılıyorsa hem eski
   hem yeni anahtarları okuyan geçişle birlikte `@SerializedName` ekle.
2. **Demo girişi canlı sürümde açık.** Giriş ekranındaki "Demo" butonu, TRT 1 / beIN Sports /
   National Geographic adları ve logolarıyla üçüncü taraf test yayınlarını gösteriyor. Marka
   riski ve müşteri için kafa karıştırıcı. *Yapılacak:* yalnızca debug derlemede göster ya da kaldır.
3. **Repo herkese açık.** Sunucu adresi ve destek numaraları XOR+Base64 ile "gizli" ama repo
   açık olduğu için çözmek saniyeler sürer. Repoyu gizlemek de otomatik güncelleyiciyi bozar
   (GitHub API'sine anonim erişiyor). *Yapılacak:* güncellemeler için sadece APK barındıran
   ayrı bir açık repo (veya kendi sunucun) → ana repo gizli. AutoUpdateManager'daki adres
   değişeceği için **kullanıcı onayı şart** (AGENTS.md "dokunma" listesinde).
4. **Release anahtarı tek bilgisayarda.** Tüm sürümler o bilgisayarın `debug.keystore`'u ile
   imzalı. Disk bozulursa mevcut kullanıcılara bir daha güncelleme gönderilemez.
   *Yapılacak (kullanıcı):* `%USERPROFILE%\.android\debug.keystore` dosyasını şifreli olarak
   en az iki yere yedekle.
5. **Güncelleme dosyası doğrulanmıyor.** İnen APK'nın boyutu/özeti kontrol edilmeden kurulum
   açılıyor (Ertelenenler'de duruyor). GitHub artık her release dosyası için SHA-256 özeti
   veriyor; bununla eksik/bozuk indirme yakalanabilir. Onay gerekir (AutoUpdateManager).
6. **Yedekleme kuralları boş.** `allowBackup=true`, `backup_rules.xml` ve
   `data_extraction_rules.xml` şablon hâlinde. Şifreler düz metin DataStore'da; büyük içerik
   veritabanları da yedeğe girmeye çalışıyor. *Yapılacak:* Room veritabanlarını yedekten çıkar,
   tercihleri bilinçli olarak dahil et ya da hariç tut.
7. **Ebeveyn kilidi PIN'i varsayılan `0000`**, düz metin. İlk kullanımda PIN belirletilmeli.

### 2.2 Kod sağlığı 🟠

8. **Dev dosyalar:** `PlayerScreen.kt` 3.902, `MainViewModel.kt` 2.517 (53 ayrı durum akışı),
   `TiviPlayerComponents.kt` 1.945, `SettingsScreen.kt` 1.902 satır. Her yeni görev bu
   dosyalara dokunuyor; çakışma ve gerileme riski yüksek (PR #63 da PlayerScreen'e dokunuyor).
   *Yapılacak:* davranış değiştirmeden, parça parça bölme (Canlı / VOD / Dizi / EPG / Ayarlar
   ViewModel'leri; PlayerScreen → jest katmanı, üst/alt çubuk, paneller).
9. **CI testleri çalıştırmıyor.** Sadece derleme kontrolü var; 11 birim testi hiç koşmuyor,
   lint yok. *Yapılacak:* `testDebugUnitTest` (+ isteğe bağlı `lintDebug`) adımı. Workflow
   dosyası olduğu için onay gerekir.
10. **105 sessiz `catch`** (hata yutuluyor, log yok) ve **çökme raporu yok.** Kullanıcıda
    çökme olursa haberin olmaz. *Yapılacak:* yerel çökme günlüğü (son 5 çökme dosyaya),
    Ayarlar → Geliştirici'de görüntüle/paylaş; kritik `catch`'lere `Log.w`.
11. **Tasarım kuralı ihlalleri:** `ui/` altında tasarım dosyaları dışında 28 sabit
    `Color(0x…)` kaldı.
12. **İzleme ilerlemesi tek JSON** içinde ve sınırsız büyüyor; her kayıtta tamamı okunup
    yazılıyor. *Yapılacak:* Room tablosuna taşı (1. maddenin geçişiyle birlikte yapılabilir),
    eski kayıtları sınırla (ör. son 500).
13. **Bağımlılıklar eski:** Media3 1.5.1 (Aralık 2024). Yeni sürümler HLS/canlı tarafında
    önemli düzeltmeler içeriyor. Jellyfin FFmpeg eklentisi Media3 sürümüne kilitli olduğu için
    ikisi birlikte yükseltilmeli; ayrı bir görev ve TV + telefonda tam test ister.
    Coil 2.7 → 3 isteğe bağlı.
14. **~50 eski uzak branch** birleştirilmiş görevlerden kalmış; temizlenmeli.

### 2.3 Eksik özellikler 🟢

15. **Geri izleme (catch-up / arşiv) yok.** Sunucu `tv_archive` bilgisini gönderiyor, uygulama
    okuyor ama hiç kullanmıyor. Türkiye'deki IPTV kullanıcılarının en çok aradığı özellik:
    EPG'de geçmiş programa tıklayıp izleme (`timeshift` adresi).
16. **EPG sınırlı.** Her görünen kanal için ayrı `get_short_epg` isteği (en çok 5 program).
    Izgara bu yüzden kısa; çok kanallı ekranlarda çok sayıda istek gidiyor.
    *Yapılacak:* `xmltv.php` ile toplu EPG'yi arka planda indirip veritabanına yazmak
    (G-028'deki "önce dosyaya indir" yöntemiyle), 1–3 günlük ızgara.
17. **Program hatırlatıcı** (EPG'den "başlayınca haber ver").
18. **M3U listesi desteği yok** (yalnızca Xtream). White-label ürün için şart değil;
    "her sağlayıcıya açık oynatıcı" hedeflenirse gerekli.
19. **Fikirler listesinden:** film/dizide takılma bekçisi, "VLC'de aç", TV'de kumandayla PIN,
    çoklu profil, komşu afişleri önceden yükleme, TS/HLS otomatik yedekli açılış.
20. **Android TV entegrasyonu:** ana ekran "İzlemeye devam et" kanalı, sesli arama yok.
    TV başlatıcı afişi (banner) uygulama simgesini kullanıyor; 320×180 özel banner gerekli.
21. **Marka:** uygulama adı belli değil, simge hâlâ Android'in hazır simgesi.

---

## 3. Önerilen sıra (fazlar)

### Faz 0 — Senin kararların (agent beklemez)
- [ ] PR #63'ü incele/birleştir veya kapat (yeni görevler PlayerScreen'e dokunacağı için önce bu).
- [ ] `feat/mobil-tablet-mini-oynatici`: al ya da sil.
- [ ] Release anahtarını yedekle (madde 4).
- [ ] Repo gizlenecek mi? (madde 3) — evetse güncelleme kanalı görevi Faz 1'e girer.
- [ ] AutoUpdateManager'a SHA-256 doğrulaması için onay (madde 5).
- [ ] CI'a test adımı için onay (madde 9).

### Faz 1 — Güvenlik ve veri kaybı riski (v1.5.2) — ✅ main'e alındı (PR #64–#67)
| Öneri # | İş | Madde |
|---|---|---|
| G-048 | Liste/izleme ilerlemesi JSON'u R8'e dayanıklı hâle getir (geçişli) | 1 |
| G-049 | Demo girişi sadece debug derlemede | 2 |
| G-050 | Yedekleme kuralları + ilk kullanımda ebeveyn PIN'i belirleme | 6, 7 |
| G-051 | Yerel çökme günlüğü ve geliştirici ekranında paylaşım | 10 |
| (onaylı) | Güncelleme SHA-256 doğrulaması / ayrı güncelleme kanalı | 3, 5 |

### Faz 2 — +18 ve kod sağlığı (görevler yazıldı, 13 Kasım)
| # | İş | Madde |
|---|---|---|
| G-052 | +18 kilidi her yerde (Tümü, arama, ana sayfa, Film/Dizi, TV) — G-050 raporundan | yeni |
| G-053 | CI'a birim testi adımı | 9 |
| G-056 | İzleme ilerlemesi en fazla 500 kayıt (Room'a taşınmıyor: içerik DB'leri yedeğe girmiyor ve yıkıcı geçişli) | 12 |
| G-057 | Kalan sabit renkler + sessiz catch'lere log | 10, 11 |
| G-054 | MainViewModel'i cephe yöntemiyle böl (davranış değişmeden) | 8 |
| G-055 | PlayerScreen'i dosyalara böl (PR #63 kapanınca) | 8 |

Sıra: 1. tur G-052, G-053, G-056 (dosyaları ayrık) → birleştir → 2. tur G-057, G-054 → birleştir → G-055.

### Faz 3 — TV ve oynatma (görevler yazıldı, 13 Kasım)
- G-058: Film/dizide takılma bekçisi (G-044'ün VOD karşılığı) — Faz 2 1. turdan sonra.
- G-034: Detaydan/oynatıcıdan dönünce aynı afiş ve kaydırma (TV odağı dahil) — G-054'ten sonra.
- G-035 (TV ana sayfa yeni düzen): telefondaki yeni ana sayfa (#58, #63) TV'de kullanılmıyor; TV'de Ana Sayfa sekmesi
  aynı `OpusDashboardScreen`'i kumandayla gösteriyor. G-034 ve G-055'ten sonra TV için ayrı değerlendirilecek.
- G-059 (TV ana ekran "İzlemeye devam et" kanalı + 320×180 banner): ertelendi — Google TV yan yüklenen uygulamaların
  satırlarını göstermeyebilir, banner için logo/ad belli değil.

### Faz 4 — Büyük özellikler (v1.7.0)
- G-060: Toplu EPG (xmltv) arka planda indirme + veritabanı + uzun ızgara.
- G-061: Geri izleme (catch-up) — G-060'a bağlı.
- G-062: Program hatırlatıcı — G-060'a bağlı.
- G-063: Media3 + FFmpeg eklentisi yükseltmesi (tek başına, geniş test).

### Faz 5 — İsteğe bağlı
- Çoklu profil, "VLC'de aç", M3U desteği, Chromecast, yeni ad ve simge (ad belli olunca).

---

## 4. Notlar
- Faz 2'deki bölme işleri birbirine ve Faz 3'e çakışır; aynı anda iki agent'a verilmemeli.
- Geri izleme sağlayıcının arşiv desteğine bağlı; önce kendi sunucunda `tv_archive=1` olan
  kanal sayısına bakılmalı.
- G-048 numaraları öneridir; onaydan sonra YAPILACAKLAR.md'ye yazılır.
