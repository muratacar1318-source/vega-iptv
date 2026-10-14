# ============================================================================
# VEGA IPTV - R8 / PROGUARD YAPILANDIRMASI
# ============================================================================

# Genel Nitelikler ve Açıklamalar
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-keepclassmembers enum * { *; }

# ----------------------------------------------------------------------------
# 1. GSON VE VERİ MODELLERİ (JSON Serileştirme Koruması)
# ----------------------------------------------------------------------------
# Xtream Codes API JSON yanıtlarının bozulmaması için tüm modelleri koru
-keep class com.tivimax.player.data.model.** { *; }
-keepclassmembers class com.tivimax.player.data.model.** { *; }

# @SerializedName ile işaretlenmiş tüm alanları sakla
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson TypeToken alt sınıflarını koru
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }
-dontwarn com.google.gson.**

# ----------------------------------------------------------------------------
# 2. ANDROIDX MEDIA3 / EXOPLAYER (Akış & Kodek Koruması)
# ----------------------------------------------------------------------------
# Media3, donanım kodekleri ve format ayırıcıları için yoğun reflection kullanır
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# TS, MKV, MP4 Format Ayırıcıları ve Kodek Seçicileri
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.exoplayer.mediacodec.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.common.** { *; }

# Jellyfin Media3 FFmpeg Decoder (AC3, EAC3, DTS)
-keep class org.jellyfin.media3.** { *; }
-keepclassmembers class org.jellyfin.media3.** { *; }
-dontwarn org.jellyfin.media3.**

# ----------------------------------------------------------------------------
# 3. OKHTTP & OKIO & GÜVENLİK (Ağ ve Failover Koruması)
# ----------------------------------------------------------------------------
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Ağ, Güvenlik ve Gizleme sınıflarını koru
-keep class com.tivimax.player.data.security.** { *; }
-keepclassmembers class com.tivimax.player.data.security.** { *; }
-keep class com.tivimax.player.data.network.** { *; }
-keepclassmembers class com.tivimax.player.data.network.** { *; }
-keep class com.tivimax.player.data.config.** { *; }
-keepclassmembers class com.tivimax.player.data.config.** { *; }

# ----------------------------------------------------------------------------
# 4. KOTLIN COROUTINES & JETPACK COMPOSE
# ----------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ----------------------------------------------------------------------------
# 5. ROOM DATABASE & SQLITE & PAGING
# ----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep class com.tivimax.player.data.local.** { *; }
-keepclassmembers class com.tivimax.player.data.local.** { *; }
-dontwarn androidx.room.paging.**
-dontwarn androidx.paging.**
