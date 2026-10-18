package com.tivimax.player.data.playlist

data class Playlist(
    val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val dbName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val lastSyncAt: String = ""
)
