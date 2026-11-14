package com.eymen.os

import android.util.Base64
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PinIstek(val baslik: String, val yeni: Boolean, val onaylandi: () -> Unit) {
    var giris by mutableStateOf("")
    var ilk by mutableStateOf("")
    var asama by mutableIntStateOf(0)
    var odak by mutableIntStateOf(0)
    var yanlis by mutableIntStateOf(0)
    var bekleBitis by mutableLongStateOf(0L)
}

object CocukKilidi {
    private fun hash(tuz: ByteArray, pin: String) = MessageDigest.getInstance("SHA-256")
        .digest(tuz + pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun pinVar(s: LauncherState) = !s.prefs.getString("kilitPin", "").isNullOrBlank()
    private fun dogru(s: LauncherState, pin: String): Boolean = try {
        val tuz = Base64.decode(s.prefs.getString("kilitTuz", "") ?: "", Base64.DEFAULT)
        val expected = s.prefs.getString("kilitPin", "") ?: ""
        tuz.size == 16 && MessageDigest.isEqual(hash(tuz, pin).toByteArray(), expected.toByteArray())
    } catch (_: Exception) { false }

    private fun sakla(s: LauncherState, pin: String) {
        val tuz = ByteArray(16).also { SecureRandom().nextBytes(it) }
        s.prefs.edit().putString("kilitTuz", Base64.encodeToString(tuz, Base64.NO_WRAP))
            .putString("kilitPin", hash(tuz, pin)).apply()
        s.refreshKey++
    }

    fun giris(s: LauncherState, title: String, action: () -> Unit) {
        s.pinIstek = PinIstek(title, !pinVar(s), action)
    }

    fun ayar(s: LauncherState) {
        if (s.kilitOturumu) { s.kilitListeTur = 0; s.kilitIndex = 0; s.screen = Screen.KILIT; return }
        giris(s, if (pinVar(s)) "PIN gir" else "Yeni PIN belirle") {
            s.kilitOturumu = true; s.kilitListeTur = 0; s.kilitIndex = 0; s.screen = Screen.KILIT
        }
    }

    fun pinKey(s: LauncherState, code: Int): Boolean {
        val p = s.pinIstek ?: return false
        if (code == KeyEvent.KEYCODE_BACK) { s.pinIstek = null; return true }
        if (System.currentTimeMillis() < p.bekleBitis) return true
        val digit = when (code) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
            else -> null
        }
        when {
            digit != null -> ekle(s, p, digit.toString())
            code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL -> p.giris = p.giris.dropLast(1)
            code == KeyEvent.KEYCODE_DPAD_LEFT -> p.odak = (p.odak - 1).coerceAtLeast(0)
            code == KeyEvent.KEYCODE_DPAD_RIGHT -> p.odak = (p.odak + 1).coerceAtMost(11)
            code == KeyEvent.KEYCODE_DPAD_UP -> p.odak = (p.odak - 3).coerceAtLeast(0)
            code == KeyEvent.KEYCODE_DPAD_DOWN -> p.odak = (p.odak + 3).coerceAtMost(11)
            code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER -> when (p.odak) {
                9 -> p.giris = p.giris.dropLast(1)
                10 -> ekle(s, p, "0")
                11 -> if (p.giris.length == 4) tamam(s, p)
                else -> ekle(s, p, (p.odak + 1).toString())
            }
        }
        return true
    }

    private fun ekle(s: LauncherState, p: PinIstek, n: String) {
        if (p.giris.length < 4) {
            p.giris += n
            if (p.giris.length == 4) tamam(s, p)
        }
    }

    private fun tamam(s: LauncherState, p: PinIstek) {
        val value = p.giris
        if (value.length != 4) return
        p.giris = ""
        if (p.yeni) {
            if (p.asama == 0) { p.ilk = value; p.asama = 1 }
            else if (p.ilk == value) { sakla(s, value); s.pinIstek = null; p.onaylandi() }
            else { p.asama = 0; p.ilk = ""; s.toast("PIN'ler aynı değil, yeniden dene") }
        } else if (dogru(s, value)) { s.pinIstek = null; p.onaylandi() }
        else {
            p.yanlis++
            if (p.yanlis >= 3) {
                p.yanlis = 0
                p.bekleBitis = System.currentTimeMillis() + 60000L
            }
        }
    }

