package com.eymen.os

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import java.io.File
import java.security.MessageDigest

object IkonOnbellegi {
    private val bellek = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun surum(ctx: Context, pkg: String): Long = try {
        val info = ctx.packageManager.getPackageInfo(pkg, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (_: Exception) {
        0L
    }

    private fun oku(anahtar: String, dosya: File): Bitmap? {
        bellek.get(anahtar)?.let { return it }
        return try {
            if (!dosya.isFile) null else BitmapFactory.decodeFile(dosya.path)?.also {
                bellek.put(anahtar, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun yaz(anahtar: String, dosya: File, bitmap: Bitmap?) {
        if (bitmap == null) return
        bellek.put(anahtar, bitmap)
        try {
            if (!dosya.parentFile.exists()) dosya.parentFile.mkdirs()
            val gecici = File(dosya.parentFile, dosya.name + ".tmp")
            gecici.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!gecici.renameTo(dosya)) gecici.delete()
        } catch (_: Exception) {
        }
    }

    /** Loads cached app artwork, invalidated by package version. */
    fun graphics(ctx: Context, pkg: String, enabled: Boolean): Pair<Bitmap?, Bitmap?> {
        if (!enabled) return Apps.graphics(ctx, pkg)
        val ver = surum(ctx, pkg)
        val klasor = File(ctx.cacheDir, "ikonlar")
        val onEk = "${pkg}_"
        try {
            klasor.listFiles()?.filter { it.name.startsWith(onEk) &&
                !it.name.startsWith("${pkg}_${ver}_") }?.forEach { it.delete() }
        } catch (_: Exception) {
        }
        val bannerFile = File(klasor, "${pkg}_${ver}_banner.png")
        val iconFile = File(klasor, "${pkg}_${ver}_icon.png")
        val bannerKey = "banner:${pkg}:${ver}"
        val iconKey = "icon:${pkg}:${ver}"
        var banner = oku(bannerKey, bannerFile)
        var icon = oku(iconKey, iconFile)
        if (banner == null || icon == null) {
            val (yeniBanner, yeniIcon) = Apps.graphics(ctx, pkg)
            if (banner == null) {
                banner = yeniBanner
                yaz(bannerKey, bannerFile, yeniBanner)
            }
            if (icon == null) {
                icon = yeniIcon
                yaz(iconKey, iconFile, yeniIcon)
            }
        }
        return banner to icon
    }

    /** Reads a channel logo from memory or the SHA-1 keyed disk cache. */
    fun logo(ctx: Context, ch: Ch, enabled: Boolean): Bitmap? {
        if (!enabled) return Logos.load(ctx, ch)
        val girdi = "${ch.key}|${ch.logoRev}|${ch.logoUrl.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(girdi.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val anahtar = "logo:${digest}"
        val dosya = File(File(ctx.cacheDir, "logolar"), "${digest}.png")
        val mevcut = oku(anahtar, dosya)
        if (mevcut != null) return mevcut
        val bitmap = Logos.load(ctx, ch)
        yaz(anahtar, dosya, bitmap)
        return bitmap
    }
}
