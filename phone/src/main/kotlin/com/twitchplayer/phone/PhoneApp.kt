package com.twitchplayer.phone

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import com.twitchplayer.phone.di.DebugModules
import com.twitchplayer.phone.di.phoneModules
import com.twitchplayer.tv.core.data.repository.SessionRepository
import okhttp3.OkHttpClient
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
            add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, IMAGE_MEMORY_FRACTION).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images").toOkioPath()).maxSizeBytes(IMAGE_DISK_BYTES).build() }
        .allowHardware(true)
        // A picture that arrives late fades in over the placeholder; one from the cache is drawn at once.
        .crossfade(true)
        .build()

    /**
     * Twitch's image CDN does not always send cache headers that let a client keep a picture, so every launch downloaded the same thumbnails
     * and category art again. Image URLs are immutable per size: whatever the CDN says, the picture is kept for a week (the 64 MB disk
     * cache bounds the space).
     */
    private val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.isSuccessful) response.newBuilder().removeHeader("Pragma").header("Cache-Control", "public, max-age=$IMAGE_MAX_AGE_S").build() else response
            }
            .build()
    }

    private companion object {
        const val IMAGE_MAX_AGE_S = 7 * 24 * 60 * 60
        const val IMAGE_MEMORY_FRACTION = 0.20
        const val IMAGE_DISK_BYTES = 64L * 1024 * 1024
    }
}
