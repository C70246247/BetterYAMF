package com.buildsession.betterYAMF.manager.core.data.source.local.room.entity

import android.content.pm.ActivityInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sidebarApp")
data class AppInfoEntity(
    @PrimaryKey
    val activityInfo: ActivityInfo,
    val userId: Int,
    val userName: String
)

