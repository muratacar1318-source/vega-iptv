package com.tivimax.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tivimax.player.data.local.dao.*
import com.tivimax.player.data.local.entity.*

@Database(
    entities = [
        LiveCategoryEntity::class,
        VodCategoryEntity::class,
        SeriesCategoryEntity::class,
        LiveStreamEntity::class,
        VodStreamEntity::class,
        SeriesStreamEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun liveDao(): LiveDao
    abstract fun vodDao(): VodDao
    abstract fun seriesDao(): SeriesDao

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
                } catch (_: Exception) {}
                instance = null
            }
        }

        fun getCurrentDbName(): String = currentDbName
    }
}
