package com.buildsession.betterYAMF.manager.core.data.source.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.buildsession.betterYAMF.manager.core.data.source.local.room.entity.AppInfoEntity

@Dao
interface AppInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAppInfo(appInfo: AppInfoEntity)

    @Query("SELECT * FROM sidebarApp")
    fun getAppInfoList(): List<AppInfoEntity>

    @Delete
    fun deleteAppInfo(appInfo: AppInfoEntity)

}
