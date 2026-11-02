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
        komut("appops set $OS SYSTEM_ALERT_WINDOW allow")
        val c = komut("cmd package set-home-activity $OS/.HomeActivity")
        komut("am start -n $OS/.HomeActivity")
        Thread.sleep(6000)
        check(komut("pidof $OS").isNotBlank()) { "EYMEN OS açılmadı, eski ekran kapatılmadı" }
        eskiEkranlariKapat()
        return "$c\n\n${durum()}"
    }

    fun durum(): String = komut(
        "echo '--- yuklu'; pm path $OS; " +
            "echo '--- ana ekran'; cmd package resolve-activity --brief -c android.intent.category.HOME android.intent.action.MAIN; " +
            "echo '--- ana ekran adaylari'; cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME; " +
            "echo '--- kapali paketler'; pm list packages -d; " +
            "echo '--- ekranda'; dumpsys activity activities | grep -m3 -E 'topResumedActivity|mResumedActivity'"
    )

    companion object {
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
