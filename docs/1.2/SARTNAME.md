# EYMEN OS 1.2.0 — Teknik Şartname

Sahibi: M. Muhammet Atasoy · Atasoy Yazılım
Durum: **ONAYLI, KİLİTLİ.** Bu belge 1.2.0 sürümünün tek kaynağıdır.

Bu belgede yazmayan hiçbir şey yapılmaz. Bir madde belirsizse yorum yapılmaz, tahminle kod yazılmaz.
O madde boş bırakılır ve PR açıklamasına `SORU:` diye yazılır. Kurallar: [`/AGENTS.md`](../../AGENTS.md).

Görsel referans: `docs/1.2/tasarim/*.dc.html`. Bu dosyalar 1280×720 tasarım ızgarasındadır.
Uygulamadaki `DesignCanvas` da 1280×720'dir: **tasarımdaki 1 px = koddaki 1.dp**. Renk, ölçü, yazı boyu,
kalınlık, köşe yarıçapı ve metinler bu dosyalardan birebir alınır.

| Tasarım dosyası | Ekran |
|---|---|
| `Main.dc.html` | Ayarlar · Temel sekmesi (eski ekran + sekme çubuğu + 1 durum satırı) |
| `Gelismis.dc.html` | Ayarlar · Gelişmiş sekmesi |
| `Yansitma.dc.html` | Ekran Yansıtma |
| `HizliPanel.dc.html` | Hızlı panel (ana ekranın üstünde) |
| `CocukKilidi.dc.html` | Çocuk kilidi + PIN paneli + **liste şablonu** |
| `Sistem.dc.html` | Sistem araçları ve hız |
| `EkranKoruyucu.dc.html` | Ekran koruyucu |
| `Telefon.dc.html` | EYMEN Kurulum · Kumanda ekranı (390×844 dp, Komut merkezinden açılır) |
| `Plan.dc.html` | Özet plan (kod değil, bilgi) |

---

## 0. Değişmeyecekler (dokunmak yasak)

1. Ana ekranın düzeni, yerleşimi ve davranışı: canlı pencere, kanal satırı, uygulama satırı, Atasoy bandı, üst çubuk.
2. Ayarlar'daki mevcut 9 kart: aynı sıra, aynı renk, aynı yazı, aynı işlev. (Sadece üstte sekme çubuğu eklenir.)
3. Güncelleme sistemi: denetleme, indirme, kendi kendini kurma, geri alma, imzalı uyarı. `Updates.kt` tümüyle korunur.
4. Açılış koruması, varsayılan ana ekran mantığı, `BootReceiver`, `EnforcerService`.
5. Renk tokenları (`Theme.kt` / `object C`), yazı tipleri (Sora, Manrope, Great Vibes), `T()`, `panel()`, `DesignCanvas`.
6. Paket adları `com.eymen.os` ve `com.eymen.kurulum`, imza anahtarı (`signing/`), SharedPreferences adı `baslatici`,
   var olan bütün pref anahtarları ve anlamları.
7. `Preview.kt`, `App.kt`, `Tv.kt` (telefon) tümüyle korunur.

Korunan dosya ve fonksiyonların tam listesi `.github/kurallar/korunan.json` içindedir. Denetim betiği bunları
parmak iziyle karşılaştırır. Tek karakter değişse bile derleme durur.

---

## 1. Dosya yapısı

Yalnız aşağıdaki **yeni** dosyalar oluşturulabilir. Başka yeni dosya yasaktır.

```
tv/proguard-rules.pro
tv/src/main/baseline-prof.txt
tv/src/main/assets/iller.json
tv/src/main/java/com/eymen/os/YerelAdb.kt        yerel ADB yardımcısı (bölüm 3)
tv/src/main/java/com/eymen/os/Gelismis.kt        sekme çubuğu, Gelişmiş ızgarası, liste şablonu, PIN paneli (bölüm 4, 6)
tv/src/main/java/com/eymen/os/Yansitma.kt        algılama + ekran (bölüm 5)
tv/src/main/java/com/eymen/os/HizliPanel.kt      hızlı panel + uyku zamanlayıcı (bölüm 7)
tv/src/main/java/com/eymen/os/CocukKilidi.kt     kilit mantığı + ekran (bölüm 8)
tv/src/main/java/com/eymen/os/EkranKoruyucu.kt   koruyucu + hava durumu (bölüm 9)
tv/src/main/java/com/eymen/os/UsbMedya.kt        USB medya (bölüm 10)
tv/src/main/java/com/eymen/os/Sistem.kt          sistem araçları + performans + açılış ölçümü (bölüm 11, 12)
tv/src/main/java/com/eymen/os/Yedek.kt           yedek + YedekReceiver (bölüm 13)
tv/src/main/java/com/eymen/os/IkonOnbellegi.kt   simge/logo disk önbelleği (bölüm 12)
tv/src/main/java/com/eymen/os/Kumanda.kt         TV tarafı kumanda bilgi ekranı (bölüm 14)
telefon/src/main/java/com/eymen/kurulum/Kumanda.kt   telefon kumanda sekmesi (bölüm 15)
telefon/src/main/java/com/eymen/kurulum/Yedekler.kt  telefon yedek sekmesi (bölüm 15)
```

Değiştirilmesine **izin verilen** var olan dosyalar (yalnız bu şartnamenin söylediği yerlerde):

```
tv/build.gradle.kts                 sürüm, R8, profileinstaller (bölüm 12, 16)
telefon/build.gradle.kts            yalnız sürüm (bölüm 16)
tv/src/main/AndroidManifest.xml     izinler + YedekReceiver (bölüm 2)
tv/src/main/java/com/eymen/os/Sources.kt        yalnız `enum class Screen` satırı (bölüm 4.4)
tv/src/main/java/com/eymen/os/LauncherState.kt  bağlantı noktaları (bölüm 4.5)
tv/src/main/java/com/eymen/os/Settings.kt       HubScreen + StatusPanel + HubTile (bölüm 4.2, 4.3, 12.8)
tv/src/main/java/com/eymen/os/Ui.kt             LauncherRoot, SettingsPill, AppTile, HomeScreen (bölüm 4.6)
tv/src/main/java/com/eymen/os/HomeActivity.kt   onCreate, onWindowFocusChanged, onDestroy (bölüm 4.7)
telefon/src/main/java/com/eymen/kurulum/MainActivity.kt   komutlar() içine 2 düğme + 3 yeni fonksiyon (bölüm 15)
RELEASE.md                          bölüm 16'daki metin
```

Kod stili: mevcut dosyalar gibi. Compose `BasicText` üstünden `T()`, kutular `Modifier.panel()`, renkler `C.*` ya da
bölüm 17'deki paletten `Color(0xFF......)`. Material bileşeni, yeni tema, yeni yazı tipi yasak.
Fonksiyon adları İngilizce ya da Türkçe olabilir. Ekrandaki bütün metinler Türkçe ve bu belgedekiyle **harfi harfine** aynıdır.
Yorumlar mevcut dosyalardaki gibi kısa İngilizce cümleler olur.

---

## 2. İzinler, bağımlılıklar, ağ adresleri

### 2.1 Manifest izinleri — son liste (fazlası yasak)

Var olanlar (aynen): `READ_TV_LISTINGS`, `com.android.providers.tv.permission.READ_EPG_DATA`, `INTERNET`,
`ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`.

Eklenecekler (yalnız bunlar):
- `android.permission.PACKAGE_USAGE_STATS` (`tools:ignore="ProtectedPermissions"`) — depolama dağılımı
- `android.permission.KILL_BACKGROUND_PROCESSES` — arka plan uygulamalarını kapat
- `android.permission.READ_EXTERNAL_STORAGE` — USB medya
- `android.permission.ACCESS_WIFI_STATE` — TV IP adresi

