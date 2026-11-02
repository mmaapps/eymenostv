package com.eymen.os

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

enum class Screen { HOME, SETUP }

class AppEntry(val pkg: String, val label: String) {
    var banner by mutableStateOf<ImageBitmap?>(null)
    var icon by mutableStateOf<ImageBitmap?>(null)
    var requested = false
}

class Prog(val title: String, val start: Long, val end: Long) {
    fun progress(now: Long): Float =
        if (end > start) ((now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f) else 0f
}

class Ch(
    val key: String,
    val number: String,
    val name: String,
    val isWeb: Boolean,
    val tvId: Long,
    val inputId: String?,
    val url: String?,
    val logoUrl: String?,
    val fav: Boolean
) {
    var logo by mutableStateOf<ImageBitmap?>(null)
    var logoRequested = false
    var now by mutableStateOf<Prog?>(null)
    var next by mutableStateOf<Prog?>(null)
    var epgAt = 0L
}

class InputEntry(val id: String, val label: String)

class SetupItem(val label: String, val value: String? = null, val action: (() -> Unit)? = null)

object Apps {
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val map = LinkedHashMap<String, AppEntry>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (cat in categories) {
            try {
                for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    val p = ri.activityInfo?.packageName ?: continue
                    if (p == ctx.packageName || map.containsKey(p)) continue
                    map[p] = AppEntry(p, ri.loadLabel(pm).toString())
                }
            } catch (e: Exception) {
            }
        }
        return map.values.toList()
    }

    /** Returns (banner, icon). Leanback apps usually have a colourful 16:9 banner. */
    fun graphics(ctx: Context, pkg: String): Pair<Bitmap?, Bitmap?> {
        val pm = ctx.packageManager
        var banner: Bitmap? = null
        var icon: Bitmap? = null
        try {
            val d = pm.getApplicationBanner(pkg)
            if (d != null) banner = d.toBitmap(320, 180)
        } catch (e: Exception) {
        }
        if (banner == null) {
            try {
                val i = pm.getLeanbackLaunchIntentForPackage(pkg)
                if (i != null) {
                    val d = pm.getActivityBanner(i)
                    if (d != null) banner = d.toBitmap(320, 180)
                }
            } catch (e: Exception) {
            }
        }
        try {
            icon = pm.getApplicationIcon(pkg).toBitmap(160, 160)
        } catch (e: Exception) {
        }
        return Pair(banner, icon)
    }

    fun launchIntent(ctx: Context, pkg: String): Intent? = try {
        ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: ctx.packageManager.getLaunchIntentForPackage(pkg)
    } catch (e: Exception) {
        null
    }
}

object Channels {
    const val EYMEN_PKG = "com.eymen.uydukontrol"
    private const val DTVKIT = "com.droidlogic.dtvkit.inputsource/"
    private val SNAPSHOT: Uri = Uri.parse("content://com.eymen.uydukontrol.state/snapshot")

    class Snapshot(val channels: List<Ch>, val last: String?)

