package com.eymen.kurulum

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.FileOutputStream

/** TV ile ADB bağlantısı ve kurulum adımları. Arayüz kodu burada yok. */
class Tv(private val ctx: Context) {
    private var adb: Dadb? = null
    private val prefs = ctx.getSharedPreferences("kurulum", Context.MODE_PRIVATE)

    var ip: String
        get() = prefs.getString("ip", "") ?: ""
        set(v) = prefs.edit().putString("ip", v).apply()

    /** Biten son adım. Wi-Fi koparsa kurulum buradan sürer. */
    var adim: Int
        get() = prefs.getInt("adim", 0)
        set(v) = prefs.edit().putInt("adim", v).apply()

    private fun anahtar(): AdbKeyPair {
        val gizli = File(ctx.filesDir, "adbkey")
        val acik = File(ctx.filesDir, "adbkey.pub")
        if (!gizli.exists()) AdbKeyPair.generate(gizli, acik)
        return AdbKeyPair.read(gizli, acik)
    }

    fun baglan(adres: String) {
        kapat()
        ip = adres
        // TV'de izin ekranı beklenebilir, bu yüzden okuma süresi uzun.
        val d = Dadb.create(adres, PORT, anahtar(), 10_000, 180_000, true)
        val cevap = d.shell("echo EYMEN").allOutput
        check(cevap.contains("EYMEN")) { "TV yanıt vermedi" }
        adb = d
    }

    fun kapat() {
        try {
            adb?.close()
        } catch (_: Exception) {
        }
        adb = null
    }

    fun komut(k: String): String {
        val d = adb ?: throw IllegalStateException("TV bağlı değil")
        return d.shell(k).allOutput.trim()
    }

    /** APK'lar telefon uygulamasının içinde gelir, internet gerekmez. */
    private fun cikar(ad: String, ilerleme: (String) -> Unit): File {
        val hedef = File(ctx.cacheDir, ad)
        ilerleme("$ad hazırlanıyor")
        ctx.assets.open(ad).use { g -> FileOutputStream(hedef).use { o -> g.copyTo(o) } }
        return hedef
    }

    private fun yukle(f: File) {
        val d = adb ?: throw IllegalStateException("TV bağlı değil")
        d.install(f, "-r")
    }

    /** Sıradaki adımı yapar. Hata atarsa [adim] değişmez, aynı yerden yeniden denenir. */
    fun adimYap(i: Int, ilerleme: (String) -> Unit) {
        when (i) {
            0 -> {
                cikar("EYMEN-TV.apk", ilerleme)
                cikar("EYMEN-OS-TV.apk", ilerleme)
            }
            1 -> {
                val liste = komut("pm list packages")
                val ev = komut("cmd package resolve-activity --brief -c android.intent.category.HOME android.intent.action.MAIN")
                File(ctx.filesDir, "tv-yedek.txt").writeText("Ana ekran:\n$ev\n\nPaketler:\n$liste\n")
            }
            2 -> yukle(File(ctx.cacheDir, "EYMEN-TV.apk"))
            3 -> yukle(File(ctx.cacheDir, "EYMEN-OS-TV.apk"))
            4 -> {
                komut("pm grant $OS android.permission.READ_TV_LISTINGS")
                komut("pm grant $OS com.android.providers.tv.permission.READ_EPG_DATA")
                komut("appops set $OS SYSTEM_ALERT_WINDOW allow")
            }
            5 -> {
                val c = komut("cmd package set-home-activity $OS/.HomeActivity")
                check(c.contains("Success", true)) { "Ana ekran ayarlanamadı: $c" }
            }
            6 -> {
                // Yeni ana ekran gerçekten açılmadan eski ekran kapatılmaz.
                komut("am start -n $OS/.HomeActivity")
                Thread.sleep(6000)
                check(komut("pidof $OS").isNotBlank()) { "EYMEN OS açılmadı, eski ekran kapatılmadı" }
                eskiEkranlariKapat()
            }
            7 -> try {
                komut("reboot")
            } catch (_: Exception) {
                // TV yeniden başlarken bağlantının kopması normal.
            }
        }
    }

