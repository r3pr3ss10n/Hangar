package eu.r3pr3ss10n.hangar

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Application entry point and root of the Hilt graph. It also supplies Coil's
 * singleton ImageLoader, wiring it to the same OkHttp client the API uses so
 * thumbnail/image requests carry the session cookie and resolve against the
 * connected server via the dynamic base-URL interceptor.
 */
@HiltAndroidApp
class HangarApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .build()
    }
}
