package com.tivimax.player.data.update

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tivimax.player.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long
)

class AutoUpdateManager(private val context: Context) {

    private val preferences = com.tivimax.player.data.preferences.UserPreferences(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    companion object {
        const val GITHUB_REPO_URL = "https://github.com/muratacar1318-source/vega-iptv"
        const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/muratacar1318-source/vega-iptv/releases/latest"

        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }

    fun checkForUpdate(isManual: Boolean = false, scope: CoroutineScope) {
        if (_isChecking.value) return

        scope.launch(Dispatchers.IO) {
            _isChecking.value = true
            _errorMessage.value = null

            try {
                val request = Request.Builder()
                    .url(GITHUB_LATEST_RELEASE_API)
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("User-Agent", "VegaIPTV-App")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    if (isManual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Güncelleme kontrolü başarısız (HTTP $code)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    _isChecking.value = false
                    return@launch
                }

                val responseBody = response.body?.string() ?: ""
                val json = JSONObject(responseBody)

                val tagName = json.optString("tag_name", "")
                val releaseNotes = json.optString("body", "Yeni özellikler ve hata düzeltmeleri içerir.")
                val assets = json.optJSONArray("assets")

                var apkDownloadUrl: String? = null
                var apkName = "vega-iptv-$tagName.apk"
                var apkSize: Long = 0L

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME

                if (apkDownloadUrl != null && isNewerVersion(tagName, currentVersion)) {
                    if (!isManual && preferences.isUpdateSnoozed(tagName)) {
                        // Kullanıcı bu sürümü erteledi; otomatik açılışta rahatsız etme
                        return@launch
                    }

                    val info = UpdateInfo(
                        tagName = tagName,
                        releaseNotes = releaseNotes,
                        apkUrl = apkDownloadUrl,
                        apkName = apkName,
                        apkSize = apkSize
                    )
                    withContext(Dispatchers.Main) {
                        _updateInfo.value = info
                    }
                } else if (isManual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Uygulamanız güncel (v$currentVersion)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Güncelleme hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun downloadAndInstall(info: UpdateInfo, scope: CoroutineScope) {
        if (_downloadProgress.value != null && _downloadProgress.value!! in 0..99) return

        scope.launch(Dispatchers.IO) {
            _downloadProgress.value = 0
            _errorMessage.value = null

            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .addHeader("User-Agent", "VegaIPTV-App")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("İndirme başarısız (HTTP ${response.code})")
                }

                val body = response.body ?: throw Exception("Boş içerik alındı")
                val contentLength = body.contentLength().let { if (it > 0) it else info.apkSize }

                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val apkFile = File(downloadDir, info.apkName.ifBlank { "vega-iptv-update.apk" })

                if (apkFile.exists()) {
                    apkFile.delete()
                }

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 99)
                                _downloadProgress.value = progress
                            }
                        }
                        output.flush()
                    }
                }

                _downloadProgress.value = 100

                // İndirme bitti, Android paket yükleyicisini başlat
                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "İndirme sırasında hata oluştu"
                _downloadProgress.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Güncelleme indirilemedi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Yükleyici başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
        _downloadProgress.value = null
        _errorMessage.value = null
    }

    fun snoozeUpdate(info: UpdateInfo, hours: Int = 24, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            preferences.snoozeUpdate(info.tagName, hours)
            withContext(Dispatchers.Main) {
                dismissUpdate()
                Toast.makeText(context, "Güncelleme $hours saat ertelendi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
