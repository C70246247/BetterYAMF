package com.buildsession.betterYAMF.manager.core.domain.usecase

import com.buildsession.betterYAMF.common.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface ReyamfRepositoryUseCase {
    fun insertAppInfo(appInfo: AppInfo)

    fun getAppInfoList(): Flow<List<AppInfo>>

    fun deleteAppInfo(appInfo: AppInfo)
}