    private fun eskiEkranlariKapat() {
        val paketler = komut("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().map { it.trim().substringBefore('/') }
            .filter { it.contains('.') && it !in KORUNAN && !it.contains(' ') }
        for (p in (paketler + ESKI).distinct()) komut("pm disable-user --user 0 $p")
    }

    /** TV kendi ekranını açıyorsa EYMEN OS'u yeniden ana ekran yapar. */
    fun anaEkraniOnar(): String {
        val sb = StringBuilder()
        if (komut("pm path $OS").isBlank()) return "EYMEN OS bu TV'de yüklü değil. Önce kurulumu bitir (TV'ye bağlan)."
        komut("appops set $OS SYSTEM_ALERT_WINDOW allow")
        sb.append("Ana ekran ayarı: ").append(komut("cmd package set-home-activity $OS/.HomeActivity")).append("\n")
        komut("am start -n $OS/.HomeActivity")
        Thread.sleep(6000)
        if (komut("pidof $OS").isBlank()) {
            // EYMEN OS açılmadı: eski ekranı KAPATMA, yoksa TV ekransız kalır.
            sb.append("\nEYMEN OS açılamadı, bu yüzden eski ekran kapatılmadı (TV boş kalmasın diye).\n")
            sb.append("Çökme olduysa sebebi altta:\n\n")
            sb.append(cokmeKaydi())
            return sb.toString()
        }
        eskiEkranlariKapat()
        sb.append("EYMEN OS çalışıyor. Eski ana ekranlar kapatıldı.\n\n")
        sb.append(durum())
        return sb.toString()
    }

    /** Açık görüntü ayarlarını okur (gerçek değerleri görmek için). */
    fun goruntuOku(): String = komut(
        "settings list system | grep -iE 'picture_|backlight_slider|tv_picture_advance|local_contrast|game_mode' | sort"
    )

    /** Bir hazır görüntü ayarını uygular. Hepsi geri alınabilir (Fabrika ile). */
    fun goruntuUygula(preset: String): String {
        val keys = PRESETLER[preset] ?: return "Bilinmeyen ayar"
        val k = keys.joinToString("; ") { "settings put system ${it.first} ${it.second}" }
        komut(k)
        return "\"$preset\" uygulandı.\n\nTV ekranına bak, fark oldu mu?\n\n" +
            keys.joinToString("\n") { "${it.first} = ${it.second}" } +
            "\n\nMediaTek görüntü motoru bu değerleri canlı almıyorsa, TV'nin kendi Görüntü menüsünden ayarla."
    }

    fun cokmeKaydi(): String {
        val c = komut("logcat -d -b crash -t 400 | grep -iE 'eymen|AndroidRuntime|FATAL' | tail -40")
        return if (c.isBlank()) "Çökme kaydı yok. EYMEN OS açılıyor ama ana ekran olarak tutunamıyor olabilir." else c
    }

    fun durum(): String = komut(
        "echo '--- yuklu'; pm path $OS; " +
            "echo '--- ana ekran'; cmd package resolve-activity --brief -c android.intent.category.HOME android.intent.action.MAIN; " +
            "echo '--- ana ekran adaylari'; cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME; " +
            "echo '--- kapali paketler'; pm list packages -d; " +
            "echo '--- ekranda'; dumpsys activity activities | grep -m3 -E 'topResumedActivity|mResumedActivity'; " +
            "echo '--- eymen os cokme'; logcat -d -b crash -t 300 | grep -iE 'EYMENOS|AndroidRuntime' | tail -20"
    )

    companion object {
        val PRESETLER: Map<String, List<Pair<String, String>>> = mapOf(
            // Gürültü azaltmayı kapatmak yumuşaklığı/bulanıklığı azaltır: en çok fark eden ayar.
            "Net görüntü" to listOf(
                "tv_picture_advance_video_dnr" to "0",
                "tv_picture_advance_video_mpeg_nr" to "0",
                "tv_picture_video_local_contrast_control" to "3"
            ),
            "Canlı renk" to listOf(
                "picture_saturation" to "58",
                "tv_picture_video_local_contrast_control" to "3",
                "picture_blue_light" to "0"
            ),
            "Gece (göz yormaz)" to listOf(
                "picture_brightness" to "60",
                "picture_blue_light" to "1"
            ),
            // TV'den okuduğumuz ilk değerler: geri dönüş noktası.
            "Fabrika görüntüsü" to listOf(
                "tv_picture_advance_video_dnr" to "3",
                "tv_picture_advance_video_mpeg_nr" to "3",
                "picture_saturation" to "49",
                "picture_brightness" to "75",
                "picture_hue" to "12",
                "tv_picture_video_local_contrast_control" to "3",
                "picture_blue_light" to "0"
            )
        )
        val KORUNAN = setOf(OS, "android", "com.android.settings", "com.android.tv.settings")
        const val PORT = 5555
        const val OS = "com.eymen.os"
        val ESKI = listOf("com.google.android.tvlauncher", "com.google.android.tvrecommendations")
        val ADIMLAR = listOf(
            "Dosyaları hazırlama", "Yedek liste", "EYMEN Uydu yükleme", "EYMEN OS yükleme",
            "İzinler", "Ana ekranı ayarlama", "Eski ekranı kapatma", "Yeniden başlatma"
        )
        val GERI_AL = "for p in ${ESKI.joinToString(" ")}; do pm enable \$p; done; pm uninstall $OS"
    }
}
