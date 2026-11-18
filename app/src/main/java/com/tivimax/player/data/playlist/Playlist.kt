package com.tivimax.player.data.playlist

import com.google.gson.annotations.SerializedName

// JSON anahtarları sabittir (G-048): yazarken gerçek ad kullanılır; v1.5.1 ve öncesinde R8'in
// kısalttığı adlar (a..i) okurken kabul edilir, böylece mevcut kayıtlı listeler kaybolmaz.
data class Playlist(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String,
    @SerializedName(value = "name", alternate = ["b"])
    val name: String,
    @SerializedName(value = "serverUrl", alternate = ["c"])
    val serverUrl: String,
    @SerializedName(value = "username", alternate = ["d"])
    val username: String,
    @SerializedName(value = "password", alternate = ["e"])
    val password: String,
    @SerializedName(value = "dbName", alternate = ["f"])
    val dbName: String,
    @SerializedName(value = "createdAt", alternate = ["g"])
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName(value = "isSynced", alternate = ["h"])
    val isSynced: Boolean = false,
    @SerializedName(value = "lastSyncAt", alternate = ["i"])
    val lastSyncAt: String = ""
)
