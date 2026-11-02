package com.eymen.kurulum

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var tv: Tv
    private var kurulumVar = false
    private var hizliIste = false
    private lateinit var kok: LinearLayout
    private val zemin = Color.parseColor("#0A1224")
    private val kart = Color.parseColor("#17213D")
    private val altin = Color.parseColor("#E8C27A")
    private val beyaz = Color.parseColor("#F2F4F8")
    private val gri = Color.parseColor("#C9D0DD")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tv = Tv(applicationContext)
        val sv = ScrollView(this)
        sv.setBackgroundColor(zemin)
        sv.fitsSystemWindows = true
        kok = LinearLayout(this)
        kok.orientation = LinearLayout.VERTICAL
        kok.setPadding(dp(20), dp(24), dp(20), dp(24))
        sv.addView(kok)
        setContentView(sv)
        hazirlik()
    }

    override fun onDestroy() {
        tv.kapat()
        super.onDestroy()
    }

    // ------------------------------------------------------- ekranlar

    private fun hazirlik() {
        temizle("ADIM 1 / 3", "Önce TV'yi hazırlayalım")
        yazi("Sırayla yap. Bitince en alttaki düğmeye bas.")
        listOf(
            "TV ve telefonu aynı Wi-Fi ağına bağla.",
            "TV'de Ayarlar > Cihaz Tercihleri > Hakkında bölümünü aç.",
            "Yapım numarasının üstüne 7 kez bas.",
            "Bir geri çık. Geliştirici seçenekleri'ni aç.",
            "Hata ayıklama (USB hata ayıklama) seçeneğini aç.",
            "Kurulum sırasında TV'yi ve modemi kapatma."
        ).forEachIndexed { i, s -> kartYazi("${i + 1}. $s") }
        dugme("Hazırım, devam et") {
            hizliIste = false
            baglanti()
        }
        dugme("EYMEN OS kurulu: Hızlı güncelle", false) {
            hizliIste = true
            baglanti()
        }
        if (tv.adim > 0) dugme("Yarım kalan kuruluma devam et", false) { baglanti() }
    }

    private fun baglanti(not: String? = null) {
        temizle("ADIM 2 / 3", "Telefonu TV'ye bağla")
        yazi("TV'nin adresi Ayarlar > Ağ ve İnternet bölümünde yazar. Noktalarıyla aynen yaz.")
        val g = EditText(this)
        g.setText(tv.ip)
        g.hint = "192.168.1.25"
        g.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        g.setTextColor(beyaz)
        g.setHintTextColor(gri)
        g.textSize = 20f
        ekle(g)
        val d = TextView(this)
        d.setTextColor(altin)
        d.textSize = 16f
        d.setPadding(0, dp(10), 0, dp(10))
        d.text = not ?: ""
        ekle(d)
        dugme("TV'ye bağlan") {
            val ip = g.text.toString().trim()
            if (ip.isEmpty()) {
                d.text = "Önce TV adresini yaz."
                return@dugme
            }
            d.text = "Bağlanıyor. TV ekranına bak: \"Her zaman izin ver\" kutusunu işaretle, sonra İzin ver'e bas."
            thread {
                try {
                    tv.baglan(ip)
                    runOnUiThread {
                        when {
                            hizliIste -> hizli()
                            tv.adim > 0 || !kurulumVar -> kurulum()
                            else -> komutlar()
                        }
                    }
                } catch (e: Throwable) {
                    runOnUiThread { wifiKoptu("Bağlanamadı: ${e.message ?: e.javaClass.simpleName}") }
                }
            }
        }
        dugme("Sadece bağlan, kurulum yapma", false) {
            kurulumVar = true
            d.text = "Bağlanıyor..."
            val ip = g.text.toString().trim()
            thread {
                try {
                    tv.baglan(ip)
                    runOnUiThread { komutlar() }
                } catch (e: Throwable) {
                    runOnUiThread { wifiKoptu("Bağlanamadı: ${e.message ?: e.javaClass.simpleName}") }
                }
            }
        }
        dugme("Komut merkezi", false) { komutlar() }
    }

    private fun kurulum() {
        temizle("ADIM 3 / 3", "EYMEN OS kuruluyor")
        yazi("Telefonu kapatma, uygulamadan çıkma. Her adımı telefon yapar.")
        val rows = Tv.ADIMLAR.map { kartYazi(it) }
        val d = TextView(this)
        d.setTextColor(altin)
        d.textSize = 16f
        d.setPadding(0, dp(10), 0, dp(10))
        ekle(d)
        fun boya(aktif: Int) {
            rows.forEachIndexed { i, t ->
                val durum = if (i < aktif) "Tamam" else if (i == aktif) "Çalışıyor" else "Bekliyor"
                t.text = "${Tv.ADIMLAR[i]}: $durum"
            }
        }
        boya(tv.adim)
        thread {
            try {
                while (tv.adim < Tv.ADIMLAR.size) {
                    val i = tv.adim
                    runOnUiThread { boya(i) }
                    tv.adimYap(i) { m -> runOnUiThread { d.text = m } }
                    tv.adim = i + 1
                }
                runOnUiThread { bitti() }
            } catch (e: Throwable) {
                runOnUiThread { wifiKoptu("Durdu: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    private fun hizli() {
        temizle("TV BAĞLI · WI-FI", "Hızlı güncelle")
        yazi("TV yeniden başlamaz. Kanalların, sıran ve ayarların silinmez.")
        val rows = Tv.HIZLI.map { kartYazi(it) }
        val d = TextView(this)
        d.setTextColor(altin)
        d.textSize = 16f
        d.setPadding(0, dp(10), 0, dp(10))
        ekle(d)
        fun boya(aktif: Int) {
            rows.forEachIndexed { i, t ->
                val durum = if (i < aktif) "Tamam" else if (i == aktif) "Çalışıyor" else "Bekliyor"
                t.text = "${Tv.HIZLI[i]}: $durum"
            }
        }
        boya(0)
        thread {
            try {
                for (i in Tv.HIZLI.indices) {
                    runOnUiThread { boya(i) }
                    tv.hizliAdim(i) { m -> runOnUiThread { d.text = m } }
                }
                runOnUiThread { hizliBitti() }
            } catch (e: Throwable) {
                runOnUiThread { wifiKoptu("Durdu: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    private fun hizliBitti() {
        temizle("TAMAM", "Hızlı güncelleme bitti")
        yazi("Bundan sonra TV kendini güncelleyecek. Yeni sürüm çıkınca ekranda uyarı görürsün, telefondan kurulum yapman gerekmez.")
        dugme("Komut merkezi") { komutlar() }
    }

    private fun bitti() {
        temizle("KURULUM BİTTİ", "EYMEN OS kuruldu")
        tv.adim = 0
        tv.kapat()
        yazi("TV yeniden açılıyor. 2 dakika bekle. Açılınca EYMEN OS ana ekranı gelecek.")
        yazi("Kanallar için EYMEN Uydu'yu TV'de bir kez aç.")
        dugme("Komut merkezi") { komutlar() }
    }

    private fun wifiKoptu(neden: String) {
        temizle("BAĞLANTI KOPTU", "Merak etme, kurulum kaydedildi")
        yazi(neden)
        listOf(
            "TV'yi fişten çekme.",
            "Modemin ışıklarına bak. Sönükse modemi aç, 2 dakika bekle.",
            "TV Ayarlar > Ağ ve İnternet bölümünden Wi-Fi'a yeniden bağlan.",
            "Telefonu da aynı Wi-Fi'a bağla.",
            "Ev Wi-Fi'ı yoksa telefon hotspotunu aç, TV'yi ona bağla. TV adresi değişir, yeni adresi yaz."
        ).forEachIndexed { i, s -> kartYazi("${i + 1}. $s") }
        dugme("Yeniden bağlan ve devam et") { baglanti() }
        dugme("Komut merkezi", false) { komutlar() }
    }

    private fun komutlar() {
        temizle("TV KOMUTLARI", "Komut Merkezi")
        yazi("Düğmeye bas, komut TV'ye gider. Cevap altta görünür.")
        val cikti = TextView(this)
        cikti.setTextColor(altin)
        cikti.textSize = 13f
        cikti.typeface = Typeface.MONOSPACE
        cikti.setTextIsSelectable(true)
        fun gonder(k: String) {
            cikti.text = "Gönderildi: $k"
            thread {
                val c = try {
                    tv.komut(k)
                } catch (e: Throwable) {
                    "Hata: ${e.message}. Önce TV'ye bağlan."
                }
                runOnUiThread { cikti.text = "$k\n\n$c" }
            }
        }
        fun calistir(f: () -> String) {
            cikti.text = "Çalışıyor, bekle..."
            thread {
                val c = try {
                    f()
                } catch (e: Throwable) {
                    "Hata: ${e.message}. Önce TV'ye bağlan."
                }
                runOnUiThread { cikti.text = c }
            }
        }
        dugme("Hızlı güncelle") { hizli() }
        dugme("Ana ekranı EYMEN OS yap") { calistir { tv.anaEkraniOnar() } }
        dugme("Durumu kontrol et", false) { calistir { tv.durum() } }
        dugme("Wi-Fi durumu", false) { gonder("cmd wifi status") }
        dugme("TV'de Wi-Fi ayarını aç", false) { gonder("am start -a android.settings.WIFI_SETTINGS") }
        dugme("EYMEN OS'u aç", false) { gonder("am start -n ${Tv.OS}/.HomeActivity") }
        dugme("Hata kaydı al", false) { gonder("logcat -d -t 300 *:E") }
        dugme("TV'yi yeniden başlat", false) { gonder("reboot") }
        dugme("Eski ekranı geri getir, EYMEN OS'u kaldır", false) { gonder(Tv.GERI_AL) }
        val g = EditText(this)
        g.hint = "Kendi komutunu yaz"
        g.setTextColor(beyaz)
        g.setHintTextColor(gri)
        ekle(g)
        dugme("Yazdığım komutu gönder", false) {
            val k = g.text.toString().trim()
            if (k.isNotEmpty()) gonder(k)
        }
        ekle(cikti)
        dugme("TV'ye bağlan") { kurulumVar = true; baglanti() }
    }

    // ------------------------------------------------------- parçalar

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun ekle(v: View, alt: Int = 10) {
        val l = LinearLayout.LayoutParams(-1, -2)
        l.bottomMargin = dp(alt)
        kok.addView(v, l)
    }

    private fun temizle(ust: String, baslik: String) {
        kok.removeAllViews()
        val u = TextView(this)
        u.text = ust
        u.setTextColor(altin)
        u.textSize = 14f
        u.setTypeface(u.typeface, Typeface.BOLD)
        ekle(u, 6)
        val t = TextView(this)
        t.text = baslik
        t.setTextColor(beyaz)
        t.textSize = 28f
        t.setTypeface(t.typeface, Typeface.BOLD)
        ekle(t, 12)
    }

    private fun yazi(s: String) {
        val t = TextView(this)
        t.text = s
        t.setTextColor(gri)
        t.textSize = 16f
        ekle(t, 14)
    }

    private fun kartYazi(s: String): TextView {
        val t = TextView(this)
        t.text = s
        t.setTextColor(beyaz)
        t.textSize = 15f
        t.setPadding(dp(14), dp(12), dp(14), dp(12))
        val g = GradientDrawable()
        g.setColor(kart)
        g.cornerRadius = dp(12).toFloat()
        t.background = g
        ekle(t, 8)
        return t
    }

    private fun dugme(s: String, ana: Boolean = true, f: () -> Unit) {
        val b = Button(this)
        b.text = s
        b.isAllCaps = false
        b.textSize = 16f
        b.gravity = Gravity.CENTER
        b.setTextColor(if (ana) Color.parseColor("#1E1505") else beyaz)
        val g = GradientDrawable()
        g.setColor(if (ana) altin else kart)
        g.cornerRadius = dp(12).toFloat()
        b.background = g
        b.minHeight = dp(52)
        b.setOnClickListener { f() }
        ekle(b, 10)
    }
}
