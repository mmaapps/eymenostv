# AGENTS.md — EYMEN OS TV

Bu depo M. Muhammet Atasoy'a (Atasoy Yazılım) aittir. Bu dosya bu depoda çalışan **bütün yapay zekâ kodlayıcıları**
(Codex, ChatGPT, Copilot, Claude ve diğerleri) için bağlayıcıdır. Kurallar pazarlığa açık değildir.

## Tek kaynak

Şu an yapılacak iş **yalnız** EYMEN OS 1.2.0'dır ve tek kaynağı şu iki yerdir:

1. `docs/1.2/SARTNAME.md` — teknik şartname (ne, nerede, nasıl)
2. `docs/1.2/tasarim/*.dc.html` — ekranların birebir görsel referansı (1 px = 1.dp, 1280×720)

Kurulum ve test notları `docs/1.2/KURULUM.md` dosyasındadır.

## Yasaklar

1. Şartnamede yazmayan **hiçbir** özellik, ekran, düğme, metin, renk, simge, animasyon, ayar, izin, kütüphane, sunucu adresi eklenmez.
   "Daha iyi olur" diye bir şey eklenmez, değiştirilmez, yeniden adlandırılmaz, yeniden düzenlenmez.
2. Şartnamenin "değişmeyecekler" listesindeki ve `.github/kurallar/korunan.json` dosyasındaki dosya ve fonksiyonlara **tek karakter bile** dokunulmaz.
   Biçimlendirme, yorum ekleme, import sıralama ve "temizlik" de değişiklik sayılır.
3. Var olan kod yeniden yazılmaz, taşınmaz, bölünmez, sadeleştirilmez. Yeni kod şartnamedeki yeni dosyalara yazılır.
   Var olan dosyalarda yalnız şartnamenin gösterdiği bağlantı noktalarına ekleme yapılır.
4. Şu dosyalar ve klasörler hiçbir koşulda değiştirilmez: `AGENTS.md`, `docs/`, `.github/`, `signing/`, `README.md`, `duyuru.json`.
5. `main` dalına doğrudan push yasaktır. Çalışma dalı `surum-1.2`'dir. İş bitince `main`'e PR açılır. PR'ı yalnız Muhammet Atasoy birleştirir.
6. Material, Material3, Hilt, Room, Retrofit, Glide, Coil, Firebase, analitik ve reklam kütüphanesi yasaktır.
   İzinli tek yeni bağımlılık şartname 2.3'te yazandır.
7. Paket adı, imza anahtarı, `versionName`/`versionCode` şemasının dışı, SharedPreferences adı ve var olan pref anahtarları değiştirilmez.

## Belirsizlik kuralı

Şartnamede açık olmayan bir nokta varsa tahmin edilmez, uydurulmaz, "makul varsayım" yapılmaz.
O madde **yapılmadan bırakılır** ve PR açıklamasına şöyle yazılır:

```
SORU: <şartname bölüm no> — <tek cümlelik soru>
```

## Çalışma sırası

Her adım ayrı commit olur. Commit mesajı adımın numarasıyla başlar (`1.2-3: Ekran yansıtma algılama`).

1. `tv/build.gradle.kts` ve `telefon/build.gradle.kts` sürüm, R8, profileinstaller (bölüm 12, 16)
2. `YerelAdb.kt` (bölüm 3), `IkonOnbellegi.kt` ve uygulama listesi önbelleği (bölüm 12)
3. `Yansitma.kt` (bölüm 5)
4. `Gelismis.kt`: sekme çubuğu, Gelişmiş ızgarası, liste şablonu, PIN paneli (bölüm 4, 6) + `Settings.kt`/`Sources.kt`/`LauncherState.kt` bağlantıları
5. `HizliPanel.kt` (bölüm 7)
6. `CocukKilidi.kt` (bölüm 8)
7. `EkranKoruyucu.kt` + `iller.json` (bölüm 9)
8. `UsbMedya.kt` (bölüm 10)
9. `Sistem.kt` (bölüm 11, 12)
10. `Yedek.kt` + manifest (bölüm 2, 13), `Kumanda.kt` TV (bölüm 14)
11. Telefon `Kumanda.kt`, `Yedekler.kt`, `MainActivity` bağlantıları (bölüm 15)
12. `RELEASE.md` (bölüm 16) ve kabul denetimi (bölüm 18)

## Her değişiklikten sonra

```
python3 .github/kurallar/denetle.py
```

Çıktı `TAMAM` değilse kod düzeltilir. **Kural dosyası, korunan.json ya da iş akışı değiştirilerek denetim geçirilmez.**
Bunu yapmak işin tamamen reddedilmesi sebebidir. Aynı denetim GitHub'da her push ve PR'da çalışır, geçmeyen kod derlenmez ve yayınlanmaz.

## Kod stili

- Kotlin, Compose. Metinler `T()`, kutular `Modifier.panel()`, renkler `C.*` ya da şartname 17.1 paleti.
- Tasarım ızgarası 1280×720 `DesignCanvas`. Ölçüler tasarım dosyalarındaki px değerleriyle birebir dp olarak yazılır.
- Ekrandaki bütün metinler Türkçedir ve şartnamedeki metinle harfi harfine aynıdır.
- Yorumlar mevcut dosyalardaki gibi kısa, İngilizce, tek cümle.
- Ağ ve disk işleri `Dispatchers.IO`'da yapılır. Ana iş parçacığında bekleyen kod yazılmaz.
- Hiçbir hata uygulamayı çökertmez: dış kaynaklı her çağrı `try/catch` içindedir ve kullanıcıya şartnamedeki toast metni gösterilir.
