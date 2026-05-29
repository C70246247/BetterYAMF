package com.buildsession.betterYAMF.manager.core.data.source.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.buildsession.betterYAMF.manager.core.data.source.local.room.dao.ActivityInfoConverter
import com.buildsession.betterYAMF.manager.core.data.source.local.room.dao.AppInfoDao
import com.buildsession.betterYAMF.manager.core.data.source.local.room.dao.ComponentNameConverter
import com.buildsession.betterYAMF.manager.core.data.source.local.room.entity.AppInfoEntity

@Database(entities = [
    AppInfoEntity::class,
], version = 4, exportSchema = false)
@TypeConverters(ActivityInfoConverter::class)
abstract class ReyamfDatabase : RoomDatabase() {
    abstract fun  appInfoDao(): AppInfoDao
}
