package com.tivimax.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tivimax.player.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

class VegaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(6))
            .decoderDispatcher(Dispatchers.Default.limitedParallelism(4))
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
