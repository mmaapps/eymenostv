package com.eymen.os

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class SistemBilgi(
    val toplam: Long, val dolu: Long,
    val uygulamalar: Long, val onbellek: Long, val diger: Long, val izin: Boolean
)

object Sistem {
    private fun fmt(b: Long): String = String.format(Locale("tr","TR"), "%.1f", b.toDouble() / 1073741824.0)
    fun mb(b: Long): String = (b / 1048576L).toString() + " MB"

    suspend fun olc(s: LauncherState) = withContext(Dispatchers.IO) {
        val disk = StatFs(Environment.getDataDirectory().path)
        val total = disk.blockCountLong * disk.blockSizeLong
        val avail = disk.availableBlocksLong * disk.blockSizeLong
        val used = (total - avail).coerceAtLeast(0L)
        var appBytes = 0L
        var cacheBytes = 0L
        var allowed = false
        try {
            val stats = s.ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val data = stats.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
            appBytes = (data.appBytes + data.dataBytes - data.cacheBytes).coerceAtLeast(0L)
            cacheBytes = data.cacheBytes
            allowed = true
        } catch (_: Exception) {
            if(!s.prefs.getBoolean("depoIzinDenendi",false) && !s.updates.busy()) {
                s.prefs.edit().putBoolean("depoIzinDenendi",true).apply()
                YerelAdb.shell(s.ctx, "appops set com.eymen.os GET_USAGE_STATS allow")
            }
        }
        val value = SistemBilgi(total,used,appBytes,cacheBytes,
            (used-appBytes-cacheBytes).coerceAtLeast(0L),allowed)
        withContext(Dispatchers.Main) { s.sistemBilgi = value }
    }

    private suspend fun temizle(s: LauncherState) {
        if(s.updates.busy()) { s.toast("Güncelleme sürüyor, biraz sonra dene"); return }
        s.temizleniyor = true
        val old = s.sistemBilgi?.onbellek ?: 0L
        val result = withContext(Dispatchers.IO) { YerelAdb.shell(s.ctx, "pm trim-caches 999G") }
        olc(s)
        s.temizleniyor = false
        if(result == null) s.toast("Temizlenemedi · ADB izni yok")
        else s.toast(mb((old - (s.sistemBilgi?.onbellek ?: old)).coerceAtLeast(0L)) + " boşaltıldı")
    }

    private suspend fun speed(s: LauncherState) {
        s.hizOlculuyor = true
        val r = withContext(Dispatchers.IO) {
            try {
                val delays = ArrayList<Long>()
                repeat(3) {
                    val start = System.nanoTime()
                    val c = URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection
                    c.connectTimeout = 10000
                    c.readTimeout = 10000
                    try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
                    delays.add((System.nanoTime() - start) / 1000000)
                }
                val delayMs = if(delays.isEmpty()) 0L else delays.average().toLong()
                val start = System.nanoTime()
                val c = URL("https://speed.cloudflare.com/__down?bytes=50000000").openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 10000
                var total = 0L
                try {
                    c.inputStream.use { stream ->
                        val buf = ByteArray(65536)
                        while(System.nanoTime() - start < 10000000000L) {
                            val read = stream.read(buf)
                            if(read < 0) break
                            total += read
                        }
                    }
                } finally { c.disconnect() }
                val seconds = ((System.nanoTime() - start) / 1_000_000_000.0).coerceAtLeast(.001)
                Pair((total * 8 / seconds / 1000000.0).toInt(),delayMs)
            } catch (_: Exception) { null }
        }
        s.hizDeger = r?.first
        s.gecikme = r?.second
        s.hizOlculuyor = false
    }

    private fun altSistem(s: LauncherState, value: String) {
        val yeni = !s.prefs.getBoolean(value, if(value=="hafifAnimasyon") false else true)
        s.prefs.edit().putBoolean(value,yeni).apply()
        s.refreshKey++
    }

