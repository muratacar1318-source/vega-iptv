package com.tivimax.player

import android.app.Application
import android.database.CursorWindow
import android.util.Log

class VegaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // IPTV listeleri (binlerce kanal, film ve EPG) için SQLite CursorWindow boyutunu 50MB'a genişlet
            val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 50 * 1024 * 1024) // 50 MB
        } catch (e: Exception) {
            Log.w("VegaApp", "CursorWindow size could not be set via reflection", e)
        }
    }
}
