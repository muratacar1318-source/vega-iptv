# Vega IPTV — Geliştirme Kuralları
## Çalışma düzeni
- Görev dosyasındaki "Agent dağılımı" satırına uy: paralel bölümlere ayrılmış bir görevde sadece sana verilen
  bölümü ve o bölümün dosyalarını değiştir.
- İş listesi YAPILACAKLAR.md'dedir. "Yapılacakları kontrol et ve uygula" denince oradaki
  "Agent için çalışma talimatı" bölümünü harfiyen uygula. Görev tarifleri docs/gorevler/ altındadır.
- Kullanıcı listede olmayan bir işi doğrudan isterse: yine kendi branch'inde yap, main'e alma, sürüm numarasını
  ARTIRMA, release YAPMA; işi bitirince kullanıcıya "Claude'un incelemesine gönderildi" de.
- YAPILACAKLAR.md'ye kendi başına yeni görev EKLEME ve mevcut görevlerin tarifini DEĞİŞTİRME;
  sadece kendi görevinin durumunu ve rapor bölümünü güncelle.
- Her iş için main'den yeni bir branch aç (fix/..., feat/..., chore/...). main'e doğrudan commit ATMA.
- İş bitince derleme/APK üretme (kullanıcı istemedikçe `./gradlew` çalıştırma); derleme kontrolünü GitHub otomatik yapar. Branch'e push et ve PR aç.
- main'e merge ETME; birleştirme kullanıcının onayıyla yapılır.
- Görev dışındaki dosyalara dokunma; refactor, biçimlendirme, import düzenlemesi yapma.
## Asla dokunulmayacaklar
- data/update/AutoUpdateManager.kt içindeki güncelleme mantığı (adresler, sürüm karşılaştırma,
  indirme, kurulum, erteleme). Yalnızca kullanıcıya görünen metinler değiştirilebilir.
- build.gradle.kts içindeki imza (signingConfigs) ayarları, keystore.properties, *.jks dosyaları.
  keystore.properties ve .jks dosyaları ASLA commit edilmez.
- .github/workflows/ altındaki dosyalar (kullanıcı istemedikçe).
## Arayüz kuralları
- Kullanıcıya görünen hiçbir metinde "GitHub", URL veya sunucu adresi geçmez.
- Hata mesajlarında e.localizedMessage / e.message kullanıcıya gösterilmez; genel Türkçe mesaj
  gösterilir, ayrıntı Log.w ile loglanır.
- TV (LocalIsTvDevice) davranışını etkileyen değişikliklerde TV yolu ayrıca kontrol edilir.
## Sürüm (release) kuralları
- Release sadece kullanıcı açıkça "release yap" dediğinde yayınlanır.
- versionCode her release'de 1 artar.
- **Release APK SADECE kullanıcının bilgisayarında** `./gradlew assembleRelease` ile derlenir (imza:
  `%USERPROFILE%\.android\debug.keystore`). GitHub Actions / bulut derlemesi ile release APK YAYINLAMA.
- Yüklemeden ÖNCE `apksigner verify --print-certs` ile imza SHA-256 kontrol edilir; şu olmalı:
  `2E:5C:18:FE:C5:F4:F6:F5:E2:C7:48:A5:D8:5C:2C:D3:5F:58:D5:E4:19:63:F2:D4:3B:51:18:BE:97:76:6F:2D`.
  Farklıysa YÜKLEME, dur ve raporla (farklı imzalı sürüm telefonlara kurulamaz).
- Release açıklaması (GitHub Release body) uygulamadaki güncelleme penceresinde kullanıcıya
  AYNEN gösterilir. Bu yüzden:
  * Son kullanıcının anlayacağı sade Türkçe ile yazılır (ör. "Filmler ekranındaki çökme düzeltildi").
  * Kod/dosya/sınıf adı, teknik terim (LazyRow, PORTRAIT_CAT_NAV_CHIPS, D-Pad odak vb.),
    link, "GitHub" kelimesi ve "Full Changelog" satırı İÇERMEZ.
  * En fazla 6 madde, her madde tek satır.
- Teknik ayrıntılar istenirse docs/release-notes/ altına ayrı dosya olarak yazılır.
- Arayüze dokunan her işte `docs/TASARIM-KURALLARI.md`'ye uy (renk, köşe, buton, odak, yazı, dil). Sabit `Color(0x…)` yazma; `ui/design` tokenlarını kullan.
