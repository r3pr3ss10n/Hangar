package eu.r3pr3ss10n.hangar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.r3pr3ss10n.hangar.data.remote.HangarApi
import eu.r3pr3ss10n.hangar.data.remote.PersistentCookieJar
import eu.r3pr3ss10n.hangar.data.remote.ServerUrlInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * NetworkModule provides the singletons of the networking stack: the OkHttp
 * client (with the dynamic server-URL interceptor and persistent cookie jar),
 * the JSON codec, and the Retrofit-backed [HangarApi]. Retrofit's base URL is a
 * throwaway placeholder; the interceptor rewrites the real origin per request.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        serverUrlInterceptor: ServerUrlInterceptor,
        cookieJar: PersistentCookieJar,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(serverUrlInterceptor)
            .addInterceptor(logging)
            // Uploads/downloads stream large bodies; keep read/write generous and
            // disable the overall call timeout for byte routes.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Placeholder; ServerUrlInterceptor overrides scheme/host/port per call.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideHangarApi(retrofit: Retrofit): HangarApi = retrofit.create(HangarApi::class.java)
}
