package com.buildsession.betterYAMF.manager.di

import android.content.Context
import androidx.room.Room
import com.buildsession.betterYAMF.manager.core.data.source.local.room.ReyamfDatabase
import com.buildsession.betterYAMF.manager.core.data.source.local.room.dao.AppInfoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReyamfDatabase {
        return Room.databaseBuilder(
            context,
            ReyamfDatabase::class.java,
            "reyamf.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideAppInfoDao(database: ReyamfDatabase): AppInfoDao = database.appInfoDao()
}
