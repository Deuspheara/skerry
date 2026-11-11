package com.twitchplayer.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.di.DebugModules
import com.twitchplayer.tv.core.di.appPlatformConfig
import com.twitchplayer.tv.core.di.productionModules
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TwitchPlayerApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TwitchPlayerApp)
            modules(productionModules(appPlatformConfig()) + DebugModules.overrides(this@TwitchPlayerApp))
        }
        get<SessionRepository>().start()
    }

    /** Images: bounded memory cache, 64 MB disk cache, hardware bitmaps; decoded at the requested size. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            DebugModules.registerImageComponents(this)
            add(OkHttpNetworkFetcherFactory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images").toOkioPath()).maxSizeBytes(64L * 1024 * 1024).build() }
        .allowHardware(true)
        .build()
}
