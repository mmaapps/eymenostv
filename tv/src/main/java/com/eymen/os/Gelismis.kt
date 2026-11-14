package com.eymen.os

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val I_ZAP = "M13 2L3 14h9l-1 8 10-12h-9l1-8z"
internal const val I_LOCK = "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2zM7 11V7a5 5 0 0 1 10 0v4"
internal const val I_MOON = "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"
internal const val I_FOLDER_PLAY = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM10 11l4 2-4 2z"
internal const val I_WRENCH = "M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z"
internal const val I_GAUGE = "M12 14l4-4M3.3 19a10 10 0 1 1 17.4 0"
internal const val I_CLOUD_UP = "M17.5 19H7a5 5 0 1 1 1-9.9A6 6 0 0 1 19.5 11a4 4 0 0 1-2 8zM12 18v-6M9 15l3-3 3 3"
internal const val I_PHONE = "M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18h2"

internal data class GelKart(
    val id: Int,
    val baslik: String,
    val deger: String,
    val renk: Color,
    val ikon: String,
    val yardim: String,
)

internal fun gelKartlar(s: LauncherState): List<GelKart> {
    val p = s.prefs
    val hizli = p.getBoolean("hizliAcilis", true) && p.getBoolean("gecOnizleme", true)
    val koruyucu = p.getInt("koruyucuDk", 10)
    val yedek = p.getLong("sonYedek", 0L)
    val sonYedek = if (yedek == 0L) "Telefona yedekle" else "Son yedek: " +
        SimpleDateFormat("d MMMM HH:mm", Locale("tr", "TR")).format(Date(yedek))
    val kartlar = listOf(
        GelKart(0, "Ekran Yansıtma", "Hazır · telefondan bağlan", Color(0xFF6FA8FF),
            Yansitma.SIMGE, "TV'nin yansıtma özelliğini açar. Telefonun ekranını TV'ye yansıtırsın."),
        GelKart(1, "Hızlı panel", "Mavi tuşla aç", Color(0xFFF5B85C),
            I_ZAP, "Ses, uyku zamanlayıcı, yansıtma ve Wi-Fi tek panelde. Ana ekranda Mavi tuşla açılır."),
        GelKart(2, "Çocuk kilidi", if (p.getBoolean("kilitAcik", false)) "Açık" else "Kapalı",
            Color(0xFFFF9A9A), I_LOCK, "PIN ile uygulama ve kanal kilitle, günlük süre sınırı koy."),
        GelKart(3, "Ekran koruyucu", if (koruyucu == 0) "Kapalı" else "${koruyucu} dakika sonra",
            Color(0xFFA9B2FF), I_MOON, "Ana ekran boş kalınca saat ve hava durumu gösterir."),
        GelKart(4, "USB medya", if (s.usbVar) "USB bellek takılı" else "Takınca kendisi açılır",
            Color(0xFF8FD3A8), I_FOLDER_PLAY, "USB bellekteki video, fotoğraf ve müzikleri açar."),
        GelKart(5, "Sistem araçları", "Temizle · hız testi", Color(0xFF7FD8F0),
            I_WRENCH, "Önbelleği temizle, internet hızını ölç, depolamayı gör."),
        GelKart(6, "Hız ve performans", if (hizli) "Hızlı mod açık" else "Hızlı mod kapalı",
            Color(0xFFC6E27A), I_GAUGE, "Açılışı hızlandıran ayarlar ve son açılış süreleri."),
        GelKart(7, "Yedekle ve geri yükle", sonYedek, Color(0xFF9CC3FF),
            I_CLOUD_UP, "Kanal sırası, gizli uygulamalar ve ayarları telefona yedekler."),
        GelKart(8, "Telefondan kumanda", "TV adresini göster", Color(0xFFE6A8FF),
            I_PHONE, "Telefondaki EYMEN Kurulum ile TV'yi kumanda gibi kullan."),
    )
    return if (s.yansitma?.hazir == true) kartlar else kartlar.drop(1)
}

