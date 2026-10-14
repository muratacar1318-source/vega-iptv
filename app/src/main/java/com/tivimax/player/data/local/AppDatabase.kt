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
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun liveDao(): LiveDao
    abstract fun vodDao(): VodDao
    abstract fun seriesDao(): SeriesDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vega_iptv.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
            }
        }
    }
}
