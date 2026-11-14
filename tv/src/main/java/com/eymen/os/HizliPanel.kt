package com.eymen.os

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HizliPanel {
    val sureler = listOf(0,15,30,60,90)

    fun key(s: LauncherState, key: Int): Boolean {
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_PROG_BLUE) {
            s.panelAcik = false
            return true
        }
        val audio = s.ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when(key) {
            KeyEvent.KEYCODE_DPAD_UP -> s.panelIndex = when(s.panelIndex) {
                0 -> 0; 7 -> 5; else -> if(s.panelIndex <= 2) 0 else s.panelIndex - 2
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> s.panelIndex = when(s.panelIndex) {
                0 -> 1; in 1..4 -> s.panelIndex + 2; else -> 7
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val diff = if(key == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                when(s.panelIndex) {
                    0 -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        if(diff > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
                    7 -> s.panelUykuIndex = (s.panelUykuIndex + diff).coerceIn(0, sureler.lastIndex)
                    else -> s.panelIndex = (s.panelIndex + diff).coerceIn(1,6)
                }
                s.refreshKey++
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> when(s.panelIndex) {
                0 -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                1 -> s.panelIndex = 7
                2 -> if(s.yansitma?.hazir == true) { s.panelAcik = false; Yansitma.ac(s) }
                3 -> {
                    val actions = listOf("android.settings.PICTURE_SETTINGS",
                        Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)
                    val intent = actions.map { Intent(it) }.firstOrNull {
                        try { it.resolveActivity(s.ctx.packageManager) != null } catch (_: Exception) { false }
                    }
                    if(intent != null) { s.panelAcik = false; s.openMirrorIntent(intent) }
                }
                4 -> {
                    s.panelAcik = false
                    s.openMirrorIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
                5 -> { s.panelAcik = false; EkranKoruyucu.ac(s) }
                6 -> {
                    CocukKilidi.giris(s, "PIN gir") {
                        s.prefs.edit().putBoolean("kilitAcik", !s.prefs.getBoolean("kilitAcik", false)).apply()
                        s.panelAcik = false
                        s.refreshKey++
                    }
                }
                7 -> {
                    val dakika = sureler[s.panelUykuIndex]
                    s.uykuBitis = if(dakika == 0) 0L else System.currentTimeMillis() + dakika * 60000L
                    s.prefs.edit().putLong("uykuBitis", s.uykuBitis).apply()
                    s.refreshKey++
                }
            }
            else -> return false
        }
        return true
    }

    suspend fun tick(s: LauncherState) {
        val end = s.uykuBitis
        if(end <= 0) return
        val remains = end - System.currentTimeMillis()
        if(remains in 1..60000 && !s.uykuUyarildi) {
            s.uykuUyarildi = true
            s.toast("TV 1 dakika sonra uyku moduna geçecek · Mavi tuş › Uyku › Kapalı ile iptal",8000L)
        }
        if(remains > 0) return
        s.uykuBitis = 0L
        s.uykuUyarildi = false
        s.prefs.edit().putLong("uykuBitis", 0L).apply()
        if(s.updates.busy()) {
            s.toast("Güncelleme sürüyor, biraz sonra dene")
            return
        }
        val result = withContext(Dispatchers.IO) { YerelAdb.shell(s.ctx, "input keyevent 223") }
        if(result == null) s.toast("TV kapatılamadı · ADB izni yok")
    }
}

@Composable
private fun PanelKart(baslik: String, deger: String, icon: String, focused: Boolean,
                      accent: Color, modifier: Modifier) {
    Column(
        modifier.height(106.dp).then(if(focused) Modifier.shadow(18.dp, RoundedCornerShape(14.dp)) else Modifier)
            .panel(if(focused) C.Fg else C.Tile, 14, if(focused) C.Fg else Color(0xFF26324F))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        GelIkon(icon, 26, if(focused) C.Navy else accent)
        T(baslik, 15, if(focused) C.Navy else C.Fg, 800, maxLines = 1)
        T(deger, 12, if(focused) C.FocusSub else C.Mut, 600, maxLines = 1)
    }
}

@Composable
fun HizliPanelScreen(s: LauncherState) {
    val audio = s.ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val percent = currentVolume * 100 / maximum
    val end = s.uykuBitis
    val sleep = if(end <= 0L) "Kapalı" else
        ((end - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toString() + " dk kaldı"
    val connected = try {
        val manager = s.ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.activeNetwork != null
    } catch (_: Exception) { false }
    val tiles = listOf(
        Triple("Uyku zamanlayıcı",sleep,I_TIMER),
        Triple("Ekran yansıtma",if(s.yansitma?.hazir == true) "Hazır" else "Bu TV'de yok",Yansitma.SIMGE),
        Triple("Görüntü ayarı","TV menüsünü açar",I_GAUGE),
        Triple("Wi-Fi",if(connected) "Bağlı" else "Bağlantı yok",I_WIFI_PANEL),
        Triple("Ekran koruyucu","Şimdi başlat",I_MOON),
        Triple("Çocuk modu",if(s.prefs.getBoolean("kilitAcik",false)) "Açık" else "Kapalı",I_LOCK),
    )
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
        listOf(Color(0x0005080F), Color(0x0005080F), Color(0xD905080F)),
        startX = 384f, endX = 1120f
    ))) {
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 18.dp, top = 18.dp, bottom = 18.dp)
                .width(440.dp).fillMaxHeight().panel(Color(0xF5070B16),22,Color(0x4DE8C27A))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Hızlı panel", 26, C.Fg, 600, display = true, modifier = Modifier.weight(1f))
                T(s.time(s.now), 22, C.Gold, 600, display = true)
            }
            Column(
                Modifier.fillMaxWidth()
                    .panel(if(s.panelIndex == 0) C.Fg else C.Tile, 14).padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Ses", 17, if(s.panelIndex == 0) C.Navy else C.Fg, 700,
                        modifier = Modifier.weight(1f))
                    T(percent.toString() + "%", 18, if(s.panelIndex == 0) C.Navy else C.Gold, 800)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).panel(Color(0xFF3A4459),999)) {
                    Box(Modifier.fillMaxWidth(percent.toFloat() / 100f).height(8.dp).panel(C.Gold,999))
                }
                T("◀ ▶ ile ayarla · OK sessiz", 12, if(s.panelIndex == 0) C.FocusSub else C.Mut,600)
            }
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { col ->
                        val i = row * 2 + col
                        val tile = tiles[i]
                        PanelKart(tile.first,tile.second,tile.third,s.panelIndex == i + 1,
                            if(i == 1) C.Mint else C.Gold,Modifier.weight(1f))
                    }
                }
            }
            T("Uyku zamanlayıcı", 15, C.Fg, 800)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for((i,n) in HizliPanel.sureler.withIndex()) {
                    val focussed = s.panelIndex == 7 && s.panelUykuIndex == i
                    Box(Modifier.weight(1f).height(34.dp)
                        .panel(if(focussed) C.Gold else C.Tile,999),contentAlignment = Alignment.Center) {
                        T(if(n==0) "Kapalı" else n.toString()+" dk", 12,
                            if(focussed) C.GoldInk else C.Fg2,700)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).panel(C.Blue,2))
                T("  Mavi tuş: aç / kapat", 13, C.Fg2, 600, modifier = Modifier.weight(1f))
                T("Geri: kapat", 13, C.Mut, 600)
            }
        }
    }
}
internal const val I_TIMER = "M12 5a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM12 9v4l2 2M9 2h6"
internal const val I_WIFI_PANEL = "M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01"
