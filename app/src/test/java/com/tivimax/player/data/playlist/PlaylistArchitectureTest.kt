package com.tivimax.player.data.playlist

import com.tivimax.player.data.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistArchitectureTest {

    @Test
    fun `keyFor default returns base key unchanged`() {
        assertEquals("fav_live", UserPreferences.keyFor("fav_live", "default"))
        assertEquals("fav_vod", UserPreferences.keyFor("fav_vod", "default"))
        assertEquals("fav_series", UserPreferences.keyFor("fav_series", "default"))
        assertEquals("watch_progress_json", UserPreferences.keyFor("watch_progress_json", "default"))
        assertEquals("pinned_categories", UserPreferences.keyFor("pinned_categories", "default"))
        assertEquals("recent_live_channels_json", UserPreferences.keyFor("recent_live_channels_json", "default"))
        assertEquals("recent_searches_json", UserPreferences.keyFor("recent_searches_json", "default"))
        assertEquals("last_played_stream_id", UserPreferences.keyFor("last_played_stream_id", "default"))
        assertEquals("is_playlist_synced", UserPreferences.keyFor("is_playlist_synced", "default"))
        assertEquals("last_sync_timestamp", UserPreferences.keyFor("last_sync_timestamp", "default"))
        assertEquals("exp_date", UserPreferences.keyFor("exp_date", "default"))
        assertEquals("max_connections", UserPreferences.keyFor("max_connections", "default"))
        assertEquals("active_connections", UserPreferences.keyFor("active_connections", "default"))
        assertEquals("status", UserPreferences.keyFor("status", "default"))
        assertEquals("is_trial", UserPreferences.keyFor("is_trial", "default"))
        assertEquals("created_at", UserPreferences.keyFor("created_at", "default"))
    }

    @Test
    fun `keyFor blank playlistId returns base key`() {
        assertEquals("fav_live", UserPreferences.keyFor("fav_live", ""))
        assertEquals("watch_progress_json", UserPreferences.keyFor("watch_progress_json", "   "))
    }

    @Test
    fun `keyFor other playlists returns key with playlistId appended`() {
        assertEquals("fav_live__pl_1", UserPreferences.keyFor("fav_live", "pl_1"))
        assertEquals("watch_progress_json__custom_99", UserPreferences.keyFor("watch_progress_json", "custom_99"))
        assertEquals("recent_searches_json__abc-123", UserPreferences.keyFor("recent_searches_json", "abc-123"))
        assertEquals("exp_date__server2", UserPreferences.keyFor("exp_date", "server2"))
    }

    @Test
    fun `dbNameFor returns vega_iptv_db for default and prefixed db for others`() {
        assertEquals("vega_iptv.db", PlaylistRepository.dbNameFor("default"))
        assertEquals("vega_pl_123.db", PlaylistRepository.dbNameFor("123"))
        assertEquals("vega_pl_custom-server.db", PlaylistRepository.dbNameFor("custom-server"))
    }

    @Test
    fun `migration creates default playlist when user is logged in and no playlists exist`() {
        val migrated = PlaylistRepository.computeMigration(
            hasExistingPlaylists = false,
            isLoggedIn = true,
            serverUrl = "http://iptv.example.com:8080",
            username = "testuser",
            password = "testpassword",
            isSynced = true,
            lastSyncAt = "18.10.2026 14:00"
        )

        assertNotNull(migrated)
        migrated!!
        assertEquals("default", migrated.id)
        assertEquals("Ana liste", migrated.name)
        assertEquals("http://iptv.example.com:8080", migrated.serverUrl)
        assertEquals("testuser", migrated.username)
        assertEquals("testpassword", migrated.password)
        assertEquals("vega_iptv.db", migrated.dbName)
        assertEquals(true, migrated.isSynced)
        assertEquals("18.10.2026 14:00", migrated.lastSyncAt)
    }

    @Test
    fun `migration returns null when user is not logged in`() {
        val migrated = PlaylistRepository.computeMigration(
            hasExistingPlaylists = false,
            isLoggedIn = false,
            serverUrl = "",
            username = "",
            password = "",
            isSynced = false,
            lastSyncAt = ""
        )
        assertNull(migrated)
    }

    @Test
    fun `migration returns null when playlists already exist`() {
        val migrated = PlaylistRepository.computeMigration(
            hasExistingPlaylists = true,
            isLoggedIn = true,
            serverUrl = "http://iptv.example.com:8080",
            username = "testuser",
            password = "testpassword",
            isSynced = true,
            lastSyncAt = ""
        )
        assertNull(migrated)
    }

    @Test
    fun `selectNextActive keeps current active if non-active playlist is removed`() {
        val remaining = listOf(
            Playlist("pl_1", "Liste 1", "url1", "u1", "p1", "db1"),
            Playlist("pl_3", "Liste 3", "url3", "u3", "p3", "db3")
        )
        val nextActive = PlaylistRepository.selectNextActive(remaining, removedId = "pl_2", currentActiveId = "pl_1")
        assertEquals("pl_1", nextActive)
    }

    @Test
    fun `selectNextActive chooses first remaining playlist if active playlist is removed`() {
        val remaining = listOf(
            Playlist("pl_2", "Liste 2", "url2", "u2", "p2", "db2"),
            Playlist("pl_3", "Liste 3", "url3", "u3", "p3", "db3")
        )
        val nextActive = PlaylistRepository.selectNextActive(remaining, removedId = "pl_1", currentActiveId = "pl_1")
        assertEquals("pl_2", nextActive)
    }

    @Test
    fun `selectNextActive returns null if last playlist is removed`() {
        val remaining = emptyList<Playlist>()
        val nextActive = PlaylistRepository.selectNextActive(remaining, removedId = "pl_1", currentActiveId = "pl_1")
        assertNull(nextActive)
    }

    @Test
    fun `migrateHttpsToHttp converts matching https urls to http`() {
        val list = listOf(
            Playlist("pl_1", "Liste 1", "https://iptv.example.com", "u1", "p1", "db1"),
            Playlist("pl_2", "Liste 2", "http://custom.example.com", "u2", "p2", "db2"),
            Playlist("pl_3", "Liste 3", "https://iptv.example.com", "u3", "p3", "db3")
        )

        val updated = PlaylistRepository.migrateHttpsToHttp(
            playlists = list,
            httpsUrl = "https://iptv.example.com",
            httpUrl = "http://iptv.example.com"
        )

        assertEquals("http://iptv.example.com", updated[0].serverUrl)
        assertEquals("http://custom.example.com", updated[1].serverUrl)
        assertEquals("http://iptv.example.com", updated[2].serverUrl)
    }

    @Test
    fun `migration converts https serverUrl to default http serverUrl`() {
        val migrated = PlaylistRepository.computeMigration(
            hasExistingPlaylists = false,
            isLoggedIn = true,
            serverUrl = com.tivimax.player.data.config.AppConfig.SERVER_URL_HTTPS,
            username = "testuser",
            password = "testpassword",
            isSynced = true,
            lastSyncAt = "18.10.2026 14:00"
        )

        assertNotNull(migrated)
        assertEquals(com.tivimax.player.data.config.AppConfig.SERVER_URL_HTTP, migrated!!.serverUrl)
    }

    @Test
    fun `playlist copy updates name while preserving other fields`() {
        val original = Playlist("id_1", "Eski Ad", "http://server", "user", "pass", "db_1", 1000L, true, "18.10.2026")
        val updated = original.copy(name = "Yeni Ad")
        assertEquals("Yeni Ad", updated.name)
        assertEquals(original.serverUrl, updated.serverUrl)
        assertEquals(original.username, updated.username)
        assertEquals(original.password, updated.password)
        assertEquals(original.isSynced, updated.isSynced)
    }

    @Test
    fun `playlist credential change invalidates sync state`() {
        val original = Playlist("id_1", "Evdeki liste", "http://server1", "user1", "pass1", "db_1", 1000L, true, "18.10.2026")
        val updated = original.copy(
            serverUrl = "http://server2",
            username = "user2",
            password = "pass2",
            isSynced = false,
            lastSyncAt = ""
        )
        assertEquals(false, updated.isSynced)
        assertEquals("", updated.lastSyncAt)
    }
}
