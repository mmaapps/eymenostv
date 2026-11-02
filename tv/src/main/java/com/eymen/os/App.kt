package com.eymen.os

import android.app.Application
import android.util.Log

/** Çökme olursa logcat'e "EYMENOS" etiketiyle yazar; telefondan teşhiste görünür. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("EYMENOS", "COKME, is parcacigi=${t.name}", e)
            prev?.uncaughtException(t, e)
        }
    }
}
