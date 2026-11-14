# EYMEN OS 1.2.0 — Kurulum ve test notları

## Kullanıcı için (TV ve telefon)

### TV

1. TV'de EYMEN OS 1.1.1 kuruluysa hiçbir şey yapma. 1.2.0 yayınlanınca TV açılışta, internete bağlanınca ya da 6 saatte bir
   yeni sürümü kendisi bulur. Ana ekranda imzalı "Güncellemeniz var" uyarısı çıkar ve 60 saniye sonra kendiliğinden kurulur.
2. Eski ayarların hepsi kalır: kanal sırası, gizli uygulamalar, ses ve açılış ayarları.
3. İlk açılışta TV uygulamayı arka planda bir kez derler (yaklaşık 2 dakika). Bu sürede TV biraz yavaş olabilir, sonra hızlanır.
4. Yeni özellikler Ayarlar › **Gelişmiş** sekmesindedir: Ayarlar'ı aç, yukarı tuşuna bas, sağa geç.
5. Hızlı panel: ana ekranda kumandanın **Mavi** tuşu.
6. Ekran Yansıtma kartı yalnız TV'de yansıtma özelliği bulunursa görünür.
7. Bazı araçlar (önbellek temizleme, uyku zamanlayıcı, USB izni) TV'nin hata ayıklama iznini kullanır.
   Bu izin 1.1 kurulumunda zaten verildi. TV ekranında izin penceresi çıkarsa "Her zaman izin ver" kutusunu işaretleyip **İzin ver**'e bas.

### Telefon (EYMEN Kurulum)

1. GitHub'daki `v1.2.0` sürümünden `EYMEN-Kurulum.apk` dosyasını indirip kur. Eski sürümün üstüne kurulur.
2. Kumanda ve Yedek: Komut merkezi › **Kumanda** ya da **Yedek**. Telefon ile TV aynı Wi-Fi ağında olmalıdır.
3. Çocuk kilidi PIN'i unutulursa: Komut merkezi › Yedek › **Çocuk kilidi PIN'ini sıfırla**.

### Geri dönüş

- Yeni sürüm açılışta çökerse TV otomatik olarak eski sürüme döner (1.1'deki geri alma sistemi).
- Elle geri dönüş: Telefon › Komut merkezi › "Eski ekranı geri getir, EYMEN OS'u kaldır".

## Geliştirici için (Codex dahil)

### Dal ve yayın

```
git checkout -b surum-1.2
# ... AGENTS.md'deki çalışma sırasıyla, her adım ayrı commit
python3 .github/kurallar/denetle.py     # her commit'ten önce: TAMAM olmalı
git push -u origin surum-1.2
# GitHub'da main'e PR aç. PR şablonu: şartname bölüm 18'deki 8 madde + varsa SORU: satırları
```

- PR açılınca GitHub önce kural denetimini çalıştırır, sonra iki APK'yı derler.
  APK'lar "EYMEN-OS-test-apk" adıyla PR'ın Actions sayfasından indirilir. PR'dan sürüm **yayınlanmaz**.
- PR `main`'e birleşince var olan iş akışı `v1.2.0` sürümünü yayınlar ve TV'ler kendini günceller.

### Yerel derleme

```
gradle :tv:assembleRelease :telefon:assembleRelease
```

Gerekenler JDK 17 ve Gradle 8.9. Yazı tipleri CI'da indirilir. Yerelde yoksa uygulama sistem yazı tipine düşer, bu bir hata değildir.

### TV'de elle test

```
adb connect <TV_IP>:5555
adb install -r tv/build/outputs/apk/release/tv-release.apk
adb shell am start -n com.eymen.os/.HomeActivity
adb logcat | grep -i eymen
```

Test APK'sı yayın APK'sıyla aynı anahtarla imzalanır (`signing/eymen.jks`), bu yüzden üstüne kurulur ve ayarlar kalır.

### Kabul listesi

Şartname bölüm 18. Sekiz maddenin hepsi PR açıklamasında `[x]` ile işaretlenir.