### 2.2 Manifest bileşeni — eklenecek tek bileşen

```xml
<receiver
    android:name=".YedekReceiver"
    android:exported="true"
    android:permission="android.permission.DUMP">
    <intent-filter>
        <action android:name="com.eymen.os.YEDEK_AL" />
        <action android:name="com.eymen.os.YEDEK_YUKLE" />
        <action android:name="com.eymen.os.PIN_SIFIRLA" />
    </intent-filter>
</receiver>
```

`DUMP` iznini yalnız ADB kabuğu taşır. Böylece bu yayınları sadece kurulum telefonu gönderebilir, başka uygulama gönderemez.

### 2.3 Bağımlılıklar

`tv/build.gradle.kts` içine yalnız şu satır eklenir:
`implementation("androidx.profileinstaller:profileinstaller:1.3.1")`
Başka kütüphane eklemek, sürüm değiştirmek ya da kaldırmak yasak. `telefon` modülüne kütüphane eklenmez.

### 2.4 İzinli internet adresleri

Kodda yalnız şu sunucular kullanılabilir:
`github.com`, `api.github.com`, `raw.githubusercontent.com` (var olan güncelleme), `atasoyyazilim.com`,
`speed.cloudflare.com` (hız testi), `api.open-meteo.com` (hava durumu). Analitik, reklam ve takip kodu yasak.

---

## 3. YerelAdb.kt

`Updates.kt` içindeki bağlantıyla **aynı anahtar dosyaları** (`filesDir/adbkey`, `filesDir/adbkey.pub`) ve aynı port (`5555`) kullanılır.
`Updates.kt` değiştirilmez, kod oradan kopyalanmaz, çağrılmaz.

```kotlin
object YerelAdb {
    /** Runs one shell command on the TV's own adb; null when adb is closed or refused. */
    suspend fun shell(ctx: Context, cmd: String, timeoutMs: Int = 15000): String?
}
```

- `Dispatchers.IO` üzerinde çalışır. `Dadb.create("127.0.0.1", 5555, AdbKeyPair.read(priv, pub), 5000, timeoutMs, true)`.
  Anahtar dosyası yoksa `null` döner, anahtar **üretmez**.
- Her çağrıda bağlanır, komutu çalıştırır, kapatır. Hata olursa `null` döner, istisna dışarı çıkmaz.
- `LauncherState.updates.busy()` `true` iken çağrılmaz. Çağıran taraf bunu denetler ve "Güncelleme sürüyor, biraz sonra dene" uyarısını gösterir.
- Bu nesneyle çalıştırılabilecek komutlar **yalnız** şunlardır:
  `input keyevent 223`, `pm trim-caches 999G`, `appops set com.eymen.os GET_USAGE_STATS allow`,
  `pm grant com.eymen.os android.permission.READ_EXTERNAL_STORAGE`, `cmd package compile -m speed-profile -f com.eymen.os`.

---

## 4. Ayarlar: sekmeler ve bağlantı noktaları

### 4.1 Sekme çubuğu (Temel ve Gelişmiş ekranlarında aynı)

`Main.dc.html` ve `Gelismis.dc.html` üst satırı. "Ayarlar" başlığının 18 dp sağında bir kapsül grup:
- Dış: arka plan `#0F1830`, kenar 1 dp `#26324F`, tam yuvarlak, iç boşluk 4 dp, sekmeler arası 6 dp.
- Etkin sekme: arka plan `#E8C27A`, yazı `#1E1505`, 14 sp, 800, yatay 18 dp / dikey 7 dp boşluk.
- Edilgen sekme: arka plan yok, yazı `#F2F4F8`.
- Temel ekranında Gelişmiş sekmesinin yanında `YENİ` rozeti: arka plan `#4C8DFF`, yazı `#05080F`, 10 sp, 800, harf aralığı 0.5,
  yatay 7 dp / dikey 2 dp. Rozet, kullanıcı Gelişmiş sekmesini ilk açtığında kalkar (pref `gelismisGoruldu=true`).
- Odak sekmedeyken (bkz. 4.5) odaktaki sekme 1.05 büyür, 14 dp gölge alır. Etkin sekme rengi değişmez.

Alt yardım satırının sağındaki ipuçları: `▲ sekmeler` · `OK aç` · `Geri: ana ekran`.

### 4.2 Temel sekmesi (`Settings.kt › HubScreen`)

Var olan HubScreen aynen kalır. Yalnız şu iki şey eklenir: 4.1'deki sekme çubuğu ve sağ alttaki ipuçlarına `▲ sekmeler`.
9 kart, renkleri, değerleri, yardım metinleri ve `hubAction` değişmez.

### 4.3 Durum paneli (`Settings.kt › StatusPanel`)

`rows` listesinde `Kanal` ile `TV` arasına tek satır eklenir:
- Etiket: `Ekran yansıtma`, solunda 16 dp yansıtma simgesi (bölüm 17.2 `I_CAST`), çizgi rengi `#6FA8FF`.
- Değer: algılama hazırsa `Hazır` (`#7BE08A`), değilse `Bu TV'de yok` (`C.Mut`).
- Satırın arka planı `#13224A`, köşe 8 dp, yatayda 8 dp taşar (tasarımdaki gibi), üst çizgisi yok.
Başka satır eklenmez, var olan satırlar değişmez.

### 4.4 Ekranlar (`Sources.kt`)

`enum class Screen` satırı tam olarak şu olur:

```kotlin
enum class Screen { HOME, HUB, SETUP, UPDATE, ABOUT, GELISMIS, YANSITMA, KILIT, SISTEM, KORUYUCU_AYAR, USB, YEDEK, KUMANDA }
```

### 4.5 LauncherState bağlantı noktaları

Eklenecek durumlar: `gelIndex: Int`, `sekmeOdak: Boolean`, `yansitma: YansitmaSonuc?`, `panelAcik: Boolean`, `panelIndex: Int`,
`koruyucuAcik: Boolean`, `kilitOturumu: Boolean`, `pinIstek: PinIstek?`, `usbVar: Boolean`, ayrıca her ekranın kendi odak indeksi.

Tuş akışı (`onKey`): sıra **tam olarak** şöyledir. Var olan dallar aynı sırada kalır, yenileri araya şöyle girer:
1. `noticeVisible` (değişmez)
2. **yeni:** `pinIstek != null` → PIN paneli tuşları (bölüm 6.2), `return true`
3. **yeni:** `koruyucuAcik` → herhangi bir tuş koruyucuyu kapatır, tuş tüketilir (bölüm 9)
4. `qrOpen` (değişmez)
5. **yeni:** `panelAcik` → hızlı panel tuşları (bölüm 7)
6. `when (screen)` içine yeni ekranların dalları eklenir. `HUB` dalı sekme mantığını alır (aşağıda).
7. kalan ana ekran dalları (değişmez) + **yeni:** `KEYCODE_PROG_BLUE` → hızlı paneli aç (yalnız `Screen.HOME`, hareket modu yokken).

`hubKey` değişikliği (Temel): `DPAD_UP` ile `hubIndex < 3` iken `sekmeOdak = true`. Sekmedeyken `LEFT/RIGHT` Temel ↔ Gelişmiş arasında
geçer ve ekran hemen değişir. `DOWN` ızgaraya döner (index 0). `OK` hiçbir şey yapmaz. `BACK/MENU/SETTINGS` → `closeHub()` (eskisi gibi).
Gelişmiş sekmesindeki ızgara gezintisi Temel'in aynısıdır (3 sütun). Görünen kart sayısı 8 ise 9. hücre boştur, oraya odak gitmez.

