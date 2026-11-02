# EYMEN OS TV

Philips 65PUS7009/62 içindeki Vestel 17MB170 anakart (Android 11) için ana ekran ve telefon kurulum uygulaması. Sistem bölümlerine yazmaz; eski ekranı kapatır ve geri alınabilir.

- `tv/`: EYMEN OS TV uygulaması (`com.eymen.os`)
- `telefon/`: EYMEN Kurulum (`com.eymen.kurulum`)
- `duyuru.json`: ana ekranın altındaki Atasoy Yazılım bandının yazıları. Buradan değiştirilen yazı TV'ye uygulama güncellemesi olmadan gider.

## Güncelleme

`main` dalına gönderilen her sürüm kararlı sürüm olarak yayınlanır. Yeni sürüm yayınlamak için `tv/build.gradle.kts` ve `telefon/build.gradle.kts` içindeki `versionName` ve `versionCode` artırılır, `RELEASE.md` yazılır.

TV açılışta, internete bağlanınca ve 6 saatte bir GitHub'a bakar. Yeni sürüm bulunca indirir, ana ekranda imzalı uyarıyı gösterir ve TV'nin kendi hata ayıklama bağlantısıyla kurar. Bu bağlantı açılmazsa Android'in kurulum ekranı çıkar.

Atasoy Yazılım · atasoyyazilim.com
