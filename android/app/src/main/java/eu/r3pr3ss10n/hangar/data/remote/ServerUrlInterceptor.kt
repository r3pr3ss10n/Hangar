package eu.r3pr3ss10n.hangar.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ServerUrlInterceptor rewrites each request's scheme/host/port to the origin
 * held by [ServerUrlProvider], preserving the request's path and query. Retrofit
 * is configured with a throwaway placeholder base URL; this interceptor makes
 * the real target dynamic. A request issued before any server is connected fails
 * fast with an IOException rather than hitting the placeholder.
 */
@Singleton
class ServerUrlInterceptor @Inject constructor(
    private val provider: ServerUrlProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = provider.baseUrl
            ?: throw IOException("No Hangar server connected")

        val request = chain.request()
        val newUrl = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
