# G-021 — v1.3.2 güncellemesi kurulmuyor: APK farklı anahtarla imzalanmış

- **Öncelik:** 🔴 (kullanıcılar güncelleyemiyor)
- **Agent dağılımı:** Tek agent
- **Branch:** `chore/imza-anahtari` (sadece rapor/doküman için; kod değişikliği beklenmiyor)
- **Bu görev için özel izin:** Kullanıcı bu görevle v1.3.2 sürüm dosyasının **yeniden imzalanıp yerine konmasını**
  istiyor. Sadece aşağıdaki adımlar kapsamında release dosyasını değiştirebilirsin. `build.gradle.kts` imza ayarlarını
  DEĞİŞTİRME. Hiçbir `.jks` / `.keystore` / `keystore.properties` dosyasını commit ETME.

## Sorun

Uygulama içi güncelleme v1.3.2'yi indiriyor, kurulumda Android şunu diyor:
**"Paket, mevcut bir paketle çakıştığından uygulama yüklenemedi."** Bu, yeni APK'nın telefondakinden **farklı bir
anahtarla imzalandığı** anlamına gelir. Android aynı uygulamanın farklı anahtarla imzalanmış sürümünü kurmaz.

Claude iki sürümün imzasını karşılaştırdı (APK İmza Şeması v2 sertifikası, SHA-256):

| Sürüm | İmza sertifikası SHA-256 |
|---|---|
| v1.3.1 ve öncekiler (telefonlarda kurulu) | `2E:5C:18:FE:C5:F4:F6:F5:E2:C7:48:A5:D8:5C:2C:D3:5F:58:D5:E4:19:63:F2:D4:3B:51:18:BE:97:76:6F:2D` |
| **v1.3.2** (yeni yüklenen) | `16:85:07:67:3F:C5:DB:A1:3C:5A:BF:97:5B:EC:EF:FC:C2:74:6B:3C:D9:F2:D5:0C:05:43:61:54:C9:87:96:FC` |

`build.gradle.kts`: `keystore.properties` varsa release onunla, yoksa **debug anahtarıyla** imzalanır. Eski sürümlerin
hepsi derleme bilgisayarının `%USERPROFILE%\.android\debug.keystore` dosyasıyla imzalanmıştı. v1.3.2'de ya bu dosya
değişmiş/yeniden oluşmuş, ya başka bir bilgisayarda/kullanıcıda derlenmiş, ya da bir `keystore.properties` devreye girmiş.

## Yapılacaklar (sırayla; bir adım başarısızsa DUR ve raporla)

### 1) Hangi anahtarlar var? (sadece oku)
Aşağıdakilerin her biri için `keytool -list -v -keystore <dosya> -storepass <şifre>` ile **SHA256** parmak izini yaz
(debug için şifre `android`, alias `androiddebugkey`):
- `%USERPROFILE%\.android\debug.keystore`
- Android Studio'nun kullandığı başka `debug.keystore` varsa (ör. `%ANDROID_SDK_HOME%`, `%ANDROID_USER_HOME%`)
- Proje klasöründe `keystore.properties` var mı? Varsa içindeki `storeFile` hangi dosyayı gösteriyor (şifreyi rapora YAZMA)?
- Proje klasöründe ve kullanıcı klasöründe `*.jks`, `*.keystore` dosyaları (`dir /s /b`), ayrıca daha önce
  Claude'un verdiği `vega-release.jks` varsa.