Kart eylemleri ve bağlantılar:
- `launchApp(app)`: kilit açık ve uygulama kilitliyse önce PIN (bölüm 8). Paket `eymen:yansitma` ise `Yansitma.ac()`.
- `openEymen(ch, …)`: kanal kilitliyse önce PIN.
- `reloadChannels` / `zap` / `closeHub`: `current` kilitli kanalsa `preview.play` çağrılmaz.
- `requestGraphics`: önce `IkonOnbellegi`. `eymen:yansitma` için çizilmiş simge (bölüm 5.5).
- `start()`: algılama (5.1), uygulama listesi önbelleği (12.4), açılış derlemesi (12.5), koruyucu sayacı (9), kilit sayacı (8.4).

Şu fonksiyonlara dokunmak yasaktır (denetim kontrol eder): `checkUpdates`, `onNetwork`, `offerNotice`, `hideNotice`, `startUpdate`,
`later`, `noticeKey`, `onRequest`, `openUpdate`, `closeUpdate`, `updateAction`, `updateKey`, `isDefaultHome`, `shouldReassert`,
`armWakeGuard`, `launch`, `openInput`, `toast`, `time`, `dateLabel`, `homeIntent`, `isOk`, `openList`, `saveOrder`,
`moveApp`, `toggleHide`, `onDestroy`. `onStop` içine yalnız `panelAcik = false` ve `koruyucuAcik = false` eklenebilir.

### 4.6 Ui.kt bağlantı noktaları

- `LauncherRoot` › `when (s.screen)`: yeni ekranlar eklenir. Katman sırası (alttan üste): ekran, hızlı panel, `QrOverlay`,
  ekran koruyucu, PIN paneli, `UpdateNotice`, toast.
- `SettingsPill`: "Hafif animasyonlar" açıksa dişli dönmez, nabız noktası sabit durur (alpha 1), odak büyümesi 1.06 yerine 1.0 olur.
  Başka değişiklik yok.
- `AppTile`: kilitli uygulamanın kartının sağ üstünde 22 dp daire (`#05080F` %80), içinde 12 dp kilit simgesi (`I_LOCK`, `#F2F4F8`).
- `HomeScreen`: `current` kilitli kanalsa canlı pencerenin ortasında var olan durum kutusu stiliyle (`Color(0xCC0A1224)`, köşe 12)
  `Kilitli kanal · OK ile PIN gir` yazar. Başka değişiklik yok.
- `TopBar`, `AdStrip`, `HeroButton`, `ChannelsRow`, `ChannelTile`, `AppsRow`, `InputTile`, `panel()` değişmez.

### 4.7 HomeActivity.kt

- `onCreate`: USB alıcısı kaydı (`ACTION_MEDIA_MOUNTED`, `ACTION_MEDIA_UNMOUNTED`, `ACTION_MEDIA_EJECT`, data scheme `file`).
- `onWindowFocusChanged`: süreçteki ilk `hasFocus == true` anında açılış süresi ölçülür (bölüm 12.6).
- `onDestroy`: USB alıcısının kaydı silinir.
- `dispatchKeyEvent`, `isSystemKey`, `bringToFront`, `hideSystemUi`, `onNewIntent`, `handleRequest` değişmez.

---

## 5. Ekran Yansıtma (`Yansitma.kt`)

### 5.1 Algılama

```kotlin
data class YansitmaSonuc(
    val wifiDirect: Boolean,      // pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
    val uygulama: String?,        // found mirroring app package, or null
    val sistemMenusu: String?,    // first resolvable settings action, or null
    val googleCast: Boolean,      // com.google.android.apps.mediashell installed
) { val hazir get() = uygulama != null || sistemMenusu != null || googleCast }
```

- **Uygulama taraması:** `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER`, sonra `CATEGORY_LAUNCHER` ile bütün etkinlikler alınır.
  Paket adı ya da etiket (`tr` yerelinde küçük harfe çevrilmiş) şu kelimelerden birini içeriyorsa aday sayılır:
  `miracast`, `wfd`, `wifidisplay`, `screenmirror`, `mirroring`, `ekran paylaş`, `ekran yansıt`, `yansıtma`,
  `smart view`, `screen share`, `screenshare`.
  Hariç tutulanlar: `com.eymen.*`, `com.google.android.apps.mediashell`, `com.google.android.youtube.tv`, etiketi `podcast` içerenler.
  Birden çok aday varsa etiketi alfabetik olarak ilk olan seçilir.
- **Sistem menüsü:** sırayla `"android.settings.CAST_SETTINGS"`, `"android.settings.WIFI_DISPLAY_SETTINGS"` denenir.
  `resolveActivity` sonucu boş olmayan ilk eylem kullanılır.
- Ne zaman çalışır: `start()` içinde `Dispatchers.IO` üzerinde, Gelişmiş sekmesi her açıldığında ve `Yeniden tara` ile.
  Sonuç `yansitma` durumuna yazılır, ayrıca pref `yansitmaSonuc` (JSON) içinde saklanır. Açılışta önce bu kayıt okunur.
- `hazir == false` ise: Gelişmiş'te Ekran Yansıtma kartı **gösterilmez**. Kalan 8 kart sola kayar, son hücre boş kalır.
  Durum panelinde `Bu TV'de yok` yazar. Hızlı paneldeki yansıtma karesinin değeri `Bu TV'de yok` olur, OK'e basınca bir şey açılmaz.

### 5.2 Açma (`Yansitma.ac()`)

Sıra: (1) bulunan uygulamanın açılış intent'i, (2) bulunan sistem menüsü intent'i.
Bunlar yoksa ve yalnız `googleCast` varsa şu toast gösterilir (7 sn):
`Bu TV Google Cast ile yansıtılır · Telefonda Google Home › Ekranımı yayınla`.
Açılış `launch(intent)` üzerinden yapılır. Böylece önizleme durur, dönüşte kaldığı yerden sürer.

### 5.3 Ekran (`Screen.YANSITMA`) — `Yansitma.dc.html` birebir

- Başlık `Ekran Yansıtma`, yanında `Ayarlar › Gelişmiş` (13 sp, `#A3ACBF`).
- Durum etiketi: hazırsa `HAZIR · BAĞLANTI BEKLENİYOR` (`#7BE08A`).
- Başlık: `Telefonunu TV'ye yansıt`. Altında `Telefonda bu adı seç:`, onun altında TV adı (`#E8C27A`, Sora 22/600).
- TV adı: `Settings.Global.getString(cr, "device_name")`, boşsa `Build.MODEL`. Tasarımdaki "EYMEN TV · Salon" yalnız örnektir.
- Düğmeler (soldan sağa odak sırası): `Yansıtmayı başlat` → `Yansitma.ac()`;
  `Ana ekrana kısayol` / kısayol varsa `Kısayolu kaldır` → pref `yansitmaKisayol`; `Yeniden tara` → 5.1.
- `Adı değiştir` kapsülü: düğme satırından `UP` ile odaklanır. Açtığı ekran sırası: `"android.settings.DEVICE_NAME"`,
  `Settings.ACTION_DEVICE_INFO_SETTINGS`, `Settings.ACTION_SETTINGS`.
