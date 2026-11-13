package com.tivimax.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tivimax.player.data.local.dao.*
import com.tivimax.player.data.local.entity.*

@Database(
    entities = [
        LiveCategoryEntity::class,
        VodCategoryEntity::class,
        SeriesCategoryEntity::class,
        LiveStreamEntity::class,
        VodStreamEntity::class,
        SeriesStreamEntity::class,
        EpgProgramEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun liveDao(): LiveDao
    abstract fun vodDao(): VodDao
    abstract fun seriesDao(): SeriesDao
    abstract fun epgDao(): EpgDao

    companion object {
        const val DEFAULT_DB_NAME = "vega_iptv.db"

        @Volatile
        private var instance: AppDatabase? = null

        @Volatile
        private var currentDbName: String = DEFAULT_DB_NAME

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: open(context, currentDbName)
            }
        }

        fun open(context: Context, dbName: String): AppDatabase {
            return synchronized(this) {
                if (instance != null && currentDbName == dbName && instance?.isOpen == true) {
                    return instance!!
                }
                close()
                currentDbName = dbName
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // G-060: 2 → 3 açık ve YIKICI OLMAYAN geçiştir (kullanıcının listesi yeniden indirilmez).
                .addMigrations(AppDatabaseMigrations.MIGRATION_2_3)
                // Yalnızca bilinmeyen eski sürümler için son çare
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
            }
        }

        fun close() {
            synchronized(this) {
                try {
                    if (instance?.isOpen == true) {
                        instance?.close()
                    }
                } catch (e: Exception) {
                    logSilentFailure("Veritabanı kapatılırken hata", e)
                }
                instance = null
            }
        }

        fun getCurrentDbName(): String = currentDbName
    }
}

/**
 * G-060: Room geçişleri. SQL dizgileri elle yazılmadı: `app/schemas/.../3.json` içindeki `createSql`
 * değerlerinden birebir alındı (TABLE_NAME yer tutucusu tablo adıyla değiştirilmiş hâliyle).
 * `AppDatabaseMigrationSqlTest` bu dizgileri 3.json ile karşılaştırır.
 */
object AppDatabaseMigrations {
    const val CREATE_EPG_PROGRAMS =
        "CREATE TABLE IF NOT EXISTS `epg_programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `channelEpgId` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT)"
    const val CREATE_EPG_PROGRAMS_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_epg_programs_channelEpgId_startMs` ON `epg_programs` (`channelEpgId`, `startMs`)"
    const val ADD_LIVE_STREAMS_TV_ARCHIVE_DURATION =
        "ALTER TABLE `live_streams` ADD COLUMN `tvArchiveDuration` INTEGER NOT NULL DEFAULT 0"

    /** 2 → 3: toplu program rehberi tablosu + geri izleme süresi sütunu (veri silinmez). */
    val MIGRATION_2_3_SQL: List<String> = listOf(
        CREATE_EPG_PROGRAMS,
        CREATE_EPG_PROGRAMS_INDEX,
        ADD_LIVE_STREAMS_TV_ARCHIVE_DURATION
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
        }
    }
}

/** G-057: Yutulan hatayı kaydeder. Mesajda adres/kimlik bilgisi olmasın diye yalnızca hata türü yazılır. */
private fun logSilentFailure(what: String, e: Throwable) {
    try {
        android.util.Log.w("AppDatabase", "$what (${e.javaClass.simpleName})")
    } catch (_: Throwable) {
        // sessiz: JVM birim test ortamında Android Log mock'lu olmayabilir
    }
}