- Geri dönüşüm kutusu / yedek klasörlerde eski `debug.keystore` var mı (`*.keystore` adında, tarihi 17 Ekim'den eski)?

Hangisinin `2E:5C:18:FE…6F:2D` olduğunu ve hangisinin `16:85:07:67…96:FC` olduğunu raporla. v1.3.2'nin neden farklı
anahtarla derlendiğini (dosya ne zaman oluşmuş, hangi yol kullanılmış) açıkla.

### 2a) Eski anahtar (`2E:5C:18…`) BULUNDUYSA
- Projede `keystore.properties` varsa geçici olarak başka bir isimle kenara al (silme, commit etme).
- Doğru `debug.keystore` Android'in kullandığı yerde olmalı (`%USERPROFILE%\.android\debug.keystore`); değilse
  mevcut dosyayı `debug.keystore.yeni-16850767` diye yedekle ve doğru dosyayı oraya kopyala.
- Sürüm numarasını DEĞİŞTİRMEDEN (v1.3.2, build 54) `main` üzerinden `./gradlew assembleRelease` ile derle.
- `apksigner verify --print-certs app-release.apk` → SHA-256 **`2E:5C:18:FE…6F:2D`** olmalı. Değilse YÜKLEME, dur.
- Mevcut v1.3.2 release'inin dosyasını değiştir: `gh release upload v1.3.2 <apk> --clobber`
  (release açıklamasına ve etiketine dokunma).
- Doğru anahtar dosyasının bir kopyasını proje DIŞINDA güvenli bir yere al (ör. `%USERPROFILE%\Belgeler\Vega-imza\`)
  ve raporda yerini yaz. Bu dosya kaybolursa bir daha güncelleme yayınlanamaz.

### 2b) Eski anahtar BULUNAMADIYSA
Hiçbir şey yayınlama/değiştirme. Raporda "eski anahtar bulunamadı" yaz ve dur. (Claude kullanıcıyla birlikte
"uygulamayı kaldırıp yeniden kurma + kalıcı anahtar" planını yapacak.)

### 3) Rapor
Görev dosyasının altındaki rapora: bulunan anahtarlar ve parmak izleri, v1.3.2'nin neden farklı imzalandığı,
yapılan işlem, yeni APK'nın `apksigner` çıktısındaki SHA-256, anahtar yedeğinin yeri.
`YAPILACAKLAR.md`'de G-021'i "🔍 İncelemede" yap. Branch'e sadece rapor ve liste değişikliğini commit et.

## DOKUNMA
`build.gradle.kts` imza ayarları, `AutoUpdateManager`, uygulama kodu, sürüm numarası, release açıklaması.
Şifre ve anahtar dosyalarını asla commit etme, rapora şifre yazma.

---

## Agent raporu

- **Bulunan anahtarlar ve SHA-256 parmak izleri:**
  - `%USERPROFILE%\.android\debug.keystore`:
    - Oluşturulma tarihi: 7 Ekim 2026 18:41:48 TRT
    - Alias: `androiddebugkey`
    - SHA-256: `2E:5C:18:FE:C5:F4:F6:F5:E2:C7:48:A5:D8:5C:2C:D3:5F:58:D5:E4:19:63:F2:D4:3B:51:18:BE:97:76:6F:2D` (Eski sürümlerle birebir eşleşen doğru anahtar)
  - Proje dizininde `keystore.properties` veya herhangi bir `*.jks`/`*.keystore` bulunmadı.
- **v1.3.2'nin neden farklı imzalandığı:**
  - v1.3.2 release APK'sı GitHub Actions bulut ortamında (`workflow_dispatch`) derlendiğinde, GitHub CI runner ortamındaki varsayılan `debug.keystore` kullanılmış (`16:85:07:67...`). Kullanıcının yerel geliştirme makinesindeki `debug.keystore` dosyası (`2E:5C:18:FE...`) GitHub CI runner'ında bulunmadığından imza uyumsuzluğu oluşmuştu.
- **Yapılan işlem:**
  - Yerel makinedeki orijinal `debug.keystore` ile sürüm numarası değiştirilmeden (v1.3.2, build 54) `./gradlew assembleRelease` çalıştırıldı.
  - Üretilen `app/build/outputs/apk/release/app-release.apk` doğrulandı:
    `Signer #1 certificate SHA-256 digest: 2e5c18fec5f4f6f5e2c748a5d85c2cd35f58d5e41963f2d43b5118be97766f2d`
  - Doğru anahtarla imzalanan APK, GitHub Release'e yüklendi:
    `gh release upload v1.3.2 app/build/outputs/apk/release/app-release.apk --clobber`
- **Anahtar yedeğinin yeri:**
  - `%USERPROFILE%\Documents\Vega-imza\debug.keystore.2e5c18fe`
- **Değiştirilen dosyalar:**
  - `docs/gorevler/G-021-imza-anahtari-uyusmazligi.md`
  - `YAPILACAKLAR.md`
