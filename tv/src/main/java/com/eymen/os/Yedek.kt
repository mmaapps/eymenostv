package com.eymen.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Yedek {
    val fields = listOf(
        "hiddenApps","appOrder","sound","autoStart","last","kilitAcik",
        "kilitliUygulamalar","kilitliKanallar","kilitSure","geceKilidi",
        "kilitTuz","kilitPin","koruyucuDk","koruyucuHava","koruyucuSehir",
        "hizliAcilis","gecOnizleme","hafifAnimasyon","yansitmaKisayol",
    )
    private val sets = setOf("hiddenApps","kilitliUygulamalar","kilitliKanallar")
    private val strings = setOf("appOrder","last","kilitTuz","kilitPin","koruyucuSehir")
    private val integers = setOf("kilitSure","koruyucuDk")
    private val bools = fields.toSet() - sets - strings - integers

    fun dosya(ctx: Context): File = File(ctx.getExternalFilesDir(null),"yedek.json")

    fun olustur(ctx: Context): Boolean = try {
        val prefs = ctx.getSharedPreferences(LauncherState.PREFS,Context.MODE_PRIVATE)
        val payload = JSONObject()
        for (field in fields) {
            if (!prefs.contains(field)) continue
            when(field) {
                in sets -> payload.put(field, JSONArray(prefs.getStringSet(field,emptySet()).orEmpty().toList()))
                in strings -> payload.put(field,prefs.getString(field,"") ?: "")
                in integers -> payload.put(field,prefs.getInt(field,0))
                in bools -> payload.put(field,prefs.getBoolean(field,false))
            }
        }
        val now = System.currentTimeMillis()
        val root = JSONObject()
            .put("surum",1)
            .put("uygulama","1.2.0")
            .put("tarih",now)
            .put("tv",Build.MODEL)
            .put("ayarlar",payload)
        dosya(ctx).writeText(root.toString(2))
        prefs.edit().putLong("sonYedek",now).apply()
        true
    } catch (_: Exception) { false }

    fun yukle(ctx: Context): Boolean = try {
        val json = JSONObject(dosya(ctx).readText())
        if (json.optInt("surum") != 1) false else {
            val fieldsJson = json.getJSONObject("ayarlar")
            val prefs = ctx.getSharedPreferences(LauncherState.PREFS,Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for (field in fields) {
                if (!fieldsJson.has(field) || fieldsJson.isNull(field)) continue
                when(field) {
                    in sets -> {
                        val values = fieldsJson.getJSONArray(field)
                        val set = (0 until values.length()).map { values.getString(it) }.toSet()
                        editor.putStringSet(field,set)
                    }
                    in strings -> editor.putString(field,fieldsJson.getString(field))
                    in integers -> editor.putInt(field,fieldsJson.getInt(field))
                    in bools -> editor.putBoolean(field,fieldsJson.getBoolean(field))
                }
            }
            editor.apply()
            val intent = Intent(ctx,HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ctx.startActivity(intent)
            true
        }
    } catch (_: Exception) { false }

    fun sifirla(ctx: Context) {
        ctx.getSharedPreferences(LauncherState.PREFS,Context.MODE_PRIVATE).edit()
            .remove("kilitPin").remove("kilitTuz").remove("kilitAcik").apply()
    }
}

class YedekReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action) {
            "com.eymen.os.YEDEK_AL" -> Yedek.olustur(context)
            "com.eymen.os.YEDEK_YUKLE" -> Yedek.yukle(context)
            "com.eymen.os.PIN_SIFIRLA" -> Yedek.sifirla(context)
        }
    }
}

object YedekMenusu {
    fun key(s: LauncherState, code: Int): Boolean {
        when(code) {
            KeyEvent.KEYCODE_BACK -> s.screen = Screen.GELISMIS
            KeyEvent.KEYCODE_DPAD_UP -> s.yedekIndex = (s.yedekIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> s.yedekIndex = (s.yedekIndex + 1).coerceAtMost(2)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> if(s.yedekIndex == 1) {
                if(Yedek.olustur(s.ctx)) {
                    s.toast("Yedek hazır · telefondan al")
                    s.refreshKey++
                } else s.toast("Yedek alınamadı")
            }
            else -> return false
        }
        return true
    }
}

@Composable
fun YedekScreen(s: LauncherState) {
    val timestamp = s.prefs.getLong("sonYedek",0L)
    val last = if(timestamp == 0L) "Yok" else
        SimpleDateFormat("d MMMM HH:mm",Locale("tr","TR")).format(Date(timestamp))
    val rows = listOf(
        GelSatir("Son yedek","TV'de hazırlanmış en son yedek",last),
        GelSatir("TV'de yedek hazırla","EYMEN Kurulum ile telefona aktarın"),
        GelSatir("Yedekte neler var","Uygulama sırası, gizli uygulamalar, kilit, ekran koruyucu ve hız ayarları"),
    )
    GelListeEkrani(s,"Yedekle ve geri yükle",I_CLOUD_UP,
        "Yedekleme telefondaki EYMEN Kurulum ile yapılır",rows,s.yedekIndex)
}
