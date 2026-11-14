package com.eymen.kurulum

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.util.Base64
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class YedekEkrani(
    private val act: Activity, private val tv: Tv,
    private val geri: () -> Unit, private val kumandayaGec: () -> Unit
) {
    private val worker = Executors.newSingleThreadExecutor()
    private val klasor: File get() = File(act.filesDir,"yedekler")
    private var liste: LinearLayout? = null
    private var status: TextView? = null

    private fun message(text: String) {
        act.runOnUiThread {
            status?.text = text
            Toast.makeText(act,text,Toast.LENGTH_LONG).show()
        }
    }

    private fun action(command: String) = "am broadcast -a com.eymen.os." + command +
        " -n com.eymen.os/.YedekReceiver"

    private fun yedekle() {
        worker.execute {
            try {
                if(tv.ip.isBlank()) throw IllegalStateException("TV bağlı değil")
                tv.komut(action("YEDEK_AL"))
                Thread.sleep(1500L)
                val raw = tv.komut("cat /sdcard/Android/data/com.eymen.os/files/yedek.json").trim()
                if(!raw.startsWith("{")) throw IllegalStateException("Yedek alınamadı")
                val json = org.json.JSONObject(raw)
                if(json.optInt("surum") != 1) throw IllegalStateException("Yedek alınamadı")
                if(!klasor.exists() && !klasor.mkdirs()) throw IllegalStateException("Yedek alınamadı")
                val name = "yedek-" + SimpleDateFormat("yyyyMMdd-HHmm",Locale.US)
                    .format(Date()) + ".json"
                File(klasor,name).writeText(raw)
                message("Yedek alındı")
                act.runOnUiThread { doldur() }
            } catch (_: Throwable) { message("Yedek alınamadı") }
        }
    }

    private fun yukle(file: File) {
        AlertDialog.Builder(act).setTitle("Yedeği geri yükle")
            .setMessage("Bu yedek TV'ye yüklensin mi?")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Yükle") { _,_ ->
                worker.execute {
                    try {
                        val raw = file.readText()
                        if(!raw.trim().startsWith("{") ||
                            org.json.JSONObject(raw).optInt("surum") != 1) throw IllegalStateException()
                        val b64 = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8),Base64.NO_WRAP)
                        val dest = "/sdcard/Android/data/com.eymen.os/files/yedek.json"
                        tv.komut("echo " + b64 + " | base64 -d > " + dest)
                        tv.komut(action("YEDEK_YUKLE"))
                        message("Yedek geri yüklendi")
                    } catch (_: Throwable) { message("Yedek geri yüklenemedi") }
                }
            }.show()
    }

    private fun pinSifirla() {
        AlertDialog.Builder(act).setTitle("Çocuk kilidi")
            .setMessage("Çocuk kilidi PIN'i sıfırlansın mı?")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Sıfırla") { _,_ ->
                worker.execute {
                    try {
                        if(tv.ip.isBlank()) throw IllegalStateException()
                        tv.komut(action("PIN_SIFIRLA"))
                        message("Çocuk kilidi PIN'i sıfırlandı")
                    } catch (_: Throwable) { message("PIN sıfırlanamadı") }
                }
            }.show()
    }

    private fun doldur() {
        val dst = liste ?: return
        dst.removeAllViews()
        val files = klasor.listFiles { f -> f.isFile && f.name.matches(Regex("yedek-[0-9]{8}-[0-9]{4}\\.json")) }
            ?.sortedByDescending { it.name }.orEmpty()
        if(files.isEmpty()) {
            KumandaStil.add(act,dst,KumandaStil.label(act,"Henüz kayıtlı yedek yok",14f,KumandaStil.ikincil))
        } else for(file in files) {
            val name = file.name.removePrefix("yedek-").removeSuffix(".json")
            val date = try {
                val d = SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).parse(name) ?: Date(file.lastModified())
                SimpleDateFormat("d MMMM yyyy HH:mm",Locale("tr","TR")).format(d)
            } catch (_: Throwable) { name }
            KumandaStil.add(act,dst,KumandaStil.button(act,date) { yukle(file) })
        }
    }

    fun gorunum(): View {
        val a = act
        val sv = ScrollView(a).apply {
            setBackgroundColor(Color.parseColor(KumandaStil.zemin))
            fitsSystemWindows = true
        }
        val root = KumandaStil.column(a).apply {
            setPadding(KumandaStil.dp(a,18),KumandaStil.dp(a,24),
                KumandaStil.dp(a,18),KumandaStil.dp(a,30))
        }
        sv.addView(root)
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        head.addView(KumandaStil.button(a,"‹",height=42) { geri() },
            LinearLayout.LayoutParams(KumandaStil.dp(a,54),-2))
        head.addView(KumandaStil.label(a,"Yedek",25f,KumandaStil.yazi,true),
            LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = KumandaStil.dp(a,16) })
        KumandaStil.add(a,root,head,8)
        status = KumandaStil.label(a,if(tv.ip.isBlank()) "Bağlı değil · Komut merkezinden bağlan"
            else tv.ip + " bağlı",14f,KumandaStil.ikincil)
        KumandaStil.add(a,root,status!!,16)

        val tabs = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(KumandaStil.button(a,"Kumanda") { kumandayaGec() },
            LinearLayout.LayoutParams(0,-2,1f))
        tabs.addView(KumandaStil.button(a,"Yedek",true) {},
            LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = KumandaStil.dp(a,8) })
        KumandaStil.add(a,root,tabs,18)

        KumandaStil.add(a,root,KumandaStil.label(a,"TV yedekleme",20f,KumandaStil.yazi,true))
        KumandaStil.add(a,root,KumandaStil.label(a,
            "Uygulama sırası, gizli uygulamalar, kilit ve ekran koruyucu ayarları.",
            14f,KumandaStil.ikincil),12)
        KumandaStil.add(a,root,KumandaStil.button(a,"TV'yi yedekle",true,55) { yedekle() },20)
        KumandaStil.add(a,root,KumandaStil.label(a,"Kayıtlı yedekler",20f,KumandaStil.yazi,true))
        liste = KumandaStil.column(a)
        KumandaStil.add(a,root,liste!!,22)
        doldur()
        KumandaStil.add(a,root,KumandaStil.button(a,"Çocuk kilidi PIN'ini sıfırla") {
            pinSifirla()
        })
        return sv
    }

    fun kapat() { worker.shutdownNow() }
}
