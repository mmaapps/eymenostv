package com.eymen.os

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class LauncherState(val activity: HomeActivity) {
    val ctx: Context = activity
    val prefs: SharedPreferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val preview = Preview(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val tr = Locale("tr", "TR")

    var screen by mutableStateOf(Screen.HOME)
    val apps = mutableStateListOf<AppEntry>()
    val hidden = mutableStateListOf<String>()
    val channels = mutableStateListOf<Ch>()
    private var allChannels: List<Ch> = emptyList()
    val inputs = mutableStateListOf<InputEntry>()
    var current by mutableStateOf<Ch?>(null)
    var now by mutableStateOf(System.currentTimeMillis())
    var row by mutableStateOf(1)
    var col by mutableStateOf(0)
    var moving by mutableStateOf<String?>(null)
    var setupIndex by mutableStateOf(0)
    /** Which list the plain settings screen shows: start-up settings or hidden apps. */
    var setupApps by mutableStateOf(false)
    var hubIndex by mutableStateOf(0)
    var online by mutableStateOf(false)
    val updates = UpdateManager(ctx, prefs, scope)
    var updateRow by mutableStateOf(0)
    var noticeVisible by mutableStateOf(false)
    var noticeIndex by mutableStateOf(0)
    var noticeLeft by mutableStateOf(NOTICE_SECONDS)
    private var noticeJob: Job? = null
    private var noticeSnoozedUntil = 0L
    val adMessages = mutableStateListOf<String>()
    var qrOpen by mutableStateOf(false)
    var siteQr by mutableStateOf<ImageBitmap?>(null)
    var toastText by mutableStateOf<String?>(null)
    var soundOn by mutableStateOf(prefs.getBoolean("sound", true))
    var autoStart by mutableStateOf(prefs.getBoolean("autoStart", true))
    var refreshKey by mutableStateOf(0)
    var fromEymen by mutableStateOf(false)
    private var visibleNow = false
    /** Set when the user opens something on purpose, so the boot guard never fights the user. */
    var userLaunch = false
    var guardUntil = 0L
    private var guardAttempts = 0

    val heroActions = listOf("EYMEN Uydu'da aç", "Yayın akışı", "Kanal listesi")

    init {
        hidden.addAll(prefs.getStringSet("hiddenApps", emptySet()) ?: emptySet())
        val saved = try {
            Duyuru.parse(prefs.getString("duyuru", "") ?: "")
        } catch (e: Exception) {
            emptyList()
        }
        adMessages.addAll(saved.ifEmpty { Duyuru.VARSAYILAN })
        updates.onFinished = {
            toast("Güncelleme tamamlandı")
            refreshKey++
            if (screen == Screen.UPDATE) closeHub()
        }
    }

    // ------------------------------------------------------------ loading

    fun start() {
        reloadApps()
        inputs.clear()
        inputs.addAll(Channels.inputs(ctx))
        scope.launch {
            while (isActive) {
                now = System.currentTimeMillis()
                refreshEpg()
                delay(20000L)
            }
        }
        scope.launch {
            while (isActive) {
                checkUpdates()
                delay(UPDATE_PERIOD)
            }
        }
        scope.launch {
            siteQr = withContext(Dispatchers.Default) { Qr.make(SITE_URL) }?.asImageBitmap()
        }
        if (!isDefaultHome() && !prefs.getBoolean("setupSeen", false)) {
            setupApps = false
            screen = Screen.SETUP
            prefs.edit().putBoolean("setupSeen", true).apply()
        }
        val seen = prefs.getString("seenVersion", "") ?: ""
        if (seen != BuildConfig.VERSION_NAME) {
            if (seen.isNotEmpty()) toast("EYMEN OS ${BuildConfig.VERSION_NAME} sürümüne güncellendi", 7000L)
            prefs.edit().putString("seenVersion", BuildConfig.VERSION_NAME).apply()
        }
    }

    // ------------------------------------------------------------ updates

    /** Looks for new versions; a find starts the download and puts the notice on screen. */
    fun checkUpdates() {
        updates.check { found ->
            if (found) {
                if (updates.auto) updates.run(false)
                offerNotice()
            }
        }
        scope.launch {
            val json = withContext(Dispatchers.IO) { Duyuru.fetch() } ?: return@launch
            val list = try {
                Duyuru.parse(json)
            } catch (e: Exception) {
                emptyList()
            }
            if (list.isNotEmpty()) {
                adMessages.clear()
                adMessages.addAll(list)
                prefs.edit().putString("duyuru", json).apply()
            }
        }
    }

    /** The TV has just reached a network. */
    fun onNetwork(available: Boolean) {
        online = available
        if (!available) return
        if (System.currentTimeMillis() - updates.lastCheck < NETWORK_MIN_GAP) return
        handler.postDelayed({ checkUpdates() }, 3000L)
    }

    /** Never on top of an app: the notice waits until the user is back on the home screen. */
    fun offerNotice() {
        if (updates.items.isEmpty() || noticeVisible || updates.busy() && updates.phase != UpdatePhase.DOWNLOADING) return
        if (!visibleNow || screen != Screen.HOME) return
        if (System.currentTimeMillis() < noticeSnoozedUntil) return
        noticeVisible = true
        noticeIndex = 0
        noticeLeft = NOTICE_SECONDS
        noticeJob?.cancel()
        if (updates.auto) {
            noticeJob = scope.launch {
                while (noticeLeft > 0) {
                    delay(1000L)
                    noticeLeft -= 1
                }
                startUpdate()
            }
        }
    }

    private fun hideNotice() {
        noticeVisible = false
        noticeJob?.cancel()
        noticeJob = null
    }

    fun startUpdate() {
        hideNotice()
        openUpdate()
        updates.run(true)
    }

    private fun later() {
        hideNotice()
        noticeSnoozedUntil = System.currentTimeMillis() + UPDATE_PERIOD
    }

    private fun noticeKey(code: Int) {
        when {
            code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                noticeIndex = 1 - noticeIndex
                noticeJob?.cancel()
            }
            isOk(code) -> if (noticeIndex == 0) startUpdate() else later()
            code == KeyEvent.KEYCODE_BACK -> later()
        }
    }

    /** EYMEN Uydu or the phone asked for something through the home intent. */
    fun onRequest(request: String) {
        when (request) {
            "izin" -> scope.launch {
                var tries = 0
                while (updates.busy() && tries < 20) {
                    delay(500L)
                    tries += 1
                }
                updates.authorize()
            }
            else -> {
                hideNotice()
                openUpdate()
                updates.check { found -> if (found) updates.run(true) }
            }
        }
    }

    fun openUpdate() {
        screen = Screen.UPDATE
        updateRow = 0
        preview.stop()
    }

    fun closeUpdate() {
        if (updates.busy()) return
        openHub()
    }

    private fun updateAction() {
        val u = updates
        if (u.busy()) return
        if (u.items.isNotEmpty()) u.run(true) else checkUpdates()
    }

    private fun updateKey(code: Int): Boolean {
        when {
            code == KeyEvent.KEYCODE_DPAD_UP -> updateRow = 0
            code == KeyEvent.KEYCODE_DPAD_DOWN -> updateRow = 1
            isOk(code) -> if (updateRow == 0) updateAction() else updates.setAutomatic(!updates.auto)
            code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU -> closeUpdate()
            else -> return false
        }
        return true
    }

    // ------------------------------------------------------------ settings cards

    fun openHub() {
        screen = Screen.HUB
        preview.stop()
        refreshKey++
    }

    fun closeHub() {
        hubIndex = 0
        screen = Screen.HOME
        row = 1
        col = 0
        current?.let { preview.play(it, !soundOn) }
        offerNotice()
    }

    private fun openList(apps: Boolean) {
        setupApps = apps
        setupIndex = 0
        refreshKey++
        screen = Screen.SETUP
    }

    private fun hubKey(code: Int): Boolean {
        when {
            code == KeyEvent.KEYCODE_DPAD_LEFT -> if (hubIndex % 3 > 0) hubIndex -= 1
            code == KeyEvent.KEYCODE_DPAD_RIGHT -> if (hubIndex % 3 < 2) hubIndex += 1
            code == KeyEvent.KEYCODE_DPAD_UP -> if (hubIndex >= 3) hubIndex -= 3
            code == KeyEvent.KEYCODE_DPAD_DOWN -> if (hubIndex < 6) hubIndex += 3
            isOk(code) -> hubAction(hubIndex)
            code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS -> closeHub()
            else -> return false
        }
        return true
    }

    private fun hubAction(i: Int) {
        when (i) {
            0 -> openUpdate()
            1 -> openEymen(null, "channels")
            2 -> openSettings(Settings.ACTION_WIFI_SETTINGS, false)
            3 -> {
                soundOn = !soundOn
                prefs.edit().putBoolean("sound", soundOn).apply()
                preview.setMuted(!soundOn)
            }
            4 -> openList(false)
            5 -> openList(true)
            6 -> openSettings(Settings.ACTION_SETTINGS, false)
            7 -> goOtherHome()
            8 -> screen = Screen.ABOUT
        }
    }

    fun channelCount(): Int = allChannels.size

    fun reloadApps() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { Apps.load(ctx) }
            val order = (prefs.getString("appOrder", "") ?: "").split(',').filter { it.isNotBlank() }
            val rank = HashMap<String, Int>()
            order.forEachIndexed { i, p -> rank[p] = i }
            if (!rank.containsKey(Channels.EYMEN_PKG)) rank[Channels.EYMEN_PKG] = -1
            val collator = Collator.getInstance(tr)
            val sorted = list.sortedWith { a, b ->
                val ra = rank[a.pkg] ?: Int.MAX_VALUE
                val rb = rank[b.pkg] ?: Int.MAX_VALUE
                if (ra != rb) ra.compareTo(rb) else collator.compare(a.label, b.label)
            }
            val old = apps.associateBy { it.pkg }
            apps.clear()
            for (a in sorted) {
                val prev = old[a.pkg]
                if (prev != null) {
                    a.banner = prev.banner
                    a.icon = prev.icon
                    a.requested = prev.requested
                }
                apps.add(a)
            }
        }
    }

    fun reloadChannels() {
        scope.launch {
            val snap = withContext(Dispatchers.IO) { Channels.fromEymen(ctx) }
            val list: List<Ch>
            val last: String?
            if (snap != null) {
                list = snap.channels
                last = snap.last
                fromEymen = true
            } else {
                list = withContext(Dispatchers.IO) { Channels.fromTv(ctx) }
                last = prefs.getString("last", null)
                fromEymen = false
            }
            val favs = list.filter { it.fav }
            val rowList = (if (favs.isNotEmpty()) favs else list).take(16)
            val cur = list.firstOrNull { it.key == last } ?: rowList.firstOrNull()
            allChannels = list
            channels.clear()
            channels.addAll(rowList)
            current = cur
            if (visibleNow && cur != null && screen == Screen.HOME) preview.play(cur, !soundOn)
            refreshEpg()
        }
    }

    private fun refreshEpg() {
        val t = System.currentTimeMillis()
        val targets = ArrayList<Ch>()
        current?.let { targets.add(it) }
        targets.addAll(channels)
        for (ch in targets.distinctBy { it.key }) {
            if (ch.isWeb || ch.tvId < 0) continue
            val n = ch.now
            if (t - ch.epgAt < 120000L && n != null && n.end > t) continue
            ch.epgAt = t
            scope.launch {
                val p = withContext(Dispatchers.IO) { Channels.programs(ctx, ch.tvId, t - 6 * 3600000L, t + 12 * 3600000L) }
                val live = p.firstOrNull { it.start <= t && it.end > t }
                ch.now = live
                ch.next = p.firstOrNull { it.start >= (live?.end ?: t) }
            }
        }
    }

    fun requestLogo(ch: Ch) {
        if (ch.logoRequested) return
        ch.logoRequested = true
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { Logos.load(ctx, ch) }
            if (bmp != null) ch.logo = bmp.asImageBitmap()
        }
    }

    fun requestGraphics(app: AppEntry) {
        if (app.requested) return
        app.requested = true
        scope.launch {
            val (banner, icon) = withContext(Dispatchers.IO) { Apps.graphics(ctx, app.pkg) }
            if (banner != null) app.banner = banner.asImageBitmap()
            if (icon != null) app.icon = icon.asImageBitmap()
        }
    }

    // ------------------------------------------------------------ lists

    fun visibleApps(): List<AppEntry> = apps.filter { !hidden.contains(it.pkg) }

    fun appsRowSize(): Int = visibleApps().size + inputs.size

    private fun cols(r: Int): Int = when (r) {
        0 -> 1
        1 -> heroActions.size
        2 -> channels.size
        3 -> appsRowSize()
        4 -> 1
        else -> 0
    }

    // ------------------------------------------------------------ actions

    private val hideToast = Runnable { toastText = null }

    fun toast(s: String, millis: Long = 3000L) {
        toastText = s
        handler.removeCallbacks(hideToast)
        handler.postDelayed(hideToast, millis)
    }

    fun openEymen(ch: Ch?, open: String?) {
        val intent = Apps.launchIntent(ctx, Channels.EYMEN_PKG)
        if (intent == null) {
            toast("EYMEN Uydu yüklü değil")
            return
        }
        if (ch != null) {
            intent.putExtra("channel_key", ch.key)
            intent.putExtra("channel_number", ch.number)
            prefs.edit().putString("last", ch.key).apply()
        }
        if (open != null) intent.putExtra("open", open)
        intent.putExtra("skip_splash", true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch(intent)
    }

    private fun launch(intent: Intent) {
        userLaunch = true
        try {
            preview.stop()
            ctx.startActivity(intent)
        } catch (e: Exception) {
            toast("Açılamadı")
            current?.let { preview.play(it, !soundOn) }
        }
    }

    fun launchApp(app: AppEntry) {
        val intent = Apps.launchIntent(ctx, app.pkg)
        if (intent == null) {
            toast("${app.label} açılamadı")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch(intent)
    }

    fun openInput(input: InputEntry) {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch(intent)
    }

    private fun saveOrder() {
        prefs.edit().putString("appOrder", apps.joinToString(",") { it.pkg }).apply()
    }

    private fun moveApp(pkg: String, dir: Int) {
        val vis = visibleApps()
        val i = vis.indexOfFirst { it.pkg == pkg }
        val j = i + dir
        if (i < 0 || j < 0 || j >= vis.size) return
        val a = apps.indexOf(vis[i])
        val b = apps.indexOf(vis[j])
        if (a < 0 || b < 0) return
        val tmp = apps[a]
        apps[a] = apps[b]
        apps[b] = tmp
        col = j
        saveOrder()
    }

    private fun toggleHide(pkg: String) {
        if (hidden.contains(pkg)) hidden.remove(pkg) else hidden.add(pkg)
        prefs.edit().putStringSet("hiddenApps", HashSet(hidden)).apply()
    }

    private fun zap(delta: Int) {
        val list = allChannels
        if (list.isEmpty()) return
        var i = list.indexOfFirst { it.key == current?.key }
        if (i < 0) i = 0 else i = ((i + delta) % list.size + list.size) % list.size
        val ch = list[i]
        current = ch
        prefs.edit().putString("last", ch.key).apply()
        preview.play(ch, !soundOn)
        refreshEpg()
    }

    // ------------------------------------------------------------ default home and permissions

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun isDefaultHome(): Boolean = try {
        val ri = ctx.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName == ctx.packageName
    } catch (e: Exception) {
        false
    }

    fun otherHomeLabel(): String? = try {
        val pm = ctx.packageManager
        pm.queryIntentActivities(homeIntent(), 0)
            .firstOrNull { it.activityInfo.packageName !in listOf(ctx.packageName, "com.android.settings", "android") }
            ?.loadLabel(pm)?.toString()
    } catch (e: Exception) {
        null
    }

    fun canOverlay(): Boolean = Settings.canDrawOverlays(ctx)

    fun enforcerEnabled(): Boolean = try {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        enabled.contains(ComponentName(ctx, EnforcerService::class.java).flattenToString())
    } catch (e: Exception) {
        false
    }

    private fun openSettings(action: String, withPackage: Boolean) {
        val tries = ArrayList<Intent>()
        if (withPackage) tries.add(Intent(action, Uri.parse("package:" + ctx.packageName)))
        tries.add(Intent(action))
        tries.add(Intent(Settings.ACTION_SETTINGS))
        userLaunch = true
        for (i in tries) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return
            } catch (e: Exception) {
            }
        }
        toast("Bu ayar ekranı TV'de yok. Sağdaki bilgisayar komutunu kullan.")
    }

    fun goOtherHome() {
        userLaunch = true
        prefs.edit().putLong("allowOtherUntil", System.currentTimeMillis() + 10 * 60000L).apply()
        try {
            val pm = ctx.packageManager
            val other = pm.queryIntentActivities(homeIntent(), 0)
                .firstOrNull { it.activityInfo.packageName !in listOf(ctx.packageName, "com.android.settings", "android") }
            if (other == null) {
                toast("Başka bir ana ekran bulunamadı")
                return
            }
            val i = homeIntent().setClassName(other.activityInfo.packageName, other.activityInfo.name)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            preview.stop()
            ctx.startActivity(i)
        } catch (e: Exception) {
            toast("Açılamadı")
        }
    }

    private fun onOff(v: Boolean) = if (v) "Açık" else "Kapalı"

    fun setupItems(): List<SetupItem> {
        val tick = refreshKey
        val list = ArrayList<SetupItem>()
        if (setupApps) {
            for (pkg in hidden.toList()) {
                val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                list.add(SetupItem("Gizli: $label", "Göster") {
                    hidden.remove(pkg)
                    prefs.edit().putStringSet("hiddenApps", HashSet(hidden)).apply()
                })
            }
            if (list.isEmpty()) list.add(SetupItem("Gizli uygulama yok"))
            list.add(SetupItem("Ayarlara dön", null) { closeSetup() })
            return list
        }
        list.add(SetupItem("Varsayılan ana ekran", if (isDefaultHome()) "EYMEN OS" else (otherHomeLabel() ?: "Seçilmedi") + tick.toString().take(0)) {
            openSettings(Settings.ACTION_HOME_SETTINGS, false)
        })
        list.add(SetupItem("Açılışta otomatik başlat", onOff(autoStart)) {
            autoStart = !autoStart
            prefs.edit().putBoolean("autoStart", autoStart).apply()
            if (autoStart && Build.VERSION.SDK_INT >= 29 && !canOverlay()) openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
        })
        list.add(SetupItem("Diğer uygulamaların üzerinde gösterme", if (canOverlay()) "İzin verildi" else "İzin yok") {
            openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
        })
        val accessibilityAvailable = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).resolveActivity(ctx.packageManager) != null
        list.add(SetupItem(
            "Ek koruma (erişilebilirlik, isteğe bağlı)",
            when {
                enforcerEnabled() -> "Açık"
                accessibilityAvailable -> "Kapalı"
                else -> "Bu TV'de yok · gerekmez"
            }
        ) {
            if (accessibilityAvailable) openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, false)
            else toast("Bu TV'de erişilebilirlik ayarı yok. Açılış koruması onsuz da çalışır.")
        })
        list.add(SetupItem("TV kanal izni", if (ctx.checkSelfPermission(PERM_TV) == PackageManager.PERMISSION_GRANTED) "Verildi" else "Verilmedi") {
            activity.requestTvPermission()
        })
        list.add(SetupItem("Ayarlara dön", null) { closeSetup() })
        return list
    }

    fun closeSetup() {
        openHub()
    }

    // ------------------------------------------------------------ keys

    private fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A

    fun onKey(code: Int): Boolean {
        if (noticeVisible) {
            noticeKey(code)
            return true
        }
        if (qrOpen) {
            if (isOk(code) || code == KeyEvent.KEYCODE_BACK) qrOpen = false
            return true
        }
        when (screen) {
            Screen.HUB -> return hubKey(code)
            Screen.UPDATE -> return updateKey(code)
            Screen.ABOUT -> {
                if (isOk(code) || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) screen = Screen.HUB
                return true
            }
            else -> {}
        }
        if (screen == Screen.SETUP) {
            val items = setupItems()
            when {
                code == KeyEvent.KEYCODE_DPAD_UP -> setupIndex = (setupIndex - 1).coerceIn(0, max(0, items.size - 1))
                code == KeyEvent.KEYCODE_DPAD_DOWN -> setupIndex = (setupIndex + 1).coerceIn(0, max(0, items.size - 1))
                isOk(code) -> {
                    items.getOrNull(setupIndex)?.action?.invoke()
                    refreshKey++
                }
                code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU -> closeSetup()
                else -> return false
            }
            return true
        }
        val mv = moving
        if (mv != null) {
            when {
                code == KeyEvent.KEYCODE_DPAD_LEFT -> moveApp(mv, -1)
                code == KeyEvent.KEYCODE_DPAD_RIGHT -> moveApp(mv, 1)
                isOk(code) || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_PROG_YELLOW -> {
                    moving = null
                    toast("Sıra kaydedildi")
                }
            }
            return true
        }
        when {
            code == KeyEvent.KEYCODE_DPAD_UP -> {
                var r = row - 1
                while (r >= 0 && cols(r) == 0) r--
                if (r >= 0) {
                    row = r
                    col = col.coerceIn(0, max(0, cols(r) - 1))
                }
            }
            code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                var r = row + 1
                while (r <= 4 && cols(r) == 0) r++
                if (r <= 4) {
                    row = r
                    col = col.coerceIn(0, max(0, cols(r) - 1))
                }
            }
            code == KeyEvent.KEYCODE_DPAD_LEFT -> if (col > 0) col -= 1
            code == KeyEvent.KEYCODE_DPAD_RIGHT -> if (col < cols(row) - 1) col += 1
            isOk(code) -> activate()
            code == KeyEvent.KEYCODE_PROG_YELLOW -> {
                val app = visibleApps().getOrNull(col)
                if (row == 3 && app != null) {
                    moving = app.pkg
                    toast("◀ ▶ ile taşı, OK ile bırak")
                }
            }
            code == KeyEvent.KEYCODE_PROG_RED -> {
                val app = visibleApps().getOrNull(col)
                if (row == 3 && app != null) {
                    toggleHide(app.pkg)
                    col = col.coerceIn(0, max(0, appsRowSize() - 1))
                    toast("${app.label} gizlendi · Kurulum'dan geri getirilir")
                }
            }
            code == KeyEvent.KEYCODE_CHANNEL_UP -> zap(1)
            code == KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(-1)
            code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS -> openHub()
            code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_PROG_GREEN -> openEymen(current, "epg")
            code == KeyEvent.KEYCODE_BACK -> {
                row = 1
                col = 0
            }
            else -> return false
        }
        return true
    }

    private fun activate() {
        when (row) {
            0 -> openHub()
            1 -> when (col) {
                0 -> openEymen(current, null)
                1 -> openEymen(current, "epg")
                else -> openEymen(current, "guide")
            }
            2 -> channels.getOrNull(col)?.let { openEymen(it, null) }
            3 -> {
                val vis = visibleApps()
                if (col < vis.size) launchApp(vis[col])
                else inputs.getOrNull(col - vis.size)?.let { openInput(it) }
            }
            4 -> qrOpen = true
        }
    }

    // ------------------------------------------------------------ lifecycle

    fun onHome() {
        if (screen == Screen.SETUP && !setupApps) return
        if (screen == Screen.UPDATE && updates.busy()) return
        if (screen != Screen.HOME) {
            closeHub()
            return
        }
        moving = null
        row = 1
        col = 0
    }

    /** True when eski ana ekran (or anything not opened by the user) covered the launcher right after start-up or wake-up. */
    fun shouldReassert(): Boolean {
        if (!autoStart || userLaunch) return false
        if (System.currentTimeMillis() < prefs.getLong("allowOtherUntil", 0L)) return false
        val bootWindow = android.os.SystemClock.elapsedRealtime() < 240000L
        val wakeWindow = System.currentTimeMillis() < guardUntil
        if (!bootWindow && !wakeWindow) {
            guardAttempts = 0
            return false
        }
        if (guardAttempts >= 6) return false
        guardAttempts++
        return true
    }

    fun armWakeGuard() {
        guardUntil = System.currentTimeMillis() + 90000L
        guardAttempts = 0
        userLaunch = false
        noticeSnoozedUntil = 0L
        handler.postDelayed({ offerNotice() }, 2500L)
    }

    fun onStart() {
        userLaunch = false
        visibleNow = true
        refreshKey++
        reloadChannels()
        offerNotice()
    }

    fun onStop() {
        visibleNow = false
        hideNotice()
        preview.stop()
    }

    fun onDestroy() {
        preview.release()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    fun time(t: Long): String = SimpleDateFormat("HH:mm", tr).format(Date(t))
    fun dateLabel(t: Long): String = SimpleDateFormat("EEEE, d MMMM", tr).format(Date(t))

    companion object {
        const val PREFS = "baslatici"
        const val PERM_TV = "android.permission.READ_TV_LISTINGS"
        const val SITE_URL = "https://atasoyyazilim.com"
        const val NOTICE_SECONDS = 60
        const val UPDATE_PERIOD = 6 * 3600000L
        const val NETWORK_MIN_GAP = 15 * 60000L
    }
}
