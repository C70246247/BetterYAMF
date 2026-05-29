package com.buildsession.betterYAMF.manager.core.domain.repository

import com.buildsession.betterYAMF.common.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface IReyamfRepository {
    fun insertAppInfo(appInfo: AppInfo)

    fun getAppInfoList(): Flow<List<AppInfo>>

    fun deleteAppInfo(appInfo: AppInfo)
}