- Alttaki iki bilgi kutusu metni tasarımdaki gibidir: `AÇILINCA / Canlı önizleme durur, ses karışmaz`, `BİTİNCE / Geri tuşuyla EYMEN OS'a dönülür`.
- Sağ üst `Algılama sonucu`: 4 satır sabit sırada: `Wi-Fi Direct donanımı`, `TV'nin yansıtma uygulaması`, `Sistem yansıtma menüsü`,
  `Google Cast alıcısı`. Değerler: `Var`/`Yok` (ilk ve üçüncü satır), `Bulundu`/`Yok` (ikinci satır), `Var`/`Yok` (dördüncü satır).
  Varsa yeşil onay dairesi (`#7BE08A`), yoksa gri çizgi dairesi (`#3A4459`).
- Sağ alt `Telefondan bağlan`: iki kapsül `Samsung` / `Diğer Android`. Düğme satırından `RIGHT` ile odaklanır, `LEFT/RIGHT` ile değişir.
  - Samsung: `1 Telefonla TV aynı Wi-Fi ağında olsun.` · `2 Ekranın üstünden aşağı kaydır, Smart View düğmesine dokun.` ·
    `3 Listeden <TV adı>'nu seç, TV'de çıkan onayı OK ile kabul et.`
  - Diğer Android: `1 Telefonla TV aynı Wi-Fi ağında olsun.` ·
    `2 Hızlı ayarlarda Yansıtma, Ekran yansıtma ya da Cast düğmesine dokun.` · aynı 3. adım.
  - Yalnız `googleCast` varsa 2. adım her iki kapsülde de: `Google Home uygulamasında Ekranımı yayınla'ya dokun.`
- Alt satır: `TV'nin kendi yansıtma ekranını açar. Bağlantı bitince EYMEN OS'a geri dönülür.` · `OK seç` · `Geri: Gelişmiş`.
- `BACK` → `Screen.GELISMIS`, odak Ekran Yansıtma kartında.

### 5.4 Gelişmiş kartı

Bölüm 6.1'deki tablonun 1. satırı.

### 5.5 Ana ekran kısayolu

`yansitmaKisayol == true` iken `visibleApps()` listesinin **başına** sanal `AppEntry("eymen:yansitma", "Ekran Yansıtma")` eklenir.
Bu giriş kaydedilmez, gizlenmez, taşınmaz. Sarı ve kırmızı tuş bu girişte çalışmaz.
Simgesi kodla çizilir: 160×160 bitmap, arka plan `#6FA8FF`, köşe 36 px, ortada `I_CAST` yolu, çizgi `#0A1224`, kalınlık 10 px, 100 px kutuya ölçeklenir.

---

## 6. Gelişmiş sekmesi, liste şablonu, PIN paneli (`Gelismis.kt`)

### 6.1 Gelişmiş ızgarası — `Gelismis.dc.html` birebir

Düzen Temel'in aynısıdır (sol panel 333 dp + 3×3 kart ızgarası, aralık 14 dp). Kart görünümü `HubTile` ile aynıdır.
Yalnız 1. kartta sağ üstte `OTOMATİK ALGILANDI` rozeti vardır: arka plan `#1B7A33`, yazı `#FFFFFF`, 10 sp, 800, harf aralığı 0.6, kapsül.

| # | Başlık | Değer | Renk | Simge | Yardım satırı | OK |
|---|---|---|---|---|---|---|
| 1 | Ekran Yansıtma | `Hazır · telefondan bağlan` | `#6FA8FF` | I_CAST | `TV'nin yansıtma özelliğini açar. Telefonun ekranını TV'ye yansıtırsın.` | `Screen.YANSITMA` |
| 2 | Hızlı panel | `Mavi tuşla aç` | `#F5B85C` | I_ZAP | `Ses, uyku zamanlayıcı, yansıtma ve Wi-Fi tek panelde. Ana ekranda Mavi tuşla açılır.` | `closeHub()` + panel açılır |
| 3 | Çocuk kilidi | `Açık` / `Kapalı` | `#FF9A9A` | I_LOCK | `PIN ile uygulama ve kanal kilitle, günlük süre sınırı koy.` | PIN (bölüm 8.1) → `Screen.KILIT` |
| 4 | Ekran koruyucu | `N dakika sonra` / `Kapalı` | `#A9B2FF` | I_MOON | `Ana ekran boş kalınca saat ve hava durumu gösterir.` | `Screen.KORUYUCU_AYAR` |
| 5 | USB medya | `Takınca kendisi açılır` / `<disk adı> takılı` | `#8FD3A8` | I_FOLDER_PLAY | `USB bellekteki video, fotoğraf ve müzikleri açar.` | `Screen.USB` |
| 6 | Sistem araçları | `Temizle · hız testi` | `#7FD8F0` | I_WRENCH | `Önbelleği temizle, internet hızını ölç, depolamayı gör.` | `Screen.SISTEM` (odak: depolama) |
| 7 | Hız ve performans | `Hızlı mod açık` / `Hızlı mod kapalı` | `#C6E27A` | I_GAUGE | `Açılışı hızlandıran ayarlar ve son açılış süreleri.` | `Screen.SISTEM` (odak: 1. performans satırı) |
| 8 | Yedekle ve geri yükle | `Telefona yedekle` / `Son yedek: d MMMM HH:mm` | `#9CC3FF` | I_CLOUD_UP | `Kanal sırası, gizli uygulamalar ve ayarları telefona yedekler.` | `Screen.YEDEK` |
| 9 | Telefondan kumanda | `TV adresini göster` | `#E6A8FF` | I_PHONE | `Telefondaki EYMEN Kurulum ile TV'yi kumanda gibi kullan.` | `Screen.KUMANDA` |

"Hızlı mod" = `hizliAcilis && gecOnizleme` (bölüm 12).

Sol panel (Gelişmiş): halka rengi `#6FA8FF`, simge çip, üst yazı `BU TV'DE BULUNANLAR`, başlık `Açılışta otomatik tarandı`.
Satırlar: `Ekran yansıtma` (`Hazır`/`Bu TV'de yok`), `USB bellek` (`Takılı değil`/`Takılı`), `Çocuk kilidi` (`Açık`/`Kapalı`),
`Hızlı mod` (`Açık`/`Kapalı`), `Son yedek` (`Yok`/`d MMMM`). Alt kutu: `Bulunmayan özelliğin kartı gizlenir`.

### 6.2 PIN paneli — `CocukKilidi.dc.html` sol panel birebir

- 380 dp genişlik. Başlık `PIN gir`, alt yazı `Kumandadaki rakamlarla da yazılır`. 4 nokta: dolu `#E8C27A`, boş 2 dp `#3A4A72` çerçeve.
- Tuş takımı 3×4: `1..9`, `Sil`, `0`, `Tamam`. Ok tuşlarıyla gezilir. Kumandanın `0..9` tuşları doğrudan yazar.
  `DEL`/`Sil` son haneyi siler. 4. hanede kendiliğinden doğrulanır.
- Alt yazı: `3 yanlış denemede 1 dakika bekletir`. 3 yanlıştan sonra 60 sn boyunca tuşlar çalışmaz, başlık `Bekle: N sn` olur.
- Bindirme olarak açıldığında (kilitli uygulama/kanal): ekran `#05080F` %90 ile karartılır, panel ortalanır, başlık `Kilitli: <ad>` olur.
- PIN belirleme akışı: başlık `Yeni PIN belirle` → `PIN'i tekrar gir` → uyuşmazsa toast `PIN'ler aynı değil, yeniden dene`.
- `BACK` iptal eder.

### 6.3 Liste şablonu — `CocukKilidi.dc.html` sağ panel birebir

