package com.eymen.os

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max

private const val HERO_X = 64
private const val HERO_Y = 80
private const val HERO_W = 498
private const val HERO_H = 280
private val trLocale = Locale("tr", "TR")

fun Modifier.panel(color: Color, radius: Int, border: Color? = null, borderWidth: Int = 1): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    var m = this.clip(shape).background(color, shape)
    if (border != null) m = m.border(borderWidth.dp, border, shape)
    return m
}

@Composable
fun LauncherRoot(s: LauncherState) {
    DesignCanvas {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val videoModifier = Modifier.offset(HERO_X.dp, HERO_Y.dp).size(HERO_W.dp, HERO_H.dp)
            AndroidView(factory = { s.preview.tvView }, modifier = videoModifier)
            AndroidView(factory = { s.preview.texture }, modifier = videoModifier)
            when (s.screen) {
                Screen.HOME -> HomeScreen(s)
                Screen.HUB -> HubScreen(s)
                Screen.SETUP -> SetupScreen(s)
                Screen.UPDATE -> UpdateScreen(s)
                Screen.ABOUT -> AboutScreen(s)
            }
            if (s.qrOpen) QrOverlay(s)
            if (s.noticeVisible) UpdateNotice(s)
            val t = s.toastText
            if (t != null) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 30.dp)
                        .panel(Color(0xF00A1224), 999, Color(0x40E8C27A)).padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandMark(18)
                    T(t, 16, C.Fg, 600)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(s: LauncherState) {
    val now = s.now
    val cur = s.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF14204A), Color(0xFF070B16), Color(0xFF05080F)),
                    center = Offset(size.width * 0.85f, 0f),
                    radius = size.width
                )
            )
            drawRoundRect(
                Color.Transparent,
                topLeft = Offset(HERO_X.dp.toPx(), HERO_Y.dp.toPx()),
                size = Size(HERO_W.dp.toPx(), HERO_H.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
                blendMode = BlendMode.Clear
            )
        }

        // Overlay on the live window: channel and programme guide
        Box(
            Modifier.offset(HERO_X.dp, HERO_Y.dp).size(HERO_W.dp, HERO_H.dp)
                .then(if (s.row == 1) Modifier.border(3.dp, C.Fg, RoundedCornerShape(4.dp)) else Modifier.border(1.dp, Color(0x29D6E2FF), RoundedCornerShape(4.dp)))
        ) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x0005080F), Color(0xE605080F))))
            )
            if (cur == null) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("Kanal bulunamadı", 18, C.Fg, 700)
                    T("EYMEN Uydu'yu bir kez açınca kanallar burada görünür.", 13, C.Mut, 500, maxLines = 2, align = TextAlign.Center)
                }
            } else {
                if (s.preview.status.isNotEmpty()) {
                    Box(Modifier.align(Alignment.Center).panel(Color(0xCC0A1224), 12).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        T(s.preview.status, 14, C.Fg, 600)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(cur.number, 14, C.Gold, 700, display = true)
                        T(cur.name, 14, C.Fg, 800, modifier = Modifier.weight(1f, fill = false))
                        LiveBadge()
                    }
                    val p = cur.now
                    if (p != null) {
                        T(p.title, 18, C.Fg, 600, display = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Progress(p.progress(now), Modifier.weight(1f), height = 4)
                            T("${s.time(p.start)} – ${s.time(p.end)}", 12, C.Fg2, 600)
                        }
                        val n = cur.next
                        if (n != null) T("Sonra ${s.time(n.start)} · ${n.title}", 12, C.Mut, 500)
                    } else {
                        T(if (cur.isWeb) "İnternet yayını" else "Yayın akışı bilgisi yok", 14, C.Mut, 500)
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 22.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TopBar(s, now)

            Row(Modifier.height(HERO_H.dp)) {
                Spacer(Modifier.width((HERO_W + 32).dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                    if (cur != null) {
                        val p = cur.now
                        T(
                            if (p != null) "ŞİMDİ YAYINDA · ${s.time(p.start)} – ${s.time(p.end)}" else "ŞİMDİ YAYINDA · ${cur.number} · ${cur.name.uppercase(trLocale)}",
                            12, C.Gold, 800, spacing = 2f
                        )
                        T(p?.title ?: cur.name, 34, C.Fg, 600, display = true, maxLines = 2, lineHeight = 40)
                        val n = cur.next
                        T(
                            if (n != null) "Sonra ${s.time(n.start)} · ${n.title}" else "OK ile EYMEN Uydu'da tam ekran açılır · CH+/CH− ile önizlemede kanal değiştir",
                            14, C.Mut, 500, maxLines = 2
                        )
                    } else {
                        T("EYMEN OS", 12, C.Gold, 800, spacing = 2f)
                        T("Hoş geldin", 34, C.Fg, 600, display = true)
                        T("Canlı yayın için EYMEN Uydu'yu bir kez aç.", 14, C.Mut, 500)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                        s.heroActions.forEachIndexed { i, label ->
                            HeroButton(label, s.row == 1 && s.col == i, i == 0)
                        }
                    }
                }
            }

            if (s.channels.isNotEmpty()) ChannelsRow(s, now)
            AppsRow(s)
            AdStrip(s)
        }
    }
}

@Composable
private fun TopBar(s: LauncherState, now: Long) {
    Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark(34)
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            T("EYMEN OS", 19, C.Fg, 600, display = true, spacing = 3f)
            T("atasoyyazilim.com", 11, C.Gold, 600, spacing = 0.5f)
        }
        Spacer(Modifier.weight(1f))
        T(s.dateLabel(now), 14, C.Mut, 500)
        Spacer(Modifier.width(14.dp))
        T(s.time(now), 26, C.Fg, 500, display = true)
        Spacer(Modifier.width(18.dp))
        SettingsPill(s)
    }
}

