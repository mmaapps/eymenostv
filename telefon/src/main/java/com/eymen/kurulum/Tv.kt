package com.eymen.kurulum

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

    private fun indir(url: String, ad: String, ilerleme: (String) -> Unit): File {
        val hedef = File(ctx.cacheDir, ad)
        val parca = File(ctx.cacheDir, "$ad.parca")
        if (hedef.length() > 0) return hedef
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        try {
            check(c.responseCode == 200) { "İndirilemedi: ${c.responseCode}" }
            val toplam = c.contentLengthLong
            c.inputStream.use { g ->
                FileOutputStream(parca).use { o ->
                    val b = ByteArray(64 * 1024)
                    var gelen = 0L
                    while (true) {
                        val n = g.read(b)
                        if (n < 0) break
                        o.write(b, 0, n)
                        gelen += n
                        if (toplam > 0) ilerleme("$ad %${gelen * 100 / toplam}")
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        check(parca.renameTo(hedef)) { "Dosya kaydedilemedi" }
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
                indir(UYDU_URL, "EYMEN-TV.apk", ilerleme)
                indir(OS_URL, "EYMEN-OS-TV.apk", ilerleme)
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
                for (p in ESKI) komut("pm disable-user --user 0 $p")
            }
            7 -> try {
                komut("reboot")
            } catch (_: Exception) {
                // TV yeniden başlarken bağlantının kopması normal.
            }
        }
    }

    companion object {
        const val PORT = 5555
        const val OS = "com.eymen.os"
        val ESKI = listOf("com.google.android.tvlauncher", "com.google.android.tvrecommendations")
        const val OS_URL = "https://github.com/mmaapps/eymenostv/releases/latest/download/EYMEN-OS-TV.apk"
        const val UYDU_URL = "https://github.com/mmustafatasoy/EymenTv/releases/latest/download/EYMEN-TV.apk"
        val ADIMLAR = listOf(
            "Dosyaları indirme", "Yedek liste", "EYMEN Uydu yükleme", "EYMEN OS yükleme",
            "İzinler", "Ana ekranı ayarlama", "Eski ekranı kapatma", "Yeniden başlatma"
        )
        val GERI_AL = "for p in ${ESKI.joinToString(" ")}; do pm enable \$p; done; pm uninstall $OS"
    }
}
