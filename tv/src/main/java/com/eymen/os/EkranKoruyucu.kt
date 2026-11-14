package com.eymen.os

import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Sehir(val ad: String, val lat: Double, val lon: Double)
data class Hava(val derece: Int, val kod: Int, val durum: String, val sehir: String)

object EkranKoruyucu {
    fun iller(ctx: Context): List<Sehir> = try {
        val data = JSONArray(ctx.assets.open("iller.json").bufferedReader().use { it.readText() })
        (0 until data.length()).map { i ->
            data.getJSONObject(i).let { Sehir(it.getString("ad"), it.getDouble("lat"), it.getDouble("lon")) }
        }
    } catch (_: Exception) { emptyList() }

    fun ac(s: LauncherState) {
        s.panelAcik = false
        s.koruyucuAcik = true
        s.preview.stop()
    }

    fun kilit(s: LauncherState, neden: String) {
        if (!s.koruyucuAcik || s.koruyucuKilit != neden) {
            s.koruyucuKilit = neden
            ac(s)
        }
    }

    fun kapat(s: LauncherState) {
        s.koruyucuAcik = false
        s.koruyucuKilit = ""
        s.sonTus = System.currentTimeMillis()
        s.resumePreview()
    }

    fun key(s: LauncherState, code: Int): Boolean {
        if (s.koruyucuKilit.isBlank()) {
            kapat(s)
            return true
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            CocukKilidi.giris(s, "PIN gir") {
                val gece = s.koruyucuKilit.startsWith("Gece kilidi")
                if (gece) s.geceIzin = true else {
                    val key = "kilitDakika_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    s.prefs.edit().putInt(key, (s.prefs.getInt(key,0) - 30).coerceAtLeast(0)).apply()
                }
                kapat(s)
            }
        }
        return true
    }

    fun ticks(s: LauncherState) {
        if (!s.koruyucuAcik && s.screen == Screen.HOME && !s.noticeVisible &&
            !s.panelAcik && !s.qrOpen) {
            val delayMs = s.prefs.getInt("koruyucuDk", 10) * 60000L
            if (delayMs > 0 && System.currentTimeMillis() - s.sonTus >= delayMs) ac(s)
        }
        if (s.koruyucuAcik) s.koruyucuKayma = ((System.currentTimeMillis() / 60000L) % 5L).toInt()
    }

    suspend fun havaGetir(s: LauncherState) = withContext(Dispatchers.IO) {
        if (!s.prefs.getBoolean("koruyucuHava", true)) {
            withContext(Dispatchers.Main) { s.koruyucuHava = null }
            return@withContext
        }
        val cities = iller(s.ctx)
        val city = cities.firstOrNull { it.ad == s.prefs.getString("koruyucuSehir", "") }
            ?: return@withContext
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=" + city.lat +
                "&longitude=" + city.lon + "&current=temperature_2m,weather_code&timezone=auto")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            try {
                val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    .getJSONObject("current")
                val code = obj.getInt("weather_code")
                val text = when(code) {
                    0 -> "Açık"
                    1 -> "Az bulutlu"
                    2 -> "Parçalı bulutlu"
                    3 -> "Kapalı"
                    45,48 -> "Sisli"
                    in 51..57 -> "Çisenti"
                    in 61..67 -> "Yağmurlu"
                    in 71..77 -> "Karlı"
                    in 80..82 -> "Sağanak"
                    in 95..99 -> "Gök gürültülü"
                    else -> "Kapalı"
                }
                val hava = Hava(obj.getDouble("temperature_2m").toInt(), code, text, city.ad)
                withContext(Dispatchers.Main) { s.koruyucuHava = hava }
            } finally { conn.disconnect() }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) { s.koruyucuHava = null }
        }
    }

    fun ayarKey(s: LauncherState, code: Int): Boolean {
        val cities = iller(s.ctx)
        val n = if (s.koruyucuSehirSecimi) cities.size else 4
        when(code) {
            KeyEvent.KEYCODE_BACK -> {
                if (s.koruyucuSehirSecimi) { s.koruyucuSehirSecimi = false; s.koruyucuIndex = 2 }
                else s.screen = Screen.GELISMIS
            }
            KeyEvent.KEYCODE_DPAD_UP -> s.koruyucuIndex = (s.koruyucuIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN ->
                s.koruyucuIndex = (s.koruyucuIndex + 1).coerceAtMost((n - 1).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!s.koruyucuSehirSecimi && s.koruyucuIndex == 0) changeTime(s,
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (s.koruyucuSehirSecimi) {
                    val city = cities.getOrNull(s.koruyucuIndex) ?: return true
                    s.prefs.edit().putString("koruyucuSehir", city.ad).apply()
                    s.koruyucuSehirSecimi = false
                    s.koruyucuIndex = 2
                    s.refreshKey++
                } else when (s.koruyucuIndex) {
                    0 -> changeTime(s, 1)
                    1 -> {
                        s.prefs.edit().putBoolean("koruyucuHava",
                            !s.prefs.getBoolean("koruyucuHava", true)).apply()
                        s.refreshKey++
                    }
                    2 -> {
                        s.koruyucuSehirSecimi = true
                        s.koruyucuIndex = cities.indexOfFirst {
                            it.ad == s.prefs.getString("koruyucuSehir", "")
                        }.coerceAtLeast(0)
                    }
                    3 -> { s.screen = Screen.HOME; ac(s) }
                }
            }
            else -> return false
        }
        return true
    }

    private fun changeTime(s: LauncherState, step: Int) {
        val values = listOf(0,5,10,15,30)
        val old = values.indexOf(s.prefs.getInt("koruyucuDk",10)).coerceAtLeast(0)
        s.prefs.edit().putInt("koruyucuDk", values[(old + step).coerceIn(0,4)]).apply()
        s.refreshKey++
    }
}