/** The way into the settings: the gear keeps turning, the line below it tells if an update waits. */
@Composable
private fun SettingsPill(s: LauncherState) {
    val focused = s.row == 0
    val waiting = s.updates.items.isNotEmpty()
    val motion = rememberInfiniteTransition(label = "ayarlar")
    val turn by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "dişli")
    val beat by motion.animateFloat(1f, 0.35f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "nabız")
    val statusColor = if (waiting) (if (focused) Color(0xFF8A5A00) else C.Gold) else (if (focused) Color(0xFF1B7A33) else C.Mint)
    Row(
        Modifier.height(42.dp)
            .scale(if (focused) 1.06f else 1f)
            .then(if (focused) Modifier.shadow(14.dp, RoundedCornerShape(21.dp)) else Modifier)
            .panel(if (focused) C.Fg else C.Tile, 21, if (focused) null else C.Gold, 2)
            .padding(start = 4.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(32.dp).panel(C.Gold, 16), contentAlignment = Alignment.Center) {
            Box(Modifier.graphicsLayer { rotationZ = turn }) {
                GearIcon(21, C.FocusInk)
            }
        }
        Column {
            T("Ayarlar", 14, if (focused) C.FocusInk else C.Fg, 800)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).graphicsLayer { alpha = beat }.background(statusColor, RoundedCornerShape(3.dp)))
                T(if (waiting) "Güncelleme var" else "Güncel", 11, statusColor, 700)
            }
        }
    }
}

/** The advert for the website along the bottom. OK opens a big QR code, the TV has no browser. */
@Composable
private fun AdStrip(s: LauncherState) {
    val focused = s.row == 4
    val messages = s.adMessages.toList()
    var i by remember { mutableStateOf(0) }
    LaunchedEffect(messages.size) {
        while (true) {
            delay(6000L)
            if (messages.isNotEmpty()) i = (i + 1) % messages.size
        }
    }
    val ink = if (focused) C.FocusInk else C.Fg
    Row(
        Modifier.fillMaxWidth().height(40.dp)
            .then(if (focused) Modifier.shadow(14.dp, RoundedCornerShape(11.dp)) else Modifier)
            .panel(if (focused) C.Fg else Color(0xFF101A33), 11, if (focused) null else Color(0xFF8A744C))
            .padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(27.dp).panel(C.Gold, 7), contentAlignment = Alignment.Center) {
            T("A", 15, C.GoldInk, 700, display = true)
        }
        T("Atasoy Yazılım", 14, ink, 800)
        Box(Modifier.size(1.dp, 16.dp).background(if (focused) C.FocusSub else Color(0xFF3A4A72)))
        Crossfade(targetState = messages.getOrElse(i) { "" }, modifier = Modifier.weight(1f), label = "reklam") { m ->
            T(m, 14, if (focused) C.FocusSub else C.Fg2, 600)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            messages.indices.forEach { k ->
                Box(Modifier.size(6.dp).background(if (k == i) C.Gold else Color(0xFF3A4A72), RoundedCornerShape(3.dp)))
            }
        }
        T("atasoyyazilim.com", 14, if (focused) C.FocusInk else C.Gold, 800)
        Box(Modifier.panel(C.Gold, 6).padding(horizontal = 8.dp, vertical = 3.dp)) {
            T("OK: QR kod", 10, C.GoldInk, 800)
        }
    }
}

