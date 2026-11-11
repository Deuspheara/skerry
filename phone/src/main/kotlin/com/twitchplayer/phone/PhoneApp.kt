package com.twitchplayer.phone

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import com.twitchplayer.phone.di.DebugModules
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.tv.core.data.repository.SessionRepository
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PhoneApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PhoneApp)
            modules(phoneModules() + DebugModules.overrides(this@PhoneApp))
        }
        get<SessionRepository>().start()
    }

    /** Images: bounded memory cache, 64 MB disk cache, hardware bitmaps; decoded at the requested size. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            DebugModules.registerImageComponents(this)
            add(OkHttpNetworkFetcherFactory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, IMAGE_MEMORY_FRACTION).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images").toOkioPath()).maxSizeBytes(IMAGE_DISK_BYTES).build() }
        .allowHardware(true)
        .build()

    private companion object {
        const val IMAGE_MEMORY_FRACTION = 0.20
        const val IMAGE_DISK_BYTES = 64L * 1024 * 1024
    }
}
