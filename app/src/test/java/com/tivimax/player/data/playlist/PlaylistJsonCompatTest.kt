package com.tivimax.player.data.playlist

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tivimax.player.data.preferences.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G-048: DataStore'daki JSON kaydı sürümler arasında okunabilir kalmalı.
 * v1.5.1 release derlemesinde (R8) alanlar kısa adlarla (a, b, c…) yazılmıştı; bunlar hâlâ okunmalı.
 */
class PlaylistJsonCompatTest {
    private val gson = Gson()
    private val playlistListType = object : TypeToken<List<Playlist>>() {}.type
    private val progressMapType = object : TypeToken<Map<String, WatchProgress>>() {}.type

    @Test
    fun `eski kisa anahtarlarla yazilmis liste okunur`() {
        // v1.5.1 mapping.txt: id=a, name=b, serverUrl=c, username=d, password=e, dbName=f,
        // createdAt=g, isSynced=h, lastSyncAt=i
        val eski = """[{"a":"p1","b":"Evim","c":"http://sunucu.example:8080","d":"kullanici","e":"sifre",
            |"f":"vega_pl_p1","g":1700000000000,"h":true,"i":"2026-11-01 10:00"}]""".trimMargin()
        val liste: List<Playlist> = gson.fromJson(eski, playlistListType)
        assertEquals(1, liste.size)
        val p = liste[0]
        assertEquals("p1", p.id)
        assertEquals("Evim", p.name)
        assertEquals("http://sunucu.example:8080", p.serverUrl)
        assertEquals("kullanici", p.username)
        assertEquals("sifre", p.password)
        assertEquals("vega_pl_p1", p.dbName)
        assertEquals(1700000000000L, p.createdAt)
        assertTrue(p.isSynced)
        assertEquals("2026-11-01 10:00", p.lastSyncAt)
    }

    @Test
    fun `yeni bicim gercek anahtarlarla yazilir ve geri okunur`() {
        val p = Playlist("p2", "Ofis", "http://s.example", "u", "pw", "vega_pl_p2", 42L, true, "dün")
        val json = gson.toJson(listOf(p))
        assertTrue(json.contains("\"password\""))
        assertTrue(json.contains("\"serverUrl\""))
        assertFalse(json.contains("\"e\":"))
        val geri: List<Playlist> = gson.fromJson(json, playlistListType)
        assertEquals(listOf(p), geri)
    }

    @Test
    fun `eski kisa anahtarlarla yazilmis izleme ilerlemesi okunur`() {
        // v1.5.1 mapping.txt: streamId=a, positionMs=b, durationMs=c, timestamp=d
        val eski = """{"12":{"a":12,"b":60000,"c":7200000,"d":1700000000000}}"""
        val harita: Map<String, WatchProgress> = gson.fromJson(eski, progressMapType)
        val w = harita.getValue("12")
        assertEquals(12, w.streamId)
        assertEquals(60000L, w.positionMs)
        assertEquals(7200000L, w.durationMs)
        assertEquals(1700000000000L, w.timestamp)
    }

    @Test
    fun `yeni bicim izleme ilerlemesi gercek anahtarlarla yazilir ve geri okunur`() {
        val w = WatchProgress(streamId = 7, positionMs = 1000L, durationMs = 5000L, timestamp = 99L)
        val json = gson.toJson(mapOf("7" to w))
        assertTrue(json.contains("\"streamId\""))
        assertTrue(json.contains("\"positionMs\""))
        val geri: Map<String, WatchProgress> = gson.fromJson(json, progressMapType)
        assertEquals(w, geri.getValue("7"))
    }
}
