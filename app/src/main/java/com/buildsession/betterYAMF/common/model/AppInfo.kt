package com.buildsession.betterYAMF.common.model

import android.content.pm.ActivityInfo
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppInfo(
    val activityInfo: ActivityInfo,
    val userId: Int,
    val userName: String
) : Parcelable
