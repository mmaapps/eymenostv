package com.eymen.os

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

data class YansitmaSonuc(
    val wifiDirect: Boolean,
    val uygulama: String?,
    val sistemMenusu: String?,
    val googleCast: Boolean,
) {
    val hazir get() = uygulama != null || sistemMenusu != null || googleCast

    fun json(): String = JSONObject().put("wifiDirect", wifiDirect)
        .put("uygulama", uygulama ?: "")
        .put("sistemMenusu", sistemMenusu ?: "")
        .put("googleCast", googleCast).toString()

    companion object {
        fun kayit(raw: String?): YansitmaSonuc? = try {
            if (raw.isNullOrBlank()) null else JSONObject(raw).let {
                YansitmaSonuc(
                    it.optBoolean("wifiDirect"),
                    it.optString("uygulama").ifBlank { null },
                    it.optString("sistemMenusu").ifBlank { null },
                    it.optBoolean("googleCast"),
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

object Yansitma {
    const val SIMGE = "M2 16.1A5 5 0 0 1 5.9 20M2 12.05A9 9 0 0 1 9.95 20M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6M2 20h.01"
    private val kelimeler = listOf(
        "miracast", "wfd", "wifidisplay", "screenmirror", "mirroring", "ekran paylaş",
        "ekran yansıt", "yansıtma", "smart view", "screen share", "screenshare",
    )

    suspend fun tara(ctx: Context): YansitmaSonuc = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val donanim = try {
            pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        } catch (_: Exception) {
            false
        }
        val tr = Locale("tr", "TR")
        val bulunan = ArrayList<Pair<String, String>>()
        for (kategori in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(kategori)
                for (ri in pm.queryIntentActivities(intent, 0)) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    val etiket = ri.loadLabel(pm).toString()
                    if (pkg.startsWith("com.eymen.") ||
                        pkg == "com.google.android.apps.mediashell" ||
                        pkg == "com.google.android.youtube.tv" ||
                        etiket.lowercase(tr).contains("podcast")) continue
                    val aranan = (pkg + " " + etiket).lowercase(tr)
                    if (kelimeler.any { aranan.contains(it) }) bulunan.add(etiket to pkg)
                }
            } catch (_: Exception) {
            }
        }
        val uygulama = bulunan.sortedWith(compareBy({ it.first.lowercase(tr) }, { it.second }))
            .firstOrNull()?.second
        val menu = listOf(
            "android.settings.CAST_SETTINGS",
            "android.settings.WIFI_DISPLAY_SETTINGS",
        ).firstOrNull { action ->
            try {
                Intent(action).resolveActivity(pm) != null
            } catch (_: Exception) {
                false
            }
        }
        val google = try {
            pm.getPackageInfo("com.google.android.apps.mediashell", 0)
            true
        } catch (_: Exception) {
            false
        }
        YansitmaSonuc(donanim, uygulama, menu, google)
    }

    fun cihazAdi(ctx: Context): String = try {
        Settings.Global.getString(ctx.contentResolver, "device_name")
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL
    } catch (_: Exception) {
        Build.MODEL
    }

    fun ac(s: LauncherState) {
        val r = s.yansitma ?: return
        val pm = s.ctx.packageManager
        val intent = try {
            r.uygulama?.let {
                pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it)
            } ?: r.sistemMenusu?.let { Intent(it) }
        } catch (_: Exception) {
            null
        }
        if (intent != null) {
            s.openMirrorIntent(intent)
        } else if (r.googleCast) {
            s.toast("Bu TV Google Cast ile yansıtılır · Telefonda Google Home › Ekranımı yayınla", 7000L)
        }
    }

    fun adiDegistir(s: LauncherState) {
        val pm = s.ctx.packageManager
        val intent = listOf(
            "android.settings.DEVICE_NAME",
            Settings.ACTION_DEVICE_INFO_SETTINGS,
            Settings.ACTION_SETTINGS,
        ).map { Intent(it) }.firstOrNull {
            try { it.resolveActivity(pm) != null } catch (_: Exception) { false }
        }
        if (intent != null) s.openMirrorIntent(intent)
    }
}

@Composable
private fun YansitmaIkon(boyut: Int, renk: Color) {
    val path = remember { PathParser().parsePathString(Yansitma.SIMGE).toPath() }
    Canvas(Modifier.size(boyut.dp)) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(
                path, renk,
                style = Stroke(width = 2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun YansitmaDugme(metin: String, secili: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.height(46.dp)
            .then(if (secili) Modifier.shadow(14.dp, RoundedCornerShape(13.dp)) else Modifier)
            .panel(if (secili) C.Fg else Color(0xFF17213D), 13,
                if (secili) C.Fg else Color(0xFF3A4A72))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(metin, 15, if (secili) C.Navy else C.Fg, 800)
    }
}

@Composable
private fun YansitmaBulgusu(metin: String, deger: String, varMi: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).panel(if (varMi) C.Mint else Color(0xFF3A4459), 13), contentAlignment = Alignment.Center) {
            T(if (varMi) "✓" else "−", 15, if (varMi) C.Navy else C.Fg2, 800)
        }
        T(metin, 14, C.Fg2, 700, modifier = Modifier.weight(1f))
        T(deger, 14, if (varMi) C.Mint else C.Mut, 800)
    }
}

@Composable
private fun YansitmaAdimi(numara: String, metin: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).panel(C.Gold, 8), contentAlignment = Alignment.Center) {
            T(numara, 14, C.GoldInk, 700, display = true)
        }
        T(metin, 14, C.Fg2, 600, modifier = Modifier.weight(1f), maxLines = 3, lineHeight = 20)
    }
}