@Composable
internal fun GelIkon(pathString: String, boyut: Int, renk: Color) {
    val path = remember(pathString) { PathParser().parsePathString(pathString).toPath() }
    Canvas(Modifier.size(boyut.dp)) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, renk, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun AyarSekmeleri(s: LauncherState, gelismis: Boolean) {
    val odak = s.sekmeOdak
    val goruldu = s.prefs.getBoolean("gelismisGoruldu", false)
    Row(
        Modifier.padding(start = 18.dp).panel(Color(0xFF0F1830), 999, Color(0xFF26324F)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0..1) {
            val etkin = if (i == 0) !gelismis else gelismis
            val scale by animateFloatAsState(if (odak && etkin) 1.05f else 1f, label = "sekme")
            Row(
                Modifier.scale(scale)
                    .then(if (odak && etkin) Modifier.shadow(14.dp, RoundedCornerShape(999.dp)) else Modifier)
                    .panel(if (etkin) C.Gold else Color(0xFF0F1830), 999)
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(if (i == 0) "Temel" else "Gelişmiş", 14, if (etkin) C.GoldInk else C.Fg, 800)
                if (i == 1 && !goruldu && !gelismis) {
                    Box(Modifier.panel(C.Blue, 999).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        T("YENİ", 10, C.Ink, 800, spacing = .5f)
                    }
                }
            }
        }
    }
}

@Composable
private fun GelKartGorunum(kart: GelKart, secili: Boolean, modifier: Modifier) {
    val buyume by animateFloatAsState(if (secili) 1.03f else 1f, label = "kart")
    Column(
        modifier.scale(buyume)
            .then(if (secili) Modifier.shadow(18.dp, RoundedCornerShape(16.dp)) else Modifier)
            .panel(if (secili) C.Fg else Color(0xFF101A33), 16,
                if (secili) kart.renk else Color(0xFF26324F), if (secili) 3 else 1)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(50.dp).panel(kart.renk, 14), contentAlignment = Alignment.Center) {
                GelIkon(kart.ikon, 28, C.FocusInk)
            }
            if (kart.id == 0) {
                Box(Modifier.panel(Color(0xFF1B7A33), 999).padding(horizontal = 6.dp, vertical = 4.dp)) {
                    T("OTOMATİK ALGILANDI", 10, C.Fg, 800, spacing = .6f)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(kart.baslik, 19, if (secili) C.Navy else C.Fg, 600, display = true, maxLines = 2)
            T(kart.deger, 14, if (secili) C.FocusSub else C.Fg2, 600, maxLines = 2)
        }
    }
}

@Composable
private fun GelDurum(s: LauncherState) {
    val p = s.prefs
    val hizli = p.getBoolean("hizliAcilis", true) && p.getBoolean("gecOnizleme", true)
    val durumlar = listOf(
        "Ekran yansıtma" to if (s.yansitma?.hazir == true) "Hazır" else "Bu TV'de yok",
        "USB bellek" to if (s.usbVar) "Takılı" else "Takılı değil",
        "Çocuk kilidi" to if (p.getBoolean("kilitAcik", false)) "Açık" else "Kapalı",
        "Hızlı mod" to if (hizli) "Açık" else "Kapalı",
        "Son yedek" to if (p.getLong("sonYedek", 0L) > 0) {
            SimpleDateFormat("d MMMM", Locale("tr", "TR")).format(Date(p.getLong("sonYedek", 0L)))
        } else "Yok",
    )
    Column(
        Modifier.width(333.dp).fillMaxHeight().panel(Color(0xFF101A33), 16, Color(0xFF26324F))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(92.dp).panel(Color(0xFF26324F), 46), contentAlignment = Alignment.Center) {
                Box(Modifier.size(78.dp).panel(Color(0xFF101A33), 39), contentAlignment = Alignment.Center) {
                    GelIkon(I_GAUGE, 42, Color(0xFF6FA8FF))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("BU TV'DE BULUNANLAR", 12, Color(0xFF6FA8FF), 800, spacing = 1.2f)
                T("Açılışta otomatik tarandı", 22, C.Fg, 600, display = true, maxLines = 2, lineHeight = 28)
            }
        }
        for ((ad, deger) in durumlar) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF26324F)))
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                T(ad, 14, C.Mut, 600, modifier = Modifier.weight(1f))
                T(deger, 14, if (deger == "Hazır" || deger == "Açık") C.Mint else C.Fg, 800)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().panel(C.Tile, 10).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).panel(Color(0xFF6FA8FF), 4))
            T("Bulunmayan özelliğin kartı gizlenir", 13, C.Fg2, 600, maxLines = 2)
        }
    }
}

@Composable
fun GelismisScreen(s: LauncherState) {
    val kartlar = gelKartlar(s)
    val focus = s.gelIndex.coerceIn(0, (kartlar.size - 1).coerceAtLeast(0))
    Box(Modifier.fillMaxSize().background(
        Brush.radialGradient(listOf(Color(0xFF14204A), Color(0xFF070B16), C.Ink),
            center = Offset(1050f, 0f), radius = 1600f)
    )) {
        Column(
            Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 28.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).panel(C.Gold, 10), contentAlignment = Alignment.Center) {
                    T("E", 20, C.GoldInk, 700, display = true)
                }
                T("Ayarlar", 30, C.Fg, 600, display = true)
                AyarSekmeleri(s, true)
                Spacer(Modifier.weight(1f))
                T(s.dateLabel(s.now), 14, C.Mut, 500)
                T(s.time(s.now), 26, C.Fg, 500, display = true)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                GelDurum(s)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (r in 0 until 3) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            for (c in 0 until 3) {
                                val i = r * 3 + c
                                if (i < kartlar.size) {
                                    GelKartGorunum(kartlar[i], i == focus && !s.sekmeOdak,
                                        Modifier.weight(1f).fillMaxHeight())
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T(kartlar.getOrNull(focus)?.yardim ?: "", 14, C.Fg, 700, modifier = Modifier.weight(1f), maxLines = 2)
                T("▲ sekmeler", 13, C.Mut, 600)
                T("OK aç", 13, C.Mut, 600)
                T("Geri: ana ekran", 13, C.Mut, 600)
            }
        }
    }
}
