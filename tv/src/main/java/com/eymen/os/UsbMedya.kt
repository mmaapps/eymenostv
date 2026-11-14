package com.eymen.os

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MedyaDosya(val uri: Uri, val baslik: String, val boyut: Long,
                      val tarih: Long, val tur: Int)

object UsbMedya {
    private fun usb(ctx: Context): StorageVolume? = try {
        (ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager)
            .storageVolumes.firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
    } catch (_: Exception) { null }

    fun tara(s: LauncherState, takildi: Boolean = false) {
        val volume = usb(s.ctx)
        val previous = s.usbVar
        s.usbVar = volume != null
        s.usbAd = volume?.getDescription(s.ctx) ?: ""
        if (volume != null && takildi && s.screen == Screen.HOME) {
            s.screen = Screen.USB
            s.usbIndex = 0
            s.usbTur = 0
            s.toast("USB takıldı")
            s.launchTask { yukle(s) }
        } else if (volume == null && previous) {
            s.usbFiles = emptyList()
            s.usbGorsel = null
            if (s.screen == Screen.USB) s.screen = Screen.GELISMIS
            s.toast("USB çıkarıldı")
        }
    }

    suspend fun yukle(s: LauncherState) {
        val volume = usb(s.ctx) ?: run { s.usbFiles = emptyList(); return }
        if (s.ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
            !s.usbIzinDenendi && !s.updates.busy()) {
            s.usbIzinDenendi = true
            val granted = withContext(Dispatchers.IO) {
                YerelAdb.shell(s.ctx, "pm grant com.eymen.os android.permission.READ_EXTERNAL_STORAGE")
            }
            if (granted == null) s.toast("Dosyalara erişim izni yok · TV'de onay ver")
        }
        val files = withContext(Dispatchers.IO) {
            val name = if (Build.VERSION.SDK_INT >= 30) volume.mediaStoreVolumeName
                else volume.uuid?.lowercase(Locale.ROOT)
            if(name.isNullOrBlank()) return@withContext emptyList<MedyaDosya>()
            val all = ArrayList<MedyaDosya>()
            val roots = listOf(
                MediaStore.Video.Media.getContentUri(name),
                MediaStore.Images.Media.getContentUri(name),
                MediaStore.Audio.Media.getContentUri(name),
            )
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED)
            for((kind,root) in roots.withIndex()) {
                try {
                    s.ctx.contentResolver.query(root, projection, null, null,
                        MediaStore.MediaColumns.DATE_MODIFIED + " DESC")?.use { cursor ->
                        while(cursor.moveToNext() && all.count { it.tur == kind } < 500) {
                            val id = cursor.getLong(0)
                            val title = cursor.getString(1) ?: ""
                            val bytes = cursor.getLong(2)
                            val date = cursor.getLong(3) * 1000L
                            all.add(MedyaDosya(android.content.ContentUris.withAppendedId(root,id),
                                title,bytes,date,kind))
                        }
                    }
                } catch (_: Exception) {}
            }
            all
        }
        s.usbFiles = files
        try {
            val directory = if(Build.VERSION.SDK_INT >= 30) volume.directory else null
            if(directory != null) {
                val info = StatFs(directory.path)
                s.usbToplam = info.blockCountLong * info.blockSizeLong
                s.usbDolu = s.usbToplam - info.availableBlocksLong * info.blockSizeLong
            }
        } catch (_: Exception) {}
    }

    private suspend fun resim(s: LauncherState, uri: Uri) {
        val bitmap = withContext(Dispatchers.IO) {
            try {
                val cr = s.ctx.contentResolver
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                var sample = 1
                while(options.outWidth/sample > 1920 || options.outHeight/sample > 1920) sample *= 2
                val values = BitmapFactory.Options().apply { inSampleSize = sample }
                cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, values) }
            } catch (_: Exception) { null }
        }
        s.usbImageBitmap = bitmap?.asImageBitmap()
        s.usbGorsel = uri
    }

    fun key(s: LauncherState, code: Int): Boolean {
        val list = s.usbFiles.filter { it.tur == s.usbTur }
        when(code) {
            KeyEvent.KEYCODE_BACK -> {
                if(s.usbGorsel != null) { s.usbGorsel = null; s.usbImageBitmap = null }
                else s.screen = Screen.GELISMIS
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if(s.usbGorsel != null) {
                    val index = list.indexOfFirst { it.uri == s.usbGorsel }
                    val delta = if(code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    list.getOrNull(index+delta)?.let { s.launchTask { resim(s,it.uri) } }
                } else if(s.usbSekmeOdak) {
                    val d = if(code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    s.usbTur = (s.usbTur + d).coerceIn(0,2)
                    s.usbIndex = 0
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> if(s.usbGorsel == null) {
                if(s.usbIndex == 0) s.usbSekmeOdak = true
                else s.usbIndex--
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if(s.usbGorsel == null) {
                if(s.usbSekmeOdak) s.usbSekmeOdak = false
                else s.usbIndex = (s.usbIndex+1).coerceAtMost((list.size-1).coerceAtLeast(0))
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if(!s.usbSekmeOdak) {
                val file = list.getOrNull(s.usbIndex) ?: return true
                if(file.tur == 1) s.launchTask { resim(s,file.uri) }
                else {
                    val mime = if(file.tur == 0) "video/*" else "audio/*"
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(file.uri,mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        if(intent.resolveActivity(s.ctx.packageManager) != null) s.openMirrorIntent(intent)
                        else s.toast("Bu dosyayı açacak oynatıcı yok")
                    } catch (_: Exception) { s.toast("Bu dosyayı açacak oynatıcı yok") }
                }
            }
            else -> return false
        }
        return true
    }
}

@Composable
fun UsbMedyaScreen(s: LauncherState) {
    val selected = s.usbFiles.filter { it.tur == s.usbTur }
    if(s.usbGorsel != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val img = s.usbImageBitmap
            if(img != null) Image(img,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xCC05080F)).padding(20.dp),
                verticalAlignment=Alignment.CenterVertically) {
                T(selected.firstOrNull { it.uri == s.usbGorsel }?.baslik ?: "",16,C.Fg,700,
                    modifier=Modifier.weight(1f))
                T("◀ ▶ önceki / sonraki · Geri: liste",14,C.Mut,600)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF070B16)).padding(64.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            T("USB medya",30,C.Fg,600,display=true,modifier=Modifier.weight(1f))
            T(s.time(s.now),26,C.Fg,500,display=true)
        }
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Column(Modifier.width(380.dp).fillMaxHeight()
                .panel(Color(0xFF101A33),18,Color(0xFF26324F)).padding(24.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                GelIkon(I_FOLDER_PLAY,64,C.Gold)
                T(if(s.usbVar) s.usbAd else "USB bellek takılı değil",23,C.Fg,700,maxLines=3)
                T(if(s.usbToplam > 0) Sistem.gb(s.usbDolu) + " / " + Sistem.gb(s.usbToplam)
                    else "USB bellekteki dosyaları görüntüle",16,C.Fg2,600,maxLines=2)
                T("USB bellekteki dosyayı TV'nin oynatıcısıyla açar.",14,C.Mut,600,maxLines=3)
            }
            Column(Modifier.weight(1f).fillMaxHeight().panel(Color(0xFF101A33),18,
                Color(0xFF26324F)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    for((i,title) in listOf("Videolar","Fotoğraflar","Müzik").withIndex()) {
                        val focused = s.usbSekmeOdak && s.usbTur == i
                        Box(Modifier.weight(1f).height(42.dp)
                            .panel(if(s.usbTur == i) C.Gold else if(focused) C.Fg else C.Tile,999),
                            contentAlignment=Alignment.Center) {
                            T(title,14,if(s.usbTur == i) C.GoldInk else C.Fg,800)
                        }
                    }
                }
                val start = (s.usbIndex-3).coerceAtLeast(0)
                    .coerceAtMost((selected.size-7).coerceAtLeast(0))
                if(selected.isEmpty()) T("Bu bölümde dosya yok",16,C.Mut,600)
                for(i in start until (start+7).coerceAtMost(selected.size)) {
                    val file = selected[i]
                    val active = i == s.usbIndex && !s.usbSekmeOdak
                    Row(Modifier.fillMaxWidth().height(58.dp)
                        .panel(if(active) C.Fg else C.Tile,12).padding(14.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(file.baslik,16,if(active) C.Navy else C.Fg,800)
                            val stamp = SimpleDateFormat("d MMMM yyyy", Locale("tr","TR"))
                                .format(Date(file.tarih))
                            T(Sistem.mb(file.boyut) + " · " + stamp,12,
                                if(active) C.FocusSub else C.Mut,600)
                        }
                        T("›",20,if(active) C.Navy else C.Fg2,700)
                    }
                }
            }
        }
        T("USB bellekteki dosyayı TV'nin oynatıcısıyla açar.  ·  OK aç  ·  Geri: Gelişmiş",
            13,C.Mut,600)
    }
}