    /** Channel list and last watched channel shared by EYMEN Uydu 2.2+. */
    fun fromEymen(ctx: Context): Snapshot? = try {
        ctx.contentResolver.query(SNAPSHOT, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) parse(c.getString(0) ?: "{}") else null
        }
    } catch (e: Exception) {
        null
    }

    private fun parse(json: String): Snapshot? {
        val o = JSONObject(json)
        val arr = o.optJSONArray("channels") ?: return null
        val list = ArrayList<Ch>()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            val key = j.optString("key", "")
            if (key.isEmpty()) continue
            list.add(
                Ch(
                    key = key,
                    number = j.optString("number", ""),
                    name = j.optString("name", key),
                    isWeb = j.optString("source", "TV") == "WEB",
                    tvId = j.optLong("tvId", -1L),
                    inputId = j.optString("inputId", "").ifEmpty { null },
                    url = j.optString("url", "").ifEmpty { null },
                    logoUrl = j.optString("logo", "").ifEmpty { null },
                    fav = j.optBoolean("fav", false)
                )
            )
        }
        if (list.isEmpty()) return null
        return Snapshot(list, o.optString("last", "").ifEmpty { null })
    }

    /** Fallback when EYMEN Uydu has not shared a list yet: the TV's own satellite channels. */
    fun fromTv(ctx: Context): List<Ch> {
        val raw = ArrayList<Ch>()
        try {
            ctx.contentResolver.query(TvContract.Channels.CONTENT_URI, null, null, null, null)?.use { c ->
                val iId = c.getColumnIndex("_id")
                val iNum = c.getColumnIndex("display_number")
                val iName = c.getColumnIndex("display_name")
                val iInput = c.getColumnIndex("input_id")
                while (c.moveToNext()) {
                    if (iId < 0) break
                    val input = if (iInput >= 0) c.getString(iInput) else null
                    if (input.isNullOrBlank()) continue
                    val id = c.getLong(iId)
                    val num = (if (iNum >= 0) c.getString(iNum) else null) ?: ""
                    val name = (if (iName >= 0) c.getString(iName) else null) ?: num
                    raw.add(Ch(id.toString(), num, name, false, id, input, null, null, false))
                }
            }
        } catch (e: Exception) {
        }
        val dtv = raw.any { it.inputId?.startsWith(DTVKIT) == true }
        return raw.filter { !dtv || it.inputId?.startsWith(DTVKIT) == true }
            .sortedWith(compareBy<Ch>({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }))
    }

    fun programs(ctx: Context, tvId: Long, from: Long, to: Long): List<Prog> {
        val out = ArrayList<Prog>()
        try {
            val uri = TvContract.buildProgramsUriForChannel(tvId, from, to)
            val projection = arrayOf("title", "start_time_utc_millis", "end_time_utc_millis")
            ctx.contentResolver.query(uri, projection, null, null, "start_time_utc_millis ASC")?.use { c ->
                while (c.moveToNext()) {
                    val s = c.getLong(1)
                    val e = c.getLong(2)
                    if (e > s) out.add(Prog(c.getString(0) ?: "Program", s, e))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun inputs(ctx: Context): List<InputEntry> = try {
        val m = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val list = m?.tvInputList ?: emptyList()
        list.filter { it.isPassthroughInput && !it.isHidden(ctx) }
            .map { InputEntry(it.id, (it.loadCustomLabel(ctx) ?: it.loadLabel(ctx)).toString()) }
    } catch (e: Exception) {
        emptyList()
    }
}

object Logos {
    private val marks = Regex("\\p{M}+")
    private val quality = Regex("\\b(hd|sd|fhd|uhd|4k)\\b")
    private val nonAlnum = Regex("[^a-z0-9]")
    private val aliases = mapOf(
        "trt1" to "trt-1-hd", "showtv" to "show", "show" to "show", "startv" to "star-tv", "star" to "star-tv",
        "fox" to "now", "foxtv" to "now", "nowtv" to "now", "cnnturk" to "cnn-turk", "ahaber" to "a-haber", "aspor" to "a-spor"
    )
    private var index: Map<String, String>? = null

    fun norm(s: String): String {
        var n = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "")
        n = n.lowercase(Locale.ROOT).replace('ı', 'i').replace(quality, " ")
        return n.replace(nonAlnum, "")
    }

    private fun assetIndex(ctx: Context): Map<String, String> {
        index?.let { return it }
        val map = HashMap<String, String>()
        try {
            for (f in ctx.assets.list("logos") ?: emptyArray()) {
                if (f.endsWith(".png")) map[f.removeSuffix(".png").replace(nonAlnum, "")] = f.removeSuffix(".png")
            }
        } catch (e: Exception) {
        }
        index = map
        return map
    }

    private fun decode(input: InputStream?): Bitmap? {
        if (input == null) return null
        val bytes = input.use { it.readBytes() }
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (o.outWidth / sample > 640 || o.outHeight / sample > 360) sample *= 2
        val d = BitmapFactory.Options()
        d.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, d)
    }

    fun load(ctx: Context, ch: Ch): Bitmap? {
        try {
            val n = norm(ch.name)
            val name = aliases[n] ?: assetIndex(ctx)[n]
            if (name != null) decode(ctx.assets.open("logos/$name.png"))?.let { return it }
        } catch (e: Exception) {
        }
        if (!ch.isWeb && ch.tvId >= 0) {
            try {
                decode(ctx.contentResolver.openInputStream(TvContract.buildChannelLogoUri(ch.tvId)))?.let { return it }
            } catch (e: Exception) {
            }
        }
        val url = ch.logoUrl ?: return null
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "EYMEN-Baslatici/1.0")
            if (conn.responseCode in 200..299) decode(conn.inputStream) else null
        } catch (e: Exception) {
            null
        }
    }
}
