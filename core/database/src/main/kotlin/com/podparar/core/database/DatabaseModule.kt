package com.podparar.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PodPararDatabase =
        Room.databaseBuilder(context, PodPararDatabase::class.java, PodPararDatabase.NAME).build()

    @Provides
    @Singleton
    fun providePodcastStore(db: PodPararDatabase): PodcastStore = RoomPodcastStore(db)
}
