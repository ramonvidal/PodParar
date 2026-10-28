package com.podparar.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

internal const val USER_AGENT = "PodParar (Android; https://github.com/ramonvidal/PodParar)"

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    @Provides
    @Singleton
    fun feedSource(client: OkHttpClient): FeedSource = OkHttpFeedSource(client)

    @Provides
    @Singleton
    fun podcastDirectory(client: OkHttpClient): PodcastDirectory = ItunesDirectory(client)

    @Provides
    @Singleton
    fun audioDownloader(client: OkHttpClient): AudioDownloader = OkHttpAudioDownloader(client)
}
