package com.eymen.os

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YerelAdb {
    private val izinliKomutlar = setOf(
        "input keyevent 223",
        "pm trim-caches 999G",
        "appops set com.eymen.os GET_USAGE_STATS allow",
        "pm grant com.eymen.os android.permission.READ_EXTERNAL_STORAGE",
        "cmd package compile -m speed-profile -f com.eymen.os",
    )

    /** Runs one shell command on the TV's own adb; null when adb is closed or refused. */
    suspend fun shell(ctx: Context, cmd: String, timeoutMs: Int = 15000): String? =
        withContext(Dispatchers.IO) {
            if (cmd !in izinliKomutlar) return@withContext null
            val priv = File(ctx.filesDir, "adbkey")
            val pub = File(ctx.filesDir, "adbkey.pub")
            if (!priv.isFile || !pub.isFile) return@withContext null
            var connection: Dadb? = null
            try {
                connection = Dadb.create(
                    "127.0.0.1", 5555, AdbKeyPair.read(priv, pub), 5000, timeoutMs, true
                )
                connection.shell(cmd).allOutput
            } catch (_: Exception) {
                null
            } finally {
                try {
                    connection?.close()
                } catch (_: Exception) {
                }
            }
        }
}