    fun key(s: LauncherState, key: Int): Boolean {
        when(key) {
            KeyEvent.KEYCODE_BACK -> s.screen = Screen.GELISMIS
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if(s.sistemIndex in 1..2) s.sistemIndex--
                else if(s.sistemIndex == 6) s.sistemIndex = 5
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if(s.sistemIndex in 0..1) s.sistemIndex++
                else if(s.sistemIndex in 3..5) s.sistemIndex = 6
            }
            KeyEvent.KEYCODE_DPAD_UP -> s.sistemIndex = if(s.sistemIndex < 3) s.sistemIndex else
                (s.sistemIndex - 3).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> s.sistemIndex = when(s.sistemIndex) {
                in 0..2 -> 3; in 3..4 -> s.sistemIndex + 1; else -> s.sistemIndex
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> when(s.sistemIndex) {
                0 -> s.openMirrorIntent(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                1 -> s.launchTask { temizle(s) }
                2 -> if(!s.hizOlculuyor) s.launchTask { speed(s) }
                3 -> altSistem(s,"hizliAcilis")
                4 -> altSistem(s,"gecOnizleme")
                5 -> altSistem(s,"hafifAnimasyon")
                6 -> {
                    try {
                        val manager = s.ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        for(pkg in s.ctx.packageManager.getInstalledApplications(0)) {
                            if(!pkg.packageName.startsWith("com.eymen.")) {
                                try { manager.killBackgroundProcesses(pkg.packageName) } catch (_: Exception) {}
                            }
                        }
                        s.toast("Arka plandaki uygulamalar kapatıldı")
                    } catch (_: Exception) {}
                }
            }
            else -> return false
        }
        return true
    }

    suspend fun derle(s: LauncherState) {
        delay(120000L)
        if(s.updates.busy()) return
        if(s.prefs.getString("derlendi", "") == BuildConfig.VERSION_NAME) return
        val result = YerelAdb.shell(s.ctx, "cmd package compile -m speed-profile -f com.eymen.os", 180000)
        if(result != null) s.prefs.edit().putString("derlendi", BuildConfig.VERSION_NAME).apply()
    }

    fun acilis(s: LauncherState) {
        val value = (android.os.SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()).coerceAtLeast(0L)
        val old = (s.prefs.getString("acilisSureleri","") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }.takeLast(4)
        s.prefs.edit().putString("acilisSureleri",(old+value).joinToString(",")).apply()
        s.refreshKey++
    }

    fun sureler(s: LauncherState): List<Long> = (s.prefs.getString("acilisSureleri","") ?: "")
        .split(",").mapNotNull { it.toLongOrNull() }.takeLast(5)

    fun gb(v: Long) = fmt(v) + " GB"
}

@Composable
private fun SistemKart(s: LauncherState, title: String, value: String, note: String,
                       index: Int, accent: Color, modifier: Modifier) {
    val active = s.sistemIndex == index
    Column(
        modifier.height(145.dp).panel(if(active) C.Fg else C.Tile,16,
            if(active) C.Fg else Color(0xFF26324F)).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        T(title,16,if(active) C.Navy else C.Fg,800)
        T(value,31,if(active) C.Navy else accent,700,display=true)
        T(note,12,if(active) C.FocusSub else C.Mut,600,maxLines=2)
    }
}

@Composable
fun SistemScreen(s: LauncherState) {
    val data = s.sistemBilgi
    val values = Sistem.sureler(s)
    val fastest = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val buttons = listOf(
        Triple("Hızlı açılış","Simgeler ve logolar hafızada tutulur, ana ekran anında dolar","hizliAcilis"),
        Triple("Önizlemeyi sonra başlat","Canlı pencere ana ekran açıldıktan sonra başlar","gecOnizleme"),
        Triple("Hafif animasyonlar","Yavaş TV'lerde dönen dişli ve büyüme efektlerini azaltır","hafifAnimasyon"),
    )
    Column(Modifier.fillMaxSize().background(Color(0xFF070B16))
        .padding(horizontal = 64.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Sistem araçları",30,C.Fg,600,display=true,modifier=Modifier.weight(1f))
            T(s.time(s.now),26,C.Fg,500,display=true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SistemKart(s,"Depolama", if(data==null) "—" else
                Sistem.gb(data.dolu) + " / " + Sistem.gb(data.toplam),
                if(data?.izin == false) "Dağılım için izin gerekli" else "Uygulamalar · Önbellek · Diğer",
                0,Color(0xFF7FB2FF),Modifier.weight(1f))
            SistemKart(s,"Önbellek",if(s.temizleniyor) "Temizleniyor…" else if(data?.izin==true)
                Sistem.mb(data.onbellek) else "—", "OK: Temizle",1,C.Gold,Modifier.weight(1f))
            SistemKart(s,"İnternet hızı",if(s.hizOlculuyor) "Ölçülüyor…" else if(s.hizDeger==null)
                "Testi başlat" else s.hizDeger.toString()+" Mbps",
                if(s.gecikme == null) "4K yayın için 25 Mbps önerilir" else "Gecikme " + s.gecikme + " ms",
                2,Color(0xFF7FD8F0),Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Uygulamalar: "+(if(data?.izin==true) Sistem.gb(data.uygulamalar) else "—"),13,C.Mut,600)
            T("Önbellek: "+(if(data?.izin==true) Sistem.gb(data.onbellek) else "—"),13,C.Mut,600)
            T("Diğer: "+(if(data?.izin==true) Sistem.gb(data.diger) else "—"),13,C.Mut,600)
        }
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().panel(Color(0xFF101A33),18,Color(0xFF26324F))
                .padding(18.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Hız ve performans",22,C.Fg,700,display=true)
                for((i,button) in buttons.withIndex()) {
                    val checked = s.prefs.getBoolean(button.third, i != 2)
                    Row(Modifier.fillMaxWidth().height(66.dp)
                        .panel(if(s.sistemIndex == i+3) C.Fg else C.Tile,12).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(button.first,15,if(s.sistemIndex==i+3) C.Navy else C.Fg,800)
                            T(button.second,12,if(s.sistemIndex==i+3) C.FocusSub else C.Mut,600)
                        }
                        T(if(checked) "Açık" else "Kapalı",14,if(s.sistemIndex==i+3) C.Navy else C.Gold,800)
                    }
                }
                Box(Modifier.fillMaxWidth().height(60.dp)
                    .panel(if(s.sistemIndex == 6) C.Fg else C.Tile,12).padding(15.dp),
                    contentAlignment = Alignment.Center) {
                    T("Arka plandaki uygulamaları kapat",15,
                        if(s.sistemIndex==6) C.Navy else C.Fg,800)
                }
            }
            Column(Modifier.width(350.dp).fillMaxHeight()
                .panel(Color(0xFF101A33),18,Color(0xFF26324F)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("Son 5 açılış",20,C.Fg,700,display=true)
                Row(Modifier.fillMaxWidth().height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom) {
                    for((i,v) in values.withIndex()) {
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height((70f*v/fastest).toInt().coerceAtLeast(3).dp)
                                .panel(if(i==values.lastIndex) C.Mint else Color(0xFF3A4459),7))
                            T(String.format(Locale("tr","TR"),"%.1f",v/1000.0)+" sn",11,C.Mut,600)
                        }
                    }
                }
                T("Son açılış · yeşil çubuk",13,C.Mut,600)
            }
        }
        T("OK seç  ·  Geri: Gelişmiş",13,C.Mut,600)
    }
}