@Composable
private fun HeroButton(label: String, focused: Boolean, primary: Boolean) {
    Row(
        Modifier.height(46.dp)
            .scale(if (focused) 1.05f else 1f)
            .then(if (focused) Modifier.shadow(14.dp, RoundedCornerShape(13.dp)) else Modifier)
            .panel(if (focused) C.Fg else Color(0x14D6E2FF), 13, if (focused) null else Color(0x33D6E2FF))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (primary) PlayIcon(13, if (focused) C.FocusInk else C.Fg)
        T(label, 15, if (focused) C.FocusInk else C.Fg, 800)
    }
}

@Composable
private fun ChannelsRow(s: LauncherState, now: Long) {
    val list = s.channels.toList()
    val visible = 6
    val start = if (s.row == 2) (s.col - 2).coerceIn(0, max(0, list.size - visible)) else 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Canlı kanallar", 18, C.Fg, 600, display = true)
            T(if (s.fromEymen) "EYMEN Uydu favorilerin · OK ile tam ekran" else "TV kanalları · OK ile EYMEN Uydu'da aç", 13, C.Dim, 500)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until visible) {
                val ch = list.getOrNull(start + i)
                if (ch == null) {
                    Spacer(Modifier.weight(1f))
                    continue
                }
                ChannelTile(s, ch, s.row == 2 && s.col == start + i, ch.key == s.current?.key, now, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChannelTile(s: LauncherState, ch: Ch, focused: Boolean, playing: Boolean, now: Long, modifier: Modifier) {
    LaunchedEffect(ch.key) { s.requestLogo(ch) }
    val logo = ch.logo
    Row(
        modifier.height(76.dp)
            .scale(if (focused) 1.05f else 1f)
            .then(if (focused) Modifier.shadow(14.dp, RoundedCornerShape(14.dp)) else Modifier)
            .panel(if (focused) C.Fg else Color(0x10D6E2FF), 14, if (focused) null else if (playing) Color(0x66E8C27A) else Color(0x17D6E2FF))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(58.dp, 38.dp).panel(if (logo != null) C.LogoBg else C.Tile, 8).padding(if (logo != null) 4.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            if (logo != null) {
                Image(bitmap = logo, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                T(ch.name.take(8).uppercase(trLocale), 10, C.Fg, 700, display = true, maxLines = 2, align = TextAlign.Center)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T("${ch.number} · ${ch.name}", 13, if (focused) C.FocusInk else C.Fg, 800)
            val p = ch.now
            T(p?.title ?: (if (ch.isWeb) "İnternet yayını" else "Canlı yayın"), 11, if (focused) C.FocusSub else C.Mut, 500)
            if (p != null) {
                Progress(
                    p.progress(now), Modifier.fillMaxWidth(),
                    color = if (focused) C.FocusInk else if (playing) C.Gold else Color(0xFF6C7891),
                    track = if (focused) Color(0x240A1224) else Color(0x1FD6E2FF), height = 3
                )
            }
        }
    }
}

@Composable
private fun AppsRow(s: LauncherState) {
    val apps = s.visibleApps()
    val total = apps.size + s.inputs.size
    val visible = 7
    val start = if (s.row == 3) (s.col - 3).coerceIn(0, max(0, total - visible)) else 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Uygulamalar ve girişler", 18, C.Fg, 600, display = true)
            T("${apps.size} uygulama · Sarı tuş taşı · Kırmızı tuş gizle · MENU kurulum", 13, C.Dim, 500)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until visible) {
                val idx = start + i
                val focused = s.row == 3 && s.col == idx
                if (idx < apps.size) {
                    val app = apps[idx]
                    AppTile(s, app, focused, s.moving == app.pkg, Modifier.weight(1f))
                } else {
                    val input = s.inputs.getOrNull(idx - apps.size)
                    if (input != null) InputTile(input, focused, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AppTile(s: LauncherState, app: AppEntry, focused: Boolean, moving: Boolean, modifier: Modifier) {
    LaunchedEffect(app.pkg) { s.requestGraphics(app) }
    val banner = app.banner
    val icon = app.icon
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(86.dp)
                .scale(if (focused || moving) 1.07f else 1f)
                .then(if (focused || moving) Modifier.shadow(16.dp, RoundedCornerShape(12.dp)) else Modifier)
                .panel(if (banner != null) Color(0xFF101830) else Color(0xFF17213D), 12, if (moving) C.Gold else if (focused) C.Fg else Color(0x1AD6E2FF), if (focused || moving) 3 else 1),
            contentAlignment = Alignment.Center
        ) {
            if (banner != null) {
                Image(bitmap = banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (icon != null) {
                Image(bitmap = icon, contentDescription = app.label, contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
            } else {
                T(app.label.take(1).uppercase(trLocale), 24, C.Fg, 700, display = true)
            }
            if (moving) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).panel(C.Gold, 6).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    T("◀ ▶", 11, C.GoldInk, 800)
                }
            }
        }
        T(app.label, 12, if (focused) C.Fg else C.Fg2, if (focused) 800 else 600, align = TextAlign.Center)
    }
}

@Composable
private fun InputTile(input: InputEntry, focused: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(86.dp)
                .scale(if (focused) 1.07f else 1f)
                .panel(Color(0xFF1D2848), 12, if (focused) C.Fg else Color(0x1AD6E2FF), if (focused) 3 else 1),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(38.dp, 24.dp).border(2.dp, C.Fg2, RoundedCornerShape(5.dp)))
                T("GİRİŞ", 10, C.Gold, 800, spacing = 1.5f)
            }
        }
        T(input.label, 12, if (focused) C.Fg else C.Fg2, if (focused) 800 else 600, align = TextAlign.Center)
    }
}

@Composable
private fun SetupScreen(s: LauncherState) {
    val items = s.setupItems()
    val index = s.setupIndex.coerceIn(0, max(0, items.size - 1))
    val visible = 9
    val start = (index - 4).coerceIn(0, max(0, items.size - visible))
    val isDefault = s.isDefaultHome()
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF14204A), Color(0xFF070B16), Color(0xFF05080F)), center = Offset(0f, 0f), radius = 2400f)
        )
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrandMark(30)
                    T(if (s.setupApps) "UYGULAMALAR" else "AÇILIŞ VE ANA EKRAN", 12, C.Gold, 800, spacing = 2f)
                }
                T(if (s.setupApps) "Gizli uygulamalar" else "TV açılınca EYMEN OS gelsin", 30, C.Fg, 600, display = true)
                if (!s.setupApps) {
                    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(9.dp).panel(if (isDefault) C.Green else C.Red, 5))
                        T(if (isDefault) "Varsayılan ana ekran: EYMEN OS" else "Varsayılan ana ekran: ${s.otherHomeLabel() ?: "seçilmedi"}", 14, C.Fg2, 600)
                    }
                }
                items.drop(start).take(visible).forEachIndexed { i, item ->
                    val focused = start + i == index
                    Row(
                        Modifier.fillMaxWidth().height(48.dp)
                            .then(if (focused) Modifier.shadow(12.dp, RoundedCornerShape(12.dp)) else Modifier)
                            .panel(if (focused) C.Fg else Color(0x0FD6E2FF), 12)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        T(item.label, 15, if (focused) C.FocusInk else C.Fg, if (focused) 800 else 700, modifier = Modifier.weight(1f))
                        val v = item.value
                        if (v != null) T(v, 14, if (focused) C.FocusSub else C.Fg2, 600)
                    }
                }
            }
            if (!s.setupApps) {
                Column(
                    Modifier.width(400.dp).panel(Color(0x0FD6E2FF), 18, C.Line).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    T("Nasıl yapılır", 18, C.Fg, 600, display = true)
                    T("1. Varsayılan ana ekran: TV ayarlarında \"Ana ekran\" seçeneği varsa EYMEN OS'u seç.", 13, C.Fg2, 500, maxLines = 3, lineHeight = 19)
                    T("2. Açılışta otomatik başlat + \"Diğer uygulamaların üzerinde gösterme\" izni: TV açılınca başlatıcı gelir.", 13, C.Fg2, 500, maxLines = 3, lineHeight = 19)
                    T("3. Ev tuşu ve açılış koruması: Erişilebilirlik › EYMEN OS koruması'nı aç. Eski ana ekran her göründüğünde EYMEN açılır.", 13, C.Fg2, 500, maxLines = 4, lineHeight = 19)
                    T("Ayar ekranı yoksa bilgisayardan (adb):", 13, C.Gold, 700)
                    Column(Modifier.fillMaxWidth().panel(Color(0xFF05080F), 10, C.Line).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Mono("cmd package set-home-activity com.eymen.os/.HomeActivity")
                        Mono("appops set com.eymen.os SYSTEM_ALERT_WINDOW allow")
                        Mono("settings put secure enabled_accessibility_services com.eymen.os/.EnforcerService")
                    }
                    T("Komutların başına \"adb shell\" yaz. eski ana ekran silinmez; listeden \"eski ana ekrana dön\" ile geçilir.", 12, C.Mut, 500, maxLines = 3, lineHeight = 17)
                }
            }
        }
    }
}

