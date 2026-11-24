package net.igng.bbs

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.model.SiteEndpoints

class BbsApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: BbsAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = BbsAppContainer(
            context = this,
            endpoints = SiteEndpoints(
                apiBaseUrl = BuildConfig.IGNG_API_BASE_URL,
                ssoBaseUrl = BuildConfig.IGNG_SSO_BASE_URL,
                bbsWebBaseUrl = BuildConfig.IGNG_BBS_BASE_URL,
            ),
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
        )
        container.warmCoreDiskCaches()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::container.isInitialized && level >= TRIM_MEMORY_BACKGROUND) {
            container.trimMemory(aggressive = level > TRIM_MEMORY_BACKGROUND)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}