    fun uygulamaKilitli(s: LauncherState, pkg: String) =
        s.prefs.getBoolean("kilitAcik", false) &&
            s.prefs.getStringSet("kilitliUygulamalar", emptySet()).orEmpty().contains(pkg)

    fun kanalKilitli(s: LauncherState, ch: Ch?) = ch != null &&
        s.prefs.getBoolean("kilitAcik", false) &&
        s.prefs.getStringSet("kilitliKanallar", emptySet()).orEmpty().contains(ch.key)

    fun koruma(s: LauncherState, baslik: String, action: () -> Unit): Boolean {
        if (!pinVar(s)) return false
        giris(s, "Kilitli: " + baslik, action)
        return true
    }

    fun key(s: LauncherState, code: Int): Boolean {
        val options = listOf(0, 30, 60, 90, 120, 180, 240)
        val apps = s.visibleApps().filter { it.pkg != "eymen:yansitma" }
        val channels = s.tumKanallar()
        val size = when(s.kilitListeTur) { 1 -> apps.size; 2 -> channels.size; else -> 6 }
        when(code) {
            KeyEvent.KEYCODE_BACK -> {
                if (s.kilitListeTur == 0) s.screen = Screen.GELISMIS
                else { s.kilitListeTur = 0; s.kilitIndex = 0 }
            }
            KeyEvent.KEYCODE_DPAD_UP -> s.kilitIndex = (s.kilitIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> s.kilitIndex = (s.kilitIndex + 1).coerceAtMost((size - 1).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (s.kilitListeTur == 0 && s.kilitIndex == 3) {
                val i = options.indexOf(s.prefs.getInt("kilitSure", 0)).coerceAtLeast(0)
                val step = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                s.prefs.edit().putInt("kilitSure", options[(i + step).coerceIn(0, options.lastIndex)]).apply()
                s.refreshKey++
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (s.kilitListeTur == 1) {
                    val pkg = apps.getOrNull(s.kilitIndex)?.pkg ?: return true
                    val set = s.prefs.getStringSet("kilitliUygulamalar", emptySet()).orEmpty().toMutableSet()
                    if (!set.add(pkg)) set.remove(pkg)
                    s.prefs.edit().putStringSet("kilitliUygulamalar", set).apply()
                    s.refreshKey++
                } else if (s.kilitListeTur == 2) {
                    val key = channels.getOrNull(s.kilitIndex)?.key ?: return true
                    val set = s.prefs.getStringSet("kilitliKanallar", emptySet()).orEmpty().toMutableSet()
                    if (!set.add(key)) set.remove(key)
                    s.prefs.edit().putStringSet("kilitliKanallar", set).apply()
                    s.refreshKey++
                } else when(s.kilitIndex) {
                    0, 4 -> {
                        val field = if(s.kilitIndex == 0) "kilitAcik" else "geceKilidi"
                        s.prefs.edit().putBoolean(field, !s.prefs.getBoolean(field, false)).apply()
                        s.refreshKey++
                    }
                    1, 2 -> { s.kilitListeTur = s.kilitIndex; s.kilitIndex = 0 }
                    3 -> {
                        val i = options.indexOf(s.prefs.getInt("kilitSure", 0)).coerceAtLeast(0)
                        s.prefs.edit().putInt("kilitSure", options[(i + 1) % options.size]).apply()
                        s.refreshKey++
                    }
                    5 -> giris(s, "PIN gir") {
                        s.pinIstek = PinIstek("Yeni PIN belirle", true) { s.kilitOturumu = true }
                    }
                }
            }
            else -> return false
        }
        return true
    }

    fun dakika(s: LauncherState) {
        if (!s.prefs.getBoolean("kilitAcik", false)) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (s.prefs.getBoolean("geceKilidi", false) && (hour >= 22 || hour < 7) && !s.geceIzin) {
            EkranKoruyucu.kilit(s, "Gece kilidi · 07:00'de açılır")
            return
        }
        val power = s.ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!power.isInteractive) return
        val key = "kilitDakika_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val editor = s.prefs.edit()
        for (old in s.prefs.all.keys) if (old.startsWith("kilitDakika_") && old != key) editor.remove(old)
        val elapsed = s.prefs.getInt(key, 0) + 1
        editor.putInt(key, elapsed).apply()
        val limit = s.prefs.getInt("kilitSure", 0)
        if (limit > 0 && elapsed >= limit) EkranKoruyucu.kilit(s, "Bugünkü süre doldu")
    }
}

@Composable
fun PinPanel(s: LauncherState) {
    val p = s.pinIstek ?: return
    val blocked = System.currentTimeMillis() < p.bekleBitis
    Box(Modifier.fillMaxSize().background(Color(0xE605080F)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(380.dp).shadow(24.dp, RoundedCornerShape(18.dp))
                .panel(Color(0xFF101A33), 18, Color(0xFF26324F)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(64.dp).panel(C.Tile, 18), contentAlignment = Alignment.Center) {
                GelIkon(I_LOCK, 32, C.Gold)
            }
            val title = if(blocked) "Bekle: " + ((p.bekleBitis - System.currentTimeMillis() + 999) / 1000) + " sn"
                else if (p.yeni && p.asama == 1) "PIN'i tekrar gir" else p.baslik
            T(title, 25, C.Fg, 600, display = true)
            T("Kumandadaki rakamlarla da yazılır", 13, C.Mut, 600)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(18.dp).panel(if (i < p.giris.length) C.Gold else C.Tile,
                        999, if (i < p.giris.length) C.Gold else Color(0xFF3A4A72), 2))
                }
            }
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Sil", "0", "Tamam")
            repeat(4) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { c ->
                        val i = r * 3 + c
                        val active = p.odak == i
                        Box(Modifier.size(96.dp, 46.dp)
                            .then(if(active) Modifier.shadow(12.dp, RoundedCornerShape(10.dp)) else Modifier)
                            .panel(if(active) C.Fg else C.Tile, 10, Color(0xFF3A4A72)),
                            contentAlignment = Alignment.Center) {
                            T(keys[i], 18, if(active) C.Navy else C.Fg, 800)
                        }
                    }
                }
            }
            T("3 yanlış denemede 1 dakika bekletir", 13, C.Mut, 600)
        }
    }
}