@Composable
private fun Mono(text: String) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = androidx.compose.ui.text.TextStyle(
            color = C.Gold,
            fontSize = 11.5.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    )
}

@Composable
fun BrandMark(sz: Int) {
    Canvas(Modifier.size(sz.dp)) {
        val w = size.width
        drawRoundRect(C.Gold, cornerRadius = CornerRadius(w * 0.28f))
        val p = Path()
        p.moveTo(w * 0.40f, w * 0.31f)
        p.lineTo(w * 0.40f, w * 0.69f)
        p.lineTo(w * 0.69f, w * 0.50f)
        p.close()
        drawPath(p, C.FocusInk)
    }
}

@Composable
fun PlayIcon(sz: Int, color: Color) {
    Canvas(Modifier.size(sz.dp)) {
        val w = size.width
        val p = Path()
        p.moveTo(w * 0.22f, w * 0.12f)
        p.lineTo(w * 0.22f, w * 0.88f)
        p.lineTo(w * 0.85f, w * 0.5f)
        p.close()
        drawPath(p, color)
    }
}

@Composable
fun GearIcon(sz: Int, color: Color) {
    Canvas(Modifier.size(sz.dp)) {
        val w = size.width
        val c = Offset(w / 2f, w / 2f)
        val sw = w * 0.12f
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0)
            val x1 = c.x + (w * 0.30f * Math.cos(a)).toFloat()
            val y1 = c.y + (w * 0.30f * Math.sin(a)).toFloat()
            val x2 = c.x + (w * 0.46f * Math.cos(a)).toFloat()
            val y2 = c.y + (w * 0.46f * Math.sin(a)).toFloat()
            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        }
        drawCircle(color, radius = w * 0.28f, center = c, style = Stroke(width = sw))
        drawCircle(color, radius = w * 0.09f, center = c)
    }
}

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Gold, track: Color = Color(0x24D6E2FF), height: Int = 4) {
    Box(modifier.height(height.dp).clip(RoundedCornerShape(height.dp)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
fun LiveBadge() {
    Box(Modifier.panel(C.Live, 5).padding(horizontal = 7.dp, vertical = 2.dp)) {
        T("CANLI", 10, Color.White, 800, spacing = 1.4f)
    }
}