@Composable
fun YansitmaScreen(s: LauncherState) {
    val r = s.yansitma
    val adi = Yansitma.cihazAdi(s.ctx)
    val kisiSamsung = s.yansitmaTur == 0
    val sadeceCast = r?.googleCast == true && r.uygulama == null && r.sistemMenusu == null
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF14204A), Color(0xFF070B16), C.Ink),
                center = Offset(1050f, 0f), radius = 1600f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 28.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(36.dp).panel(C.Gold, 10), contentAlignment = Alignment.Center) {
                    T("E", 20, C.GoldInk, 700, display = true)
                }
                T("Ekran Yansıtma", 30, C.Fg, 600, display = true)
                T("Ayarlar › Gelişmiş", 13, C.Mut, 600, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                T(s.dateLabel(s.now), 14, C.Mut, 500)
                T(s.time(s.now), 26, C.Fg, 500, display = true)
            }
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().panel(Color(0xFF101A33), 18, Color(0xFF26324F))
                        .padding(horizontal = 32.dp, vertical = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        Box(Modifier.size(150.dp).border(1.dp, Color(0x306FA8FF), RoundedCornerShape(75.dp)),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(118.dp).border(1.dp, Color(0x596FA8FF), RoundedCornerShape(59.dp)),
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.size(84.dp).panel(Color(0xFF6FA8FF), 24), contentAlignment = Alignment.Center) {
                                    YansitmaIkon(46, C.Navy)
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (r?.hazir == true) T("HAZIR · BAĞLANTI BEKLENİYOR", 12, C.Mint, 800, spacing = 2f)
                            T("Telefonunu TV'ye yansıt", 32, C.Fg, 600, display = true, maxLines = 2, lineHeight = 40)
                            T("Telefonda bu adı seç:", 15, C.Mut, 500)
                            T(adi, 22, C.Gold, 600, display = true)
                            YansitmaDugme("Adı değiştir", s.yansitmaOdak == 3)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        YansitmaDugme("Yansıtmayı başlat", s.yansitmaOdak == 0)
                        YansitmaDugme(
                            if (s.yansitmaKisayol) "Kısayolu kaldır" else "Ana ekrana kısayol",
                            s.yansitmaOdak == 1,
                        )
                        YansitmaDugme("Yeniden tara", s.yansitmaOdak == 2)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(
                            Modifier.weight(1f).panel(C.Tile, 12).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            T("AÇILINCA", 12, C.Mut, 700, spacing = 1f)
                            T("Canlı önizleme durur, ses karışmaz", 15, C.Fg, 700, maxLines = 2)
                        }
                        Column(
                            Modifier.weight(1f).panel(C.Tile, 12).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            T("BİTİNCE", 12, C.Mut, 700, spacing = 1f)
                            T("Geri tuşuyla EYMEN OS'a dönülür", 15, C.Fg, 700, maxLines = 2)
                        }
                    }
                }
                Column(
                    Modifier.width(430.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().panel(Color(0xFF101A33), 18, Color(0xFF26324F))
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    ) {
                        T("Algılama sonucu", 18, C.Fg, 600, display = true)
                        YansitmaBulgusu("Wi-Fi Direct donanımı", if (r?.wifiDirect == true) "Var" else "Yok", r?.wifiDirect == true)
                        YansitmaBulgusu("TV'nin yansıtma uygulaması", if (r?.uygulama != null) "Bulundu" else "Yok", r?.uygulama != null)
                        YansitmaBulgusu("Sistem yansıtma menüsü", if (r?.sistemMenusu != null) "Var" else "Yok", r?.sistemMenusu != null)
                        YansitmaBulgusu("Google Cast alıcısı", if (r?.googleCast == true) "Var" else "Yok", r?.googleCast == true)
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f).panel(Color(0xFF101A33), 18, Color(0xFF26324F))
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T("Telefondan bağlan", 18, C.Fg, 600, display = true, modifier = Modifier.weight(1f))
                            Box(Modifier.panel(if (s.yansitmaOdak == 4 && kisiSamsung) C.Fg else if (kisiSamsung) C.Gold else C.Tile, 999).padding(horizontal = 9.dp, vertical = 5.dp)) {
                                T("Samsung", 12, if (kisiSamsung) C.GoldInk else C.Fg2, 800)
                            }
                            Box(Modifier.panel(if (s.yansitmaOdak == 4 && !kisiSamsung) C.Fg else C.Tile, 999, C.FocusSub).padding(horizontal = 9.dp, vertical = 5.dp)) {
                                T("Diğer Android", 12, if (s.yansitmaOdak == 4 && !kisiSamsung) C.GoldInk else C.Fg2, 800)
                            }
                        }
                        YansitmaAdimi("1", "Telefonla TV aynı Wi-Fi ağında olsun.")
                        YansitmaAdimi("2", if (sadeceCast) {
                            "Google Home uygulamasında Ekranımı yayınla'ya dokun."
                        } else if (kisiSamsung) {
                            "Ekranın üstünden aşağı kaydır, Smart View düğmesine dokun."
                        } else {
                            "Hızlı ayarlarda Yansıtma, Ekran yansıtma ya da Cast düğmesine dokun."
                        })
                        YansitmaAdimi("3", "Listeden ${adi}'nu seç, TV'de çıkan onayı OK ile kabul et.")
                    }
                }
            }
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T("TV'nin kendi yansıtma ekranını açar. Bağlantı bitince EYMEN OS'a geri dönülür.",
                    14, C.Fg, 700, modifier = Modifier.weight(1f))
                T("OK seç", 13, C.Mut, 600)
                T("Geri: Gelişmiş", 13, C.Mut, 600)
            }
        }
    }
}
