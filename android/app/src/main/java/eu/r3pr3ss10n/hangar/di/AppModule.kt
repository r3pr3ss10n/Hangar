package eu.r3pr3ss10n.hangar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.r3pr3ss10n.hangar.data.remote.ServerUrlProvider
import eu.r3pr3ss10n.hangar.ui.util.UrlBuilder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideUrlBuilder(serverUrl: ServerUrlProvider): UrlBuilder = UrlBuilder(serverUrl)
}