@Composable
fun CocukKilidiScreen(s: LauncherState) {
    val apps = s.visibleApps().filter { it.pkg != "eymen:yansitma" }
    val channels = s.tumKanallar()
    val lockedApps = s.prefs.getStringSet("kilitliUygulamalar", emptySet()).orEmpty()
    val lockedChannels = s.prefs.getStringSet("kilitliKanallar", emptySet()).orEmpty()
    val times = mapOf(0 to "Sınırsız", 30 to "30 dk", 60 to "1 saat", 90 to "1,5 saat",
        120 to "2 saat", 180 to "3 saat", 240 to "4 saat")
    val rows = when(s.kilitListeTur) {
        1 -> apps.map { GelSatir(it.label, it.pkg, if(it.pkg in lockedApps) "Kilitli" else "Açık") }
        2 -> channels.map { GelSatir(it.number + " · " + it.name, "", if(it.key in lockedChannels) "Kilitli" else "Açık") }
        else -> listOf(
            GelSatir("Çocuk kilidi", "Açıkken kilitli uygulama ve kanallar PIN ister", anahtar = s.prefs.getBoolean("kilitAcik", false)),
            GelSatir("Kilitli uygulamalar", "Ana ekranda kilit simgesiyle görünür", lockedApps.size.toString() + " kilitli · + Ekle"),
            GelSatir("Kilitli kanallar", "Ana ekranda ve önizlemede PIN ister", lockedChannels.size.toString() + " kilitli · + Kanal seç"),
            GelSatir("Günlük süre sınırı", "Süre dolunca ekran koruyucu açılır", "◀ " + (times[s.prefs.getInt("kilitSure",0)] ?: "Sınırsız") + " ▶"),
            GelSatir("Gece kilidi", "22:00 – 07:00 arası TV PIN ister", anahtar = s.prefs.getBoolean("geceKilidi", false)),
            GelSatir("PIN'i değiştir", "Unutursan EYMEN Kurulum'dan sıfırlanır"),
        )
    }
    GelListeEkrani(s, "Çocuk kilidi", I_LOCK, "PIN ile uygulama ve kanal kilitle, günlük süre sınırı koy.", rows, s.kilitIndex,
        "◀ ▶ ile süreyi değiştir. Değişiklikler PIN girildikten sonra kaydedilir.")
}