@Composable
fun EkranKoruyucuAyarScreen(s: LauncherState) {
    val dur = s.prefs.getInt("koruyucuDk",10)
    val cities = EkranKoruyucu.iller(s.ctx)
    val rows = if (s.koruyucuSehirSecimi) cities.map { GelSatir(it.ad, "", "›") } else listOf(
        GelSatir("Süre", "Ana ekran boş kalınca açılır", "◀ " +
            (if (dur == 0) "Kapalı" else dur.toString() + " dk") + " ▶"),
        GelSatir("Hava durumu", "Seçilen şehrin anlık bilgileri",
            anahtar = s.prefs.getBoolean("koruyucuHava", true)),
        GelSatir("Şehir", "Hava durumunun gösterileceği il", s.prefs.getString("koruyucuSehir", "") ?: ""),
        GelSatir("Şimdi başlat", "Ekran koruyucuyu görüntüle"),
    )
    GelListeEkrani(s, "Ekran koruyucu", I_MOON, "Ana ekran boşta kalınca açılır.",
        rows, s.koruyucuIndex)
}

@Composable
fun EkranKoruyucuScreen(s: LauncherState) {
    val now = s.now
    val offsets = listOf(IntOffset.Zero, IntOffset(24,-16), IntOffset(-24,16),
        IntOffset(24,16), IntOffset(-24,-16))
    val movement by animateIntOffsetAsState(offsets[s.koruyucuKayma.coerceIn(0,4)],
        animationSpec = tween(600), label = "yanik-koruma")
    Box(Modifier.fillMaxSize().background(
        Brush.radialGradient(listOf(Color(0xFF14204A), C.Deep, C.Ink))
    )) {
        Column(Modifier.align(Alignment.Center).offset {
            IntOffset(movement.x.dp.roundToPx(), movement.y.dp.roundToPx())
        }, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            T(SimpleDateFormat("EEEE · d MMMM", Locale("tr","TR")).format(Date(now))
                .uppercase(Locale("tr","TR")), 15, C.Gold, 800, spacing = 4f)
            T(SimpleDateFormat("HH:mm", Locale("tr","TR")).format(Date(now)),
                200, C.Fg, 300, display = true, spacing = -6f)
            if (s.koruyucuKilit.isNotBlank()) {
                T(s.koruyucuKilit, 20, C.Gold, 700)
            } else s.koruyucuHava?.let { h ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    GelIkon(if(h.kod == 0) I_SUN else I_MOON, 40, C.Gold)
                    T(h.derece.toString() + "°", 34, C.Fg, 400, display = true)
                    T(h.durum + " · " + h.sehir, 18, C.Mut, 600)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            T("EYMEN OS", 20, C.Gold, 800, modifier = Modifier.weight(1f), display = true)
            T(if (s.koruyucuKilit.isNotBlank()) "Devam etmek için OK, sonra PIN" else
                "Herhangi bir tuşa bas, kaldığın yere dön", 15, C.Fg2, 600)
            T("atasoyyazilim.com", 15, C.Gold, 600, modifier = Modifier.weight(1f))
        }
    }
}

internal const val I_SUN = "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
