package com.buildsession.betterYAMF.manager.utils

import com.buildsession.betterYAMF.common.model.AppInfo
import com.buildsession.betterYAMF.manager.core.data.source.local.room.entity.AppInfoEntity

object DataMapper {
    fun appInfoToEntity(appInfo: AppInfo): AppInfoEntity =
        AppInfoEntity(
            appInfo.activityInfo,
            appInfo.userId,
            appInfo.userName
        )

    fun appInfoEntityToModel(appInfoEntity: AppInfoEntity): AppInfo =
        AppInfo(
            appInfoEntity.activityInfo,
            appInfoEntity.userId,
            appInfoEntity.userName
        )
}
