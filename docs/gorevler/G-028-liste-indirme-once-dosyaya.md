# G-028 — Liste indirme: önce dosyaya indir, sonra işle + sonraki güncellemeler arka planda

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `perf/liste-indirme-dosyaya`
- **Commit mesajı:** `perf(sync): listeler önce dosyaya indiriliyor, sonra işleniyor; sonraki güncellemeler arka planda`
- **Ön koşul:** G-027 main'de olmalı (madde 5'teki ölçüm sonuçları bu görevin "önce/sonra" kıyası için gerekli).

## Neden (referans inceleme — Claude)
Kullanıcının beğendiği açık kaynak oynatıcı (Extreme InfiniTV, GPL-3 — **kod kopyalanmaz, sadece yaklaşım**) kanal/film/dizi
listesini şöyle indiriyor: yanıt gövdesini **önce tam hızla geçici bir dosyaya** yazıyor, bağlantı kapanınca dosyayı
ayrıştırıp kaydediyor; ilk kurulumdan sonraki yenilemeler **arka planda** çalışıyor, uygulama kullanılmaya devam ediyor.

Bizde (`XtreamRepository.syncLiveStreams/syncVodStreams/syncSeriesStreams`) JSON **ağdan okunurken** ayrıştırılıp
2000'lik parçalar halinde Room'a yazılıyor. Veritabanı yazımı ağdan yavaş olduğu için sunucu bağlantısı dakikalarca açık
kalıyor: tek bağlantıya izin veren / zaman aşımı düşük sağlayıcıda bağlantı kopuyor, 502/504/429 artıyor, yeniden
denemede her şey baştan iniyor. (Kullanıcının varsayılan sağlayıcısında filmlerin "bazen hiç gelmemesi"nin olası nedeni.)

## Yapılacaklar

### 1) Önce dosyaya, sonra veritabanına
- `executeWithRetry` ile alınan yanıtın gövdesini `context.cacheDir/sync/<tür>-<zaman>.json` dosyasına **akış halinde**
  yaz (`body.source()` → `Okio sink`), bitince bağlantıyı kapat. İndirme ilerlemesi: indirilen MB ("Filmler indiriliyor… 12,4 MB").
- Gövde eksik kaldıysa (`Content-Length` varsa ve tutmuyorsa, ya da okuma hatası) → adım başarısız, dosyayı sil.
- Sonra **dosyadan** mevcut `JsonReader` + parça parça Room ekleme mantığıyla ayrıştır (G-024 güvenlik kuralları —
  dizi değilse hata, `safeStaleIds` — aynen geçerli). İlerleme metni: "Filmler kaydediliyor… 8.000 / 17.500".
- Kategori bazlı yedek moddaki istekler de aynı yöntemle (her kategori kendi dosyası).
- Başarı/başarısızlık fark etmeksizin geçici dosyalar silinsin; uygulama açılışında `cacheDir/sync` kalıntıları temizlensin.
- Yeniden denemede (`executeWithRetry`) sadece indirme tekrarlanır.

### 2) İlk kurulumdan sonra güncellemeler arka planda
- Liste daha önce tamamlanmışsa (`isSynced == true`) "İçerikleri güncelle" **tam ekran kurulum ekranını açmasın**:
  güncelleme arka planda başlasın, ana ekranda küçük bir şerit/rozet göstersin ("Liste güncelleniyor… %40"), kullanıcı
  uygulamayı kullanmaya devam etsin. Bitince kısa bilgi: "Liste güncellendi". Hata olursa: "Liste güncellenemedi. Tekrar dene."
  (dokununca yeniden dener). Mevcut liste bu sırada aynen kullanılabilir kalır.
- İlk kurulum (hiç tamamlanmamış liste) bugünkü tam ekran akışla devam eder.
- Arka plan güncellemesi sürerken bir içerik açılırsa (tek bağlantı sağlayıcısı): güncelleme **duraklatılsın** ya da
  iptal edilip oynatma bitince/yarım saat sonra yeniden denensin — oynatma her zaman önceliklidir. Seçtiğin yolu raporla.
- Oynatma listesi geçişinde (G-025) arka plan güncellemesi iptal edilir.

## DOKUNMA
G-024 güvenlik kuralları (silme koşulları), oynatıcı, oynatma listesi altyapısının mantığı, tasarım bileşenleri,
güncelleme sistemi, imza, CI, sürüm numarası.

## Test
- İlk kurulum: liste iniyor, ilerleme MB → kayıt sayısı olarak görünüyor.
- G-027 madde 5'teki ölçüm tablosunu bu görevden sonra tekrarla: önce/sonra süreler, hata/yeniden deneme sayısı.
- Kurulu listede "İçerikleri güncelle": ana ekran kullanılabilir, şerit görünüyor, bitince "Liste güncellendi".
- Güncelleme sürerken film aç: film hemen açılıyor (güncelleme duruyor/erteleniyor).
- Uçak modunda güncelle: liste bozulmadan duruyor, "Liste güncellenemedi" mesajı.
- Yerelde `compileDebugKotlin`; ölçüm için debug kurulum serbest; release APK üretme. Test edemediğini yaz.

---

## Agent raporu

_(Agent doldurur.)_
