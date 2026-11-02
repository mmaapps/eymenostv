package com.eymen.os

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val I_UPDATE = "M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5"
private const val I_TV = "M3 6h18v12H3zM8 21h8M12 18v3"
private const val I_WIFI = "M2 9a16 16 0 0 1 20 0M5.5 12.5a11 11 0 0 1 13 0M9 16a6 6 0 0 1 6 0M12 19.5h.01"
private const val I_SOUND = "M4 9v6h4l5 4V5L8 9zM16.5 8.5a5 5 0 0 1 0 7M19 6a9 9 0 0 1 0 12"
private const val I_POWER = "M12 3v9M6.3 6.3a8 8 0 1 0 11.4 0"
private const val I_APPS = "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"
private const val I_SLIDERS = "M4 7h10M18 7h2M4 17h2M10 17h10M14 4v6M6 14v6"
private const val I_HOME = "M3 11l9-8 9 8M5 10v10h14V10"
private const val I_INFO = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5h.01"
private const val I_CHECK = "M6 12.5l4 4 8-9"

private val nightGround = Brush.radialGradient(
    listOf(Color(0xFF14204A), Color(0xFF070B16), Color(0xFF05080F)),
    center = Offset(0f, 0f),
    radius = 2400f
)

@Composable
private fun SvgIcon(d: String, sz: Int, color: Color, width: Float = 2f) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(Modifier.size(sz.dp)) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** "M. Muhammet Atasoy" writing itself from left to right as [progress] goes from 0 to 1. */
@Composable
fun SignatureLine(progress: Float, fontSize: Int, lineWidth: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.drawWithContent {
                clipRect(right = size.width * progress) {
                    this@drawWithContent.drawContent()
                }
            }
        ) {
            BasicText(
                "M. Muhammet Atasoy",
                style = TextStyle(fontFamily = Fonts.signature(), fontSize = fontSize.sp, color = C.Gold)
            )
        }
        Canvas(Modifier.size(lineWidth.dp, 8.dp)) {
            val end = size.width * progress
            if (end > 2f) {
                drawLine(
                    Color(0x99E8C27A),
                    Offset(size.width * 0.06f, size.height / 2f),
                    Offset(size.width * 0.06f + end * 0.88f, size.height / 2f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun signatureMotion(): Pair<Float, Float> {
    val sign = remember { Animatable(0f) }
    val site = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(350L)
        sign.animateTo(1f, tween(1700, easing = LinearEasing))
        site.animateTo(1f, tween(500))
    }
    return Pair(sign.value, site.value)
}

private class HubCard(val title: String, val value: String, val color: Color, val icon: String, val help: String)

@Composable
fun HubScreen(s: LauncherState) {
    val u = s.updates
    val tick = s.refreshKey
    val waiting = u.items.isNotEmpty()
    val cards = listOf(
        HubCard("Güncelleme", if (waiting) "Yeni sürüm hazır" else "Güncel · ${BuildConfig.VERSION_NAME}", C.Gold, I_UPDATE, "Sürümü gör, şimdi denetle, otomatik güncellemeyi aç ya da kapat."),
        HubCard("Kanallar", "${s.channelCount()} kanal", Color(0xFF57C7E3), I_TV, "EYMEN Uydu'da kanal yönetimini açar: taşı, sil, logo ekle."),
        HubCard("Ağ ve Wi-Fi", if (s.online) "İnternete bağlı" else "Bağlantı yok", C.Mint, I_WIFI, "TV'nin Wi-Fi ayarlarını açar."),
        HubCard("Ses ve önizleme", if (s.soundOn) "Önizleme sesi açık" else "Önizleme sesi kapalı", Color(0xFFFFA07A), I_SOUND, "OK ile ana ekrandaki canlı pencerenin sesini aç ya da kapat."),
        HubCard("Açılış ve ana ekran", if (s.isDefaultHome()) "Varsayılan: EYMEN OS" else "Varsayılan: ${s.otherHomeLabel() ?: "seçilmedi"}", Color(0xFFB79CFF), I_POWER, "Açılışta otomatik başlatma ve varsayılan ana ekran."),
        HubCard("Uygulamalar", "${s.visibleApps().size} uygulama · ${s.hidden.size} gizli", Color(0xFF7FB2FF), I_APPS, "Gizlediğin uygulamaları geri getir."),
        HubCard("TV sistem ayarları", "TV'nin kendi menüsü", Color(0xFFFF8FB3), I_SLIDERS, "TV'nin kendi ayar menüsünü açar."),
        HubCard("Eski ana ekran", "10 dakikalığına geç", Color(0xFFF0D98C), I_HOME, "Eski ana ekrana geçici olarak döner."),
        HubCard("Hakkında", "M. Muhammet Atasoy", Color(0xFF7FE0D0), I_INFO, "Sürüm bilgisi ve imza.")
    )
    val focus = s.hubIndex.coerceIn(0, cards.size - 1)
    Box(Modifier.fillMaxSize().background(nightGround)) {
        Column(
            Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 28.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark(36)
                T("Ayarlar", 30, C.Fg, 600, display = true)
                Spacer(Modifier.weight(1f))
                T(s.dateLabel(s.now), 14, C.Mut, 500)
                T(s.time(s.now), 26, C.Fg, 500, display = true)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatusPanel(s, waiting, tick)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (r in 0 until 3) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            for (c in 0 until 3) {
                                val i = r * 3 + c
                                HubTile(cards[i], i == focus, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T(cards[focus].help, 14, C.Fg, 700, modifier = Modifier.weight(1f))
                T("OK aç", 13, C.Mut, 600)
                T("Geri: ana ekran", 13, C.Mut, 600)
            }
        }
    }
}

@Composable
private fun StatusPanel(s: LauncherState, waiting: Boolean, tick: Int) {
    val turning = rememberInfiniteTransition(label = "durum")
    val angle by turning.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "halka")
    val ring = if (waiting) C.Gold else C.Mint
    Column(
        Modifier.width(333.dp).fillMaxHeight().panel(Color(0xFF101A33), 16, Color(0xFF26324F)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) {
                    val w = 7.dp.toPx()
                    drawCircle(Color(0xFF26324F), radius = (size.minDimension - w) / 2f, style = Stroke(w))
                    drawArc(
                        ring, 0f, 110f, false,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round)
                    )
                }
                SvgIcon(if (waiting) I_UPDATE else I_CHECK, 44, ring, 2.6f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T("EYMEN OS ${BuildConfig.VERSION_NAME}", 12, ring, 800, spacing = 1.2f)
                T(if (waiting) "Güncellemeniz var" else "Her şey güncel", 22, C.Fg, 600, display = true, maxLines = 2, lineHeight = 28)
            }
        }
        val checked = s.updates.lastCheck
        val rows = listOf(
            Pair("EYMEN Uydu", s.updates.versionOf(UpdateManager.UYDU_PKG) ?: "Yüklü değil"),
            Pair("Son kontrol", if (checked > 0L) s.time(checked) else "Henüz yok"),
            Pair("İnternet", if (s.online) "Bağlı" else "Yok"),
            Pair("Kanal", "${s.channelCount()}"),
            Pair("TV", Build.MODEL ?: "")
        )
        Column {
            rows.forEach { row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF26324F)))
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(row.first, 14, C.Mut, 600, modifier = Modifier.weight(1f))
                    T(row.second, 14, C.Fg, 800)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().panel(C.Tile, 10).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).background(if (s.updates.auto) C.Mint else C.Dim, RoundedCornerShape(4.dp)))
            T(if (s.updates.auto) "Otomatik güncelleme açık" else "Otomatik güncelleme kapalı" + "".take(tick * 0), 13, C.Fg2, 600)
        }
    }
}