Tasarımı çizilmemiş ekranlar (KORUYUCU_AYAR, USB, YEDEK, KUMANDA) **yalnız** bu şablonla yapılır:
- Başlık satırı Çocuk kilidi ekranıyla aynıdır: marka + başlık + `Ayarlar › Gelişmiş` + saat.
- Sol panel 380 dp (bilgi), sağ panel esnek (satırlar), aralık 20 dp, kenar `#26324F`, arka plan `#101A33`, köşe 18 dp.
- Satır: en az 62 dp yükseklik, yatay 18 dp boşluk, üst çizgi 1 dp `#26324F`. Başlık 16 sp/800, alt yazı 13 sp/600 `#A3ACBF`.
- Odaktaki satır: arka plan `#F2F4F8`, yazılar `#0A1224` / `#3A4459`, köşe 12, kenarlardan 10 dp içeride, 24 dp gölge.
- Anahtar: 48×28 dp. Açık `#1B7A33`, kapalı `#3A4459`, top 22 dp `#F2F4F8`. Seçim değeri: Sora 20/700 `◀ değer ▶`. Geçiş: `›`.
- Alt satır: yardım metni + `OK seç` + `Geri: Gelişmiş`.

---

## 7. Hızlı panel (`HizliPanel.kt`) — `HizliPanel.dc.html` birebir

- Açma/kapama: ana ekranda `KEYCODE_PROG_BLUE`. Ayrıca Gelişmiş 2. kart. Kapatma: `PROG_BLUE` ya da `BACK`.
  Ana ekran arkada çalışmaya devam eder, önizleme durmaz. Arkadaki ana ekran soluklaşmaz; yalnız sağa doğru koyulaşan geçiş katmanı eklenir:
  `linear-gradient(90deg, #05080F00 %30, #05080FD9 %62)`.
- Panel: sağdan 18 dp, üst/alt 18 dp, genişlik 440 dp, arka plan `#070B16` %96, kenar `#E8C27A` %30, köşe 22 dp, iç boşluk 22 dp.
- Odak sırası (`panelIndex`): `0` Ses · `1..6` kareler (2 sütun, soldan sağa, yukarıdan aşağı) · `7` uyku kapsülleri.
  `UP/DOWN` satırlar arasında, `LEFT/RIGHT` sütunlar arasında gezer. Odaktaki kare beyaza döner (Temel kart odak stili).
- **Ses:** `LEFT/RIGHT` → `AudioManager.adjustStreamVolume(STREAM_MUSIC, ±1, 0)`. Gösterilen değer: `cur * 100 / max`. `OK` sessize alır / sesi açar.
  Alt yazı `◀ ▶ ile ayarla · OK sessiz`.
- **Kareler** (sıra sabit):
  1. `Uyku zamanlayıcı` — `Kapalı` / `N dk kaldı` — OK odağı uyku kapsüllerine taşır.
  2. `Ekran yansıtma` — `Hazır` (`#7BE08A`) / `Bu TV'de yok` — OK `Yansitma.ac()`.
  3. `Görüntü ayarı` — `TV menüsünü açar` — OK: `"android.settings.PICTURE_SETTINGS"`, sonra `Settings.ACTION_DISPLAY_SETTINGS`, sonra `ACTION_SETTINGS`.
  4. `Wi-Fi` — `Bağlı` / `Bağlantı yok` — OK `Settings.ACTION_WIFI_SETTINGS`.
  5. `Ekran koruyucu` — `Şimdi başlat` — OK paneli kapatır, koruyucuyu açar.
  6. `Çocuk modu` — `Açık` / `Kapalı` — OK önce PIN ister, sonra kilidi açar/kapatır.
- **Uyku kapsülleri:** `Kapalı`, `15 dk`, `30 dk`, `60 dk`, `90 dk`. Seçili kapsül `#E8C27A` / `#1E1505`. OK seçimi uygular.
  - Bitiş zamanı pref `uykuBitis`'e (epoch ms) yazılır. `LauncherState` saniyede bir değil, **30 sn'de bir** denetler.
  - Bitişe 60 sn kala toast (8 sn): `TV 1 dakika sonra uyku moduna geçecek · Mavi tuş › Uyku › Kapalı ile iptal`.
  - Süre dolunca `YerelAdb.shell("input keyevent 223")` çalışır. `null` dönerse toast: `TV kapatılamadı · ADB izni yok`. Ardından `uykuBitis = 0`.
- Alt satır: mavi kare + `Mavi tuş: aç / kapat` · `Geri: kapat`.

---

## 8. Çocuk kilidi (`CocukKilidi.kt`) — `CocukKilidi.dc.html` birebir

### 8.1 PIN

- Saklama: pref `kilitTuz` (16 rastgele bayt, Base64) + `kilitPin` = `SHA-256(tuz + pin)` hex. Düz PIN hiçbir yere yazılmaz.
- PIN yoksa Çocuk kilidi kartına OK'e basınca 6.2'deki belirleme akışı açılır, sonra `Screen.KILIT` gelir.
- PIN varsa KILIT ekranına girerken PIN istenir. Doğru girilince `kilitOturumu = true` olur. Ana ekrana dönünce `false` olur.

### 8.2 Ekran satırları (sıra sabit)

1. `Çocuk kilidi` — `Açıkken kilitli uygulama ve kanallar PIN ister` — anahtar, pref `kilitAcik`.
2. `Kilitli uygulamalar` — `Ana ekranda kilit simgesiyle görünür` — kilitli uygulamalar kapsül olarak listelenir, en sonda `+ Ekle`.
   OK seçici listeyi açar: liste şablonuyla `visibleApps()` sıralanır, her satırın değeri `Kilitli`/`Açık` olur. Pref `kilitliUygulamalar` (StringSet).
3. `Kilitli kanallar` — `Ana ekranda ve önizlemede PIN ister` — aynı biçimde, kapsül `+ Kanal seç`. Seçici: bütün kanallar (`allChannels`),
   satır başlığı `numara · ad`. Pref `kilitliKanallar` (StringSet, `Ch.key`).
4. `Günlük süre sınırı` — `Süre dolunca ekran koruyucu açılır` — `◀ ▶` ile: `Sınırsız`, `30 dk`, `1 saat`, `1,5 saat`, `2 saat`, `3 saat`, `4 saat`. Pref `kilitSure` (dakika, 0 = sınırsız).
5. `Gece kilidi` — `22:00 – 07:00 arası TV PIN ister` — anahtar, pref `geceKilidi`. Saatler sabittir, değiştirilemez.
6. `PIN'i değiştir` — `Unutursan EYMEN Kurulum'dan sıfırlanır` — `›`. Akış: eski PIN → yeni PIN → tekrar.

Alt satır: `◀ ▶ ile süreyi değiştir. Değişiklikler PIN girildikten sonra kaydedilir.` · `OK seç` · `Geri: Gelişmiş`.

### 8.3 Uygulama

Kilit yalnız `kilitAcik == true` iken çalışır:
- Kilitli uygulama: `launchApp` önce PIN bindirmesini açar. Doğru PIN o tek açılış için izin verir.
- Kilitli kanal: `openEymen(ch, …)` önce PIN ister. Önizlemede çalınmaz, 4.6'daki yazı görünür. `CH+/CH−` kilitli kanala geçebilir ama oynatmaz.

### 8.4 Süre ve gece kilidi

