package com.eymen.os

import android.content.Context
import android.net.wifi.WifiManager
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import java.util.Locale

object KumandaBilgi {
    fun ip(ctx: Context): String = try {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val address = wifi.connectionInfo.ipAddress
        if (address == 0) "Bağlantı yok" else
            (0..3).joinToString(".") { ((address shr (it * 8)) and 0xFF).toString() }
    } catch (_: Exception) { "Bağlantı yok" }

    fun key(s: LauncherState, code: Int): Boolean {
        when(code) {
            KeyEvent.KEYCODE_BACK -> s.screen = Screen.GELISMIS
            KeyEvent.KEYCODE_DPAD_UP -> s.kumandaIndex = (s.kumandaIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> s.kumandaIndex = (s.kumandaIndex + 1).coerceAtMost(2)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {}
            else -> return false
        }
        return true
    }
}

@Composable
fun KumandaBilgiScreen(s: LauncherState) {
    val rows = listOf(
        GelSatir("TV adresi","EYMEN Kurulum'da bu adresle bağlan",KumandaBilgi.ip(s.ctx)),
        GelSatir("Bağlantı noktası","TV'ye ADB bağlantısı","5555"),
        GelSatir("Telefonla TV aynı Wi-Fi ağında olsun",
            "İlk bağlantıda TV'de çıkan izni Her zaman izin ver ile onayla"),
    )
    GelListeEkrani(s,"Telefondan kumanda",I_PHONE,
        "Telefonda EYMEN Kurulum › Kumanda sekmesini aç",rows,s.kumandaIndex)
}