@Composable
private fun HubTile(card: HubCard, focused: Boolean, modifier: Modifier) {
    val grow by animateFloatAsState(if (focused) 1.03f else 1f, label = "kart")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .then(if (focused) Modifier.shadow(18.dp, shape) else Modifier)
            .panel(if (focused) C.Fg else Color(0xFF101A33), 16, if (focused) card.color else Color(0xFF26324F), if (focused) 3 else 1)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(50.dp).panel(card.color, 14), contentAlignment = Alignment.Center) {
            SvgIcon(card.icon, 28, C.FocusInk)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(card.title, 19, if (focused) C.FocusInk else C.Fg, 600, display = true)
            T(card.value, 14, if (focused) C.FocusSub else C.Fg2, 600)
        }
    }
}

@Composable
fun UpdateScreen(s: LauncherState) {
    val u = s.updates
    val waiting = u.items.isNotEmpty()
    val first = u.items.firstOrNull()
    val ringColor = when {
        u.phase == UpdatePhase.FAILED -> C.Live
        u.phase == UpdatePhase.IDLE && !waiting -> C.Mint
        u.phase == UpdatePhase.INSTALLING -> Color(0xFF57C7E3)
        else -> C.Gold
    }
    val tag: String
    val title: String
    val sub: String
    val big: String
    val fraction: Float
    when (u.phase) {
        UpdatePhase.CHECKING -> {
            tag = "DENETLENİYOR"
            title = "Yeni sürüm aranıyor"
            sub = "GitHub'daki son kararlı sürüme bakılıyor."
            big = "…"
            fraction = 0.08f
        }
        UpdatePhase.DOWNLOADING -> {
            tag = if (first != null) "${first.version} BULUNDU" else "İNDİRİLİYOR"
            title = "İndiriliyor"
            sub = u.message
            big = "%${u.percent}"
            fraction = u.percent / 100f
        }
        UpdatePhase.ALLOW -> {
            tag = "BİR KEZLİK İZİN"
            title = "TV'nin izni gerekiyor"
            sub = u.message
            big = "…"
            fraction = 1f
        }
        UpdatePhase.INSTALLING -> {
            tag = "SON ADIM"
            title = "Kuruluyor"
            sub = u.message
            big = "%100"
            fraction = 1f
        }
        UpdatePhase.FAILED -> {
            tag = "TAMAMLANAMADI"
            title = "Güncelleme yarım kaldı"
            sub = u.message
            big = "!"
            fraction = 1f
        }
        UpdatePhase.IDLE -> if (waiting) {
            tag = "YENİ SÜRÜM"
            title = "Güncellemeniz var"
            sub = "Lütfen güncelleyin. Kanallarınız ve ayarlarınız silinmez."
            big = first?.version ?: ""
            fraction = 1f
        } else {
            tag = if (u.lastCheck > 0L) "SON KONTROL ${s.time(u.lastCheck)}" else "HENÜZ DENETLENMEDİ"
            title = "EYMEN OS güncel"
            sub = "TV açılışta, internete bağlanınca ve 6 saatte bir yeni sürüme bakar."
            big = BuildConfig.VERSION_NAME
            fraction = 1f
        }
    }
    val button = when {
        u.busy() -> "Bekle"
        waiting -> "Şimdi güncelle"
        else -> "Şimdi denetle"
    }
    Box(Modifier.fillMaxSize().background(nightGround)) {
        Column(
            Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).panel(C.Gold, 13), contentAlignment = Alignment.Center) {
                    SvgIcon(I_UPDATE, 26, C.FocusInk, 2.2f)
                }
                Column {
                    T("Ayarlar", 12, C.Mut, 700)
                    T("Güncelleme", 26, C.Fg, 600, display = true)
                }
                Spacer(Modifier.weight(1f))
                T(s.time(s.now), 26, C.Fg, 500, display = true)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        Modifier.weight(1f).fillMaxWidth().panel(Color(0xFF101A33), 16, Color(0xFF26324F)).padding(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val w = 13.dp.toPx()
                                drawCircle(Color(0xFF26324F), radius = (size.minDimension - w) / 2f, style = Stroke(w))
                                drawArc(
                                    ringColor, -90f, 360f * fraction.coerceIn(0f, 1f), false,
                                    topLeft = Offset(w / 2f, w / 2f),
                                    size = Size(size.width - w, size.height - w),
                                    style = Stroke(w, cap = StrokeCap.Round)
                                )
                            }
                            T(big, 36, C.Fg, 600, display = true)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            T(tag, 13, ringColor, 800, spacing = 1.5f)
                            T(title, 30, C.Fg, 600, display = true, maxLines = 2, lineHeight = 37)
                            T(sub, 16, C.Fg2, 500, maxLines = 3, lineHeight = 23)
                            Spacer(Modifier.height(4.dp))
                            val focused = s.updateRow == 0
                            Box(
                                Modifier.height(48.dp)
                                    .panel(if (focused) C.Fg else C.Tile, 14, if (focused) null else C.Line)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                T(button, 16, if (focused) C.FocusInk else C.Fg, 800)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AppLine("EYMEN OS", s, "com.eymen.os", Modifier.weight(1f))
                        AppLine("EYMEN Uydu", s, UpdateManager.UYDU_PKG, Modifier.weight(1f))
                    }
                    val focused = s.updateRow == 1
                    Row(
                        Modifier.fillMaxWidth().height(62.dp)
                            .panel(if (focused) C.Fg else Color(0xFF101A33), 14, if (focused) null else Color(0xFF26324F))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            T("Otomatik güncelle", 16, if (focused) C.FocusInk else C.Fg, 800)
                            T("Yayın izlerken kesmez, ana ekrandayken kurar", 13, if (focused) C.FocusSub else C.Fg2, 500)
                        }
                        Box(
                            Modifier.size(60.dp, 32.dp).panel(if (u.auto) C.Mint else Color(0xFF3A4A72), 16).padding(4.dp),
                            contentAlignment = if (u.auto) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(Modifier.size(24.dp).background(Color.White, RoundedCornerShape(12.dp)))
                        }
                    }
                }
                Column(
                    Modifier.width(400.dp).fillMaxHeight().panel(Color(0xFF101A33), 16, Color(0xFF26324F)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    T("BU SÜRÜMDE", 12, C.Gold, 800, spacing = 1.5f)
                    if (u.items.isEmpty()) {
                        T("Yeni sürüm çıkınca notları burada görünür.", 15, C.Fg2, 500, maxLines = 3, lineHeight = 22)
                    }
                    u.items.toList().forEach { item ->
                        T("${item.label} ${item.version}", 20, C.Fg, 600, display = true)
                        item.notes.forEach { note ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Box(Modifier.padding(top = 7.dp).size(7.dp).background(C.Gold, RoundedCornerShape(4.dp)))
                                T(note, 15, C.Fg2, 600, maxLines = 3, lineHeight = 21)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLine(label: String, s: LauncherState, pkg: String, modifier: Modifier) {
    val item = s.updates.items.firstOrNull { it.pkg == pkg }
    val installed = s.updates.versionOf(pkg)
    Row(
        modifier.height(66.dp).panel(Color(0xFF101A33), 14, Color(0xFF26324F)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandMark(36)
        Column {
            T(label, 16, C.Fg, 800)
            T(
                when {
                    item != null -> "${installed ?: "Yüklü değil"} → ${item.version}"
                    installed != null -> "$installed · Güncel"
                    else -> "Yüklü değil"
                },
                13, C.Fg2, 600
            )
        }
    }
}

@Composable
fun AboutScreen(s: LauncherState) {
    val (sign, site) = signatureMotion()
    Box(Modifier.fillMaxSize().background(nightGround), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandMark(92)
            Row {
                T("EYMEN ", 44, C.Fg, 600, display = true, spacing = 6f)
                T("OS", 44, C.Gold, 600, display = true, spacing = 6f)
            }
            T("Sürüm ${BuildConfig.VERSION_NAME}", 16, C.Mut, 600)
            Spacer(Modifier.height(8.dp))
            SignatureLine(sign, 52, 400)
            Box(Modifier.alpha(site)) {
                T("Atasoy Yazılım · atasoyyazilim.com", 17, C.Mut, 500, spacing = 1f)
            }
            Spacer(Modifier.height(10.dp))
            T("Geri: Ayarlar" + "".take(s.hubIndex * 0), 13, C.Dim, 600)
        }
    }
}

/** Comes up by itself on the home screen when a new version is found. */
@Composable
fun UpdateNotice(s: LauncherState) {
    val (sign, site) = signatureMotion()
    val u = s.updates
    Box(Modifier.fillMaxSize().background(Color(0xD905080F)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(680.dp)
                .shadow(30.dp, RoundedCornerShape(24.dp))
                .panel(Color(0xFC0A1224), 24, C.Gold, 2)
                .padding(horizontal = 34.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrandMark(44)
            T(u.items.joinToString(" · ") { "${it.label} ${it.version}" }.uppercase(java.util.Locale("tr", "TR")), 13, C.Gold, 800, spacing = 1.5f)
            T("Güncellemeniz var", 36, C.Fg, 600, display = true)
            T("Lütfen güncelleyin. Kanallarınız ve ayarlarınız silinmez.", 16, C.Fg2, 500, align = TextAlign.Center, maxLines = 2)
            u.items.flatMap { it.notes }.take(3).forEach { note ->
                T(note, 14, C.Mut, 500, align = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            SignatureLine(sign, 44, 340)
            Box(Modifier.alpha(site)) {
                T("atasoyyazilim.com", 17, C.Gold, 700, spacing = 1f)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NoticeButton("Şimdi güncelle", s.noticeIndex == 0, Modifier.weight(1f))
                NoticeButton("Sonra", s.noticeIndex == 1, Modifier.weight(1f))
            }
            if (u.auto) {
                T("Dokunmazsan ${s.noticeLeft} saniye sonra kendiliğinden güncellenir", 13, C.Mut, 500)
            }
        }
    }
}

@Composable
private fun NoticeButton(label: String, focused: Boolean, modifier: Modifier) {
    Box(
        modifier.height(50.dp).panel(if (focused) C.Fg else Color(0x14D6E2FF), 13, if (focused) null else C.Line),
        contentAlignment = Alignment.Center
    ) {
        T(label, 16, if (focused) C.FocusInk else C.Fg, 800)
    }
}

/** The TV has no browser, so the website opens on the phone. */
@Composable
fun QrOverlay(s: LauncherState) {
    Box(Modifier.fillMaxSize().background(Color(0xDB05080F)), contentAlignment = Alignment.Center) {
        Row(
            Modifier.width(680.dp).panel(Color(0xFF101A33), 18, C.Gold, 2).padding(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(Modifier.size(270.dp).panel(Color.White, 12).padding(14.dp), contentAlignment = Alignment.Center) {
                val qr = s.siteQr
                if (qr != null) {
                    Image(bitmap = qr, contentDescription = "atasoyyazilim.com QR kodu", modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("ATASOY YAZILIM", 13, C.Gold, 800, spacing = 2f)
                T("Telefonla okut, siteyi aç", 28, C.Fg, 600, display = true, maxLines = 2, lineHeight = 36)
                T("Telefon kamerasını kareye tut. atasoyyazilim.com telefonunda açılır.", 16, C.Fg2, 500, maxLines = 3, lineHeight = 23)
                Box(Modifier.height(46.dp).panel(C.Fg, 12).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    T("Kapat", 16, C.FocusInk, 800)
                }
            }
        }
    }
}