- Sayaç: `LauncherState` içinde dakikada bir `PowerManager.isInteractive` ise pref `kilitDakika_yyyyMMdd` 1 artar. Önceki günlerin anahtarları silinir.
- Süre dolunca ya da gece kilidi saatine girilince: `HomeActivity.bringToFront()` çağrılır (değiştirilmeden; erişim için
  HomeActivity'de yeni `fun kilitOneGetir() = bringToFront()` eklenebilir), sonra ekran koruyucu **kilit biçiminde** açılır:
  saat aynı, hava satırı yerine `Bugünkü süre doldu` ya da `Gece kilidi · 07:00'de açılır`, alt satırda `Devam etmek için OK, sonra PIN`.
  Bu biçimde herhangi bir tuş koruyucuyu kapatmaz. Yalnız OK PIN panelini açar. Doğru PIN süreye **30 dk** ekler ya da gece kilidini o gece için kaldırır.

---

## 9. Ekran koruyucu (`EkranKoruyucu.kt`) — `EkranKoruyucu.dc.html` birebir

- Tetikleme: `Screen.HOME`, uygulama görünür, uyarı yok, panel kapalı ve son tuştan beri `koruyucuDk` dakika geçmiş.
  Pref `koruyucuDk`: `0` (kapalı), `5`, `10` (varsayılan), `15`, `30`. Açıkken `preview.stop()`, kapanınca önizleme kaldığı kanaldan sürer.
- Görünüm: saat Sora 200 sp / 300 kalınlık, harf aralığı -6. Üstte tarih `CUMARTESİ · 14 KASIM` biçiminde (`EEEE · d MMMM`, tr, büyük harf, `#E8C27A`, 15 sp/800, aralık 4).
  Hava satırı: bulut simgesi (kod 0 ise güneş `I_SUN`), sıcaklık Sora 34/400 `14°`, `<durum> · <şehir>` 18 sp/600 `#A3ACBF`.
  Alt satır: marka + `EYMEN OS` · `Herhangi bir tuşa bas, kaldığın yere dön` · `atasoyyazilim.com`.
- Yanık izi koruması: içerik bloğu her 60 sn'de sırayla (0,0) → (24,−16) → (−24,16) → (24,16) → (−24,−16) dp kayar, 600 ms geçişle.
- Hava durumu: `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,weather_code&timezone=auto`.
  30 dk'da bir yenilenir. İnternet yoksa ya da şehir seçilmemişse hava satırı gizlenir.
  Kod → metin: `0` Açık · `1` Az bulutlu · `2` Parçalı bulutlu · `3` Kapalı · `45,48` Sisli · `51–57` Çisenti · `61–67` Yağmurlu ·
  `71–77` Karlı · `80–82` Sağanak · `95–99` Gök gürültülü.
- Ayar ekranı (`Screen.KORUYUCU_AYAR`, liste şablonu): sol panel ay simgesi + `Ekran koruyucu` + `Ana ekran boşta kalınca açılır`.
  Satırlar: `Süre` (`◀ Kapalı/5 dk/10 dk/15 dk/30 dk ▶`) · `Hava durumu` (anahtar, pref `koruyucuHava`) ·
  `Şehir` (`›`, 81 il listesi `iller.json`, pref `koruyucuSehir`) · `Şimdi başlat` (`›`).
- `iller.json`: `[{"ad":"Adana","lat":37.0,"lon":35.3213}, …]` biçiminde 81 il, alfabetik (tr). Koordinat il merkezidir.

---

## 10. USB medya (`UsbMedya.kt`)

- Algılama: `StorageManager.storageVolumes` içinde `isRemovable && state == MOUNTED` olan ilk birim. `MEDIA_MOUNTED` gelince ve
  `Screen.HOME` görünürse `Screen.USB` kendiliğinden açılır, toast `USB takıldı`. Çıkarılınca ekran açıksa `Screen.GELISMIS`'e dönülür, toast `USB çıkarıldı`.
- İzin: `READ_EXTERNAL_STORAGE` verilmemişse `YerelAdb.shell("pm grant com.eymen.os android.permission.READ_EXTERNAL_STORAGE")` bir kez denenir.
  Olmazsa satır: `Dosyalara erişim izni yok · TV'de onay ver`.
- Dosyalar MediaStore'dan okunur: `MediaStore.Video/Images/Audio.Media.getContentUri(volume.mediaStoreVolumeName)`,
  `DATE_MODIFIED` azalan, en çok 500 kayıt. Klasör gezgini yoktur.
- Ekran (liste şablonu): sol panel klasör simgesi + disk adı (`volume.getDescription`) + `Dolu / Toplam` (StatFs).
  Sağ panel en üstte üç kapsül `Videolar` · `Fotoğraflar` · `Müzik` (seçili `#E8C27A`). Liste en üstteyken `UP` kapsüllere çıkar, `LEFT/RIGHT` değiştirir.
  Satır: başlık = dosya adı, alt = `boyut · d MMMM yyyy`. Boşsa tek satır `Bu bölümde dosya yok`.
- OK: video ve müzik → `Intent.ACTION_VIEW` (content uri, MIME, `FLAG_GRANT_READ_URI_PERMISSION`, `launch()` ile).
  Açacak uygulama yoksa toast `Bu dosyayı açacak oynatıcı yok`.
  Fotoğraf → uygulama içi tam ekran gösterici: siyah zemin, `inSampleSize` ile 1920 px'e küçültülmüş bitmap, sığdırılmış.
  Alt şerit (`#05080F` %80): dosya adı + `◀ ▶ önceki / sonraki · Geri: liste`.
- Alt satır: `USB bellekteki dosyayı TV'nin oynatıcısıyla açar.` · `OK aç` · `Geri: Gelişmiş`.

---

## 11. Sistem araçları (`Sistem.kt`) — `Sistem.dc.html` birebir

- **Depolama:** `StatFs(Environment.getDataDirectory())` → `kullanılan / toplam GB` (virgüllü, 1 hane, tr).
  Dağılım için `StorageStatsManager.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())`:
  Uygulamalar = `appBytes + dataBytes - cacheBytes`, Önbellek = `cacheBytes`, Diğer = kullanılan − ikisinin toplamı.
  Kullanım istatistiği izni yoksa bir kez `YerelAdb.shell("appops set com.eymen.os GET_USAGE_STATS allow")` denenir.
  Yine olmazsa tek renkli çubuk gösterilir (`#7FB2FF`) ve açıklama yazısı yerine `Dağılım için izin gerekli` yazar.
  OK: `Settings.ACTION_INTERNAL_STORAGE_SETTINGS`.
- **Önbellek:** değer = `cacheBytes` (`MB`, izin yoksa `—`). OK `Temizle` → `YerelAdb.shell("pm trim-caches 999G")`, sonra yeniden ölçülür.
  Toast: `N MB boşaltıldı` ya da `Temizlenemedi · ADB izni yok`. Çalışırken düğme yazısı `Temizleniyor…` olur.
- **İnternet hızı:** OK `Testi başlat` → `https://speed.cloudflare.com/__down?bytes=50000000` en çok 10 sn indirilir.
  `Mbps = bayt × 8 / sn / 1 000 000` (tamsayı). Gecikme: `__down?bytes=0` 3 kez, ortalama ms. Sonuç: `NN Mbps` büyük, altında `Gecikme N ms`.
  Çalışırken `Ölçülüyor…` yazar ve ince ilerleme çubuğu görünür (`#7FD8F0`). Sabit açıklama: `İndirme hızı ve gecikme ölçülür. 4K yayın için 25 Mbps önerilir.`
- Odak sırası: üst sıra 3 kart (`LEFT/RIGHT`) → `DOWN` performans satırları (3 satır) → performans satırından `RIGHT` → `Arka plandaki uygulamaları kapat`.
- `Arka plandaki uygulamaları kapat`: yüklü bütün paketler için `ActivityManager.killBackgroundProcesses(pkg)` çalışır.
  `com.eymen.*` paketleri hariç tutulur. Toast `Arka plandaki uygulamalar kapatıldı`.

## 12. Hızlandırma

1. **R8:** `tv` release: `isMinifyEnabled = true`, `isShrinkResources = true`,
   `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`. `proguard-rules.pro` içeriği:
   ```
   -keep class com.eymen.os.** { *; }
   -keep class dadb.** { *; }
   -keepclassmembers class * { @androidx.compose.runtime.Composable *; }
   -dontwarn org.bouncycastle.**
   -dontwarn org.conscrypt.**
   -dontwarn org.openjsse.**
   ```
   R8 başka eksik sınıf bildirirse yalnız `build/outputs/mapping/release/missing_rules.txt` içindeki `-dontwarn` satırları eklenir. Başka kural eklenmez.
2. **Baseline Profile:** `profileinstaller` bağımlılığı + `tv/src/main/baseline-prof.txt` tam olarak şu satırlar:
   ```
   HSPLcom/eymen/os/**->**(**)**
   HSPLandroidx/compose/runtime/**->**(**)**
   HSPLandroidx/compose/ui/**->**(**)**
   HSPLandroidx/compose/foundation/**->**(**)**
   HSPLandroidx/compose/animation/**->**(**)**
   Lcom/eymen/os/**;
   ```
3. **Simge/logo önbelleği (`IkonOnbellegi.kt`):** `cacheDir/ikonlar/<pkg>_<versionCode>_banner.png` ve `_icon.png`.
   Bellek önbelleği `LruCache` 24 MB. `requestGraphics` önce belleğe, sonra diske bakar. Yoksa `Apps.graphics` çalışır ve sonuç diske yazılır.
   Paket güncellenince eski `versionCode` dosyaları silinir. Kanal logoları için aynı düzen: `cacheDir/logolar/<key-sha1>.png`.
   Pref `hizliAcilis` (varsayılan `true`) kapalıysa önbellek kullanılmaz.
4. **Uygulama listesi önbelleği:** pref `uygulamaListesi` = `[{"p":pkg,"l":label}]`. `start()` önce bunu gösterir, `Apps.load` arka planda tazeler.
5. **Açılış derlemesi:** her yeni `versionName`'in ilk açılışından 120 sn sonra, güncelleme sürmüyorsa, bir kez
   `YerelAdb.shell("cmd package compile -m speed-profile -f com.eymen.os", 180000)` çalışır. Pref `derlendi=<versionName>`.
6. **Açılış ölçümü:** süreçteki ilk pencere odağında `SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()`.
   Son 5 değer pref `acilisSureleri` (virgüllü ms) içinde tutulur. Sistem ekranında 5 çubuk gösterilir, en sonuncusu `#7BE08A`, diğerleri `#3A4459`.
   Yükseklik en büyüğe oranlanır (en büyük = 70 dp). Yanında `N,N sn` ve `Son açılış · yeşil çubuk` yazar.
7. **Önizlemeyi sonra başlat:** pref `gecOnizleme` (varsayılan `true`) açıkken ilk `preview.play`, ilk pencere odağından 1200 ms sonra çağrılır.
8. **Hafif animasyonlar:** pref `hafifAnimasyon` (varsayılan `false`). Bölüm 4.6'daki etki dışında `HubTile` büyümesi de kapanır.

Performans paneli satırları (`Sistem.dc.html`): `Hızlı açılış` / `Simgeler ve logolar hafızada tutulur, ana ekran anında dolar` (hizliAcilis) ·
`Önizlemeyi sonra başlat` / `Canlı pencere ana ekran açıldıktan sonra başlar` (gecOnizleme) ·
`Hafif animasyonlar` / `Yavaş TV'lerde dönen dişli ve büyüme efektlerini azaltır` (hafifAnimasyon).

## 13. Yedek (`Yedek.kt`)

- Yedeklenen pref anahtarları **yalnız** şunlardır: `hiddenApps`, `appOrder`, `sound`, `autoStart`, `last`, `kilitAcik`, `kilitliUygulamalar`,
  `kilitliKanallar`, `kilitSure`, `geceKilidi`, `kilitTuz`, `kilitPin`, `koruyucuDk`, `koruyucuHava`, `koruyucuSehir`, `hizliAcilis`,
  `gecOnizleme`, `hafifAnimasyon`, `yansitmaKisayol`.
- Dosya: `getExternalFilesDir(null)/yedek.json` →
  `{"surum":1,"uygulama":"1.2.0","tarih":<epoch ms>,"tv":"<Build.MODEL>","ayarlar":{...}}`.
- `YedekReceiver`:
  - `YEDEK_AL` dosyayı yazar ve pref `sonYedek`'e tarihi kaydeder.
  - `YEDEK_YUKLE` dosyayı okur. `surum == 1` değilse işlemi reddeder. Yalnız listedeki anahtarları yazar, sonra
    `HomeActivity`'yi `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` ile yeniden açar ve toast gösterir: `Yedek geri yüklendi`.
  - `PIN_SIFIRLA` `kilitPin`, `kilitTuz` ve `kilitAcik` anahtarlarını siler.
- TV ekranı (`Screen.YEDEK`, liste şablonu): sol panel bulut simgesi + `Yedekle ve geri yükle` + `Yedekleme telefondaki EYMEN Kurulum ile yapılır`.
  Satırlar: `Son yedek` (`Yok` / `d MMMM HH:mm`) · `TV'de yedek hazırla` (`›`, YEDEK_AL'ın işini yapar, toast `Yedek hazır · telefondan al`) ·
  `Yedekte neler var` (alt yazı: `Uygulama sırası, gizli uygulamalar, kilit, ekran koruyucu ve hız ayarları`).

## 14. TV kumanda bilgi ekranı (`Kumanda.kt`, `Screen.KUMANDA`)

Liste şablonu. Sol panel telefon simgesi + `Telefondan kumanda` + `Telefonda EYMEN Kurulum › Kumanda sekmesini aç`.
Satırlar (yalnız bilgi): `TV adresi` (değer Sora 20/700: Wi-Fi IPv4, yoksa `Bağlantı yok`) · `Bağlantı noktası` (`5555`) ·
`Telefonla TV aynı Wi-Fi ağında olsun` (alt yazı: `İlk bağlantıda TV'de çıkan izni Her zaman izin ver ile onayla`).

## 15. Telefon: EYMEN Kurulum 1.2.0

Telefon uygulamasının var olan akışı (Adım 1–3, Hızlı güncelle, Komut merkezi) **hiç değişmez**.
Yeni iki ekran yalnız Komut merkezinden açılır.

- `MainActivity.komutlar()` içinde var olan düğmelerin **en üstüne** iki düğme eklenir:
  `dugme("Kumanda") { kumandaAc() }` ve `dugme("Yedek", false) { yedekAc() }`. `komutlar()` içinde başka değişiklik yapılmaz.
- `MainActivity`'ye yalnız şunlar eklenir: `kumandaAc()`, `yedekAc()`, `onBackPressed()`.
  Yeni ekran açıkken `onBackPressed()` önce `setContentView(<eski ScrollView>)` ile Komut merkezine döner, sonra `komutlar()` çağrılır.
- `Kumanda.kt`: `class KumandaEkrani(act: Activity, tv: Tv, geri: () -> Unit, yedegeGec: () -> Unit)` ve `fun gorunum(): View`.
  `Yedekler.kt`: aynı imzayla `class YedekEkrani(...)`.
  Görünüm `Telefon.dc.html` ile birebir aynıdır: View tabanlı, `GradientDrawable`, ölçüler dp. Arka plan `#070B16`.
  Üstte geri düğmesi, başlık `Kumanda` ve bağlantı durumu (`<adres> bağlı` yeşil / `Bağlı değil` `#A3ACBF`) bulunur.
  Altında `Kumanda | Yedek` sekmesi vardır.
- Bağlantı: `MainActivity`'nin var olan `tv` nesnesi verilir. `Tv.kt` değişmez, yalnız `komut()` çağrılır.
  `tv.ip` boşsa ya da komut hata verirse ekranda `Bağlı değil · Komut merkezinden bağlan` yazar, düğmeler pasifleşir (alpha 0.4).
- **Tuşlar** → `input keyevent N`:
  Yukarı 19 · Aşağı 20 · Sol 21 · Sağ 22 · OK 23 · Geri 4 · Ana ekran 3 · Menü 82 · Ses+ 24 · Ses− 25 · Sessiz 164 ·
  Kanal+ 166 · Kanal− 167 · Kırmızı 183 · Yeşil 184 · Sarı 185 · Mavi 186 · Kapat 223 · sağ üstteki şimşek (hızlı panel) 186.
  Komutlar tek bir arka plan iş parçacığında sırayla gönderilir. Basılı tutulunca yalnız ok tuşları ve ses tuşları 150 ms'de bir yinelenir.
- **TV'ye yaz:** klavyedeki "Gönder" ile `input text` gönderilir. Boşluk `%s` olur. `ç ğ ı ö ş ü Ç Ğ İ Ö Ş Ü` sade harfe çevrilir.
  Kabuk için `'` karakteri `'\''` biçiminde kaçırılır. Alanın altındaki not: `Türkçe harfler sade harfe çevrilir`.
- **Yedek sekmesi** (Kumanda ile aynı görsel dil: `#101A33` kartlar, altın ana düğme):
  - `TV'yi yedekle` (ana düğme): önce `am broadcast -a com.eymen.os.YEDEK_AL -n com.eymen.os/.YedekReceiver` gönderilir, 1500 ms beklenir,
    sonra `cat /sdcard/Android/data/com.eymen.os/files/yedek.json` okunur → `filesDir/yedekler/yedek-yyyyMMdd-HHmm.json`.
    Okunan içerik `{` ile başlamazsa toast `Yedek alınamadı`.
  - Kayıtlı yedekler kart olarak listelenir (yeni üstte, `d MMMM yyyy HH:mm`). Karta dokununca onay penceresi açılır: `Bu yedek TV'ye yüklensin mi?`.
    Onaylanırsa içerik Base64'e çevrilir, `echo <b64> | base64 -d > /sdcard/Android/data/com.eymen.os/files/yedek.json` yazılır,
    ardından `am broadcast -a com.eymen.os.YEDEK_YUKLE -n com.eymen.os/.YedekReceiver` gönderilir.
  - `Çocuk kilidi PIN'ini sıfırla` (ikincil düğme): onay penceresi açılır, sonra `PIN_SIFIRLA` yayını gönderilir.

## 16. Sürüm ve yayın

- `tv`: `versionCode = 8`, `versionName = "1.2.0"`. `telefon`: `versionCode = 8`, `versionName = "1.2.0"`.
- Codex `surum-1.2` dalında çalışır, `main`'e PR açar. **`main`'e doğrudan push yasak.** PR'ı yalnız Muhammet Atasoy birleştirir.
  Birleşince var olan iş akışı derler, yayınlar ve TV'ler kendini günceller.
- `RELEASE.md` içeriği tam olarak:

```
# EYMEN OS TV 1.2.0

Vestel 17MB170 / Android 11 TV için EYMEN OS.

- Ayarlar'a Gelişmiş sekmesi eklendi
- Ekran yansıtma otomatik algılanır, TV'de varsa Ayarlar'da görünür
- Mavi tuşla açılan hızlı panel: ses, uyku zamanlayıcı, yansıtma, Wi-Fi
- Çocuk kilidi, ekran koruyucu, USB medya ve sistem araçları
- Daha hızlı açılış: simge önbelleği ve derleme iyileştirmeleri
- EYMEN Kurulum'a telefondan kumanda ve yedekleme eklendi

Dosyalar:

`EYMEN-Kurulum.apk`: telefona kurulur. İlk kurulumu, Hızlı güncelle'yi, kumandayı ve yedeği yapar.
`EYMEN-OS-TV.apk`: TV uygulaması. Kurulum uygulamasının içinde de gelir.

Geri dönüş: Telefon > Komut Merkezi > "Eski ekranı geri getir, EYMEN OS'u kaldır".
```

## 17. Palet ve simgeler

### 17.1 Palet

Yeni kodda `Color(0x..)` / `parseColor("#..")` olarak yalnız `C.*` tokenları ve şu RGB değerleri kullanılabilir (saydamlık serbest):
#000000 #05080F #070B16 #0A1224 #0B1226 #0F1830 #101830 #101A33 #101A3A #13224A #14204A #17213D #1B7A33 #1C2B55 #1D2848 #1E1505 #26324F #3A4459 #3A4A72 #3FB950 #4C8DFF #57C7E3 #6C7891 #6FA8FF #7BE08A #7FB2FF #7FD8F0 #7FE0D0 #8A5A00 #8A744C #8C96AA #8FD3A8 #9CC3FF #9DD8C8 #A3ACBF #A9B2FF #B79CFF #C6E27A #C9D0DD #D6E2FF #E5484D #E6A8FF #E8C27A #E9C46A #EEF1F6 #F0D98C #F2D9A6 #F2F4F8 #F5B85C #FF5468 #FF8FB3 #FF9A9A #FFA07A #FFFFFF.
Denetim betiği yeni ve değişen dosyalardaki bütün renkleri bu listeyle karşılaştırır.

### 17.2 Simgeler (24×24 çizgi yolu, `SvgIcon` ile aynı çizim)

```
I_CAST        M2 16.1A5 5 0 0 1 5.9 20M2 12.05A9 9 0 0 1 9.95 20M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6M2 20h.01
I_ZAP         M13 2L3 14h9l-1 8 10-12h-9l1-8z
I_LOCK        M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2zM7 11V7a5 5 0 0 1 10 0v4
I_MOON        M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z
I_SUN         M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4
I_FOLDER_PLAY M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM10 11l4 2-4 2z
I_WRENCH      M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z
I_GAUGE       M12 14l4-4M3.3 19a10 10 0 1 1 17.4 0
I_CLOUD_UP    M17.5 19H7a5 5 0 1 1 1-9.9A6 6 0 0 1 19.5 11a4 4 0 0 1-2 8zM12 18v-6M9 15l3-3 3 3
I_PHONE       M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18h2
I_TIMER       M12 5a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM12 9v4l2 2M9 2h6
I_WIFI        (Settings.kt içindeki var olan I_WIFI)
```

## 18. Kabul denetimi (PR'dan önce hepsi geçmeli)

1. `python3 .github/kurallar/denetle.py` → `TAMAM`.
2. `gradle :tv:assembleRelease :telefon:assembleRelease` hatasız.
3. Temel sekmesi 1.1.1 ile piksel piksel aynı. Tek farklar sekme çubuğu, `▲ sekmeler` ipucu ve durum panelindeki yansıtma satırı.
4. Ana ekran 1.1.1 ile aynı. Tek farklar kilit rozeti (kilit açıkken) ve yansıtma kısayolu (açıksa).
5. Güncelleme: 1.1.1 kurulu TV, 1.2.0'ı kendisi bulur, imzalı uyarıyı gösterir ve kurar. Ayarlar ve gizli uygulamalar korunur.
6. Yansıtma uygulaması olmayan bir cihazda (örnek: emülatör) Ekran Yansıtma kartı görünmez, uygulama çökmez.
7. ADB izni olmayan cihazda temizleme, uyku ve derleme adımları toast gösterir, çökme olmaz.
8. PR açıklamasında bu 8 maddenin her biri için `[x]` ve kısa not bulunur.
