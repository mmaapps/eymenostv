package com.eymen.os

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class UpdatePhase { IDLE, CHECKING, DOWNLOADING, ALLOW, INSTALLING, FAILED }

class UpdateItem(
    val label: String,
    val pkg: String,
    val installed: String?,
    val version: String,
    val url: String,
    val notes: List<String>
) {
    var file: File? = null
}

/**
 * Keeps EYMEN OS and EYMEN Uydu on the newest stable release.
 *
 * A normal app cannot install without a question on Android 11, but this TV already has its
 * debugging port open, so EYMEN OS connects to it on 127.0.0.1 and installs with pm. The TV asks
 * "Allow debugging" once. When that does not work the system installer opens instead.
 */
class UpdateManager(
    private val ctx: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope
) {
    var phase by mutableStateOf(UpdatePhase.IDLE)
    var percent by mutableStateOf(0)
    var message by mutableStateOf("")
    var lastCheck by mutableStateOf(prefs.getLong("updateChecked", 0L))
    var auto by mutableStateOf(prefs.getBoolean("autoUpdate", true))
    val items = mutableStateListOf<UpdateItem>()
    /** Called after the updates of the other app finished; EYMEN OS itself restarts instead. */
    var onFinished: (() -> Unit)? = null
    private var installWhenReady = false

    fun busy(): Boolean = phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING ||
        phase == UpdatePhase.ALLOW || phase == UpdatePhase.INSTALLING

    fun versionOf(pkg: String): String? = try {
        ctx.packageManager.getPackageInfo(pkg, 0).versionName
    } catch (e: Exception) {
        null
    }

    fun setAutomatic(on: Boolean) {
        auto = on
        prefs.edit().putBoolean("autoUpdate", on).apply()
    }

    // ------------------------------------------------------------ check

    fun check(done: ((Boolean) -> Unit)? = null) {
        if (busy()) {
            done?.invoke(items.isNotEmpty())
            return
        }
        phase = UpdatePhase.CHECKING
        message = "Yeni sürüm aranıyor"
        scope.launch {
            val found = withContext(Dispatchers.IO) { findUpdates() }
            phase = UpdatePhase.IDLE
            if (found == null) {
                message = "İnternete ulaşılamadı"
                done?.invoke(false)
                return@launch
            }
            items.clear()
            items.addAll(found)
            lastCheck = System.currentTimeMillis()
            prefs.edit().putLong("updateChecked", lastCheck).apply()
            message = if (found.isEmpty()) "En güncel sürüm yüklü" else "Yeni sürüm hazır"
            done?.invoke(found.isNotEmpty())
        }
    }

    /** Null when neither release could be read, an empty list when everything is up to date. */
    private fun findUpdates(): List<UpdateItem>? {
        val out = ArrayList<UpdateItem>()
        var reached = false
        for (s in SOURCES) {
            try {
                val json = JSONObject(text("https://api.github.com/repos/${s.repo}/releases/latest"))
                reached = true
                val version = Regex("(\\d+(?:\\.\\d+)+)").find(json.optString("tag_name", ""))?.value ?: continue
                val installed = versionOf(s.pkg)
                if (installed != null && !newer(version, installed)) continue
                val url = "https://github.com/${s.repo}/releases/download/${json.optString("tag_name")}/${s.asset}"
                out.add(UpdateItem(s.label, s.pkg, installed, version, url, notes(json.optString("body", ""))))
            } catch (e: Exception) {
            }
        }
        return if (reached) out else null
    }

    private fun notes(body: String): List<String> =
        body.lines().map { it.trim() }.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }.take(4)

    private fun text(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "EYMEN-OS")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------ download and install

    /** Downloads what is missing. With [install] the update goes on right after the download. */
    fun run(install: Boolean) {
        if (install) installWhenReady = true
        if (busy() || items.isEmpty()) return
        scope.launch {
            val list = items.toList()
            try {
                phase = UpdatePhase.DOWNLOADING
                for (item in list) {
                    message = "${item.label} ${item.version} indiriliyor"
                    percent = 0
                    item.file = withContext(Dispatchers.IO) { fetch(item) }
                }
                percent = 100
                phase = UpdatePhase.IDLE
                message = "Yeni sürüm indirildi"
                if (installWhenReady) {
                    installWhenReady = false
                    installAll(list)
                }
            } catch (e: Exception) {
                installWhenReady = false
                phase = UpdatePhase.FAILED
                message = "Güncellenemedi: ${e.message ?: "bağlantı koptu"}"
            }
        }
    }

    private fun fetch(item: UpdateItem): File {
        val dir = File(ctx.filesDir, "guncelleme")
        dir.mkdirs()
        val target = File(dir, "${item.pkg}-${item.version}.apk")
        if (target.exists() && matches(target, item)) return target
        for (old in dir.listFiles() ?: emptyArray()) {
            if (old.name.startsWith(item.pkg)) old.delete()
        }
        val tmp = File(dir, target.name + ".part")
        val conn = URL(item.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "EYMEN-OS")
        try {
            if (conn.responseCode != 200) throw IOException("sunucu ${conn.responseCode} döndürdü")
            val total = conn.contentLength.toLong()
            var got = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        got += n
                        if (total > 0) percent = (got * 100 / total).toInt()
                    }
                }
            }
            if (total > 0 && got != total) throw IOException("dosya yarım indi")
        } finally {
            conn.disconnect()
        }
        if (!tmp.renameTo(target)) throw IOException("dosya kaydedilemedi")
        if (!matches(target, item)) {
            target.delete()
            throw IOException("inen dosya bozuk")
        }
        return target
    }

    private fun matches(f: File, item: UpdateItem): Boolean {
        val info = ctx.packageManager.getPackageArchiveInfo(f.path, 0) ?: return false
        return info.packageName == item.pkg && info.versionName == item.version
    }

    private suspend fun installAll(list: List<UpdateItem>) {
        val first = !prefs.getBoolean("adbAllowed", false)
        phase = if (first) UpdatePhase.ALLOW else UpdatePhase.INSTALLING
        message = if (first) "TV'de çıkan pencerede Her zaman izin ver'i işaretle, İzin ver'e bas" else "Kurulum başlıyor"
        val adb = withContext(Dispatchers.IO) { connect() }
        if (adb == null) {
            systemInstall(list)
            return
        }
        try {
            phase = UpdatePhase.INSTALLING
            val self = list.firstOrNull { it.pkg == ctx.packageName }
            for (item in list) {
                if (item === self) continue
                message = "${item.label} ${item.version} kuruluyor"
                withContext(Dispatchers.IO) { installOther(adb, item) }
                items.remove(item)
            }
            if (self != null) {
                message = "EYMEN OS ${self.version} kuruluyor, ana ekran birazdan yeniden açılır"
                withContext(Dispatchers.IO) { installSelf(adb, self) }
            }
            phase = UpdatePhase.IDLE
            message = "Güncellendi"
            onFinished?.invoke()
        } catch (e: Exception) {
            phase = UpdatePhase.FAILED
            message = "Kurulamadı: ${e.message ?: "bilinmeyen hata"}"
        } finally {
            withContext(Dispatchers.IO) { close(adb) }
        }
    }

    private fun keyPair(): AdbKeyPair {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /** Blocks until the TV answers; the first time that means until the user allows it. */
    private fun connect(): Dadb? {
        var d: Dadb? = null
        return try {
            d = Dadb.create("127.0.0.1", ADB_PORT, keyPair(), 5000, 90000, true)
            if (!d.shell("echo EYMEN").allOutput.contains("EYMEN")) throw IOException("yanıt yok")
            prefs.edit().putBoolean("adbAllowed", true).apply()
            d.shell("settings put global eymen_otomatik 1")
            d
        } catch (e: Throwable) {
            if (d != null) close(d)
            null
        }
    }

    private fun close(d: Dadb) {
        try {
            d.close()
        } catch (e: Exception) {
        }
    }

    private fun installOther(adb: Dadb, item: UpdateItem) {
        val file = item.file ?: throw IOException("dosya yok")
        val remote = "/data/local/tmp/eymen-${item.pkg}.apk"
        adb.push(file, remote, 420, System.currentTimeMillis())
        val answer = adb.shell("pm install -r $remote; rm -f $remote").allOutput
        if (!answer.contains("Success")) throw IOException(lastLine(answer))
    }

    /**
     * Installing itself kills this process, so the job goes to a shell that outlives it.
     * The shell also puts EYMEN OS back as home screen and opens it.
     */
    private suspend fun installSelf(adb: Dadb, item: UpdateItem) {
        val file = item.file ?: throw IOException("dosya yok")
        val pkg = ctx.packageName
        val remote = "/data/local/tmp/eymen-os.apk"
        val log = "/data/local/tmp/eymen-os.log"
        adb.push(file, remote, 420, System.currentTimeMillis())
        adb.shell("rm -f $log")
        val script = "pm install -r $remote > $log 2>&1; rm -f $remote; " +
            "cmd package set-home-activity $pkg/.HomeActivity > /dev/null 2>&1; sleep 1; " +
            "am start -n $pkg/.HomeActivity > /dev/null 2>&1"
        adb.shell("nohup sh -c \"$script\" < /dev/null > /dev/null 2>&1 &")
        repeat(90) {
            delay(1000)
            val answer = try {
                adb.shell("cat $log 2>/dev/null").allOutput
            } catch (e: Exception) {
                ""
            }
            if (answer.contains("Failure") || answer.contains("Exception")) throw IOException(lastLine(answer))
        }
        throw IOException("kurulum yanıt vermedi")
    }

    private fun lastLine(s: String): String = s.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: "bilinmeyen hata"

    /** The way without the debugging port: Android's own installer, one "Yükle" for each app. */
    private fun systemInstall(list: List<UpdateItem>) {
        val item = list.firstOrNull { it.pkg != ctx.packageName } ?: list.first()
        val file = item.file
        if (file == null) {
            phase = UpdatePhase.FAILED
            message = "Dosya bulunamadı"
            return
        }
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            try {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e: Exception) {
            }
            phase = UpdatePhase.FAILED
            message = "EYMEN OS için bilinmeyen kaynak iznini aç, sonra yeniden dene"
            return
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            phase = UpdatePhase.IDLE
            message = "Ekrandaki Yükle düğmesine bas"
        } catch (e: Exception) {
            phase = UpdatePhase.FAILED
            message = "Kurulum ekranı açılamadı"
        }
    }

    /** Asks for the TV's permission once, while the phone is setting things up. */
    fun authorize() {
        if (busy()) return
        phase = UpdatePhase.ALLOW
        message = "TV'de çıkan pencerede Her zaman izin ver'i işaretle, İzin ver'e bas"
        scope.launch {
            val adb = withContext(Dispatchers.IO) { connect() }
            if (adb != null) {
                withContext(Dispatchers.IO) { close(adb) }
                phase = UpdatePhase.IDLE
                message = "Otomatik güncelleme açık"
            } else {
                phase = UpdatePhase.FAILED
                message = "İzin verilmedi. Güncellemelerde ekranda Yükle onayı çıkacak."
            }
        }
    }

    private class Source(val label: String, val pkg: String, val repo: String, val asset: String)

    companion object {
        private const val ADB_PORT = 5555
        const val UYDU_PKG = "com.eymen.uydukontrol"
        private val SOURCES = listOf(
            Source("EYMEN OS", "com.eymen.os", "mmaapps/eymenostv", "EYMEN-OS-TV.apk"),
            Source("EYMEN Uydu", UYDU_PKG, "mmustafatasoy/EymenTv", "EYMEN-TV.apk")
        )

        fun newer(candidate: String, installed: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
